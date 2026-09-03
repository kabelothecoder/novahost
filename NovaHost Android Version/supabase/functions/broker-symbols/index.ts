import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * Reads the instrument list off the subscriber's own broker account and matches
 * it against the canonical names NovaHost trades in.
 *
 * ## Why this exists
 *
 * NovaHost speaks canonical names -- XAUUSD, NAS100 -- because that is what the
 * mentor picks and what the licence allows. Almost no broker agrees. The same
 * gold is `XAUUSD` at one, `XAUUSD.m` on a micro book, `XAUUSDpro` on a
 * raw-spread one and `GOLD` at a fifth. An order naming a symbol the broker has
 * never heard of is rejected outright.
 *
 * Until now the only way that name was ever found was inside `metacopier-execute`:
 * send an order, take the rejection, try the next alias. That costs a signal per
 * instrument, and it only ever works for the twelve instruments in the hardcoded
 * alias table -- a broker calling gold `XAUUSD_i` was undiscoverable at any price.
 *
 * MetaCopier will simply tell us. `GET /accounts/{id}/symbols` returns the
 * account's Market Watch. So the mapping stops being a guess: the app shows the
 * user the real list, pre-selects the match, and the user confirms it once.
 *
 * This function only READS and SUGGESTS. Nothing here decides what trades -- the
 * user's confirmed choice is written by `sync-symbol-config` and enforced by
 * `metacopier-execute`.
 */

/** The canonical NovaHost name for an instrument: letters and digits, upper case. */
function canon(s: unknown): string {
  return String(s ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

/**
 * What else the same instrument trades under.
 *
 * Kept in step with the table in `metacopier-execute`, which is the authority at
 * execution time. This copy exists so that a suggestion made here and an order
 * sent there agree about what counts as the same instrument.
 */
const SYMBOL_ALIASES: Record<string, string[]> = {
  XAUUSD: ['GOLD', 'GOLDUSD', 'XAUUSD'],
  XAGUSD: ['SILVER', 'SILVERUSD', 'XAGUSD'],
  NAS100: ['USTEC', 'US100', 'NDX100', 'NAS100', 'USTECH', 'NASDAQ'],
  US30:   ['DJ30', 'WS30', 'US30', 'USA30', 'DOW'],
  SPX500: ['US500', 'SP500', 'SPX500', 'USA500'],
  GER40:  ['DE40', 'DAX40', 'GER40', 'GER30', 'DAX'],
  UK100:  ['FTSE100', 'UK100'],
  JP225:  ['JPN225', 'JP225', 'NIKKEI'],
  USOIL:  ['WTI', 'XTIUSD', 'USOIL', 'CRUDE', 'OIL'],
  UKOIL:  ['BRENT', 'XBRUSD', 'UKOIL'],
  BTCUSD: ['BTCUSD', 'BITCOIN', 'BTC'],
  ETHUSD: ['ETHUSD', 'ETHEREUM', 'ETH'],
  VIX:    ['VIX', 'VOLATILITY', 'VIXX'],
}

/**
 * Every name an instrument is known by, reachable from ANY of those names.
 *
 * `SYMBOL_ALIASES` is keyed only by the canonical name, which assumed mentors
 * would always configure robots canonically. They do not: a mentor whose own
 * broker calls gold `GOLD` types GOLD into the robot, and `SYMBOL_ALIASES.GOLD`
 * is undefined. Flattening the table makes the lookup symmetric, so which
 * member of a family the mentor happened to type stops mattering.
 *
 * Kept in step with the identical table in `metacopier-execute`.
 */
const ALIAS_FAMILY: Record<string, string[]> = (() => {
  const out: Record<string, string[]> = {}
  for (const [canonical, aliases] of Object.entries(SYMBOL_ALIASES)) {
    const family = Array.from(new Set([canonical, ...aliases]))
    for (const member of family) {
      // First writer wins, so a name shared by two families keeps the one that
      // declared it first rather than silently switching instrument.
      if (!out[member]) out[member] = family
    }
  }
  return out
})()

/** The alias family for a symbol, whichever member of it was named. */
function aliasesFor(base: string): string[] {
  return ALIAS_FAMILY[base] ?? []
}

/** One instrument as the broker lists it. */
interface BrokerSymbol {
  /** Exactly as the broker spells it -- this is what an order must carry. */
  name: string
  /** Letters and digits only, for comparison. */
  key: string
}

/**
 * MetaCopier's symbol payload, reduced to names.
 *
 * The shape is not contractual across brokers -- some entries come back as bare
 * strings, others as objects keyed `name`, `symbol` or `id`. Anything that
 * yields no usable name is dropped rather than guessed at, because a wrong name
 * here becomes a rejected order later.
 */
function extractNames(payload: unknown): BrokerSymbol[] {
  const list = Array.isArray(payload) ? payload : []
  const out: BrokerSymbol[] = []
  const seen = new Set<string>()

  for (const entry of list) {
    let raw = ''
    if (typeof entry === 'string') {
      raw = entry
    } else if (entry && typeof entry === 'object') {
      const o = entry as Record<string, unknown>
      raw = String(o.name ?? o.symbol ?? o.symbolName ?? o.id ?? '')
    }

    const name = raw.trim()
    if (!name || seen.has(name)) continue
    seen.add(name)
    out.push({ name, key: canon(name) })
  }

  return out
}

type Confidence = 'exact' | 'suffix' | 'decorated' | 'alias'

/**
 * The broker's name for one canonical instrument, or null when the account does
 * not appear to list it at all.
 *
 * Ranked most-certain first, and within a rank the SHORTEST name wins -- a book
 * carrying both `XAUUSD` and `XAUUSDpro` means the plain one, and `GOLD` beats
 * `GOLDSPOT` for the same reason. Least decoration is the closest thing to an
 * exact answer that a prefix match can offer.
 *
 * Cross-instrument matching is confined to the alias table on purpose. Free
 * substring matching would happily bind `US30` to `US300`, and a mapping that
 * silently points at the wrong instrument is far worse than no mapping at all:
 * the order fills, on something the user never chose.
 */
function suggestFor(
  base: string,
  brokerSymbols: BrokerSymbol[],
  suffix: string
): { match: string | null; confidence: Confidence | null } {
  const shortest = (a: BrokerSymbol, b: BrokerSymbol) => a.name.length - b.name.length
  const all = (pred: (s: BrokerSymbol) => boolean) => brokerSymbols.filter(pred).sort(shortest)

  // 1. The broker spells it exactly as we do.
  const exact = all((s) => s.key === base)
  if (exact.length) return { match: exact[0].name, confidence: 'exact' }

  // 2. The account's known decoration, e.g. a micro book's ".m".
  if (suffix) {
    const withSuffix = canon(base + suffix)
    const suffixed = all((s) => s.key === withSuffix)
    if (suffixed.length) return { match: suffixed[0].name, confidence: 'suffix' }
  }

  // 3. Our name plus decoration we did not know about -- XAUUSDpro, XAUUSD_i.
  const decorated = all((s) => s.key.startsWith(base) && s.key.length <= base.length + 5)
  if (decorated.length) return { match: decorated[0].name, confidence: 'decorated' }

  // 4. A different name for the same instrument, from the alias table only.
  for (const alias of aliasesFor(base)) {
    const a = canon(alias)
    const hit = all((s) => s.key === a || (s.key.startsWith(a) && s.key.length <= a.length + 5))
    if (hit.length) return { match: hit[0].name, confidence: 'alias' }
  }

  return { match: null, confidence: null }
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  const json = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
    })

  try {
    const METACOPIER_API_KEY = Deno.env.get('METACOPIER_API_KEY') ?? ''
    if (!METACOPIER_API_KEY) {
      console.error('METACOPIER_API_KEY is not configured')
      return json({ success: false, code: 'SERVER_MISCONFIGURED', error: 'Server misconfiguration.' }, 500)
    }

    const novaHost = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    const body = await req.json().catch(() => ({}))
    const rawKey = body.license_key

    if (!rawKey || !String(rawKey).trim()) {
      return json({ success: false, code: 'MISSING_PARAMETERS', error: 'A licence key is required.' }, 400)
    }

    // Authorised by licence, like every other device-facing function: the app
    // holds a mentor-issued key and has no NovaHost auth session. The account id
    // is resolved from the licence and never accepted from the caller.
    const key = String(rawKey).trim().toUpperCase()
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select('id, status, expires_at, allowed_symbols, metadata')
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr
    if (!license) {
      return json({ success: false, code: 'LICENCE_UNKNOWN', error: 'Licence not recognised.' }, 401)
    }
    if (license.status !== 'active') {
      return json({ success: false, code: 'LICENCE_INACTIVE', error: 'Licence is not active.' }, 403)
    }
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return json({ success: false, code: 'LICENCE_EXPIRED', error: 'Licence has expired.' }, 403)
    }

    const metadata = (license.metadata ?? {}) as Record<string, unknown>
    const accountId = metadata.metacopier_account_id as string | undefined

    if (!accountId) {
      return json({
        success: false,
        code: 'NO_ACCOUNT_LINKED',
        error: 'Connect your trading account first -- there is no broker to read symbols from yet.',
      }, 409)
    }

    // ---- Ask the broker what it lists ---------------------------------------
    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/symbols`,
      { headers: { 'X-API-KEY': METACOPIER_API_KEY, 'Accept': 'application/json' } }
    )

    if (!res.ok) {
      const detail = await res.text()
      console.error(`[broker-symbols] symbols query failed ${res.status}: ${detail}`)

      // A disconnected account is the common case and is the user's to fix, so
      // it is named rather than folded into a generic gateway error.
      const disconnected = /NOT_CONNECTED|OFFLINE|DISCONNECT/i.test(detail)
      return json({
        success: false,
        code: disconnected ? 'ACCOUNT_DISCONNECTED' : 'BROKER_UNREACHABLE',
        error: disconnected
          ? 'Your trading account is not connected to the broker right now. Reconnect it and try again.'
          : 'Could not read the symbol list from your broker.',
        details: detail,
      }, 502)
    }

    const brokerSymbols = extractNames(await res.json().catch(() => []))

    if (brokerSymbols.length === 0) {
      return json({
        success: false,
        code: 'NO_SYMBOLS',
        error: 'Your broker returned an empty symbol list. This usually means the account is still connecting.',
      }, 502)
    }

    // ---- Match them to what this licence is allowed to trade ----------------
    const suffix = String(metadata.symbol_suffix ?? '').trim().replace(/[^A-Za-z0-9._-]/g, '')
    const allowed = Array.isArray(license.allowed_symbols) ? license.allowed_symbols : []

    // What the user (or a previous discovery) already settled on. An existing
    // answer is reported as-is and never quietly replaced by a fresh guess.
    const { data: existing } = await novaHost
      .from('license_symbol_config')
      .select('symbol, broker_symbol')
      .eq('license_id', license.id)

    const confirmed = new Map<string, string>()
    for (const row of existing ?? []) {
      if (row.broker_symbol) confirmed.set(canon(row.symbol), String(row.broker_symbol))
    }

    const learned = (metadata.symbol_map ?? {}) as Record<string, string>

    const mappings = allowed.map((raw: string) => {
      const base = canon(raw)
      const settled = confirmed.get(base) ?? learned[base] ?? null

      // A suggestion is resolved even when something is already settled: it is
      // what lets the app say "the name you saved is not on this broker any more".
      const { match, confidence } = suggestFor(base, brokerSymbols, suffix)

      return {
        symbol: base,
        /** What is stored today, if anything. */
        current: settled,
        /** What the broker's own list says it should be. */
        suggested: match,
        confidence,
        /** False when a saved name is no longer in the broker's Market Watch. */
        current_is_valid: settled ? brokerSymbols.some((s) => s.name === settled) : null,
      }
    })

    console.log(
      `[broker-symbols] licence ${license.id}: ${brokerSymbols.length} broker symbols, ` +
      `${mappings.filter((m: { suggested: string | null }) => m.suggested).length}/${mappings.length} matched`
    )

    return json({
      success: true,
      account_id: accountId,
      /** Every name the broker lists, so the app can offer a picker for the misses. */
      broker_symbols: brokerSymbols.map((s) => s.name),
      mappings,
    })

  } catch (err) {
    console.error('[broker-symbols] fatal:', err)
    return json({ success: false, code: 'FATAL', error: (err as Error).message }, 500)
  }
})
