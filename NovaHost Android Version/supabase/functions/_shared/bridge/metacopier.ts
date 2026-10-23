import type { SupabaseClient } from 'https://esm.sh/@supabase/supabase-js@2'
import type {
  AccountSummary, BridgeFailure, BridgeResult, BrokerBridge, ConnectInput, ConnectResult,
  Deal, HealthState, OrderRefusal, OrderRequest, OrderResult, Position, Quote, SymbolSpec,
} from './types.ts'

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * The MetaCopier adapter.
 *
 * Everything in this file is MetaCopier's and nobody else's: its REST paths,
 * its bracketed refusal tokens, its account de-duplication and slot reclaim,
 * its server catalogue. It was lifted out of the nine functions unchanged in
 * behaviour, so that when the last licence has moved to MetaRPC this file can
 * simply be deleted.
 */

const num = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : null)
const str = (v: unknown) => (v == null || v === '' ? null : String(v))

/**
 * MetaCopier reports faults as bracketed codes, and many of them carry
 * parameters: `[ACCOUNT_LIMIT_PER_PROJECT_REACHED_TRIAL|allowed=10|needed=160]`.
 *
 * The first version of this matched `/\[([A-Z0-9_]+)\]/g`, which requires the
 * WHOLE bracket to be a bare code -- so every parameterised fault parsed to
 * an empty code list and fell through to the generic "check the login,
 * password and server". That is how a project sitting at its trial account
 * cap told users their credentials were wrong: four different brokers, ten
 * attempts, all correct, all blamed on the password.
 *
 * Stopping at the first `|` keeps the code and discards the parameters.
 */
const codesIn = (detail: string) =>
  [...detail.matchAll(/\[([A-Z0-9_]+)(?:\|[^\]]*)?\]/g)].map((m) => m[1])

/**
 * Bare bracket tokens, the way the order path has always read them. Kept apart
 * from `codesIn` on purpose: order refusals are never parameterised, and the
 * walk's safety rests on reading them exactly as it did before the move.
 */
const bareCodesIn = (detail: string) =>
  (detail.match(/\[([A-Z0-9_]+)\]/g) ?? []).map((c) => c.slice(1, -1))

/**
 * MetaCopier does not always bracket this. An IFX account answered an order for
 * `US30` with `{"errors":["Symbol not found"]}` -- no `[SYMBOL_...]` token -- so
 * the walk read it as a refusal of the ORDER, stopped after one name and never
 * asked the broker, which lists the Dow as `WS30.ifx_m`.
 */
const UNKNOWN_SYMBOL_PROSE =
  /symbol\s+(is\s+)?(not\s+found|unknown|does\s+not\s+exist)|unknown\s+symbol|invalid\s+symbol|no\s+such\s+symbol/i

/**
 * "Autotrading disabled by server", and the other ways a broker says it.
 *
 * Read in two places, for two jobs. It names the refusal to the user. In the
 * executor's walk, it is a reason to check the NAME first: every time this has
 * been seen live, the order had gone out under a name the broker does not list
 * -- `Gold` on two RCG accounts, bare `XAUUSD` on an IFX book whose gold is
 * `XAUUSD.ifx` -- while the exact listed name filled on the same broker.
 */
const AUTOTRADING_REFUSAL =
  /auto\s*-?\s*trading\s+(is\s+)?disabled|trading\s+(is\s+)?disabled\s+by\s+server|algo(rithmic)?\s+trading\s+(is\s+)?(disabled|not\s+allowed)/i

/** A read failure, in our vocabulary. */
function readFailure(status: number, detail: string): BridgeFailure {
  // A disconnected account and an unreachable API are different problems with
  // different fixes -- one is "reconnect your broker", the other is "try
  // again in a minute" -- so they are not collapsed into one code.
  const disconnected = /NOT_CONNECTED|OFFLINE|DISCONNECT|ACCOUNT_WAS_DELETED/i.test(detail)
  return { ok: false, code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'BROKER_UNREACHABLE', status, detail }
}

function specFrom(info: Record<string, unknown>): SymbolSpec {
  return {
    digits: num(info.digits),
    point: num(info.points),
    baseCurrency: str(info.baseCurrency),
    quoteCurrency: str(info.quoteCurrency),
    contractSize: num(info.lotSize),
    minVolume: num(info.minimalVolume),
    stepVolume: num(info.stepVolume),
    maxVolume: num(info.maximalVolume),
    disabled: info.disabled === true,
  }
}

function positionFrom(p: Record<string, unknown>): Position {
  return {
    id: String(p?.id ?? ''),
    symbol: String(p?.symbol ?? ''),
    orderType: String(p?.orderType ?? ''),
    volume: num(p?.volume),
    openPrice: num(p?.openPrice),
    stopLoss: num(p?.stopLoss),
    takeProfit: num(p?.takeProfit),
    profit: num(p?.profit),
    netProfit: num(p?.netProfit),
    swap: num(p?.swap),
    commission: num(p?.commission),
    openTime: str(p?.openTime),
    magic: str(p?.magicNumber),
    comment: str(p?.comment),
  }
}

function dealFrom(p: Record<string, unknown>): Deal {
  // `dealType` names a ledger row outright, but only when the broker
  // populates it. Anything that is not DealBuy/DealSell is a ledger entry --
  // including `CanceledBuy`/`CanceledSell`, which are orders that never
  // became positions. Empty means the broker did not say.
  const dealType = String(p?.dealType ?? '')
  return {
    ...positionFrom(p),
    kind: !dealType ? null : /^Deal(Buy|Sell)$/.test(dealType) ? 'trade' : 'ledger',
    closePrice: num(p?.closePrice),
    closeTime: str(p?.closeTime),
  }
}

export interface MetaCopierContext {
  /** Service-role client. Connect reads licences to protect live accounts during a reclaim. */
  db: SupabaseClient
}

export class MetaCopierBridge implements BrokerBridge {
  readonly provider = 'metacopier' as const

  constructor(
    private readonly apiKey: string,
    private readonly accountId: string | null,
    private readonly ctx: MetaCopierContext,
  ) {}

  private get headers() {
    return { 'X-API-KEY': this.apiKey, 'Content-Type': 'application/json', 'Accept': 'application/json' }
  }

  private get acct() {
    if (!this.accountId) throw new Error('MetaCopier bridge opened without an account')
    return `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(this.accountId)}`
  }

  /** GET that reports its own failure as data rather than throwing. */
  private async read<T>(url: string, map: (raw: unknown) => T | null, timeoutMs?: number): Promise<BridgeResult<T>> {
    let res: Response
    try {
      res = await fetch(url, {
        headers: this.headers,
        ...(timeoutMs ? { signal: AbortSignal.timeout(timeoutMs) } : {}),
      })
    } catch (e) {
      return { ok: false, code: 'BROKER_UNREACHABLE', status: 0, detail: (e as Error).message }
    }
    if (!res.ok) return readFailure(res.status, await res.text().catch(() => ''))
    const data = map(await res.json().catch(() => null))
    return data === null
      ? { ok: false, code: 'BROKER_UNREACHABLE', status: res.status, detail: 'unexpected response shape' }
      : { ok: true, data }
  }

  // ---- Reads ---------------------------------------------------------------

  accountSummary(timeoutMs?: number): Promise<BridgeResult<AccountSummary>> {
    return this.read(`${this.acct}/information`, (raw) => {
      if (!raw || typeof raw !== 'object') return null
      const info = raw as Record<string, unknown>
      return {
        balance: num(info.balance),
        equity: num(info.equity),
        credit: num(info.credit),
        freeMargin: num(info.freeMargin),
        currency: info.currency ? String(info.currency).toUpperCase().slice(0, 3) : null,
        leverage: num(info.leverage),
        connected: typeof info.connected === 'boolean' ? info.connected : null,
        wrongCredentials: info.wrongCredentials === true,
        investorPassword: info.isInvestorPassword === true,
        tradingDisabled: info.tradingDisabled === true || info.tradeDisabled === true,
      }
    }, timeoutMs)
  }

  /**
   * MetaCopier's symbol payload, reduced to names.
   *
   * The shape is not contractual across brokers -- some entries come back as
   * bare strings, others as objects keyed `name`, `symbol` or `id`. Anything
   * that yields no usable name is dropped rather than guessed at, because a
   * wrong name here becomes a rejected order later.
   */
  symbols(): Promise<BridgeResult<string[]>> {
    return this.read(`${this.acct}/symbols`, (raw) => {
      if (!Array.isArray(raw)) return null
      const out: string[] = []
      for (const entry of raw) {
        let name = ''
        if (typeof entry === 'string') {
          name = entry
        } else if (entry && typeof entry === 'object') {
          const o = entry as Record<string, unknown>
          name = String(o.name ?? o.symbol ?? o.symbolName ?? o.id ?? '')
        }
        name = name.trim()
        if (name && !out.includes(name)) out.push(name)
      }
      return out
    })
  }

  symbolSpec(symbol: string, timeoutMs?: number): Promise<BridgeResult<SymbolSpec>> {
    return this.read(
      `${this.acct}/symbols/${encodeURIComponent(symbol)}`,
      (raw) => (raw && typeof raw === 'object' ? specFrom(raw as Record<string, unknown>) : null),
      timeoutMs,
    )
  }

  async quote(symbol: string): Promise<BridgeResult<Quote>> {
    const r = await this.read(`${this.acct}/quote/${encodeURIComponent(symbol)}`, (raw) => {
      if (!raw || typeof raw !== 'object') return null
      const q = raw as Record<string, unknown>
      return {
        bid: num(q.bid),
        ask: num(q.ask),
        timestamp: str(q.timestamp),
        spec: q.symbolInfo && typeof q.symbolInfo === 'object'
          ? specFrom(q.symbolInfo as Record<string, unknown>)
          : null,
      }
    })
    // A 404, or a refusal naming the symbol, means this book does not carry
    // that name -- the one quote failure where trying another spelling helps.
    if (!r.ok && r.code !== 'ACCOUNT_DISCONNECTED' && (r.status === 404 || /SYMBOL|NOT_FOUND/i.test(r.detail))) {
      return { ...r, code: 'UNKNOWN_SYMBOL' }
    }
    return r
  }

  positions(timeoutMs?: number): Promise<BridgeResult<Position[]>> {
    return this.read(
      `${this.acct}/positions`,
      (raw) => (Array.isArray(raw) ? raw.map((p) => positionFrom(p as Record<string, unknown>)) : null),
      timeoutMs,
    )
  }

  /**
   * `start` and `stop` are REQUIRED query parameters on MetaCopier's history
   * endpoint. Omitting them is refused outright, which is how broker-history
   * once reported "could not measure" on every call.
   */
  history(since: Date, until = new Date(), timeoutMs?: number): Promise<BridgeResult<Deal[]>> {
    const qs = new URLSearchParams({ start: since.toISOString(), stop: until.toISOString() })
    return this.read(
      `${this.acct}/history/positions?${qs}`,
      (raw) => (Array.isArray(raw) ? raw.map((p) => dealFrom(p as Record<string, unknown>)) : null),
      timeoutMs,
    )
  }

  async health(): Promise<HealthState> {
    try {
      const res = await fetch(`${this.acct}/information`, { headers: this.headers })
      if (!res.ok) {
        const codes = codesIn(await res.text().catch(() => ''))
        return codes.some((c) => c === 'ACCOUNT_WAS_DELETED' || c === 'ACCOUNT_WAS_ALREADY_DELETED' || c === 'ACCOUNT_NOT_FOUND')
          ? 'gone'
          : 'unknown'
      }
      const info = await res.json()
      if (info?.wrongCredentials === true) return 'wrong_credentials'
      if (info?.connected === true) return 'connected'
      return 'connecting'
    } catch {
      return 'unknown'
    }
  }

  /** Raw MetaCopier payloads, asked together: a rejection is usually only explicable side by side. */
  async diagnostics(symbol: string | null): Promise<Record<string, unknown>> {
    const get = async (url: string): Promise<unknown> => {
      try {
        const res = await fetch(url, { headers: this.headers })
        const text = await res.text()
        if (!res.ok) return { _error: res.status, _body: text.slice(0, 500) }
        return text ? JSON.parse(text) : null
      } catch (e) {
        return { _error: 'threw', _body: (e as Error).message }
      }
    }
    const [account, information, positions, logs, symbolSpec, quote] = await Promise.all([
      get(this.acct),
      get(`${this.acct}/information`),
      get(`${this.acct}/positions`),
      get(`${this.acct}/logs`),
      symbol ? get(`${this.acct}/symbols/${encodeURIComponent(symbol)}`) : Promise.resolve(null),
      symbol ? get(`${this.acct}/quote/${encodeURIComponent(symbol)}`) : Promise.resolve(null),
    ])
    return { account, information, positions, logs, symbol_spec: symbolSpec, quote }
  }

  // ---- Orders --------------------------------------------------------------

  /**
   * Opens one position. Success is 204 No Content -- there is no body to parse.
   *
   * `requestId` is MetaCopier's short-window duplicate guard (required, capped
   * at 999). A network failure throws rather than returning a refusal: the
   * order may have reached the broker, and nothing here can tell.
   */
  async placeOrder(order: OrderRequest): Promise<OrderResult> {
    const res = await fetch(`${this.acct}/positions`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({
        symbol: order.symbol,
        orderType: order.orderType,
        volume: order.volume,
        openPrice: order.openPrice,
        // Broker-side cancellation for an order that never fills. Sent only on
        // pending types -- MetaCopier ignores it on a market order, and
        // including it anyway would suggest a lifetime that does not exist.
        ...(order.openPrice > 0 && order.pendingExpirySeconds > 0
          ? { pendingExpirySeconds: order.pendingExpirySeconds }
          : {}),
        stopLoss: order.stopLoss,
        takeProfit: order.takeProfit,
        requestId: order.clientId,
        comment: order.comment,
        // The clean half of the trade's identity. The comment arrives in the
        // terminal wearing MetaCopier's `API|<requestId>|` prefix and nothing
        // we send can strip it; this field carries no decoration at all.
        magicNumber: order.magic,
      }),
    })

    if (res.ok) return { ok: true }

    const detail = await res.text()
    console.error(`[MetaCopier] open position failed ${res.status} for ${order.symbol}: ${detail}`)
    return { ok: false, definitive: true, status: res.status, detail, ...classifyRefusal(detail) }
  }

  closeAll(): Promise<BridgeResult<{ requested: true }>> {
    return (async () => {
      try {
        const res = await fetch(`${this.acct}/actions/closeAllPositions`, { method: 'POST', headers: this.headers })
        if (!res.ok) return readFailure(res.status, await res.text().catch(() => ''))
        return { ok: true as const, data: { requested: true as const } }
      } catch (e) {
        return { ok: false as const, code: 'BROKER_UNREACHABLE' as const, status: 0, detail: (e as Error).message }
      }
    })()
  }

  // ---- Account lifecycle ---------------------------------------------------

  async reconnect(): Promise<boolean> {
    try {
      const r = await fetch(`${this.acct}/actions/reconnect`, { method: 'POST', headers: this.headers })
      return r.ok
    } catch {
      return false
    }
  }

  async disconnect(): Promise<boolean> {
    return this.accountId ? await this.deleteAccount(this.accountId) : false
  }

  /**
   * Every server name MetaCopier knows for this platform, or null when the
   * list cannot be read. Callers must treat null as "unknown", never as
   * "no servers": a lookup failure must not block a connect that would work.
   */
  async servers(platform: string): Promise<string[] | null> {
    try {
      const r = await fetch(
        `${METACOPIER_BASE}/rest/api/v1/brokers/${platform === 'MT4' ? 'mt4' : 'mt5'}/all`,
        { headers: this.headers },
      )
      if (!r.ok) {
        console.warn(`[MetaCopier] server list ${r.status}: ${await r.text()}`)
        return null
      }
      const raw = await r.json()
      return Array.isArray(raw) ? raw.map((s) => String(s)) : null
    } catch (e) {
      console.warn(`[MetaCopier] server list failed: ${(e as Error).message}`)
      return null
    }
  }

  /** Stops an account, so a DELETE is not refused for one still running. */
  private async stopAccount(id: string) {
    try {
      await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}/actions/stop`, {
        method: 'POST', headers: this.headers,
      })
    } catch { /* best effort -- the delete below is what matters */ }
  }

  /**
   * Deletes a MetaCopier account. Best-effort -- a failed cleanup is logged,
   * not fatal.
   *
   * Stops it first. A bare DELETE on a running account was answering 400, and
   * because the failure is non-fatal every orphan it was meant to clear stayed
   * in the project and kept consuming a slot against the plan's account cap.
   */
  private async deleteAccount(id: string) {
    try {
      await this.stopAccount(id)
      const r = await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}`, {
        method: 'DELETE', headers: this.headers,
      })
      if (!r.ok) {
        console.warn(`[MetaCopier] delete ${id} refused ${r.status}: ${await r.text()}`)
        return false
      }
      console.log(`[MetaCopier] deleted account ${id}`)
      return true
    } catch (e) {
      console.warn(`[MetaCopier] delete ${id} failed: ${(e as Error).message}`)
      return false
    }
  }

  /**
   * Every account in the project, following pagination.
   *
   * The list call used to be a bare `GET /accounts`, which returns one
   * default-sized page. With ~160 accounts in the project that meant the
   * de-duplication step could not see most of them: a duplicate past the
   * first page was invisible, so a "no existing account" conclusion was drawn
   * from a partial list and yet another duplicate was created.
   */
  private async listAllAccounts(): Promise<Record<string, unknown>[]> {
    const out: Record<string, unknown>[] = []
    const PAGE = 200
    for (let offset = 0; offset < 5000; offset += PAGE) {
      const r = await fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts?limit=${PAGE}&offset=${offset}`,
        { headers: this.headers },
      )
      if (!r.ok) break
      const raw = await r.json()
      // Bare array from the accounts endpoint today; unwrap the common
      // pagination envelopes too so a shape change does not silently turn the
      // de-dup off and let a duplicate through.
      const page: unknown[] = Array.isArray(raw)
        ? raw
        : Array.isArray(raw?.content) ? raw.content
        : Array.isArray(raw?.data) ? raw.data
        : Array.isArray(raw?.accounts) ? raw.accounts
        : []
      out.push(...(page as Record<string, unknown>[]))
      if (page.length < PAGE) break
    }
    return out
  }

  /** Fetches one MetaCopier account, or null if it does not exist / cannot be read. */
  private async fetchAccount(id: string): Promise<Record<string, unknown> | null> {
    try {
      const r = await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}`, { headers: this.headers })
      if (!r.ok) return null
      return await r.json() as Record<string, unknown>
    } catch { return null }
  }

  /**
   * Frees account slots by deleting MetaCopier accounts that no licence claims.
   *
   * ## The safety rule
   *
   * An account is deleted only when NOTHING in our own database points at it.
   * The keep-set is built from two independent signals, and an account matching
   * either one survives:
   *
   *   1. the account id on every licence that is not revoked --
   *      `metadata.bridge_account_id` and the older `metacopier_account_id`
   *      both count, so an id in either is a live trading link by definition.
   *   2. the alias `NovaHost <LICENCE KEY>` that `create` stamps on every
   *      account it makes. This is the backstop for the window where an
   *      account was created but the write-back to the licence had not landed
   *      yet -- without it, a connect interrupted at the wrong moment would
   *      leave an account that looks orphaned and is not.
   *
   * ## Why the DB read cannot fail open
   *
   * If the licence query errors, the keep-set would be empty and every account
   * in the project would look like an orphan -- the reclaim would delete live
   * trading links for all users. It returns 0 on any error instead, so the
   * failure mode is "the cap stays full", never "everyone is disconnected".
   */
  private async reclaimOrphanAccounts(): Promise<number> {
    // Pulls only the metadata keys this needs, not the whole jsonb column.
    // `licenses.metadata` has carried multi-megabyte base64 avatars, and
    // selecting the full column forced PostgREST to detoast and serialize
    // every one of those blobs just to read a 36-character account id --
    // which is what was timing out (Postgres error 57014) on every reclaim.
    const { data: live, error: liveErr } = await this.ctx.db
      .from('licenses')
      .select(
        'license_key, status, ' +
        'metacopier_account_id:metadata->>metacopier_account_id, ' +
        'bridge_account_id:metadata->>bridge_account_id'
      )
      .neq('status', 'revoked')

    if (liveErr || !Array.isArray(live)) {
      console.error('[MetaCopier] reclaim aborted: could not read licences', liveErr)
      return 0
    }

    const keepIds = new Set<string>()
    const keepAliases = new Set<string>()
    for (const l of live as { license_key?: string; metacopier_account_id?: string; bridge_account_id?: string }[]) {
      // Over-keeping is safe; under-keeping disconnects a user. Any id on a
      // live licence survives, whichever provider it claims to belong to.
      for (const raw of [l.metacopier_account_id, l.bridge_account_id]) {
        const id = String(raw ?? '').trim()
        if (id) keepIds.add(id)
      }
      const k = String(l.license_key ?? '').trim().toUpperCase()
      if (k) keepAliases.add(`NOVAHOST ${k}`)
    }

    // A project with no recorded links at all is not a project full of
    // orphans -- it is a read that told us nothing useful. Refuse to treat
    // every account in it as disposable.
    if (keepIds.size === 0 && keepAliases.size === 0) {
      console.error('[MetaCopier] reclaim aborted: no live licence references found')
      return 0
    }

    let all: Record<string, unknown>[]
    try {
      all = await this.listAllAccounts()
    } catch (e) {
      console.error(`[MetaCopier] reclaim aborted: account list failed: ${(e as Error).message}`)
      return 0
    }
    if (!all.length) return 0

    const orphans = all.filter((a) => {
      if (isDead(a)) return false          // already gone; a second DELETE answers 400
      const id = String(a.id ?? '')
      if (!id || keepIds.has(id)) return false
      const alias = String(a.alias ?? '').trim().toUpperCase()
      return !keepAliases.has(alias)
    })

    console.log(
      `[MetaCopier] reclaim: ${all.length} accounts in project, ` +
      `${keepIds.size} bound to a licence, ${orphans.length} orphaned`
    )

    let freed = 0
    for (const a of orphans) {
      if (await this.deleteAccount(String(a.id))) freed++
      // One free slot is all this connect needs. Stopping early keeps the
      // request inside its wall-clock budget on a project holding hundreds of
      // orphans; the next connect reclaims the next one.
      if (freed >= 5) break
    }
    return freed
  }

  /**
   * Registers a broker login with MetaCopier, de-duplicated against what the
   * project already holds, and waits for the session to come up.
   *
   * Region and account-type ids are looked up at runtime rather than
   * hardcoded, because those ids are project/environment specific.
   */
  async connect(input: ConnectInput): Promise<ConnectResult> {
    const normalize = (s: unknown) => String(s ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '')
    const wantPlatform = normalize(input.platform || 'MT5')
    const typedServer = String(input.server ?? '').trim()
    const wantLogin = String(input.login ?? '').replace(/\s+/g, '')
    const key = input.licenseKey

    /**
     * The closest known servers to what the user typed, best first.
     *
     * Restricted to the same broker -- the text before the first "-", so
     * "XMGlobal" for "XMGlobal-MT5 20" -- because a suggestion from a different
     * broker is worse than none. Ranked by how long a prefix they share once
     * spacing and case are ignored. Only used for names that fit no family the
     * broker has; a missing server NUMBER is handled separately, because the
     * neighbouring number is a different server, not a correction.
     */
    const suggestServers = (list: string[], typed: string, max = 6): string[] => {
      const t = normalize(typed)
      const brand = normalize(typed.split('-')[0])
      if (brand.length < 2) return []
      const shared = (s: string) => {
        const n = normalize(s)
        let i = 0
        while (i < n.length && i < t.length && n[i] === t[i]) i++
        return i * 100 - Math.abs(n.length - t.length)
      }
      return list
        .filter((s) => normalize(s).startsWith(brand))
        .sort((a, b) => shared(b) - shared(a) || a.localeCompare(b))
        .slice(0, max)
    }

    // MetaCopier matches loginServer by its exact spelling, but users copy it
    // from a phone screen: "XMGlobal-MT520" for "XMGlobal-MT5 20", or the wrong
    // case. When exactly one known server is the same name once spacing, case
    // and punctuation are ignored, that IS the server they meant -- send its
    // real spelling. Anything less certain is left as typed, so a lookup that
    // fails or a list that does not cover the broker changes nothing.
    let finalServer = typedServer
    const knownServers = await this.servers(wantPlatform)
    if (knownServers && !knownServers.includes(typedServer)) {
      const same = knownServers.filter((s) => normalize(s) === normalize(typedServer))
      if (same.length === 1) {
        console.log(`[MetaCopier] server "${typedServer}" resolved to known spelling "${same[0]}"`)
        finalServer = same[0]
      }
    }

    type Created = { id: string } | { failure: ConnectResult & { ok: false }; limited?: boolean }

    /** Resolves account type + region and registers the account with MetaCopier. */
    const resolveAndCreate = async (): Promise<Created> => {
      const [typesRes, regionsRes] = await Promise.all([
        fetch(`${METACOPIER_BASE}/rest/api/v1/types/accountTypes`, { headers: this.headers }),
        fetch(`${METACOPIER_BASE}/rest/api/v1/types/regions`, { headers: this.headers }),
      ])

      if (!typesRes.ok || !regionsRes.ok) {
        const detail = `accountTypes=${typesRes.status} regions=${regionsRes.status}`
        console.error(`[MetaCopier] lookup failed: ${detail}`)
        return { failure: { ok: false, status: 502, error: 'Could not reach MetaCopier.', extra: { details: detail } } }
      }

      const accountTypes = await typesRes.json()
      const regions = await regionsRes.json()

      // Exact match first. A substring match will happily bind an "MT5_INVESTOR"
      // style type to a plain MT5 request, and an investor password cannot place
      // orders -- the broker rejects the login and the user is told their password
      // is wrong. Substring is kept only as a fallback for projects that name the
      // type "METATRADER5" rather than "MT5".
      const typeList = (Array.isArray(accountTypes) ? accountTypes : []) as { id?: string; name?: string }[]
      const matchedType =
        typeList.find((t) => normalize(t?.name) === wantPlatform) ??
        typeList.find((t) => normalize(t?.name).includes(wantPlatform))

      if (!matchedType) {
        return { failure: {
          ok: false, status: 400, code: 'PLATFORM_UNAVAILABLE',
          error: `Platform ${wantPlatform} is not offered by this MetaCopier project.`,
          extra: { available: typeList.map((t) => t?.name) },
        } }
      }

      // Region is the datacenter that dials the broker. Silently taking the first
      // one the API happens to list makes every connection depend on undeclared
      // ordering, so an explicit choice wins: the caller's, then the project
      // default, then the first entry -- and whichever it lands on gets logged so
      // a failure is attributable.
      const regionList = (Array.isArray(regions) ? regions : []) as { id?: string; name?: string }[]
      const preferredRegion = input.region ?? Deno.env.get('METACOPIER_REGION') ?? ''
      const matchedRegion = preferredRegion
        ? regionList.find((r) => normalize(r?.name) === normalize(preferredRegion))
        : regionList[0]

      if (!matchedRegion) {
        return { failure: {
          ok: false, status: 400, code: 'REGION_UNAVAILABLE',
          error: preferredRegion
            ? `MetaCopier region "${preferredRegion}" was not found.`
            : 'No MetaCopier region available.',
          extra: { available: regionList.map((r) => r?.name) },
        } }
      }

      console.log(
        `[MetaCopier] resolved type=${matchedType.name} region=${matchedRegion.name} ` +
        `platform=${wantPlatform} server=${finalServer}`
      )

      // Whitespace is stripped from all three because these values are pasted out
      // of a broker email far more often than they are typed, and a trailing
      // newline on the password is indistinguishable, to the user, from a correct
      // password. MetaTrader does not accept leading or trailing whitespace in any
      // of them, so nothing legitimate is lost by removing it here.
      const accountPayload = {
        loginAccountNumber: wantLogin,
        loginAccountPassword: String(input.password).trim(),
        loginServer: finalServer,
        type: { id: matchedType.id },
        region: { id: matchedRegion.id },
        alias: `NovaHost ${key}`,
        // The de-duplication step below is the real guard against a second
        // account for one login. This stays `false` as a backstop: if that
        // lookup could not run (MetaCopier's list endpoint unreachable or shaped
        // unexpectedly), a reconnect must still succeed rather than fail on an
        // "account exists" error -- the next clean reconnect will converge it.
        failIfAccountExistsInProject: false,
      }

      const createRes = await fetch(`${METACOPIER_BASE}/rest/api/v1/accounts`, {
        method: 'POST',
        headers: this.headers,
        body: JSON.stringify(accountPayload),
      })

      if (!createRes.ok) {
        const detail = await createRes.text()
        console.error(
          `[MetaCopier] account create failed ${createRes.status}: ${detail} ` +
          `(type=${matchedType.name} region=${matchedRegion.name} server=${finalServer})`
        )

        // MetaCopier reports faults as bracketed codes in an `errors` array, e.g.
        // {"errors":["[WRONG_CREDENTIALS]"]}. Match on the code rather than on
        // loose substrings of the raw body.
        const codes = codesIn(detail)

        // Keep the raw refusal where an operator can query it: users only see a
        // generic message and function logs are not reachable from our tooling.
        // Best-effort and credential-free -- the body is MetaCopier's reply, not
        // our request, and a failure here must never change the response.
        try {
          await this.ctx.db.from('provider_refusals').insert({
            source: 'broker-connect',
            http_status: createRes.status,
            code: codes[0] ?? null,
            detail: detail.slice(0, 2000),
            context: { platform: wantPlatform, server: finalServer, type: matchedType.name, region: matchedRegion.name },
          })
        } catch (_) { /* logging only */ }
        const has = (...names: string[]) => names.some((n) => codes.includes(n))

        let status = 400
        let code = codes[0] ?? 'CONNECT_FAILED'
        let message = 'Could not connect this trading account. Check the login, password and server.'
        let suggestions: string[] = []

        if (has('PLEASE_FUND_YOUR_PROJECT')) {
          status = 502
          code = 'PROVIDER_UNFUNDED'
          message = 'Account connection is unavailable: the NovaHost trading provider needs funding. This is on our side, not your login details.'
        } else if (codes.some((c) => c.includes('LIMIT'))) {
          status = 502
          code = 'PROVIDER_LIMIT'

          /* The numbers go to the LOG, not to the user.
           *
           * `allowed=10 | needed=160` is exactly what an operator needs — it
           * separates a plan ceiling from a genuinely full project, and names
           * the size of the cleanup — and exactly what a paying customer
           * should never read. "Our provider is on a trial plan" is not a
           * status update, it is a reason to ask for a refund.
           *
           * What the user gets is the one fact that changes their behaviour:
           * stop retyping your password, it is not you. `details` still
           * carries the raw body for support. */
          const allowed = detail.match(/allowed=(\d+)/)?.[1] ?? '?'
          const needed  = detail.match(/needed=(\d+)/)?.[1] ?? '?'
          console.error(
            `[MetaCopier] PROJECT AT ACCOUNT CAP: allowed=${allowed} needed=${needed} ` +
            `trial=${codes.some((c) => c.includes('TRIAL'))} — no user can link a broker until this is raised or reclaimed`
          )

          message =
            'Connecting a trading account is unavailable right now. This is a fault on our side, ' +
            'not your login details — there is nothing wrong with your account number, password ' +
            'or server. Please try again shortly, or contact support if it persists.'
        } else if (has('WRONG_CREDENTIALS', 'INVALID_CREDENTIALS', 'AUTHENTICATION_FAILED', 'AUTHENTICATION')) {
          code = 'WRONG_CREDENTIALS'
          message =
            `Your broker rejected this login. Check that the account number and password are the ` +
            `${wantPlatform} trading password (not the investor password), and that "${finalServer}" ` +
            `is the server name exactly as MetaTrader shows it. If the account is ` +
            `${wantPlatform === 'MT5' ? 'MT4' : 'MT5'}, switch the platform tab and try again.`
        } else if (codes.some((c) => c.includes('SERVER'))) {
          code = 'UNKNOWN_SERVER'
          // "Copy it exactly" is no help when they already did: a server can be
          // real at the broker and still missing from MetaCopier's catalogue.
          // Seen 2026-10-07: it lists XMGlobal-MT5, 2 and 4-18, but not
          // "XMGlobal-MT5 20", and a correctly typed name was rejected over and over.
          //
          // A numbered server is NOT a typo of its neighbour. An account lives on
          // one server, so suggesting "XMGlobal-MT5 2" to someone on "20" only
          // trades this error for a wrong-password one. When the broker's
          // family is known and only this number is missing, say so plainly and
          // suggest nothing. Suggestions are only for a name that does not fit
          // any family the broker has, which is a real typo.
          const stem = (s: string) => normalize(s).replace(/\d+$/, '')
          const family = knownServers
            ? knownServers.filter((s) => stem(s) === stem(finalServer))
            : []
          if (family.length) {
            code = 'SERVER_NOT_SUPPORTED'
            message =
              `"${finalServer}" is your broker's server, but our trading provider cannot connect ` +
              `to that one yet. Do not switch to a different server number, because your account ` +
              `only works on this one. Please contact support so we can get it added.`
            console.error(
              `[MetaCopier] server "${finalServer}" missing from provider catalogue; ` +
              `known in family: ${family.join(', ')}`
            )
          } else {
            suggestions = knownServers ? suggestServers(knownServers, finalServer) : []
            message = suggestions.length
              ? `"${finalServer}" is not a ${wantPlatform} server we can connect to. ` +
                `Did you mean: ${suggestions.join(', ')}? Check the exact name in MetaTrader ` +
                `under Tools -> Options -> Server.`
              : `"${finalServer}" was not recognised as a ${wantPlatform} server. Copy it exactly ` +
                `as it appears in MetaTrader under Tools -> Options -> Server.`
          }
        }

        // Broker credentials are never echoed back to the client.
        return {
          failure: { ok: false, status, code, error: message, extra: { suggestions, details: detail } },
          limited: code === 'PROVIDER_LIMIT',
        }
      }

      const created = await createRes.json().catch(() => ({}))
      const id = created?.id == null ? '' : String(created.id)
      if (!id) {
        return { failure: { ok: false, status: 502, code: 'NO_ACCOUNT_ID', error: 'MetaCopier did not return an account id.' } }
      }
      return { id }
    }

    /**
     * Starts the account and waits for the broker session to actually come up.
     *
     * Returning "connected" the instant the account is registered is why a
     * signal fired in the first minute after a reconnect failed with
     * `[ACCOUNT_IS_NOT_CONNTECTED]`: the account existed, the MT4/MT5 session
     * behind it did not. Up to ~24s of polling turns that window from a failed
     * trade into a "still connecting" state the app can show honestly.
     */
    const startAndVerify = async (id: string): Promise<'connected' | 'connecting' | 'wrong_credentials' | 'account_gone'> => {
      await fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}/actions/start`,
        { method: 'POST', headers: this.headers }
      ).catch((e) => console.warn('[MetaCopier] start failed (non-fatal):', e))

      for (let i = 0; i < 6; i++) {
        await new Promise((r) => setTimeout(r, 4000))
        try {
          const infoRes = await fetch(
            `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(id)}/information`,
            { headers: this.headers },
          )
          if (!infoRes.ok) {
            // isDead() stops an ALREADY-deleted account from being reused. This
            // covers the account dying mid-poll instead -- a concurrent
            // reclaim, or the user deleting it at their broker while we sit in
            // this loop. Only codes that mean "this id is never coming back"
            // short-circuit to account_gone; anything else (rate limits, a
            // transient 5xx, a region still spinning up) keeps polling.
            const detail = await infoRes.text().catch(() => '')
            const codes = codesIn(detail)
            if (codes.some((c) => c === 'ACCOUNT_WAS_DELETED' || c === 'ACCOUNT_WAS_ALREADY_DELETED' || c === 'ACCOUNT_NOT_FOUND')) {
              console.warn(`[MetaCopier] account ${id} gone mid-verify: ${detail}`)
              return 'account_gone'
            }
            continue
          }
          const info = await infoRes.json()
          if (info?.wrongCredentials === true) return 'wrong_credentials'
          if (info?.connected === true) return 'connected'
        } catch { /* keep waiting */ }
      }
      return 'connecting'
    }

    // ---- De-duplicate: one MetaCopier account per broker login --------------
    //
    // MetaCopier will hold two accounts for one MT4/MT5 login, and they fight
    // over the broker session -- each one's connect knocks the other offline,
    // which surfaces on trades as intermittent [ACCOUNT_IS_NOT_CONNTECTED] and
    // [BROKER_REJECTION]. The old `failIfAccountExistsInProject: false` created a
    // fresh duplicate on every reconnect.
    //
    // Three signals, cheapest and most authoritative first:
    //   1. the id already stored on THIS licence -- if it still exists and still
    //      points at the same login+server, this connect is a no-op: re-start it
    //      and confirm it is up. Nothing is created.
    //   2. any account in the project already registered for this login+server --
    //      catches accounts made before ids were stored, or out of band.
    //   3. neither -> register a new one.
    let accountId = ''
    let reused = false

    const priorId = String(input.priorAccountId ?? '').trim()
    if (priorId) {
      const prior = await this.fetchAccount(priorId)
      if (prior && isDead(prior)) {
        // Gone at the provider already -- deleting it again would just 400
        // with [ACCOUNT_WAS_ALREADY_DELETED]. Leave accountId unset so a fresh
        // account gets created below, and the caller's write-back replaces
        // the dead id on the licence.
        console.log(`[MetaCopier] stored account ${priorId} was deleted at the provider -- abandoning it`)
      } else if (prior) {
        const sameLogin =
          normalize(prior.loginAccountNumber) === normalize(wantLogin) &&
          normalize(prior.loginServer) === normalize(finalServer)
        if (sameLogin) {
          accountId = priorId
          reused = true
          console.log(`[MetaCopier] licence already holds account ${priorId} for this login -- reusing`)
        } else {
          // The licence is being pointed at a different broker login. The old
          // account is now orphaned -- remove it so it cannot keep a stale
          // session (and a provider charge) alive.
          console.log(`[MetaCopier] licence switching login -- deleting old account ${priorId}`)
          await this.deleteAccount(priorId)
        }
      }
    }

    if (!accountId) {
      try {
        const all = await this.listAllAccounts()
        const matches = all.filter((a) =>
          !isDead(a) &&
          normalize(a.loginAccountNumber) === normalize(wantLogin) &&
          normalize(a.loginServer) === normalize(finalServer)
        )
        matches.sort((a: Record<string, unknown>, b: Record<string, unknown>) =>
          String(a.created ?? '').localeCompare(String(b.created ?? '')))

        if (matches.length === 1) {
          // Exactly one -- reuse it. A stale password on it is caught by the
          // verify step below and repaired there.
          accountId = String(matches[0].id)
          reused = true
          console.log(`[MetaCopier] reusing existing account ${accountId} for login ${wantLogin}`)
        } else if (matches.length > 1) {
          // Already in a contending state. Clear them all and build one clean
          // account, so the outcome does not depend on which duplicate wins.
          console.warn(`[MetaCopier] ${matches.length} duplicate accounts for login ${wantLogin} -- clearing`)
          for (const a of matches) await this.deleteAccount(String(a.id))
        }
      } catch (e) {
        console.warn(`[MetaCopier] account list failed (will create fresh): ${(e as Error).message}`)
      }
    }

    if (!accountId) {
      let r = await resolveAndCreate()

      // Out of account slots. Reclaim what this project is holding for licences
      // that no longer exist, then try once more.
      //
      // Not a nicety: a project that has been creating a duplicate on every
      // reconnect accumulates orphans until the cap is hit, and from that moment
      // NOBODY can connect an account -- the failure is total and permanent, and
      // it presents to every user as "your password is wrong". Reclaiming is the
      // difference between a self-healing cap and a dead product.
      if ('failure' in r && r.limited) {
        const freed = await this.reclaimOrphanAccounts()
        if (freed > 0) {
          console.log(`[MetaCopier] reclaimed ${freed} orphaned account(s); retrying create`)
          r = await resolveAndCreate()
        }
      }

      if ('failure' in r) return r.failure
      accountId = r.id
    }

    let linkState = await startAndVerify(accountId)

    // A reused account whose stored password no longer works: the credentials
    // cannot be edited in place, so drop it and register the login fresh once.
    if (linkState === 'wrong_credentials' && reused) {
      console.log(`[MetaCopier] reused account ${accountId} has stale credentials -- recreating`)
      await this.deleteAccount(accountId)
      reused = false
      const r = await resolveAndCreate()
      if ('failure' in r) return r.failure
      accountId = r.id
      linkState = await startAndVerify(accountId)
    }

    // The account died while we were polling it -- not a credentials problem,
    // so recreate exactly like the branch above rather than reporting a false
    // CONNECTING.
    if (linkState === 'account_gone') {
      console.log(`[MetaCopier] account ${accountId} was deleted mid-verify -- recreating`)
      reused = false
      const r = await resolveAndCreate()
      if ('failure' in r) return r.failure
      accountId = r.id
      linkState = await startAndVerify(accountId)
    }

    if (linkState === 'wrong_credentials') {
      return {
        ok: false, status: 400, code: 'WRONG_CREDENTIALS',
        error:
          `Your broker rejected this login. Check that the account number and password are the ` +
          `${wantPlatform} trading password (not the investor password), and that "${finalServer}" ` +
          `is the server name exactly as MetaTrader shows it.`,
      }
    }

    // Recreated once above and it STILL came back gone -- give up rather than
    // write an outcome callers were never promised.
    if (linkState === 'account_gone') {
      return {
        ok: false, status: 502, code: 'CONNECT_FAILED',
        error: 'Could not establish a trading session for this account. Please try again.',
      }
    }

    return { ok: true, accountId, state: linkState, reused, server: finalServer, platform: wantPlatform }
  }
}

/**
 * True when MetaCopier still knows about an account but has deleted it.
 *
 * `GET /accounts/{id}` answers 200 for a deleted account -- it does not
 * 404 -- and hands back the same shape as a live one, just with
 * `deleted` set and `status.name === 'Deleted'`. Every call the account
 * needs (`/actions/start`, `/information`) then 400s with
 * `[ACCOUNT_WAS_DELETED]`. Without this check the corpse gets reused and the
 * licence reports CONNECTING forever, because every retry lands on the same
 * dead id.
 */
const isDead = (a: Record<string, unknown> | null | undefined): boolean =>
  !!a && (a.deleted === true || (a.status as Record<string, unknown> | undefined)?.name === 'Deleted')

/**
 * Reads a MetaCopier order refusal into our vocabulary.
 *
 * The precedence is the executor's, unchanged: autotrading first (terminal,
 * and only the broker can lift it), then connection, then a deleted account,
 * then the instrument name, then our own malformed request, then the order's
 * size, margin, stops and market state. A bare `[BROKER_REJECTION]` -- or a
 * refusal with no token at all -- asks the caller to `explain` it.
 */
function classifyRefusal(detail: string): Omit<OrderRefusal, 'ok' | 'definitive' | 'status' | 'detail'> {
  const codes = bareCodesIn(detail)
  const hit = (needle: string) => codes.some((c) => c.includes(needle))
  const says = (re: RegExp) => re.test(detail)

  // ---- What the broker actually said, in words -----------------------------
  //
  // MetaCopier answers a refusal with `{"errors":[...]}`, and the entries are
  // NOT always bracketed tokens. A real broker sentence arrives there too:
  // `{"errors":["Autotrading disabled by server"]}`. It is the most specific
  // thing anyone will ever have about the refusal, so it is extracted and kept,
  // and only ever replaced by a message that is MORE specific.
  const brokerMessage = ((): string | null => {
    try {
      const parsed = JSON.parse(detail) as Record<string, unknown>
      const errs = parsed?.errors
      const first = Array.isArray(errs)
        ? errs.map((e) => String(e ?? '').trim()).find((e) => e.length > 0)
        : typeof errs === 'string' ? errs.trim() : null
      if (!first) return null
      // A bare `[TOKEN]` is a code, not a sentence.
      return /^\[[A-Z0-9_]+\]$/.test(first) ? null : first
    } catch {
      return null
    }
  })()

  const wrongName = hit('SYMBOL') || says(UNKNOWN_SYMBOL_PROSE)
  const base = { wrongName, brokerMessage, providerCode: codes[0] ?? null, accountGone: false, explain: false }

  if (says(AUTOTRADING_REFUSAL)) return { ...base, code: 'AUTOTRADING_DISABLED' }

  // MetaCopier spells its token `[ACCOUNT_IS_NOT_CONNTECTED]` (their typo).
  if (hit('CONNTECT') || hit('NOT_CONNECTED') || hit('OFFLINE') || hit('DISCONNECT')) {
    return { ...base, code: 'ACCOUNT_DISCONNECTED' }
  }

  // Gone, not merely offline. NOT_FOUND only when it is not the SYMBOL that was
  // not found: `[SYMBOL_NOT_FOUND]` contains the same substring, and matching it
  // here would unlink a healthy account over an unknown instrument name.
  if (hit('DELETED') || codes.some((c) => c.includes('NOT_FOUND') && !c.includes('SYMBOL'))) {
    return { ...base, code: 'ACCOUNT_GONE', accountGone: true }
  }

  if (hit('SYMBOL') || says(UNKNOWN_SYMBOL_PROSE)) return { ...base, code: 'UNKNOWN_SYMBOL' }

  // Every field of the request is built server-side, so a validation failure
  // is ours -- named separately so it can never read as a market problem.
  if (says(/requestId|must be less than|must be greater than|constraint|validation/i)) {
    return { ...base, code: 'REQUEST_INVALID' }
  }

  if (hit('VOLUME') || hit('LOT')) return { ...base, code: 'INVALID_VOLUME' }
  if (hit('MONEY') || hit('MARGIN') || hit('FUNDS')) return { ...base, code: 'INSUFFICIENT_MARGIN' }
  if (hit('STOP') || hit('INVALID_PRICE')) return { ...base, code: 'INVALID_STOPS' }
  if (hit('DISABLED') || hit('PROHIBIT') || hit('CLOSED') || hit('MARKET')) return { ...base, code: 'TRADING_PROHIBITED' }

  // MetaCopier's catch-all for a broker refusal with no reason attached.
  if (hit('BROKER_REJECT') || codes.length === 0) return { ...base, code: 'BROKER_REJECTED', explain: true }

  // A token we have no translation for. Reported as itself, never guessed.
  return { ...base, code: codes[0] }
}
