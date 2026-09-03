import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

const METACOPIER_BASE = 'https://api.metacopier.io'

/**
 * MetaCopier orderType, from the side and how the entry is reached.
 *
 * Their enum is `Buy, Sell, BuyLimit, SellLimit, BuyStop, SellStop`. Anything
 * else is rejected rather than guessed.
 *
 * [entry] defaults to MARKET, which is what every mentor signal has always sent
 * and must keep sending. The scanner is the caller that needs the other two: a
 * chart entry is rarely where price is standing, and filling "buy the retest at
 * 1.0850" at the market when price is 1.0880 puts the position 30 pips from the
 * level its stop and targets were measured against.
 */
function toOrderType(side: string, entry?: string): string | null {
  const base = (side ?? '').trim().toUpperCase()
  const kind = (entry ?? 'MARKET').trim().toUpperCase()

  if (base !== 'BUY' && base !== 'SELL') return null

  const word = base === 'BUY' ? 'Buy' : 'Sell'
  switch (kind) {
    case 'MARKET': return word
    case 'LIMIT':  return word + 'Limit'
    case 'STOP':   return word + 'Stop'
    default:       return null
  }
}

/** True for the four pending variants, which are the ones that need an openPrice. */
function isPending(orderType: string): boolean {
  return orderType.endsWith('Limit') || orderType.endsWith('Stop')
}

/**
 * The order comment MetaTrader shows against the position.
 *
 * Format is `<robot>-NovaHost`, e.g. `QuantumBreaker-NovaHost`, so anyone
 * reading the trade history can tell at a glance which positions were placed by
 * automation and which robot placed them.
 *
 * Built here rather than sent by the client: the robot name is an attribute of
 * the licence, and a device that could choose its own comment could attribute
 * its trades to somebody else's robot.
 *
 * MetaTrader shows this behind an `API|<requestId>|` prefix that MetaCopier
 * adds itself -- `API|255|QuantumBreaker-NovaHost`. That prefix is NOT ours and
 * cannot be removed. Their schema documents it as prepended by the
 * open-position endpoint, and their modify endpoint accepts only volume, open
 * price, take profit and stop loss, so it cannot be rewritten afterwards
 * either. Everything after the second `|` is the part we control, and this
 * function is all of it.
 *
 * The suffix is protected and the robot name is what gets shortened -- losing
 * the tail of a long robot name is survivable, losing "-NovaHost" defeats the
 * point of having a comment at all.
 *
 * The budget is 23, not the 31 MetaTrader allows, because that prefix is 5-8
 * characters and the broker's 31-character limit applies to the whole string.
 * Sizing to 31 here meant the broker did the trimming instead, from the right
 * -- which is precisely where "-NovaHost" lives.
 */
const COMMENT_SUFFIX = '-NovaHost'
const COMMENT_MAX = 23

/**
 * Words a robot name can lose without becoming a different robot.
 *
 * "Quantum Breaker EA" and "Quantum Breaker" are the same product to the person
 * reading their trade history, so " EA" is the first thing spent when the name
 * does not fit -- ahead of any letter of the name itself.
 */
const COMMENT_NOISE = /[\s_-]+(EA|BOT|ROBOT|V\d+(\.\d+)*)$/i

/**
 * Fits a robot name into [room] characters, cheapest loss first.
 *
 * The ladder matters more than any one rung. A mid-word cut is what the first
 * live trades actually showed -- `Quantum Breake-NovaHost` -- and that reads as
 * a broken string rather than a name, which is the one thing a comment exists
 * to avoid. So words are kept whole: a noise word goes first, then the spaces
 * between the words that remain, then whole words off the tail. Cutting into a
 * word happens only when a single unbreakable word is still too long.
 *
 * Closing the spaces before dropping a word is what keeps the name intact at
 * the sizes that actually occur. "Quantum Breaker AI" does not fit in 14 and
 * neither does "Quantum Breaker", but "QuantumBreaker" does -- and that is
 * still legibly the robot, where "Quantum" alone has lost half its identity.
 */
function fitName(name: string, room: number): string {
  if (name.length <= room) return name

  // " EA", " Bot", " v2" -- present in most robot names, meaningful in none.
  const base = name.replace(COMMENT_NOISE, '').trim() || name
  const words = base.split(' ').filter(Boolean)

  // Longest first, so the result keeps as many whole words as will fit. Within
  // a given number of words the spaced form is tried before the squeezed one:
  // both are readable, but only one of them is what the mentor typed.
  for (let n = words.length; n > 0; n--) {
    const kept = words.slice(0, n)
    const spaced = kept.join(' ')
    if (spaced.length <= room) return spaced
    const squeezed = kept.join('')
    if (squeezed.length <= room) return squeezed
  }

  // One word, longer than the budget. Nothing left to spend but letters.
  return (words[0] ?? base).slice(0, room)
}

function tradeComment(license: Record<string, unknown>): string {
  const ea = license.expert_advisors as { name?: string; display_name?: string } | null
  const raw = (ea?.display_name || ea?.name || 'NovaHost Bot').toString()

  // Strip what MetaTrader will not carry cleanly in a comment field, and
  // collapse runs of whitespace so a stray double space cannot spend budget
  // that a letter of the name could have used.
  const cleaned = raw.replace(/[^\w \-.]/g, '').replace(/\s+/g, ' ').trim() || 'NovaHost Bot'

  return fitName(cleaned, COMMENT_MAX - COMMENT_SUFFIX.length) + COMMENT_SUFFIX
}

/**
 * MetaCopier's hard ceiling on `requestId`. From the API schema:
 *
 *   "A client request ID to avoid the request being executed multiple times due
 *    to network or client errors. The IDs start at 0 and increment up to 999,
 *    then begin again at 0."
 *
 * It is a REQUIRED field with `maximum: 999`, and it is explicitly designed to
 * wrap -- so it is a short-window idempotency key, not a durable one. Reusing a
 * number weeks later is normal and expected, which is what makes deriving it by
 * hash-and-wrap safe.
 */
const MAX_REQUEST_ID = 999

/**
 * Stable request id derived from the signal id, inside MetaCopier's range.
 *
 * The same signal must always produce the same number: that is what stops a
 * network hiccup turning one mentor call into two positions, and it keeps the
 * id fixed across the candidate-name walk so the broker sees one request rather
 * than five.
 *
 * The modulo is not a detail -- it is the whole bug this function once had. The
 * hash below is 32-bit and was returned raw, producing ids like 2105094537
 * against a field capped at 999. Every live order was refused with
 * `requestId -> must be less than or equal to 999`: the licence resolved, the
 * broker symbol resolved, the request was well formed, and the position was
 * rejected on a validation rule nobody had read. It looked like a broker
 * problem for as long as nobody opened `signal_logs`.
 */
function requestIdFrom(seed: string): number {
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return Math.abs(h | 0) % (MAX_REQUEST_ID + 1)
}

/** The canonical NovaHost name for an instrument: letters and digits, upper case. */
function baseSymbol(pair: unknown): string {
  return String(pair ?? '').replace(/[^A-Za-z0-9]/g, '').toUpperCase()
}

/**
 * What this particular broker calls the instrument.
 *
 * NovaHost speaks in canonical names -- XAUUSD, NAS100 -- because that is what
 * the mentor picks, what the licence allows and what the subscriber ticks. Very
 * few brokers agree with it. The same gold is `XAUUSD` at one, `XAUUSD.m` on a
 * micro book, `XAUUSDpro` on a raw-spread one and `GOLD` at a fifth, and an
 * order naming a symbol the broker has never heard of is rejected outright.
 *
 * That rejection was this pipeline's most expensive silence: `metacopier-connect`
 * accepted a `symbol_suffix` from the app, threw it away, and the executor sent
 * the bare canonical name to every account regardless. Micro-account users had a
 * robot that received every signal, sent every order and filled none of them.
 *
 * Four levels, most specific first:
 *
 *  - `license_symbol_config.broker_symbol`, the name the subscriber confirmed
 *    against their own broker's Market Watch. An answer, not a guess.
 *  - `metadata.symbol_map`, an explicit `{ "XAUUSD": "GOLD" }` override, and
 *    the place a discovered name is remembered.
 *  - `metadata.symbol_suffix`, the common case -- `.m`, `.pro`, `.raw`, `m`.
 *  - a built-in alias list, walked only when none of the above is recognised.
 *
 * And when every one of those is refused, `resolveFromBrokerList` asks the
 * broker directly rather than giving up.
 *
 * Only the ORDER carries the broker name. Every permission check stays on the
 * canonical one, so a rename can never widen what a licence is allowed to trade.
 */

/**
 * What else the same instrument trades under.
 *
 * Not a preference list -- a discovery list. `SYMBOL_ALIASES.XAUUSD` says the
 * gold on an unknown broker may be called GOLD or GOLDUSD, and the executor
 * finds out which by trying, once, and then remembering.
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
 * `SYMBOL_ALIASES` is keyed only by the canonical name, which quietly assumed
 * mentors would always configure robots canonically. They do not. A mentor whose
 * own broker calls gold `GOLD` types GOLD into the robot, and that becomes the
 * licence's allowance and the signal's pair -- at which point `SYMBOL_ALIASES.GOLD`
 * is undefined, the alias walk finds nothing, and the trade fails for every
 * subscriber whose broker happens to call it XAUUSD. The mentor's own account
 * works, so the fault is invisible from where it was created.
 *
 * Flattening the table so every member maps to the whole family makes the
 * lookup symmetric: GOLD finds XAUUSD, XAUUSD finds GOLD, and which one the
 * mentor happened to type stops mattering.
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

/** A broker suffix reduced to what MetaTrader permits inside a symbol name. */
function safeSuffix(metadata: Record<string, unknown> | null): string {
  return String(metadata?.symbol_suffix ?? '').trim().replace(/[^A-Za-z0-9._-]/g, '')
}

/**
 * Every name this order could legitimately go out under, best guess first.
 *
 * The first entry is what the old `brokerSymbol` returned on its own, so an
 * account that was already configured correctly still sends exactly one order,
 * to exactly the name it always did. Everything after it exists only for the
 * account that would otherwise have taken UNKNOWN_SYMBOL and stopped there.
 *
 * [userSymbol] outranks everything, including the learned map. It is the name
 * the subscriber confirmed on the Trading Symbols screen, against the list read
 * off their own broker by `broker-symbols` -- an answer, where the rest of this
 * function is a search. When it is present the search still follows it, because
 * a saved name can go stale when someone moves account type and the fallbacks
 * cost nothing until the first one fails.
 */
function symbolCandidates(
  base: string,
  metadata: Record<string, unknown> | null,
  userSymbol?: string | null
): string[] {
  const out: string[] = []
  const push = (s: string | undefined | null) => {
    const v = (s ?? '').trim()
    if (v && !out.includes(v)) out.push(v)
  }

  const map = metadata?.symbol_map as Record<string, string> | undefined
  const suffix = safeSuffix(metadata)

  // The subscriber's own answer, ahead of anything this function inferred.
  push(userSymbol)

  // An explicit or previously-learned name is tried next, and always.
  push(map?.[base])

  // Then the canonical name, decorated the way this account decorates.
  if (suffix) push(base + suffix)
  push(base)

  // Then the aliases, each also offered in this account's decoration.
  for (const alias of aliasesFor(base)) {
    if (suffix) push(alias + suffix)
    push(alias)
  }

  return out
}

/**
 * Asks the broker what it actually lists, and picks this instrument out of it.
 *
 * Everything above is a *guess* -- a fixed table of names that most brokers use
 * for the same dozen instruments. Live accounts proved the table is not enough.
 * One Trade245 book lists gold as `Gold`, the Nasdaq as `.USTECH.` and the Dow
 * as `.US30.`; against the guessed list -- NAS100, USTEC, US100, NDX100,
 * USTECH, NASDAQ -- not one of those matched, so every signal on that account
 * spent six orders and filled none. A leading dot is not something a table can
 * be made to anticipate.
 *
 * MetaCopier will just tell us: `GET /accounts/{id}/symbols` is the account's
 * own Market Watch. Comparison is on letters and digits only, which is what
 * makes `.US30.` recognisable as US30 and `Gold` as GOLD -- the decoration that
 * defeats string matching is exactly what this strips.
 *
 * Called only after the guessed names have all been refused for a naming fault.
 * That keeps it off the hot path: an account whose names are already known --
 * because they were guessed right, confirmed by the user, or learned here once
 * and remembered -- never makes this request at all.
 *
 * Returns null when the broker cannot be asked or genuinely does not list the
 * instrument, which is the honest answer and the one the user needs to see.
 */
async function resolveFromBrokerList(
  accountId: string,
  base: string,
  suffix: string,
  apiKey: string
): Promise<string | null> {
  try {
    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/symbols`,
      { headers: { 'X-API-KEY': apiKey, 'Accept': 'application/json' } }
    )
    if (!res.ok) {
      console.warn(`[metacopier-execute] symbol list unavailable ${res.status}`)
      return null
    }

    const payload = await res.json()
    if (!Array.isArray(payload)) return null

    // Names only. Some brokers answer with strings, others with objects.
    const names: { name: string; key: string }[] = []
    for (const entry of payload) {
      const raw = typeof entry === 'string'
        ? entry
        : String((entry as Record<string, unknown>)?.name ??
                 (entry as Record<string, unknown>)?.symbol ?? '')
      const name = raw.trim()
      if (name) names.push({ name, key: baseSymbol(name) })
    }
    if (names.length === 0) return null

    // Shortest wins within a rank: a book carrying both `XAUUSD` and `XAUUSDpro`
    // means the plain one.
    const pick = (pred: (n: { name: string; key: string }) => boolean) =>
      names.filter(pred).sort((a, b) => a.name.length - b.name.length)[0]?.name ?? null

    const exact = pick((n) => n.key === base)
    if (exact) return exact

    if (suffix) {
      const decorated = baseSymbol(base + suffix)
      const hit = pick((n) => n.key === decorated)
      if (hit) return hit
    }

    // Our name carrying decoration we did not know about.
    const prefixed = pick((n) => n.key.startsWith(base) && n.key.length <= base.length + 5)
    if (prefixed) return prefixed

    // A different name for the same instrument -- alias table only. Free
    // substring matching would bind US30 to US300, and a mapping that points at
    // the wrong instrument is worse than none: the order fills, on something the
    // subscriber never chose.
    for (const alias of aliasesFor(base)) {
      const a = baseSymbol(alias)
      const hit = pick((n) => n.key === a || (n.key.startsWith(a) && n.key.length <= a.length + 5))
      if (hit) return hit
    }

    return null
  } catch (e) {
    console.warn(`[metacopier-execute] symbol list threw: ${(e as Error).message}`)
    return null
  }
}

/**
 * Checks a stop/target pair for the faults a broker answers with error 130
 * (invalid stops), before an order is spent finding out.
 *
 * There is no market price here -- MetaCopier's REST surface does not quote --
 * so this cannot check distance from the market. What it CAN check is that the
 * two levels are on the right sides of each other for the direction, which is
 * true of every valid bracket regardless of price:
 *
 *   Buy  -> stop below target
 *   Sell -> stop above target
 *
 * Plus a magnitude guard: two levels on the same instrument are within an order
 * of magnitude of each other. `sl 98699 / tp 988` on XAUUSD is a typed digit,
 * not a trade, and both live examples of this in `signals` came from the mentor
 * portal, which validates each field alone and never the pair.
 *
 * A level that is absent, zero, negative or unparseable means "no bracket" and
 * is normalised to 0 -- MetaCopier's own "not set" -- rather than rejected.
 */
function normalizeStops(
  orderType: string,
  slRaw: unknown,
  tpRaw: unknown
): { sl: number; tp: number; error?: string } {
  const level = (v: unknown): number => {
    const n = Number(v)
    return Number.isFinite(n) && n > 0 ? n : 0
  }

  const sl = level(slRaw)
  const tp = level(tpRaw)

  if (sl === 0 || tp === 0) return { sl, tp }

  // `startsWith`, not `===`. This function used to compare `orderType === 'Buy'`
  // back when those were the only two values it could take. Adding the four
  // pending variants broke it silently and in the worst possible direction: a
  // `BuyLimit` failed the equality, fell to the else branch, and was validated
  // as if it were a sell -- so every pending buy was rejected with "Stop 3320
  // is not above target 3400 for a sell", a message naming a direction the
  // caller never asked for.
  //
  // Widening an enum does not announce the exact-match comparisons elsewhere
  // that were relying on it being narrow.
  const isBuy = orderType.startsWith('Buy')
  const wrongWayRound = isBuy ? sl >= tp : sl <= tp
  if (wrongWayRound) {
    return {
      sl, tp,
      error: isBuy
        ? `Stop ${sl} is not below target ${tp} for a buy.`
        : `Stop ${sl} is not above target ${tp} for a sell.`,
    }
  }

  const ratio = Math.max(sl, tp) / Math.min(sl, tp)
  if (ratio > 10) {
    return {
      sl, tp,
      error: `Stop ${sl} and target ${tp} are not on the same price scale.`,
    }
  }

  return { sl, tp }
}

/**
 * How many positions are already open on this account for one symbol.
 *
 * Returns null when the broker could not be asked, which callers must treat as
 * "unknown" and never as "none" -- reading a failed request as zero would turn
 * every outage into an unlimited position cap.
 *
 * Compared on the canonical name so a `.m` / `.pro` book still counts against
 * the cap the user set against `XAUUSD`.
 */
async function countOpenPositions(
  accountId: string,
  names: string[],
  apiKey: string
): Promise<number | null> {
  try {
    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/positions`,
      { headers: { 'X-API-KEY': apiKey, 'Accept': 'application/json' } }
    )
    if (!res.ok) {
      console.warn(`[metacopier-execute] positions query failed ${res.status}`)
      return null
    }
    const positions = await res.json()
    if (!Array.isArray(positions)) return null

    // A broker symbol is one of this instrument's names plus decoration, so
    // compare on the prefix rather than on equality -- `XAUUSD.m` is an open
    // XAUUSD position.
    //
    // Against every name, not just the canonical one. On a broker that calls
    // gold GOLD, matching only "XAUUSD" counted zero open positions however many
    // were actually held, and max_trades -- a risk control the user set
    // deliberately -- silently stopped capping anything.
    //
    // BOTH sides are reduced to letters and digits. The held symbol always was;
    // the names were not, and once those started arriving as real broker
    // spellings -- `.US30.`, `Gold` -- the comparison could never match again,
    // because `US30`.startsWith(`.US30.`) is false for every position ever
    // opened. The cap was reading as "none open" on precisely the accounts that
    // needed the decoration handled.
    const wanted = names.map((n) => baseSymbol(n)).filter(Boolean)

    return positions.filter((p: Record<string, unknown>) => {
      const held = baseSymbol(p?.symbol)
      return wanted.some((n) => held.startsWith(n))
    }).length
  } catch (e) {
    console.warn(`[metacopier-execute] positions query threw: ${(e as Error).message}`)
    return null
  }
}

/**
 * An account's live broker link and margin state, from `/information`.
 *
 * Used for two things the executor could not previously tell apart: whether the
 * broker session is actually up *before* an order is spent finding out, and --
 * on a bare `[BROKER_REJECTION]` -- which of a read-only login, no margin or a
 * closed symbol was the real cause.
 *
 * Every field degrades to "don't know" rather than throwing. A null `connected`
 * means the question could not be answered, and callers treat that as "proceed",
 * never as "disconnected" -- a flaky probe must not block a trade.
 */
interface AccountState {
  connected: boolean | null
  wrongCredentials: boolean
  investorPassword: boolean
  tradingDisabled: boolean
  freeMargin: number | null
}

async function readAccountState(accountId: string, apiKey: string): Promise<AccountState> {
  const unknown: AccountState = {
    connected: null, wrongCredentials: false, investorPassword: false,
    tradingDisabled: false, freeMargin: null,
  }
  try {
    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/information`,
      { headers: { 'X-API-KEY': apiKey, 'Accept': 'application/json' }, signal: AbortSignal.timeout(6000) }
    )
    if (!res.ok) {
      console.warn(`[metacopier-execute] account information unavailable ${res.status}`)
      return unknown
    }
    const info = await res.json() as Record<string, unknown>
    const num = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : null)
    return {
      connected: typeof info.connected === 'boolean' ? info.connected : null,
      wrongCredentials: info.wrongCredentials === true,
      investorPassword: info.isInvestorPassword === true,
      tradingDisabled: info.tradingDisabled === true || info.tradeDisabled === true,
      freeMargin: num(info.freeMargin),
    }
  } catch (e) {
    console.warn(`[metacopier-execute] account information threw: ${(e as Error).message}`)
    return unknown
  }
}

/**
 * Turns a bare `[BROKER_REJECTION]` into the specific thing that was wrong.
 *
 * MetaCopier collapses a whole class of broker refusals -- no margin, a
 * read-only login, a symbol the broker has closed, a size outside the book's
 * limits -- into one token with no reason attached. The reason is on
 * `/information` and `/symbols/{symbol}`, which the hot path has no cause to
 * read until an order has already been refused. Best-effort: any failure here
 * just leaves the generic message in place.
 */
async function explainRejection(
  accountId: string,
  brokerSymbol: string,
  lots: number,
  apiKey: string,
): Promise<{ code: string; message: string } | null> {
  const state = await readAccountState(accountId, apiKey)

  if (state.connected === false) {
    return {
      code: 'ACCOUNT_DISCONNECTED',
      message: 'Your trading account is not connected to the broker right now. Reconnect it in Broker Setup and try again.',
    }
  }
  if (state.wrongCredentials) {
    return {
      code: 'ACCOUNT_WRONG_CREDENTIALS',
      message: 'Your broker rejected the saved login. Reconnect the account with the trading (master) password.',
    }
  }
  if (state.investorPassword || state.tradingDisabled) {
    return {
      code: 'ACCOUNT_READONLY',
      message: 'This account cannot place orders -- it is linked with an investor (read-only) password, or the broker has trading disabled on it.',
    }
  }

  try {
    const res = await fetch(
      `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(accountId)}/symbols/${encodeURIComponent(brokerSymbol)}`,
      { headers: { 'X-API-KEY': apiKey, 'Accept': 'application/json' }, signal: AbortSignal.timeout(6000) },
    )
    if (res.ok) {
      const spec = await res.json() as Record<string, unknown>
      const n = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : null)
      const min = n(spec.minimalVolume)
      const max = n(spec.maximalVolume)
      if (spec.disabled === true) {
        return { code: 'MARKET_CLOSED', message: `Your broker has ${brokerSymbol} closed for trading right now.` }
      }
      if (min !== null && lots < min) {
        return { code: 'INVALID_VOLUME', message: `${lots} lots is below your broker's minimum of ${min} for ${brokerSymbol}.` }
      }
      if (max !== null && lots > max) {
        return { code: 'INVALID_VOLUME', message: `${lots} lots is above your broker's maximum of ${max} for ${brokerSymbol}.` }
      }
    }
  } catch (e) {
    console.warn(`[metacopier-execute] symbol spec threw: ${(e as Error).message}`)
  }

  if (state.freeMargin !== null && state.freeMargin <= 0) {
    return {
      code: 'INSUFFICIENT_MARGIN',
      message: 'Your account has no free margin. Close an open position or add funds, then try again.',
    }
  }

  return null
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  try {
    const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? ''
    const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    const METACOPIER_API_KEY = Deno.env.get('METACOPIER_API_KEY') ?? ''

    const novaHost = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    const body = await req.json().catch(() => ({}))
    const { license_key, pair, side, volume, sl, tp, signal_id, dry_run, order_type, open_price, pending_expiry_seconds } = body

    const key = license_key ? String(license_key).trim().toUpperCase() : null
    const dryRun = dry_run === true

    // ---- Every outcome is recorded, not just the ones the broker saw --------
    //
    // This function used to write `signal_logs` only after MetaCopier had
    // answered. Every rejection before that point -- unknown licence, no
    // account connected, symbol switched off, position cap reached -- returned
    // to a handset and vanished. `signal_logs` was empty, which reads as "no
    // signal ever arrived" when the truth may be "signals arrived and were
    // refused here, for a reason nobody recorded".
    //
    // A trade that does not happen is an event. It is logged like one.
    let licenseId: string | null = null
    let eaId: string | null = null

    const finish = async (
      status: string,
      httpStatus: number,
      payload: Record<string, unknown>,
      detail: Record<string, unknown> = {}
    ) => {
      try {
        await novaHost.from('signal_logs').insert([{
          license_id: licenseId,
          license_key: key,
          ea_id: eaId,
          raw_data: {
            signal_id: signal_id ?? null,
            pair: pair ?? null,
            side: side ?? null,
            volume: volume ?? null,
            sl: sl ?? null,
            tp: tp ?? null,
            dry_run: dryRun,
            outcome: payload.code ?? status,
            ...detail,
          },
          status: dryRun ? `dry_run_${status}` : status,
        }])
      } catch (e) {
        // Never let the audit trail take the trade down with it.
        console.error(`[metacopier-execute] could not write signal_logs: ${(e as Error).message}`)
      }

      return new Response(JSON.stringify(payload), {
        status: httpStatus,
        headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
      })
    }

    // Fail closed and loudly rather than pretending a trade went out.
    if (!METACOPIER_API_KEY) {
      console.error('METACOPIER_API_KEY is not configured')
      return await finish('failed', 500, {
        success: false, code: 'SERVER_MISCONFIGURED',
        error: 'Server misconfiguration.',
      })
    }

    // NOTE: the MetaCopier account is resolved from the licence server-side.
    // It is deliberately NOT taken from the request -- otherwise a caller could
    // point a trade at any account id they liked.
    if (!key || !pair || !side) {
      return await finish('rejected', 400, {
        success: false, code: 'MISSING_PARAMETERS',
        error: 'Missing required trade parameters.',
      })
    }

    const orderType = toOrderType(side, order_type)
    if (!orderType) {
      return await finish('rejected', 400, {
        success: false, code: 'BAD_SIDE',
        error: `Unsupported side "${side}". Expected BUY or SELL.`,
      })
    }

    const lots = Number(volume)
    if (!Number.isFinite(lots) || lots <= 0) {
      return await finish('rejected', 400, {
        success: false, code: 'BAD_VOLUME',
        error: 'Volume must be a positive number of lots.',
      })
    }

    // ---- Authorize: the licence must exist, be active, and be unexpired ------
    // The caller is a device holding a mentor-issued key, not a logged-in user,
    // so the licence IS the credential. Never trust the client's word for it.
    const { data: license, error: licErr } = await novaHost
      .from('licenses')
      .select(
        'id, ea_id, status, expires_at, allowed_symbols, metadata, ' +
        'expert_advisors:expert_advisors!licenses_ea_id_fkey(name, display_name)'
      )
      .eq('license_key', key)
      .maybeSingle()

    if (licErr) throw licErr

    if (!license) {
      return await finish('rejected', 401, {
        success: false, code: 'LICENCE_UNKNOWN',
        error: 'Licence not recognised.',
      })
    }

    licenseId = license.id
    eaId = license.ea_id

    if (license.status !== 'active') {
      return await finish('rejected', 403, {
        success: false, code: 'LICENCE_INACTIVE',
        error: 'Licence is not active.',
      }, { licence_status: license.status })
    }
    if (license.expires_at && new Date(license.expires_at) < new Date()) {
      return await finish('rejected', 403, {
        success: false, code: 'LICENCE_EXPIRED',
        error: 'Licence has expired.',
      }, { expires_at: license.expires_at })
    }

    const metadata = (license.metadata ?? null) as Record<string, unknown> | null
    const account_id = metadata?.metacopier_account_id as string | undefined

    if (!account_id) {
      return await finish('rejected', 409, {
        success: false, code: 'NO_ACCOUNT_LINKED',
        error: 'No trading account is connected to this licence.',
      })
    }

    // Respect a per-licence symbol restriction when one is set.
    const allowed = Array.isArray(license.allowed_symbols) ? license.allowed_symbols : []
    const cleanPair = baseSymbol(pair)
    if (allowed.length > 0 && !allowed.map((s: string) => baseSymbol(s)).includes(cleanPair)) {
      return await finish('rejected', 403, {
        success: false, code: 'SYMBOL_NOT_LICENSED',
        error: `Symbol ${cleanPair} is not enabled on this licence.`,
      }, { allowed })
    }

    // ---- Apply the subscriber's own per-symbol plan --------------------------
    //
    // licenses.allowed_symbols above is the MENTOR's allowance. This is the
    // other half: of the symbols the robot permits, which ones this subscriber
    // actually enabled, at what size, and how many at once. Set on the Trading
    // Symbols screen and pushed here by sync-symbol-config.
    //
    // Enforced server-side because the handset is not a trustworthy place to
    // keep a cap. A stale, rolled-back or tampered-with client would otherwise
    // size positions off numbers the user thought they had changed.
    //
    // Absent configuration is NOT a block: a licence that has never synced has
    // no rows here, and those installs must keep trading exactly as before.
    const { data: symbolCfg, error: cfgErr } = await novaHost
      .from('license_symbol_config')
      .select('enabled, lot, max_trades, smart_lot, broker_symbol')
      .eq('license_id', license.id)
      .eq('symbol', cleanPair)
      .maybeSingle()

    if (cfgErr) {
      // Reading the plan failed, which says nothing about what the user wants.
      // Logged and skipped rather than thrown: a status query hiccup must not
      // become a trading outage.
      console.warn(`[metacopier-execute] symbol config unreadable: ${cfgErr.message}`)
    }

    // The name the subscriber confirmed for this instrument on their own broker.
    // Read out here rather than inside the block below because it is needed for
    // every order, including on a licence that has no per-symbol plan at all.
    const userSymbol = (symbolCfg?.broker_symbol ?? null) as string | null

    let effectiveLots = lots
    let cappedFrom: number | null = null

    if (symbolCfg) {
      if (symbolCfg.enabled === false) {
        return await finish('rejected', 403, {
          success: false, code: 'SYMBOL_DISABLED',
          error: `${cleanPair} is switched off in your trading symbols.`,
        })
      }

      // The configured size is a ceiling, not a replacement. A client sending
      // less than the user configured is honoured -- it may be sizing down for
      // a reason this function cannot see -- but nothing may exceed the number
      // the user actually set on the screen.
      const riskProfile = metadata?.risk_profile as Record<string, unknown> | undefined

      const smartLotSize = Number(riskProfile?.smart_lot_size) || 0
      const ceiling = symbolCfg.smart_lot && smartLotSize > 0
        ? smartLotSize
        : Number(symbolCfg.lot) || 0

      if (ceiling > 0 && effectiveLots > ceiling) {
        console.log(
          `[metacopier-execute] ${cleanPair} volume ${effectiveLots} capped to configured ${ceiling}`
        )
        cappedFrom = effectiveLots
        effectiveLots = ceiling
      }

      // ---- Concurrency cap --------------------------------------------------
      const maxTrades = Number(symbolCfg.max_trades) || 0
      if (maxTrades > 0) {
        const open = await countOpenPositions(account_id, symbolCandidates(cleanPair, metadata, userSymbol), METACOPIER_API_KEY)

        // null means the broker could not be asked. Fail open and say so: a
        // transient failure on the positions endpoint blocking every order is a
        // worse outcome than briefly exceeding a self-imposed cap, and silence
        // here would make that choice invisible.
        if (open === null) {
          console.warn(
            `[metacopier-execute] could not count open ${cleanPair} positions; cap not enforced`
          )
        } else if (open >= maxTrades) {
          return await finish('rejected', 409, {
            success: false, code: 'POSITION_CAP',
            error: `Already holding ${open} ${cleanPair} position(s); your limit is ${maxTrades}.`,
          }, { open, max_trades: maxTrades })
        }
      }
    }

    // ---- Bracket sanity ------------------------------------------------------
    // Caught here rather than at the broker: a rejected order costs the signal,
    // and "invalid stops" arrives from MetaTrader as a bare numeric code that
    // tells the subscriber nothing about which of their mentor's two numbers was
    // wrong.
    const stops = normalizeStops(orderType, sl, tp)
    if (stops.error) {
      return await finish('rejected', 400, {
        success: false, code: 'INVALID_STOPS',
        error: stops.error,
      }, { sl_parsed: stops.sl, tp_parsed: stops.tp })
    }

    // ---- Place the position -------------------------------------------------
    //
    // One canonical instrument, possibly several broker names for it. The list
    // is walked only as far as the first name the broker recognises, and ONLY
    // past a naming fault: an order rejected for volume, margin, stops or a
    // closed market stops the walk where it stands. Retrying those would risk a
    // second position on an account that had already accepted the first.
    const candidates = symbolCandidates(cleanPair, metadata, userSymbol)

    // Stable across attempts. The same trade keeps the same id no matter how
    // many names it took to find the instrument, so a retry can never be
    // mistaken by MetaCopier for a second order.
    const requestId = requestIdFrom(String(signal_id ?? `${license.id}:${Date.now()}`))

    // ---- Pending entry -------------------------------------------------------
    // 0 is MetaCopier's "fill at market" flag. A pending orderType sent with 0
    // is rejected, and a market orderType sent WITH a price silently becomes a
    // pending order that may never fill -- so the two fields have to agree, and
    // that is checked here rather than discovered on a live signal.
    const pending = isPending(orderType)
    const entryPrice = Number(open_price)

    // Clamped rather than trusted. A caller asking for a year-long pending order
    // is either confused or tampering, and either way the broker would be left
    // holding an order nobody remembers placing. One day is the longest horizon
    // the scanner's own modes produce.
    const MAX_PENDING_EXPIRY = 24 * 60 * 60
    const requestedExpiry = Number(pending_expiry_seconds)
    const expirySeconds = Number.isFinite(requestedExpiry) && requestedExpiry > 0
      ? Math.min(Math.round(requestedExpiry), MAX_PENDING_EXPIRY)
      : 0

    if (pending && (!Number.isFinite(entryPrice) || entryPrice <= 0)) {
      return await finish('rejected', 400, {
        success: false, code: 'MISSING_ENTRY_PRICE',
        error: `A ${orderType} order needs the price to wait at.`,
      }, { order_type, open_price })
    }

    // A pending entry on the wrong side of its own bracket is not a trade. A
    // BuyLimit below its stop loss, or a BuyStop above its take profit, is a
    // typed digit -- and the broker's rejection for it is a bare numeric code.
    if (pending && stops.sl > 0 && stops.tp > 0) {
      const buy = orderType.startsWith('Buy')
      const bracketWrong = buy
        ? !(stops.sl < entryPrice && entryPrice < stops.tp)
        : !(stops.tp < entryPrice && entryPrice < stops.sl)

      if (bracketWrong) {
        return await finish('rejected', 400, {
          success: false, code: 'ENTRY_OUTSIDE_BRACKET',
          error: `Entry ${entryPrice} is not between the stop ${stops.sl} and target ${stops.tp}.`,
        }, { order_type, open_price, sl: stops.sl, tp: stops.tp })
      }
    }

    const buildRequest = (forSymbol: string) => ({
      symbol: forSymbol,
      orderType,
      volume: effectiveLots,
      // 0 => market. A price only when the type is one of the four pending
      // variants, so the two can never disagree.
      openPrice: pending ? entryPrice : 0,
      // Broker-side cancellation for an order that never fills. Sent only on
      // pending types -- MetaCopier ignores it on a market order, and including
      // it anyway would suggest a lifetime that does not exist.
      ...(pending && expirySeconds > 0 ? { pendingExpirySeconds: expirySeconds } : {}),
      stopLoss: stops.sl,                 // 0 => no stop loss
      takeProfit: stops.tp,               // 0 => no take profit
      requestId,
      comment: tradeComment(license),
    })

    const symbol = candidates[0]
    const positionRequest = buildRequest(symbol)

    // ---- Dry run -------------------------------------------------------------
    // Everything above has run for real: the licence was authorised, the plan
    // applied, the size capped, the bracket checked and the broker symbol
    // resolved. Only the order is withheld. This exists so the pipeline can be
    // proved end to end -- from the app, from a support session, from a
    // deployment check -- without a live position being the test instrument.
    if (dryRun) {
      const open = await countOpenPositions(account_id, candidates, METACOPIER_API_KEY)

      // A dry run is the one place it is worth paying for certainty: if none of
      // the guessed names would be recognised, say so HERE rather than letting
      // the user find out on a live signal.
      const discovered = await resolveFromBrokerList(
        account_id, cleanPair, safeSuffix(metadata), METACOPIER_API_KEY
      )

      // What a REAL order would send, which is candidates[0] and nothing else.
      //
      // Reporting `discovered` here instead would have been the exact failure
      // this endpoint exists to prevent: with a user-set name of "Silver" ahead
      // of a learned "Gold", a live order goes out as Silver while the dry run
      // said Gold. A dry run that does not predict the order is worse than no
      // dry run, because it is trusted.
      //
      // `discovered` is still reported -- as a diagnostic beside the answer,
      // never in place of it.
      const message = discovered === null
        ? `Would send ${orderType} ${symbol} ${effectiveLots} lots, but your broker does ` +
          `not appear to list ${cleanPair} under any name.`
        : discovered !== symbol
          ? `Would send ${orderType} ${symbol} ${effectiveLots} lots. Your broker lists ` +
            `this instrument as "${discovered}".`
          : `Would send ${orderType} ${symbol} ${effectiveLots} lots.`

      return await finish('skipped', 200, {
        success: true,
        code: 'DRY_RUN',
        message,
        would_send: positionRequest,
        account_id,
        broker_reachable: open !== null,
        candidates,
        /** What the account's Market Watch says, for comparison with candidates[0]. */
        broker_symbol: discovered,
        open_positions: open,
        capped_from: cappedFrom,
      }, {
        would_send: positionRequest,
        account_id,
        broker_reachable: open !== null,
        broker_symbol: discovered,
      })
    }

    // ---- Pre-flight: is the broker session actually up? --------------------
    //
    // MetaCopier accepts an order the instant an account is *registered*, well
    // before the MT4/MT5 session behind it has connected -- and a signal that
    // lands in that window is refused with `[ACCOUNT_IS_NOT_CONNTECTED]`, which
    // reads to the subscriber as "the broker refused the trade". One
    // `/information` read here turns that first minute after a reconnect from a
    // failed trade into a clearly-labelled skipped one.
    //
    // Only a definite `connected: false` short-circuits. A read that fails or is
    // ambiguous falls straight through to the order attempt, which surfaces the
    // real error rather than blocking a trade on a flaky probe.
    const link = await readAccountState(account_id, METACOPIER_API_KEY)
    if (link.connected === false) {
      if (link.wrongCredentials) {
        return await finish('failed', 409, {
          success: false,
          code: 'ACCOUNT_WRONG_CREDENTIALS',
          error: 'Your broker rejected the saved login. Reconnect the account with the trading (master) password.',
        }, { account_id, connected: false, wrong_credentials: true })
      }

      // "Still coming up" and "been down for an hour" call for different words.
      // A link made in the last few minutes, or one metacopier-connect last saw
      // as `connecting`, is the reconnect window -- hold the signal quietly. Any
      // older disconnection is a real fault the user needs to act on.
      const linkedAt = Date.parse(String(metadata?.connected_at ?? '')) || 0
      const stillConnecting =
        metadata?.metacopier_status === 'connecting' ||
        (linkedAt > 0 && Date.now() - linkedAt < 5 * 60 * 1000)

      return await finish(stillConnecting ? 'skipped' : 'failed', 409, {
        success: false,
        code: stillConnecting ? 'ACCOUNT_CONNECTING' : 'ACCOUNT_DISCONNECTED',
        error: stillConnecting
          ? 'Your broker was still connecting when this signal arrived, so it was not placed. ' +
            'Newer signals will go through once the connection is up.'
          : "Your broker isn't connected right now. Reconnect it in Broker Setup, then try again.",
      }, { account_id, connected: false, metacopier_status: metadata?.metacopier_status ?? null })
    }

    let placed: string | null = null
    const attempted: string[] = []
    let lastDetail = ''
    let lastStatus = 0

    /**
     * Sends the order under one name.
     *
     * Returns 'placed' when the broker took it, 'wrong-name' when it refused
     * because it does not list that symbol, and 'stop' for every other refusal.
     * The distinction is the whole safety property here: a naming fault means
     * nothing happened and another name is safe to try, while a volume, margin,
     * stop or closed-market refusal is a property of the ORDER and would fail
     * identically under every alias -- retrying those is how one signal becomes
     * two positions.
     */
    const attempt = async (candidate: string): Promise<'placed' | 'wrong-name' | 'stop'> => {
      attempted.push(candidate)

      const mcResponse = await fetch(
        `${METACOPIER_BASE}/rest/api/v1/accounts/${encodeURIComponent(account_id)}/positions`,
        {
          method: 'POST',
          headers: {
            'X-API-KEY': METACOPIER_API_KEY,
            'Content-Type': 'application/json',
            'Accept': 'application/json',
          },
          body: JSON.stringify(buildRequest(candidate)),
        }
      )

      // Success is 204 No Content -- there is no body to parse.
      if (mcResponse.ok) {
        placed = candidate
        return 'placed'
      }

      lastStatus = mcResponse.status
      lastDetail = await mcResponse.text()
      console.error(
        `[MetaCopier] open position failed ${lastStatus} for ${candidate}: ${lastDetail}`
      )

      const faults = (lastDetail.match(/\[([A-Z0-9_]+)\]/g) ?? []).map((c) => c.slice(1, -1))
      return faults.some((c) => c.includes('SYMBOL')) ? 'wrong-name' : 'stop'
    }

    let outcome: 'placed' | 'wrong-name' | 'stop' = 'wrong-name'

    for (const candidate of candidates) {
      outcome = await attempt(candidate)
      if (outcome !== 'wrong-name') break
    }

    // ---- Last resort: ask the broker what it calls this ---------------------
    //
    // Reached only when every name we could think of was refused as unknown --
    // which, on a book that spells the Nasdaq `.USTECH.`, is every signal
    // forever. One request converts that from a permanent failure into a
    // one-time cost: the answer is written back to the licence below, so the
    // next signal on this instrument goes out as a single order to a known name.
    if (!placed && outcome === 'wrong-name') {
      const discovered = await resolveFromBrokerList(
        account_id, cleanPair, safeSuffix(metadata), METACOPIER_API_KEY
      )

      if (discovered && !attempted.includes(discovered)) {
        console.log(`[MetaCopier] broker lists ${cleanPair} as "${discovered}" -- retrying`)
        outcome = await attempt(discovered)
      }
    }

    if (!placed) {
      const symbolTried = attempted[attempted.length - 1] ?? cleanPair

      // MetaCopier reports faults as bracketed codes, e.g. ["[UNKNOWN_SYMBOL]"].
      // Translated here because the alternative the subscriber sees is a raw
      // MetaTrader number -- 130, 131, 134, 4109 -- which tells them nothing
      // about which of these they can actually fix.
      // MetaCopier reports faults as bracketed codes, e.g. ["[UNKNOWN_SYMBOL]"],
      // but plain-language validation errors arrive unbracketed. Both are
      // matched, because the second kind is what a malformed request produces
      // and it is the kind that reads as "the broker refused" when it is really
      // "we sent something invalid".
      const codes = (lastDetail.match(/\[([A-Z0-9_]+)\]/g) ?? []).map((c) => c.slice(1, -1))
      const hit = (needle: string) => codes.some((c) => c.includes(needle))
      const says = (re: RegExp) => re.test(lastDetail)

      let code = codes[0] ?? 'BROKER_REJECTED'
      let message = 'Trade could not be placed with the broker.'

      // Connection state first -- it is the most common cause and the most
      // actionable, and MetaCopier spells its token `[ACCOUNT_IS_NOT_CONNTECTED]`
      // (their typo, "CONNTECTED"). The previous check looked for `NOT_CONNECTED`
      // and missed it on every live account, so every reconnect-window failure
      // fell through to the catch-all "Trade could not be placed with the broker".
      if (hit('CONNTECT') || hit('NOT_CONNECTED') || hit('OFFLINE') || hit('DISCONNECT')) {
        code = 'ACCOUNT_DISCONNECTED'
        message = "Your broker isn't connected right now. Reconnect it in Broker Setup, then try again."
      } else if (hit('SYMBOL')) {
        code = 'UNKNOWN_SYMBOL'
        // By this point the account's own symbol list has been read and searched,
        // so this is no longer "we ran out of guesses" -- it is "your broker does
        // not carry this instrument". Said plainly, because the action it calls
        // for is different: there is nothing to add to a mapping table, and the
        // subscriber needs to either switch the symbol off or change account.
        message =
          `Your broker does not offer ${cleanPair} on this account. Its symbol list was ` +
          `checked directly and nothing matching it is there. Tried: ${attempted.join(', ')}. ` +
          `Turn ${cleanPair} off in Trading Symbols, or set the exact name from your ` +
          `MetaTrader Market Watch if you can see it there.`
      } else if (says(/requestId|must be less than|must be greater than|constraint|validation/i)) {
        // Not the broker's doing. Named separately so it can never again be read
        // as a market or account problem: every field of this request is built
        // server-side, so a validation failure is ours to fix and nothing the
        // subscriber does will change it.
        //
        // This branch exists because its absence cost three live signals. A
        // `requestId` of 2105094537 against a field capped at 999 came back as
        // a bare 400 and was reported to the user as "Trade could not be placed
        // with the broker" -- pointing at the broker, the account and the
        // market, none of which had anything to do with it.
        code = 'REQUEST_INVALID'
        message =
          'NovaHost sent an order the trading provider rejected as malformed. ' +
          'This is a fault on our side, not with your account or your broker.'
      } else if (hit('VOLUME') || hit('LOT')) {
        code = 'INVALID_VOLUME'
        message = `Your broker rejected ${effectiveLots} lots on ${symbolTried}. It is outside the size this account allows.`
      } else if (hit('MONEY') || hit('MARGIN') || hit('FUNDS')) {
        code = 'INSUFFICIENT_MARGIN'
        message = `Not enough free margin to open ${effectiveLots} lots of ${symbolTried}.`
      } else if (hit('STOP') || hit('INVALID_PRICE')) {
        code = 'INVALID_STOPS'
        message = `Your broker refused the stop or target on ${symbolTried} -- they are too close to the current price.`
      } else if (hit('DISABLED') || hit('PROHIBIT') || hit('CLOSED') || hit('MARKET')) {
        code = 'TRADING_PROHIBITED'
        message = `Trading ${symbolTried} is not permitted on this account right now -- the market may be closed, or the account may be read-only.`
      } else if (hit('BROKER_REJECT') || code === 'BROKER_REJECTED') {
        // MetaCopier's catch-all for a broker refusal with no reason attached.
        // Ask `/information` and `/symbols` what it actually was -- read-only
        // login, no margin, a closed market, a size outside the book's limits --
        // rather than handing the subscriber a token they cannot act on.
        const explained = await explainRejection(account_id, symbolTried, effectiveLots, METACOPIER_API_KEY)
        if (explained) {
          code = explained.code
          message = explained.message
        } else {
          code = 'BROKER_REJECTED'
          message =
            `Your broker refused the order on ${symbolTried} without giving a reason. ` +
            `Check the symbol's trading hours and your free margin.`
        }
      }

      return await finish('failed', 502, {
        success: false,
        code,
        error: message,
        details: lastDetail,
      }, {
        broker_status: lastStatus,
        detail: lastDetail,
        request: buildRequest(symbolTried),
        account_id,
        tried: attempted,
      })
    }

    // ---- Remember what this broker calls it ---------------------------------
    //
    // Discovery costs one rejected order per instrument, and only the first
    // time. Writing the answer back to the licence means the next signal on the
    // same instrument goes out as a single order to a known name, and it means
    // the account's real vocabulary is visible in the database instead of being
    // rediscovered on every trade.
    //
    // Best-effort: a position is already open, and failing to record how it was
    // named must never turn a filled trade into a reported failure.
    const filledAs: string = placed
    const knownMap = (metadata?.symbol_map ?? {}) as Record<string, string>
    if (knownMap[cleanPair] !== filledAs) {
      const { error: mapErr } = await novaHost
        .from('licenses')
        .update({ metadata: { ...(metadata ?? {}), symbol_map: { ...knownMap, [cleanPair]: filledAs } } })
        .eq('id', license.id)

      if (mapErr) {
        console.warn(
          `[metacopier-execute] could not remember ${cleanPair} -> "${filledAs}": ${mapErr.message}`
        )
      } else {
        console.log(
          `[MetaCopier] licence ${license.id}: ${cleanPair} trades as "${filledAs}" here -- remembered`
        )
      }
    }

    return await finish('executed', 200, {
      success: true,
      code: 'EXECUTED',
      // effectiveLots, not lots: reporting the requested size when a cap reduced
      // it tells the user they hold a position they do not hold.
      message: `${orderType} ${filledAs} ${effectiveLots} lots sent.`,
      requestId,
    }, { request: buildRequest(filledAs), account_id, capped_from: cappedFrom, tried: attempted })
  } catch (error) {
    console.error('[metacopier-execute] fatal:', error)
    return new Response(
      JSON.stringify({ success: false, code: 'FATAL', error: (error as Error).message }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } }
    )
  }
})
