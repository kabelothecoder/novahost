/**
 * The confluence engine, the guardrails and the take-profit ladder.
 *
 * Pure functions over one `analyze-chart` reading. No network, no stores, no
 * Vue — the screen renders what this returns, and everything that decides
 * whether a trade is sane lives here where it can be reasoned about at once.
 *
 * ## Why the scoring is local
 *
 * The design doc's own line is "Scored on your device. Nothing about your
 * account leaves the phone." `analyze-chart` returns a READING of the chart —
 * direction, entry, stop, pattern, key level, candle range, per-timeframe
 * bias. It is never told the balance, the risk appetite or the guardrails.
 * Those are applied here, against the reading, on the handset.
 *
 * ## Unknown is a real answer
 *
 * Two of the doc's five confluence checks and two of its four guardrails have
 * no data source on this platform — there is no economic calendar, and
 * `trade_logs` is empty with no client-side read. They report UNKNOWN and are
 * excluded from the denominator rather than scored as passes (which flatters
 * every setup) or as failures (which would block every setup forever). Same
 * rule the Fleet tiles follow: an em dash beats an invention.
 */

/* ── Pip size ────────────────────────────────────────────────────────
   An estimate, and labelled as one wherever a pip figure is shown. The true
   value is a broker/instrument property this screen cannot see; the families
   below cover everything a licence currently lists. */
export function pipSize(symbol) {
  const s = String(symbol || '').toUpperCase()
  if (/JPY/.test(s)) return 0.01
  if (/^XAU|GOLD/.test(s)) return 0.01
  if (/^XAG|SILVER/.test(s)) return 0.001
  if (/US30|NAS|SPX|US500|GER|UK100|USTECH|DAX|DJ/.test(s)) return 1
  if (/BTC|ETH|SOL|XRP|DOGE/.test(s)) return 1
  if (/OIL|WTI|BRENT|NGAS/.test(s)) return 0.01
  return 0.0001
}

export function toPips(priceDistance, symbol) {
  const p = pipSize(symbol)
  return p > 0 ? Math.abs(priceDistance) / p : 0
}

/* ── Ladder ──────────────────────────────────────────────────────────
   TP1 is the model's own first target, kept verbatim — it is the level it
   actually read off the chart. TP2 and TP3 are 2R and 3R projections of the
   SAME stop, which is arithmetic rather than analysis, and is why the UI
   labels them as projections. */
export const ALLOCATIONS = [
  { id: 'safe',     name: 'Safe',     split: [60, 30, 10] },
  { id: 'balanced', name: 'Balanced', split: [50, 25, 25] },
  { id: 'runner',   name: 'Runner',   split: [34, 33, 33] },
]

/** Smallest volume any broker on this platform accepts. Mirrors the floor
 *  metacopier-execute applies, so the UI cannot promise a lot the executor
 *  would round away. */
export const MIN_LOT = 0.01

export function buildLadder(reading, opts) {
  const allocation = (opts && opts.allocation) || 'balanced'
  const totalLot = (opts && Number(opts.totalLot)) || 0

  const entry = Number(reading && reading.entry)
  const sl = Number(reading && reading.sl)
  const tp1 = Number(reading && reading.tp)
  const isBuy = String(reading && reading.direction).toUpperCase() === 'BUY'
  const symbol = reading && reading.symbol_on_chart

  const risk = Math.abs(entry - sl)
  if (!(risk > 0)) return { valid: false, reason: 'Entry and stop are the same price.', legs: [] }

  const project = r => (isBuy ? entry + risk * r : entry - risk * r)
  const rrOf = price => Math.abs(price - entry) / risk

  const found = ALLOCATIONS.find(a => a.id === allocation)
  const split = (found || ALLOCATIONS[1]).split

  const targets = [
    { level: 'TP1', price: tp1, projected: false },
    { level: 'TP2', price: project(2), projected: true },
    { level: 'TP3', price: project(3), projected: true },
  ]

  const legs = targets.map((t, i) => {
    const rr = rrOf(t.price)
    /* Floored, never rounded up: the split carves a risk budget the user has
       already set, and rounding a slice up spends money they did not
       allocate. A slice under the floor becomes 0 and is reported as
       undersized rather than silently promoted to 0.01. */
    const raw = totalLot * (split[i] / 100)
    const lot = raw >= MIN_LOT ? Math.floor(raw / MIN_LOT) * MIN_LOT : 0
    return {
      level: t.level,
      price: t.price,
      projected: t.projected,
      rr,
      alloc: split[i],
      lot: Number(lot.toFixed(2)),
      undersized: raw > 0 && raw < MIN_LOT,
      pips: toPips(t.price - entry, symbol),
    }
  })

  /* Blended R:R is allocation-weighted, not an average of the three — a
     ladder that puts 60% on the nearest target does NOT earn the 1:3 of a
     runner it barely funds, and treating it as if it did is exactly how a
     plan passes an R:R guardrail it should have failed. */
  const blendedRR = legs.reduce((sum, l) => sum + (l.alloc / 100) * l.rr, 0)
  const slPips = toPips(entry - sl, symbol)

  return { valid: true, isBuy, entry, sl, slPips, risk, legs, blendedRR, allocation, symbol }
}

/* ── Confluence ──────────────────────────────────────────────────────
   Five checks, each pass / fail / unknown. The score is passed over KNOWN,
   scaled to 100 — a reading that could only answer four of them scores out
   of four and says so, rather than losing 20 points to a question nobody
   was able to ask. */

/** The ATR band the doc calls "moderate". Below is too quiet to reach a
 *  target, above is too wild for the stop to survive noise. In pips. */
const ATR_BAND = [10, 22]

export function scoreConfluence(reading, ladder) {
  const symbol = reading && reading.symbol_on_chart
  const dir = String((reading && reading.direction) || '').toUpperCase()
  const bias = String((reading && reading.trend_bias) || '').toUpperCase()
  const patterns = Array.isArray(reading && reading.patterns) ? reading.patterns : []
  const valid = !!(ladder && ladder.valid)

  const checks = []

  // 1 · Blended R:R
  checks.push({
    id: 'rr',
    label: 'Blended R:R above 1:1.5',
    ok: valid ? ladder.blendedRR >= 1.5 : null,
    /* The wording has to follow what is actually being judged: in single
       mode this ladder has one leg, and calling that "across the three
       targets" describes a plan the user is not placing. */
    detail: valid
      ? '1:' + ladder.blendedRR.toFixed(2)
        + (ladder.legs.length > 1 ? ' across ' + ladder.legs.length + ' targets' : ' at TP1')
      : 'No usable stop distance',
  })

  // 2 · Pattern aligns with the higher-timeframe trend
  let alignOk = null
  let alignDetail = 'No pattern or trend read from this chart'
  if (patterns.length && bias && bias !== 'NEUTRAL') {
    alignOk = (dir === 'BUY' && bias === 'BULLISH') || (dir === 'SELL' && bias === 'BEARISH')
    alignDetail = patterns[0] + ' · ' + (reading.trend_timeframe || 'higher timeframe') + ' ' + bias.toLowerCase()
  } else if (bias === 'NEUTRAL') {
    alignOk = false
    alignDetail = (reading.trend_timeframe || 'Higher timeframe') + ' is ranging — no trend to align with'
  }
  checks.push({ id: 'align', label: 'Pattern aligns with the trend', ok: alignOk, detail: alignDetail })

  // 3 · Volatility inside the band
  const acr = reading && reading.average_candle_range
  let volOk = null
  let volDetail = 'Candles not distinguishable on this chart'
  if (acr != null && Number(acr) > 0) {
    const atrPips = toPips(Number(acr), symbol)
    volOk = atrPips >= ATR_BAND[0] && atrPips <= ATR_BAND[1]
    volDetail = 'ATR ~' + atrPips.toFixed(1) + 'p vs the ' + ATR_BAND[0] + '–' + ATR_BAND[1] + 'p band'
  }
  checks.push({ id: 'vol', label: 'Volatility inside the band', ok: volOk, detail: volDetail })

  // 4 · Entry sits on a key level
  const kl = reading && reading.key_level
  const touches = Number(reading && reading.key_level_touches) || 0
  let lvlOk = null
  let lvlDetail = 'No structural level identified'
  if (kl != null && Number(kl) > 0 && valid) {
    const distPips = toPips(Number(kl) - ladder.entry, symbol)
    lvlOk = distPips <= toPips(ladder.risk, symbol) && touches >= 2
    lvlDetail = distPips.toFixed(1) + 'p from ' + kl + ' · ' + touches + ' touch' + (touches === 1 ? '' : 'es')
  }
  checks.push({ id: 'level', label: 'Entry sits on a key level', ok: lvlOk, detail: lvlDetail })

  // 5 · Event window — no calendar exists on this platform.
  checks.push({
    id: 'event',
    label: 'Event window clear',
    ok: null,
    detail: 'No calendar connected — this check cannot run',
  })

  const passed = checks.filter(c => c.ok === true).length
  const failed = checks.filter(c => c.ok === false)
  /* Not `=== null`: the three buckets must be exhaustive, or a check that
     yielded undefined vanishes and quietly shrinks the denominator. */
  const unknown = checks.filter(c => c.ok !== true && c.ok !== false)
  const known = passed + failed.length
  const score = known > 0 ? Math.round((passed / known) * 100) : null

  let grade = 'UNKNOWN'
  if (score != null) grade = score >= 80 ? 'STRONG' : score >= 50 ? 'MODERATE' : 'WEAK'

  return { checks, passed, known, failed, unknown, score, grade, direction: dir }
}

/* ── Guardrails ──────────────────────────────────────────────────────
   The user's own hard limits. A BLOCK is not advice — the execute control
   refuses. Two of the doc's four are unreachable here and say so; an
   unknown never blocks, because a rule that cannot be evaluated must not
   silently forbid every trade. */

export const DEFAULT_RULES = {
  minBlendedRR: 1.5,
  maxStopPips: 50,
  maxConsecutiveLosses: 3,
  eventBufferMinutes: 30,
}

export function checkGuardrails(ladder, rules) {
  const r = rules || DEFAULT_RULES
  const out = []

  if (ladder && ladder.valid) {
    out.push({
      id: 'rr',
      label: 'Blended R:R ≥ 1:' + r.minBlendedRR,
      status: ladder.blendedRR >= r.minBlendedRR ? 'PASS' : 'BLOCK',
      detail: 'Plan gives 1:' + ladder.blendedRR.toFixed(2),
    })
    const over = ladder.slPips > r.maxStopPips
    out.push({
      id: 'stop',
      label: 'Stop loss ≤ ' + r.maxStopPips + ' pips',
      /* Wider than the limit is a WARN, not a BLOCK: it costs more than
         planned but the trade is still coherent. The doc grades it so too. */
      status: over ? 'WARN' : 'PASS',
      detail: ladder.slPips.toFixed(1) + 'p'
        + (over ? ' — ' + (ladder.slPips - r.maxStopPips).toFixed(1) + 'p over your limit' : ''),
    })
  } else {
    out.push({ id: 'rr', label: 'Blended R:R', status: 'BLOCK', detail: 'No usable stop distance' })
  }

  out.push({
    id: 'losses',
    label: 'Consecutive losses < ' + r.maxConsecutiveLosses,
    status: 'UNKNOWN',
    detail: 'Trade history is not readable from this device',
  })
  out.push({
    id: 'event',
    label: 'No high-impact event ≤ ' + r.eventBufferMinutes + 'm',
    status: 'UNKNOWN',
    detail: 'No calendar connected',
  })

  const blocks = out.filter(x => x.status === 'BLOCK')
  const warns = out.filter(x => x.status === 'WARN')
  const passes = out.filter(x => x.status === 'PASS')
  const unknowns = out.filter(x => x.status === 'UNKNOWN')

  return {
    rules: out, blocks, warns, passes, unknowns,
    blocked: blocks.length > 0,
    summary: blocks.length + ' blocker' + (blocks.length === 1 ? '' : 's')
      + ' · ' + warns.length + ' warning' + (warns.length === 1 ? '' : 's')
      + ' · ' + passes.length + ' passed',
  }
}
