import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
import {
  linkStatusOf, openBridge, resolveLink, withoutLink,
  type BrokerBridge, type OrderRefusal, type OrderType,
} from '../_shared/bridge/index.ts'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

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
 * function is all of it. See [magicFor] for the undecorated half of a trade's
 * identity.
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
 * network hiccup turning one mentor call into two positions. (A later name in
 * the candidate walk is seeded with the name as well -- see `requestIdFor`.)
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

/**
 * MetaTrader's own field for "which robot placed this".
 *
 * The comment cannot carry that job alone. MetaCopier prepends
 * `API|<requestId>|` to every comment on the open-position endpoint -- their
 * schema documents it, their modify endpoint explicitly cannot rewrite it
 * ("only volume, open price, take profit, and stop loss can be modified"), and
 * the `hideComment` / `customComment` switches they do offer apply only to the
 * copier path, which NovaHost does not use. So a trade reads as
 * `API|780|QuantumBreaker-NovaHost` in the terminal and there is no request we
 * can send that changes it.
 *
 * The magic number is the field MetaTrader provides for exactly this, it has
 * its own column, and nothing decorates it. Filtering, reporting and "was this
 * mine or the robot's" all work off it in every terminal and every third-party
 * tool.
 *
 * The namespace is the point: every NovaHost trade begins 77, so the whole
 * platform's activity is one filter, while the six digits after it are derived
 * from the robot so two robots on one account stay distinguishable.
 *
 * Kept under eight digits deliberately. MT5 stores the magic as a 64-bit value
 * but MT4 stores it as a signed 32-bit int (ceiling 2,147,483,647), and a magic
 * that overflows on MT4 is silently truncated into somebody else's namespace.
 */
const MAGIC_PREFIX = 77
const MAGIC_ROBOT_RANGE = 1_000_000

/**
 * A stable magic number for one robot. Same robot, same number, forever.
 *
 * Derived rather than stored because it has to be identical on every account
 * this robot ever trades, including accounts linked before the column existed --
 * and a value computed from the id is that, with nothing to migrate.
 */
function magicFor(eaId: unknown): string {
  const seed = String(eaId ?? '').trim()
  if (!seed) return String(MAGIC_PREFIX * MAGIC_ROBOT_RANGE)

  // Same FNV-1a as requestIdFrom, for the same reason: cheap, stable, and
  // dependency-free.
  let h = 2166136261
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return String(MAGIC_PREFIX * MAGIC_ROBOT_RANGE + (Math.abs(h | 0) % MAGIC_ROBOT_RANGE))
}

/**
 * The smallest volume increment any broker on this platform accepts.
 *
 * Every account seen here quotes `stepVolume: 0.01` and `minimalVolume: 0.01`.
 * It is a constant rather than a per-symbol read on purpose: this runs on the
 * hot path ahead of the order, and an extra round trip to `/symbols/{name}` to
 * confirm a value that has never varied would cost every trade latency to
 * protect against a book nobody trades. `explainRejection` still reads the real
 * spec on the way out, so a broker that genuinely differs is reported by name
 * rather than silently mis-sized.
 */
const LOT_STEP = 0.01

/**
 * The nearest tradeable volume at or below [value].
 *
 * Down, never up: [value] is already the user's risk budget expressed in lots,
 * and rounding up spends more of their account than they authorised.
 */
function toLotStep(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return LOT_STEP
  // Scaled to integers before flooring -- 0.07/0.01 is 6.999999999999999 in
  // binary floating point, and flooring that gives 0.06.
  const steps = Math.floor(Math.round(value * 1000) / (LOT_STEP * 1000))
  return Math.max(LOT_STEP, Math.round(steps * LOT_STEP * 100) / 100)
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
 * That rejection was this pipeline's most expensive silence: `broker-connect`
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
  BTCUSD: ['BTCUSD', 'BITCOIN', 'BTC', 'XBTUSD'],
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

/**
 * Whether a chart-reported symbol should count as one the licence allows.
 *
 * The chart scanner reports whatever text is printed on the chart, which on
 * many brokers carries their own decoration -- BTCUSDM, XAUUSDPRO -- rather
 * than the canonical name a mentor's allowance is written against. Reading
 * that as "not licensed" was the actual fault behind a subscriber whose
 * licence allowed BTCUSD being refused it outright: the allowance said
 * BTCUSD, the chart said BTCUSDM, and the two were compared for exact
 * equality with no room for a broker's own spelling.
 *
 * Checked against every member of the canonical's alias family, not just
 * itself -- an allowance written as GOLD must still cover a chart reporting
 * XAUUSDM. The extra characters must be LETTERS ONLY: this gate decides
 * whether a trade is allowed to happen at all, so unlike the order-routing
 * lookups elsewhere in this file, a length-tolerant guess is not safe here
 * -- US30 must never satisfy an allowance written for US300, and a trailing
 * digit is a different instrument, never a broker's decoration.
 */
function coversSymbol(reported: string, allowedCanonical: string): boolean {
  const family = aliasesFor(allowedCanonical)
  const members = family.length > 0 ? family : [allowedCanonical]
  return members.some((member) => {
    if (reported === member) return true
    if (!reported.startsWith(member)) return false
    const extra = reported.slice(member.length)
    return extra.length > 0 && extra.length <= 5 && /^[A-Z]+$/.test(extra)
  })
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
  bridge: BrokerBridge,
  base: string,
  suffix: string,
): Promise<string | null> {
  try {
    const read = await bridge.symbols()
    if (!read.ok) {
      console.warn(`[broker-execute] symbol list unavailable ${read.status}`)
      return null
    }

    const names = read.data.map((name) => ({ name, key: baseSymbol(name) }))
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
    console.warn(`[broker-execute] symbol list threw: ${(e as Error).message}`)
    return null
  }
}

/**
 * Checks a stop/target pair for the faults a broker answers with error 130
 * (invalid stops), before an order is spent finding out.
 *
 * There is no market price here -- the order path does not fetch a quote --
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
 * is normalised to 0 -- the bridge's "not set" -- rather than rejected.
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
  bridge: BrokerBridge,
  names: string[],
): Promise<number | null> {
  try {
    const read = await bridge.positions()
    if (!read.ok) {
      console.warn(`[broker-execute] positions query failed ${read.status}`)
      return null
    }
    const positions = read.data

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

    return positions.filter((p) => {
      const held = baseSymbol(p.symbol)
      return wanted.some((n) => held.startsWith(n))
    }).length
  } catch (e) {
    console.warn(`[broker-execute] positions query threw: ${(e as Error).message}`)
    return null
  }
}

/**
 * An account's live broker link and margin state, from the account summary.
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

async function readAccountState(bridge: BrokerBridge): Promise<AccountState> {
  const unknown: AccountState = {
    connected: null, wrongCredentials: false, investorPassword: false,
    tradingDisabled: false, freeMargin: null,
  }
  try {
    const read = await bridge.accountSummary(6000)
    if (!read.ok) {
      console.warn(`[broker-execute] account information unavailable ${read.status}`)
      return unknown
    }
    const info = read.data
    return {
      connected: info.connected,
      wrongCredentials: info.wrongCredentials,
      investorPassword: info.investorPassword,
      tradingDisabled: info.tradingDisabled,
      freeMargin: info.freeMargin,
    }
  } catch (e) {
    console.warn(`[broker-execute] account information threw: ${(e as Error).message}`)
    return unknown
  }
}

/**
 * Turns a refusal that gave no reason into the specific thing that was wrong.
 *
 * MetaCopier collapses a whole class of broker refusals -- no margin, a
 * read-only login, a symbol the broker has closed, a size outside the book's
 * limits -- into one token with no reason attached, and its adapter marks
 * those `explain`. The reason is in the account summary and the symbol's
 * spec, which the hot path has no cause to read until an order has already
 * been refused. Best-effort: any failure here just leaves the generic message
 * in place.
 */
async function explainRejection(
  bridge: BrokerBridge,
  brokerSymbol: string,
  lots: number,
): Promise<{ code: string; message: string } | null> {
  const state = await readAccountState(bridge)

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
    const read = await bridge.symbolSpec(brokerSymbol, 6000)
    if (read.ok) {
      const spec = read.data
      const min = spec.minVolume
      const max = spec.maxVolume
      if (spec.disabled) {
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
    console.warn(`[broker-execute] symbol spec threw: ${(e as Error).message}`)
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
    const novaHost = createClient(SUPABASE_URL, SERVICE_ROLE_KEY)

    const body = await req.json().catch(() => ({}))
    const { license_key, pair, side, volume, sl, tp, signal_id, dry_run, order_type, open_price, pending_expiry_seconds } = body
    // One of this licence's linked accounts (multi-account). Absent = the primary.
    const targetAccount = body?.account_id ? String(body.account_id) : null

    const key = license_key ? String(license_key).trim().toUpperCase() : null
    const dryRun = dry_run === true

    /**
     * True when a person pressed Execute, rather than a signal arriving on its
     * own. Governs the concurrency cap below and nothing else.
     *
     * Every automated path names the signal it is acting on -- the server's
     * `auto-execute-signals` sends `signal.signal_id ?? signal.id`, Android's
     * SignalListener sends `signal.signal_id ?: signal.id` -- and the chart
     * scanner has no signal to name, on either platform. So the ABSENCE of
     * `signal_id` is the distinction, and it needs no new field.
     *
     * Inferred rather than read from a client-set flag on purpose: a new request
     * field would only take effect on handsets that had installed a new build,
     * and the users living with this are precisely the ones who cannot be asked
     * to reinstall. Read this way, the fix lands on every device already out
     * there the moment this function deploys.
     */
    const manual = !signal_id

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
        console.error(`[broker-execute] could not write signal_logs: ${(e as Error).message}`)
      }

      return new Response(JSON.stringify(payload), {
        status: httpStatus,
        headers: { 'Content-Type': 'application/json', ...CORS_HEADERS },
      })
    }

    // NOTE: the broker account is resolved from the licence server-side.
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

    let metadata = (license.metadata ?? null) as Record<string, unknown> | null
    let link = resolveLink(metadata)

    // ---- A named account, when the licence has several -----------------------
    //
    // Still never trusted from the request on its own: the id must be a live
    // row on THIS licence, so a caller can only ever pick among its own
    // accounts. A secondary account reads its own symbol map and suffix, since
    // the licence's are learnt from the primary's broker and an IFX spelling
    // sent to Exness is a guaranteed refusal.
    let secondaryRowId: string | null = null
    if (targetAccount && targetAccount !== link?.accountId) {
      const { data: row } = await novaHost
        .from('license_broker_accounts')
        .select('id, provider, bridge_account_id, is_primary, symbol_map, symbol_suffix, created_at, status, platform')
        .eq('license_id', license.id)
        .eq('bridge_account_id', targetAccount)
        .is('disconnected_at', null)
        .maybeSingle()
      if (!row) {
        return await finish('rejected', 404, {
          success: false, code: 'ACCOUNT_NOT_ON_LICENCE',
          error: 'That trading account is not linked to this licence.',
        }, { account_id: targetAccount })
      }
      if (!row.is_primary) {
        secondaryRowId = row.id
        link = { provider: row.provider, accountId: row.bridge_account_id }
        metadata = {
          ...(metadata ?? {}),
          symbol_map: row.symbol_map ?? {},
          symbol_suffix: row.symbol_suffix ?? '',
          connected_at: row.created_at,
          bridge_status: row.status,
          platform: row.platform,
        }
      }
    }

    if (!link) {
      return await finish('rejected', 409, {
        success: false, code: 'NO_ACCOUNT_LINKED',
        error: 'No trading account is connected to this licence.',
      })
    }
    const account_id = link.accountId

    // Fail closed and loudly rather than pretending a trade went out. Never
    // falls back to another provider: a licence on one bridge must not be
    // traded through another because a key was missing.
    const bridge = openBridge(link, novaHost)
    if (!bridge) {
      return await finish('failed', 500, {
        success: false, code: 'SERVER_MISCONFIGURED',
        error: 'Server misconfiguration.',
      }, { bridge_provider: link.provider })
    }

    // Respect a per-licence symbol restriction when one is set.
    const allowed = Array.isArray(license.allowed_symbols) ? license.allowed_symbols : []
    const cleanPair = baseSymbol(pair)
    if (allowed.length > 0 && !allowed.some((s: string) => coversSymbol(cleanPair, baseSymbol(s)))) {
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
      console.warn(`[broker-execute] symbol config unreadable: ${cfgErr.message}`)
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
          `[broker-execute] ${cleanPair} volume ${effectiveLots} capped to configured ${ceiling}`
        )
        cappedFrom = effectiveLots
        effectiveLots = ceiling
      }

      // ---- Concurrency cap --------------------------------------------------
      //
      // `max_trades` is the ROBOT's appetite for a symbol, and it was being
      // charged against the user's own hand as well. That made one shape of
      // refusal dominate this endpoint: gold, one position open, limit one, 58
      // times -- the hosted robot holding the only slot, so a subscriber could
      // never execute their own scan of the instrument their robot was already
      // trading. From the handset it read as "execute is broken".
      //
      // A deliberate tap on Execute is not the runaway this limit exists to
      // stop, so it is exempt. Automated signals are still counted and still
      // capped, which is the half that was doing real work.
      const maxTrades = Number(symbolCfg.max_trades) || 0
      if (maxTrades > 0 && manual) {
        console.log(
          `[broker-execute] ${cleanPair} manual order: max_trades ${maxTrades} not applied`
        )
      } else if (maxTrades > 0) {
        const open = await countOpenPositions(bridge, symbolCandidates(cleanPair, metadata, userSymbol))

        // null means the broker could not be asked. Fail open and say so: a
        // transient failure on the positions endpoint blocking every order is a
        // worse outcome than briefly exceeding a self-imposed cap, and silence
        // here would make that choice invisible.
        if (open === null) {
          console.warn(
            `[broker-execute] could not count open ${cleanPair} positions; cap not enforced`
          )
        } else if (open >= maxTrades) {
          return await finish('rejected', 409, {
            success: false, code: 'POSITION_CAP',
            error: `Already holding ${open} ${cleanPair} position(s); your limit is ${maxTrades}.`,
          }, { open, max_trades: maxTrades })
        }
      }
    }

    // ---- Lot step --------------------------------------------------------
    //
    // Brokers accept volume in discrete increments, and until here nothing
    // guaranteed one. `smart_lot_size` is the calculator's raw quotient -- a
    // full-precision double such as 0.015560200210315327 -- and the ceiling
    // above assigns it straight into `effectiveLots`. That number went to the
    // broker verbatim. Gold on a live book here quotes `stepVolume: 0.01`, so
    // it is not a size the account can trade under any circumstances, and
    // MetaCopier refuses it without ever mentioning volume.
    //
    // Outside the `symbolCfg` block deliberately: a licence that has never
    // synced its plan has no config row, and its orders are sized entirely by
    // the handset -- which is exactly the case with nothing else checking.
    //
    // Rounded DOWN, for the same reason the app's own sizing floors: rounding
    // up puts the position above the budget the user set, and a limit breached
    // by half of all roundings is not a limit.
    const steppedLots = toLotStep(effectiveLots)
    if (steppedLots !== effectiveLots) {
      console.log(
        `[broker-execute] ${cleanPair} volume ${effectiveLots} -> ${steppedLots} (lot step)`
      )
      effectiveLots = steppedLots
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

    // Stable per name. The first name carries the signal's own id, exactly as
    // it always has, so a network retry of the same order is still recognised
    // by MetaCopier as one order.
    //
    // A LATER name gets an id of its own. It is only ever tried after the broker
    // refused the previous one outright, so nothing was opened under it -- and
    // MetaCopier documents the id as a duplicate guard without saying what it
    // does with one it has already refused. Reusing it risks the retry being
    // answered from that guard: dropped, or acknowledged without executing,
    // which would be reported here as a filled trade that never happened.
    const requestSeed = String(signal_id ?? `${license.id}:${Date.now()}`)
    const requestId = requestIdFrom(requestSeed)
    const requestIdFor = (forSymbol: string) =>
      forSymbol === candidates[0] ? requestId : requestIdFrom(`${requestSeed}|${forSymbol}`)

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
      requestId: requestIdFor(forSymbol),
      comment: tradeComment(license),
      // The clean half of the trade's identity. The comment arrives in the
      // terminal wearing MetaCopier's `API|<requestId>|` prefix and nothing we
      // send can strip it; this field carries no decoration at all.
      magicNumber: magicFor(license.ea_id),
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
      const open = await countOpenPositions(bridge, candidates)

      // A dry run is the one place it is worth paying for certainty: if none of
      // the guessed names would be recognised, say so HERE rather than letting
      // the user find out on a live signal.
      const discovered = await resolveFromBrokerList(bridge, cleanPair, safeSuffix(metadata))

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
    // A provider can accept an order the instant an account is *registered*,
    // well before the MT4/MT5 session behind it has connected -- and a signal
    // that lands in that window is refused (MetaCopier says
    // `[ACCOUNT_IS_NOT_CONNTECTED]`), which reads to the subscriber as "the
    // broker refused the trade". One account-summary read here turns that first
    // minute after a reconnect from a failed trade into a clearly-labelled
    // skipped one.
    //
    // Only a definite `connected: false` short-circuits. A read that fails or is
    // ambiguous falls straight through to the order attempt, which surfaces the
    // real error rather than blocking a trade on a flaky probe.
    const session = await readAccountState(bridge)
    if (session.connected === false) {
      if (session.wrongCredentials) {
        return await finish('failed', 409, {
          success: false,
          code: 'ACCOUNT_WRONG_CREDENTIALS',
          error: 'Your broker rejected the saved login. Reconnect the account with the trading (master) password.',
        }, { account_id, connected: false, wrong_credentials: true })
      }

      // "Still coming up" and "been down for an hour" call for different words.
      // A link made in the last few minutes, or one broker-connect last saw
      // as `connecting`, is the reconnect window -- hold the signal quietly. Any
      // older disconnection is a real fault the user needs to act on.
      const linkedAt = Date.parse(String(metadata?.connected_at ?? '')) || 0
      const linkStatus = linkStatusOf(metadata)
      const stillConnecting =
        linkStatus === 'connecting' ||
        (linkedAt > 0 && Date.now() - linkedAt < 5 * 60 * 1000)

      return await finish(stillConnecting ? 'skipped' : 'failed', 409, {
        success: false,
        code: stillConnecting ? 'ACCOUNT_CONNECTING' : 'ACCOUNT_DISCONNECTED',
        error: stillConnecting
          ? 'Your broker was still connecting when this signal arrived, so it was not placed. ' +
            'Newer signals will go through once the connection is up.'
          : "Your broker isn't connected right now. Reconnect it in Broker Setup, then try again.",
      }, { account_id, connected: false, metacopier_status: linkStatus })
    }

    let placed: string | null = null
    const attempted: string[] = []
    let lastDetail = ''
    let lastStatus = 0
    let lastRefusal: OrderRefusal | null = null

    /** The audit-trail request above, as the bridge's neutral order. */
    const toOrder = (r: ReturnType<typeof buildRequest>) => ({
      symbol: r.symbol,
      orderType: r.orderType as OrderType,
      volume: r.volume,
      openPrice: r.openPrice,
      pendingExpirySeconds: r.pendingExpirySeconds ?? 0,
      stopLoss: r.stopLoss,
      takeProfit: r.takeProfit,
      clientId: r.requestId,
      comment: r.comment,
      magic: r.magicNumber,
    })

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
     *
     * An outcome the bridge could not confirm (`definitive: false`) is 'stop'
     * too, and the strongest kind: the order may be open, so no other name is
     * tried and nothing is retried.
     */
    const attempt = async (candidate: string): Promise<'placed' | 'wrong-name' | 'stop'> => {
      attempted.push(candidate)

      const result = await bridge.placeOrder(toOrder(buildRequest(candidate)))

      if (result.ok) {
        placed = candidate
        return 'placed'
      }

      lastRefusal = result
      lastStatus = result.status
      lastDetail = result.detail

      return result.definitive && result.wrongName ? 'wrong-name' : 'stop'
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
    //
    // "Autotrading disabled" also earns one look at the list. It reads as an
    // account-wide verdict, but on every live occurrence it was a name the
    // broker does not list (see AUTOTRADING_REFUSAL in the MetaCopier adapter).
    // If the list names exactly what was sent, nothing is retried and the
    // refusal stands as written.
    // Safe either way: the broker refused, so nothing is open under that name.
    // `as` because TypeScript cannot see the assignment inside attempt().
    const refusedWith = lastRefusal as OrderRefusal | null
    const autotradingRefused = !placed && outcome === 'stop' &&
      refusedWith?.definitive === true && refusedWith.code === 'AUTOTRADING_DISABLED'

    if (!placed && (outcome === 'wrong-name' || autotradingRefused)) {
      const discovered = await resolveFromBrokerList(bridge, cleanPair, safeSuffix(metadata))

      if (discovered && !attempted.includes(discovered)) {
        console.log(`[broker-execute] broker lists ${cleanPair} as "${discovered}" -- retrying`)
        outcome = await attempt(discovered)
      }
    }

    if (!placed) {
      const symbolTried = attempted[attempted.length - 1] ?? cleanPair

      // The adapter has already read the refusal into our vocabulary -- how a
      // provider spells "no margin" is its business. What is left here is the
      // part that is ours: the words the subscriber reads, which name their
      // symbol and their size. Translated because the alternative they would
      // see is a raw MetaTrader number -- 130, 131, 134, 4109 -- which tells
      // them nothing about which of these they can actually fix.
      const refusal = (lastRefusal as OrderRefusal | null) ?? {
        ok: false as const, code: 'BROKER_REJECTED', definitive: true, wrongName: false,
        accountGone: false, explain: true, brokerMessage: null, providerCode: null,
        status: lastStatus, detail: lastDetail,
      }

      // The broker's own sentence is the most specific thing anyone will ever
      // have about the refusal. It is only ever replaced below by a message
      // that is MORE specific -- never by a generic. (Four live orders were once
      // told "check the symbol's trading hours and your free margin" while the
      // broker had said "Autotrading disabled by server" in plain English.)
      const brokerSays = refusal.brokerMessage

      let code = refusal.code
      let message = brokerSays
        ? `Your broker refused the order on ${symbolTried}: ${brokerSays}`
        : 'Trade could not be placed with the broker.'

      if (!refusal.definitive) {
        // The bridge could not tell whether the order reached the broker. It
        // may be open. Saying "refused" here would invite the user -- or the
        // next signal -- to place it again.
        code = 'ORDER_UNCONFIRMED'
        message =
          `We could not confirm whether your ${symbolTried} order reached the broker. ` +
          `Check your open trades before placing it again.`
      }
      // ---- Autotrading switched off at the broker ----------------------------
      //
      // Checked before anything else because it is terminal in a way none of the
      // others are: the account is connected, funded, holds the symbol and has
      // the right password, and not one order will ever fill. Only the broker
      // can lift it, so sending them to Trading Symbols or Broker Setup wastes
      // their evening.
      else if (code === 'AUTOTRADING_DISABLED') {
        message =
          `Your broker has automated trading switched off on this account, so no order ` +
          `NovaHost sends can be filled. Ask your broker to enable algorithmic (EA) ` +
          `trading on it, or link a licence to an account that allows it. Nothing is ` +
          `wrong with your licence, your balance or your symbols.`
      }
      // Connection state next -- it is the most common cause and the most
      // actionable.
      else if (code === 'ACCOUNT_DISCONNECTED') {
        message = "Your broker isn't connected right now. Reconnect it in Broker Setup, then try again."
      }
      // ---- The account itself is gone, not merely offline --------------------
      //
      // A disconnected account may come back on its own, so its link is left
      // alone. A deleted one will NEVER reconnect. Reported with the same code
      // and copy as a normal disconnect (the app already knows how to render
      // `ACCOUNT_DISCONNECTED`), but this branch ALSO clears the dead id off
      // the licence. Without that, two licences once kept showing CONNECTED and
      // traded against a deleted account for days, every signal failing here,
      // alone, unread. Clearing it makes `license-status` report NOT LINKED
      // immediately, and the next signal fails fast on NO_ACCOUNT_LINKED.
      else if (refusal.accountGone) {
        code = 'ACCOUNT_DISCONNECTED'
        message = "Your trading account's connection to the broker was lost. Reconnect it in Broker Setup to keep trading."

        const clearedMetadata = withoutLink(
          (metadata ?? {}) as Record<string, unknown>,
          refusal.providerCode ?? 'ACCOUNT_WAS_DELETED',
        )

        // A dead secondary retires its own row; the licence's link is the primary's.
        const { error: clearErr } = secondaryRowId
          ? await novaHost.from('license_broker_accounts')
              .update({ disconnected_at: new Date().toISOString(), status: 'disconnected' })
              .eq('id', secondaryRowId)
          : await novaHost
              .from('licenses')
              .update({ metadata: clearedMetadata })
              .eq('id', license.id)

        if (clearErr) {
          // Best-effort, like every other write in this file: a failed
          // cleanup must not turn an already-failed trade into a crashed
          // request, and the next signal on this licence gets another chance
          // to clear it.
          console.error(`[broker-execute] could not clear dead account link for licence ${license.id}: ${clearErr.message}`)
        } else {
          console.warn(`[broker-execute] licence ${license.id}'s account ${account_id} was deleted at the provider -- link cleared, will now show NOT LINKED`)
        }
      } else if (code === 'UNKNOWN_SYMBOL') {
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
      } else if (code === 'REQUEST_INVALID') {
        // Not the broker's doing. Every field of this request is built
        // server-side, so a validation failure is ours to fix and nothing the
        // subscriber does will change it. (A `requestId` of 2105094537 against
        // a field capped at 999 once cost three live signals, reported as "Trade
        // could not be placed with the broker".)
        message =
          'NovaHost sent an order the trading provider rejected as malformed. ' +
          'This is a fault on our side, not with your account or your broker.'
      } else if (code === 'INVALID_VOLUME') {
        message = `Your broker rejected ${effectiveLots} lots on ${symbolTried}. It is outside the size this account allows.`
      } else if (code === 'INSUFFICIENT_MARGIN') {
        message = `Not enough free margin to open ${effectiveLots} lots of ${symbolTried}.`
      } else if (code === 'INVALID_STOPS') {
        message = `Your broker refused the stop or target on ${symbolTried} -- they are too close to the current price.`
      } else if (code === 'TRADING_PROHIBITED' || code === 'MARKET_CLOSED') {
        message = `Trading ${symbolTried} is not permitted on this account right now -- the market may be closed, or the account may be read-only.`
      } else if (refusal.explain) {
        // A refusal with no reason attached. Ask the account and the
        // instrument what it actually was -- read-only login, no margin, a
        // closed market, a size outside the book's limits -- rather than
        // handing the subscriber a token they cannot act on.
        const explained = await explainRejection(bridge, symbolTried, effectiveLots)
        if (explained) {
          code = explained.code
          message = explained.message
        } else {
          code = 'BROKER_REJECTED'
          // "Without giving a reason" is only true when the broker gave none.
          message = brokerSays
            ? `Your broker refused the order on ${symbolTried}: ${brokerSays}`
            : `Your broker refused the order on ${symbolTried} without giving a reason. ` +
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
      const learnt = { ...knownMap, [cleanPair]: filledAs }
      const { error: mapErr } = secondaryRowId
        ? await novaHost.from('license_broker_accounts').update({ symbol_map: learnt }).eq('id', secondaryRowId)
        : await novaHost
            .from('licenses')
            .update({ metadata: { ...(license.metadata ?? {}), symbol_map: learnt } })
            .eq('id', license.id)

      if (mapErr) {
        console.warn(
          `[broker-execute] could not remember ${cleanPair} -> "${filledAs}": ${mapErr.message}`
        )
      } else {
        console.log(
          `[broker-execute] licence ${license.id}: ${cleanPair} trades as "${filledAs}" here -- remembered`
        )
      }
    }

    return await finish('executed', 200, {
      success: true,
      code: 'EXECUTED',
      // effectiveLots, not lots: reporting the requested size when a cap reduced
      // it tells the user they hold a position they do not hold.
      message: `${orderType} ${filledAs} ${effectiveLots} lots sent.`,
      requestId: requestIdFor(filledAs),
      // `cap_exempt` is recorded, not merely acted on: this order may sit above
      // the user's own max_trades, and a position nobody can explain later is
      // worse than one the audit trail accounts for.
    }, { request: buildRequest(filledAs), account_id, capped_from: cappedFrom, tried: attempted, cap_exempt: manual })
  } catch (error) {
    console.error('[broker-execute] fatal:', error)
    return new Response(
      JSON.stringify({ success: false, code: 'FATAL', error: (error as Error).message }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...CORS_HEADERS } }
    )
  }
})
