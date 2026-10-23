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

/**
 * A chart timeframe label, in minutes. Null when there is nothing to read.
 *
 * Handles the spellings a charting package actually prints: `M15`, `15M`, `H4`,
 * `4H`, `D1`, `1D`, `W1`, `MN1`, and a bare `15`. Mirrors
 * `ScanSource.timeframeMinutes` on Android — the two engines score the same
 * chart and must agree about what "higher timeframe" means, or the same
 * screenshot grades differently on two handsets.
 *
 * `MN` is tested before `M`: in MetaTrader `M` is minutes and `MN` is months, so
 * reading `MN1` as one minute turns the longest timeframe there is into the
 * shortest.
 */
export function timeframeMinutes(raw) {
  const s = String(raw || '').trim().toUpperCase().replace(/\s+/g, '')
  if (!s) return null
  const digits = parseInt(s.replace(/\D/g, ''), 10)
  if (!(digits > 0)) return null
  if (s.includes('MN')) return digits * 43200
  if (s.includes('W')) return digits * 10080
  if (s.includes('D')) return digits * 1440
  if (s.includes('H')) return digits * 60
  if (s.includes('M')) return digits
  if (/^\d+$/.test(s)) return digits
  return null
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

/**
 * Money per pip for one lot, from the broker's own contract spec.
 *
 * `contract_size` x `pip_size` is the value of a one-pip move on a single
 * lot, in the instrument's QUOTE currency: 100000 x 0.0001 = $10 a pip on a
 * standard FX lot, 100 x 0.01 = $1 a pip on gold. It is deliberately not
 * derived from the symbol's spelling — that is what `pipSize()` above does
 * as a fallback, and its own header calls it an estimate.
 *
 * Returns null without a spec, and every caller renders an em dash for that
 * rather than a zero. A money figure on a trading screen is either right or
 * it is a lie; there is no useful third state.
 */
function pipValuePerLot(spec) {
  if (!spec) return null
  const contract = Number(spec.contract_size)
  const pip = Number(spec.pip_size)
  if (!(contract > 0) || !(pip > 0)) return null
  return contract * pip
}

/**
 * @param {object} reading  the `analyze-chart` reading
 * @param {object} opts     { allocation, totalLot, spec }
 *                          `spec` is a `broker-quote` answer — optional, and
 *                          the only thing that turns pip distances into money.
 */
export function buildLadder(reading, opts) {
  const allocation = (opts && opts.allocation) || 'balanced'
  const totalLot = (opts && Number(opts.totalLot)) || 0
  const spec = (opts && opts.spec) || null
  const perPip = pipValuePerLot(spec)

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

  /* The broker's own pip size beats the family estimate wherever we have
     it: `pipSize()` reads the SPELLING of the symbol, and a broker that
     lists gold as "GOLD" or the Nasdaq as ".USTECH." is exactly the case it
     gets wrong. See [[novahost-broker-symbol-names]]. */
  const pipOf = d => (spec && Number(spec.pip_size) > 0
    ? Math.abs(d) / Number(spec.pip_size)
    : toPips(d, symbol))

  const legs = targets.map((t, i) => {
    const rr = rrOf(t.price)
    /* Floored, never rounded up: the split carves a risk budget the user has
       already set, and rounding a slice up spends money they did not
       allocate. A slice under the floor becomes 0 and is reported as
       undersized rather than silently promoted to 0.01. */
    const raw = totalLot * (split[i] / 100)
    const lot = raw >= MIN_LOT ? Math.floor(raw / MIN_LOT) * MIN_LOT : 0
    const pips = pipOf(t.price - entry)
    return {
      level: t.level,
      price: t.price,
      projected: t.projected,
      rr,
      alloc: split[i],
      lot: Number(lot.toFixed(2)),
      undersized: raw > 0 && raw < MIN_LOT,
      pips,
      /* What this rung is worth if it fills, in the quote currency. Null
         without a broker spec — never 0, which would read as "this target
         earns nothing". */
      money: perPip != null ? lot * perPip * pips : null,
    }
  })

  /* Blended R:R is allocation-weighted, not an average of the three — a
     ladder that puts 60% on the nearest target does NOT earn the 1:3 of a
     runner it barely funds, and treating it as if it did is exactly how a
     plan passes an R:R guardrail it should have failed. */
  const blendedRR = legs.reduce((sum, l) => sum + (l.alloc / 100) * l.rr, 0)
  const slPips = pipOf(entry - sl)

  /* Risk is the WHOLE position against the stop — every rung shares one
     stop, so a partial exit does not reduce what is at risk before the first
     fill. Reward is the sum of the rungs, which is the other side of exactly
     the same arithmetic the blended R:R expresses as a ratio. */
  const riskMoney = perPip != null ? totalLot * perPip * slPips : null
  const rewardMoney = perPip != null
    ? legs.reduce((sum, l) => sum + (l.money || 0), 0)
    : null

  return {
    valid: true, isBuy, entry, sl, slPips, risk, legs, blendedRR, allocation, symbol,
    riskMoney, rewardMoney,
    /** The quote currency the two figures above are denominated in. */
    currency: (spec && spec.quote_currency) || null,
    /** True when the broker priced this plan rather than the name did. */
    priced: perPip != null,
  }
}

/* ── Confluence ──────────────────────────────────────────────────────
   Five checks, each pass / fail / unknown. The score is passed over KNOWN,
   scaled to 100 — a reading that could only answer four of them scores out
   of four and says so, rather than losing 20 points to a question nobody
   was able to ask. */

/**
 * How many candle-ranges of room the stop needs, and how many is too many.
 *
 * ## Why this is a ratio and not a pip band
 *
 * This check used to compare the chart's ATR against a fixed `[10, 22]` pip
 * band. That band describes an FX major and nothing else. Gold's pip size here
 * is 0.01, so an ordinary $3.50 M15 candle measures as **350 pips** and the
 * check failed — not because gold was volatile, but because the units were
 * never gold's. The same arithmetic condemned every index and every crypto pair
 * on the book. A live scan showed it exactly: `ATR ~350.0p vs the 10–22p band`,
 * a red cross and a permanent −25 on an instrument that could not have passed.
 *
 * And it was worse than a lost point. The score is passed-over-KNOWN, so a
 * check that can only ever fail drags the denominator down with it: four real
 * questions became three, and every remaining answer got heavier.
 *
 * The stop-to-ATR ratio asks the thing the band was reaching for, in units that
 * cancel. Both numbers come off the same chart in the same price scale, so no
 * pip conversion happens at all — which removes the entire class of bug rather
 * than retuning it per instrument:
 *
 *  - Under 1 candle-range, the stop sits *inside* normal noise. Ordinary
 *    two-way movement takes it out before the setup has been wrong about
 *    anything.
 *  - Past 8, a 1R target needs eight consecutive candles of one-way movement.
 *    The R:R on paper is real; reaching it inside the trade's horizon is not.
 */
const STOP_ATR_BAND = [1.0, 8.0]

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

  /* 2 · Pattern aligns with a HIGHER timeframe
     ─────────────────────────────────────────
     The qualifier is the whole check, and it used to be missing.

     `analyze-chart` defines `trend_bias` as "the dominant bias of the highest
     timeframe legible on this chart". On a single-timeframe screenshot — which
     is what people actually send — the highest legible timeframe IS the chart's
     own. So a bearish Change of Character on M15 was being scored against an
     M15 bearish bias and awarded full marks for agreeing with itself. A live
     scan printed it plainly: `Change of Character · M15 bearish`, a green tick,
     +25. There is no confirmation in that row. The pattern and the "trend" are
     one reading of one chart, counted twice.

     Confirmation now has to come from somewhere the setup cannot see. Where the
     bias timeframe is not strictly wider than the entry timeframe, this FAILS.

     Fails, and does not report unknown — which was the first attempt, and it
     made the problem worse. The score is passed-over-KNOWN, so a check that
     leaves the denominator makes every remaining answer heavier: the same gold
     scan went from 75 to a perfect 100 STRONG for the sole reason that nothing
     had confirmed it. An unknown is the right answer for the event window,
     because no calendar exists on this platform and the app genuinely cannot
     ask. It is the wrong answer here. The question was asked and answered:
     a single-timeframe screenshot carries no higher-timeframe confluence, the
     same way a level with one touch is a level with one touch. */
  const chartTf = timeframeMinutes(reading && reading.timeframe_on_chart)
  const biasTf = timeframeMinutes(reading && reading.trend_timeframe)
  const biasLabel = (reading && reading.trend_timeframe) || 'higher timeframe'

  let alignOk = null
  let alignDetail = 'No pattern or trend read from this chart'

  if (chartTf != null && biasTf != null && biasTf <= chartTf) {
    alignOk = false
    alignDetail = biasLabel + ' is the chart’s own timeframe — nothing wider confirms this setup'
  } else if (bias === 'NEUTRAL') {
    alignOk = false
    alignDetail = biasLabel + ' is ranging — no trend to align with'
  } else if (patterns.length && bias) {
    alignOk = (dir === 'BUY' && bias === 'BULLISH') || (dir === 'SELL' && bias === 'BEARISH')
    alignDetail = patterns[0] + ' · ' + biasLabel + ' ' + bias.toLowerCase()
  }
  checks.push({ id: 'align', label: 'Pattern aligns with a higher timeframe', ok: alignOk, detail: alignDetail })

  /* 3 · The stop clears the chart's own noise. See [[STOP_ATR_BAND]] above for
     why this is a ratio rather than the pip band it replaced. */
  const acr = Number(reading && reading.average_candle_range)
  let volOk = null
  let volDetail = 'Candles not distinguishable on this chart'
  if (Number.isFinite(acr) && acr > 0 && valid) {
    const ratio = ladder.risk / acr
    volOk = ratio >= STOP_ATR_BAND[0] && ratio <= STOP_ATR_BAND[1]
    volDetail = 'Stop is ' + ratio.toFixed(1) + '× the average candle'
      + (ratio < STOP_ATR_BAND[0]
        ? ' — inside the noise'
        : ratio > STOP_ATR_BAND[1]
          ? ' — too wide for the target to be reached'
          : '')
  }
  checks.push({ id: 'vol', label: 'Stop clears the chart’s noise', ok: volOk, detail: volDetail })

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

  /* What each check was WORTH, so the breakdown can show its arithmetic.
     Not a constant 20: the score is passed-over-KNOWN scaled to 100, so on a
     reading that could only answer four questions each one is worth 25, and
     printing "+20" there would not add up to the number above it. An unknown
     is worth nothing because it is not in the denominator either — it is
     marked "n/a" rather than "+0", which is a different claim. */
  const each = known > 0 ? Math.round(100 / known) : 0
  for (const c of checks) {
    c.weight = c.ok === true || c.ok === false ? each : null
    c.points = c.ok === true ? each : c.ok === false ? 0 : null
  }

  return { checks, passed, known, failed, unknown, score, grade, direction: dir, each }
}

/**
 * A minute count as a trader would say it out loud.
 *
 * Deliberately coarse: the input is an age the server derived from a time axis
 * read off an image, against a chart clock whose timezone is not printed
 * anywhere. "About 3 days" is the whole of what that supports.
 */
export function describeAge(minutes) {
  const m = Number(minutes)
  if (!Number.isFinite(m)) return 'an unknown time'
  if (m < 90) return Math.round(m) + ' minutes'
  const hours = Math.round(m / 60)
  return hours < 36 ? hours + ' hours' : Math.round(hours / 24) + ' days'
}

/* ── Guardrails ──────────────────────────────────────────────────────
   The user's own hard limits. A BLOCK is not advice — the execute control
   refuses. Two of the doc's four are unreachable here and say so; an
   unknown never blocks, because a rule that cannot be evaluated must not
   silently forbid every trade. */

export const DEFAULT_RULES = {
  minBlendedRR: 1.5,
  /** Ceiling on the stop as a multiple of the chart's average candle. Shares
   *  [[STOP_ATR_BAND]]'s upper bound, and replaces a `maxStopPips: 50` that
   *  measured gold's ordinary ten-dollar stop as 1000 pips and warned on every
   *  metal and index trade the app ever planned. */
  maxStopAtr: 8,
  maxConsecutiveLosses: 3,
  eventBufferMinutes: 30,
}

/**
 * How far the chart's entry may sit from the live quote before the chart is
 * judged to be of a different instrument. A current chart is within a percent
 * or two of the market, and stale ones are blocked separately; gold against
 * the Nasdaq is off by more than eighty. Shared with Android's
 * `Guardrails.MAX_PRICE_GAP`.
 */
export const MAX_PRICE_GAP = 0.10

/**
 * @param {object} ladder  the plan actually being sent
 * @param {object} rules   the user's limits, or [[DEFAULT_RULES]]
 * @param {object} reading the `analyze-chart` reading — needed for the two
 *                         rules that judge the CHART rather than the plan.
 *                         Optional; both report UNKNOWN without it.
 * @param {object} ctx     { livePrice, symbol, sizing } from the screen.
 *                         `sizing` is { risk, budget, share, pct, lots,
 *                         atMinimum, maxStopPips, riskText, balanceText } —
 *                         `risk` and `budget` in the ACCOUNT's currency, `share`
 *                         as a percent of balance — or null without a broker.
 */
export function checkGuardrails(ladder, rules, reading, ctx) {
  const r = rules || DEFAULT_RULES
  const out = []

  /* ── Is the chart still the market? ──────────────────────────────
     First in the list because it invalidates every rule under it. A stop
     distance, an R:R and a key level are all statements about a market that
     existed when the screenshot was taken, and none of them survives the
     screenshot being old.

     This is a BLOCK rather than a warning, and it is the only new blocker
     here. It has to be: the scan that prompted it read a gold chart whose
     last candle was three days back and whose terminal had "Market closed"
     printed on it, returned a confident SELL at 4348.50, scored it MODERATE,
     and armed execute. Price had already rallied to 4403 — an hour after that
     final candle. The reading was a fair account of the chart. The chart was
     not a fair account of the market, and nothing in the app was looking. */
  if (reading && reading.stale) {
    out.push({
      id: 'stale',
      label: 'Chart is current',
      status: 'BLOCK',
      detail: (reading.stale_reason || 'This chart is not current.')
        + ' Take a fresh screenshot and scan again.',
    })
  } else if (reading && reading.chart_age_minutes == null && !reading.market_closed) {
    /* The time axis was cropped or unreadable. Unknown, never a pass — an
       unchecked clock is not a clock that said "now". */
    out.push({
      id: 'stale',
      label: 'Chart is current',
      status: 'UNKNOWN',
      detail: 'No readable time axis on this chart — its age could not be checked',
    })
  } else if (reading) {
    out.push({
      id: 'stale',
      label: 'Chart is current',
      status: 'PASS',
      detail: 'Last candle about ' + describeAge(reading.chart_age_minutes) + ' old',
    })
  }

  /* ── Is the chart a picture of the instrument being priced? ──────────
     Price is the one thing on a chart that cannot be mislabelled. On
     2026-10-01 a gold chart (entries near 4180) was scanned as NAS100, where
     the Nasdaq trades near 25,000, and a NAS100 sell with its stop at 4183.5
     reached the executor — a deleted broker account stopped it, not the app. */
  const live = Number(ctx && ctx.livePrice)
  const sym = (ctx && ctx.symbol) || 'this instrument'
  if (ladder && ladder.valid && Number.isFinite(live) && live > 0) {
    const gap = Math.abs(ladder.entry - live) / live
    const px = v => (v >= 1000 ? v.toFixed(2) : v.toFixed(v < 10 ? 5 : 3))
    out.push(gap > MAX_PRICE_GAP
      ? {
          id: 'scale',
          label: 'Chart is ' + sym,
          status: 'BLOCK',
          detail: 'The chart’s entry ' + px(ladder.entry) + ' is ' + (gap * 100).toFixed(0)
            + '% from ' + sym + '’s live ' + px(live) + ' — this is a chart of a different instrument.',
        }
      : {
          id: 'scale',
          label: 'Chart is ' + sym,
          status: 'PASS',
          detail: 'Entry is within ' + (gap * 100).toFixed(1) + '% of the live price',
        })
  } else if (ladder && ladder.valid) {
    out.push({
      id: 'scale',
      label: 'Chart is ' + sym,
      status: 'UNKNOWN',
      detail: 'No live price from your broker — could not confirm the chart shows this instrument',
    })
  }

  /* ── Does the stop cost what the user said they would lose? ──────────
     The rule this list was missing. Sizing floors to the 0.01 minimum and
     cannot go below it, so on a small account a wide stop risks many times
     the budget — and nothing compared the two. On Android a R1,008 gold
     account at 1% was shown a plan risking R784, 78% of the balance, with
     every rule green; here the stepper simply kept its 0.05 default. A BLOCK,
     because it is the user's own number. */
  const s = ctx && ctx.sizing
  if (!s) {
    out.push({
      id: 'risk',
      label: 'Risk within your budget',
      status: 'UNKNOWN',
      detail: 'No broker balance — the plan cannot be checked against your account',
    })
  } else {
    const pctLabel = s.pct + '%'
    const over = s.risk > s.budget * 1.001
    out.push({
      id: 'risk',
      label: 'Risk ≤ ' + pctLabel + ' of balance',
      status: over ? 'BLOCK' : 'PASS',
      detail: s.lots.toFixed(2) + ' lots risks ' + s.riskText + ' on this stop — '
        + s.share.toFixed(1) + '% of your ' + s.balanceText + ' balance',
      remedy: !over ? null : s.atMinimum
        ? '0.01 lots is the smallest order a broker accepts, and on this stop it already risks more than your '
          + pctLabel + '. At this balance, ' + pctLabel + ' covers a stop of about '
          + Math.floor(s.maxStopPips) + ' pips on ' + sym + '. This setup does not fit this account.'
        : 'This plan is larger than your ' + pctLabel + ' allows.',
    })
  }

  if (ladder && ladder.valid) {
    out.push({
      id: 'rr',
      label: 'Blended R:R ≥ 1:' + r.minBlendedRR,
      status: ladder.blendedRR >= r.minBlendedRR ? 'PASS' : 'BLOCK',
      detail: 'Plan gives 1:' + ladder.blendedRR.toFixed(2),
    })

    /* Measured against the chart's own candles rather than a pip count, for
       the reason [[STOP_ATR_BAND]] sets out. UNKNOWN when the model could not
       measure the candles — there is then no yardstick, and inventing one
       would warn or clear at random. */
    const acr = Number(reading && reading.average_candle_range)
    if (Number.isFinite(acr) && acr > 0) {
      const ratio = ladder.risk / acr
      const over = ratio > r.maxStopAtr
      out.push({
        id: 'stop',
        label: 'Stop ≤ ' + r.maxStopAtr + '× the average candle',
        /* Wider than the limit is a WARN, not a BLOCK: it costs more than
           planned but the trade is still coherent. The doc grades it so too. */
        status: over ? 'WARN' : 'PASS',
        detail: 'Stop is ' + ratio.toFixed(1) + '× the average candle'
          + (over ? ' — the target needs more candles than the trade has' : ''),
      })
    } else {
      out.push({
        id: 'stop',
        label: 'Stop ≤ ' + r.maxStopAtr + '× the average candle',
        status: 'UNKNOWN',
        detail: 'The chart’s candles could not be measured',
      })
    }
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
