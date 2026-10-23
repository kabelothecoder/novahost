/**
 * The pure parts of the v3 scanner flow ("Nova Edge - Chart Scanner v3").
 *
 * v3 sizes a scan as LOT × TRADES rather than as one total lot carved into a
 * TP1/TP2/TP3 ladder. Every trade carries the same lot and the same stop, and
 * the targets are split between TP1 and TP2. These helpers turn a
 * `buildLadder()` answer into that shape, so `checkGuardrails()` and
 * `sendPlan()` see the orders that will actually be placed and not a ladder
 * the user never asked for.
 *
 * Nothing in here touches the network or the DOM.
 */

/** Below this confidence the verdict is NO TRADE, whatever side the model leant.
 *  `analyze-chart` always picks BUY or SELL, and its prompt says a guess from a
 *  noisy chart should come back as low confidence. 50 is the point where the
 *  model no longer backs its own side. */
export const NO_TRADE_BELOW = 50

/** Trades per scan. Each one is a separate position and counts against the
 *  executor's position cap, so the ceiling stays low. A cap refusal halfway
 *  through leaves the earlier trades open. */
export const MAX_TRADES = 3

export const STYLES = [
  { id: 'scalp', name: 'Scalp',     hold: 'A few minutes', tf: 'M5 – M15', tfs: ['M5', 'M15', 'H1'], stop: 'Tight' },
  { id: 'day',   name: 'Day trade', hold: 'Closes today',  tf: 'H1 – H4',  tfs: ['M15', 'H1', 'H4'], stop: 'Medium' },
  { id: 'swing', name: 'Swing',     hold: 'A few days',    tf: 'H4 – D1',  tfs: ['H4', 'D1', 'W1'],  stop: 'Wide' },
]

/* The ids are the keys of STRATEGY_BRIEFS in `analyze-chart`. The client sends
   the name, never the brief. */
export const STRATEGIES = [
  { id: 'SMC',          name: 'SMC',          tag: 'SMART MONEY CONCEPTS',
    desc: 'Follows order blocks, fair value gaps, liquidity sweeps and a change of character.',
    looks: 'liquidity taken, a change of character, a zone price is returning to.',
    order: 'Entry: usually a limit order back at the zone.' },
  { id: 'CRT',          name: 'CRT',          tag: 'CANDLE RANGE THEORY',
    desc: 'Uses a completed higher-timeframe candle as the range. Waits for a sweep of its high or low, then a close back inside.',
    looks: 'a sweep, a close back inside, a target at the other side of the range.',
    order: 'Entry: a limit order at the 50% of the sweep candle.' },
  { id: 'PRICE_ACTION', name: 'Price action', tag: 'SUPPORT · RESISTANCE · CANDLES',
    desc: 'Reads horizontal support and resistance and the candles that print at them. No indicators.',
    looks: 'a reaction at a level, rejection wicks, the level price has respected most.',
    order: 'Entry: a market or limit order once the candle confirms.' },
  { id: 'BREAKOUT',     name: 'Breakout',     tag: 'RANGES · TRIANGLES · FLAGS',
    desc: 'Finds the consolidation and the level that ends it, and prefers the retest to the breaking candle.',
    looks: 'a range, a clean break of its edge, a retest that holds.',
    order: 'Entry: a stop order past the edge, or a limit on the retest.' },
]

/**
 * The orders a scan places: `trades` legs of `lot` each, one shared stop.
 *
 * TP1 is the level read off the chart. The second target is the first ladder
 * rung further out than TP1, which is the 2R projection unless TP1 already
 * reaches it. The first half of the trades (rounded up) take TP1 and the rest
 * take the second target, so one trade is TP1 only and two is one of each.
 *
 * Blended R:R is the plain average. Every leg carries the same lot, so equal
 * weights are the real allocation.
 */
export function planLegs(ladder, lot, trades) {
  if (!ladder || !ladder.valid) return { ...ladder, valid: false, legs: [] }
  const n = Math.max(1, Math.min(MAX_TRADES, trades | 0))
  const first = ladder.legs[0]
  const second = ladder.legs.slice(1).find(l => l.rr > first.rr + 1e-9) || first
  const toFirst = Math.ceil(n / 2)
  /* Money per pip per lot, recovered from the ladder's risk figure. The ladder
     must be built with `totalLot = lot × trades` for this to hold. Its own leg
     lots are floored split slices and can be zero. */
  const total = n * lot
  const perPipLot = ladder.riskMoney != null && total > 0 && ladder.slPips > 0
    ? ladder.riskMoney / (total * ladder.slPips)
    : null

  const legs = []
  for (let i = 0; i < n; i++) {
    const t = i < toFirst ? first : second
    legs.push({
      level: i < toFirst ? 'TP1' : 'TP2',
      price: t.price,
      rr: t.rr,
      pips: t.pips,
      projected: t.projected,
      lot: Number(lot.toFixed(2)),
      money: perPipLot != null ? lot * perPipLot * t.pips : null,
    })
  }

  const blendedRR = legs.reduce((s, l) => s + l.rr, 0) / n

  return {
    ...ladder,
    legs,
    blendedRR,
    totalLot: Number(total.toFixed(2)),
    usesSecond: n > toFirst,
    tp1: first,
    tp2: second,
    rewardMoney: perPipLot == null ? null : legs.reduce((s, l) => s + l.money, 0),
  }
}

/** BUY, SELL or NO TRADE. */
export function verdictOf(reading) {
  if (!reading) return 'NO TRADE'
  const c = Number(reading.confidence)
  if (Number.isFinite(c) && c < NO_TRADE_BELOW) return 'NO TRADE'
  const d = String(reading.direction || '').toUpperCase()
  return d === 'BUY' || d === 'SELL' ? d : 'NO TRADE'
}

export function bandOf(c) {
  if (!Number.isFinite(c)) return 'Unknown'
  return c < 60 ? 'Low' : c < 80 ? 'Moderate' : 'High'
}

export function orderNameOf(side, type) {
  const b = side === 'SELL' ? 'Sell' : 'Buy'
  const t = String(type || 'MARKET').toUpperCase()
  return t === 'LIMIT' ? `${b} Limit` : t === 'STOP' ? `${b} Stop` : `${b} at market`
}

/**
 * The currencies whose news moves a symbol, for matching calendar rows.
 *
 * Reads the canonical symbol (`normaliseSymbol` output), not the broker's
 * spelling. Metals and US indices are dollar-priced, so US releases move them.
 * Anything unrecognised returns `[]`, and the symbol then gets no news badge.
 * A wrong badge would be worse than none.
 */
const INDEX_CCY = {
  US30: 'USD', NAS100: 'USD', US100: 'USD', USTEC: 'USD', SPX500: 'USD', US500: 'USD',
  GER40: 'EUR', DE40: 'EUR', UK100: 'GBP', JP225: 'JPY', AUS200: 'AUD',
}
export function currenciesOf(symbol) {
  const s = String(symbol || '').toUpperCase()
  if (INDEX_CCY[s]) return [INDEX_CCY[s]]
  if (/^(XAU|XAG|XPT|XPD)/.test(s)) return ['USD']
  if (/^(BTC|ETH|SOL|XRP|LTC)/.test(s)) return ['USD']
  if (/^[A-Z]{6}$/.test(s)) return [s.slice(0, 3), s.slice(3, 6)]
  return []
}

/** The next high-impact release for `symbol` inside `withinMin`, or null. */
export function nextHighEvent(events, symbol, now = Date.now(), withinMin = 120) {
  const ccys = currenciesOf(symbol)
  if (!ccys.length || !Array.isArray(events)) return null
  let best = null
  for (const e of events) {
    if (e.impact !== 'high' || !ccys.includes(String(e.currency || '').toUpperCase())) continue
    const t = new Date(e.at).getTime()
    if (!Number.isFinite(t) || t < now) continue
    if ((t - now) / 60000 > withinMin) continue
    if (!best || t < best.t) best = { ...e, t }
  }
  return best
}
