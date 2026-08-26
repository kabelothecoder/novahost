package com.novahost.app.ui.scanner

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The scanner's domain model and the engine that scores it.
 *
 * All of this is deliberately pure Kotlin with no Compose, no Android and no
 * network. The design makes a specific promise on two separate screens --
 * "Scored on your device. Nothing about your account leaves the phone." and
 * "Split on device, then sent as one basket" -- and a promise like that is only
 * worth printing if the arithmetic behind it is actually here and actually
 * auditable. A screen that renders five hardcoded "+20" chips would say the
 * same words and mean none of them.
 *
 * The seam this file does NOT close is the *reading*: what pattern is on the
 * chart, which way each timeframe leans, where the key level sits. That comes
 * from the vision pass (`/functions/v1/analyze-chart`, per the architecture
 * brief) or from a TA feed, and arrives here as [ScanReading]. Everything
 * downstream of that -- the score, the ladder, the lot split, the guardrail
 * verdict -- is computed, not mocked.
 */

// -- Inputs -----------------------------------------------------------------

/**
 * The horizon the user picked, and the volatility band that follows from it.
 *
 * The band is part of the mode rather than a global constant because "ATR 14
 * pips" is a calm hour on a day trade and a dead one on a swing. Screen 02
 * scores volatility against this band, so the same chart legitimately scores
 * differently under Scalp and under Swing.
 */
enum class ScanMode(
    val label: String,
    val timeframes: String,
    val atrFloorPips: Double,
    val atrCeilingPips: Double,
    /** How long the trade is expected to stay open. Feeds the event-window check. */
    val windowMinutes: Int
) {
    SCALP("Scalp", "M5-M15", 4.0, 12.0, 45),
    DAY("Day", "H1-H4", 10.0, 22.0, 240),
    SWING("Swing", "H4-D1", 18.0, 60.0, 1440)
}

enum class Direction(val label: String) {
    BUY("BUY"),
    SELL("SELL");

    val opposite: Direction get() = if (this == BUY) SELL else BUY
}

enum class Bias(val label: String) {
    BULLISH("Bullish"),
    BEARISH("Bearish"),
    NEUTRAL("Neutral");

    /** A neutral timeframe neither confirms nor contradicts -- it abstains. */
    fun agreesWith(direction: Direction): Boolean = when (this) {
        BULLISH -> direction == Direction.BUY
        BEARISH -> direction == Direction.SELL
        NEUTRAL -> false
    }
}

/** One row of the timeframe alignment grid. */
data class TimeframeRead(
    val timeframe: String,
    val bias: Bias,
    /** One line of evidence. Shown under the bias so the grid is readable rather than merely coloured. */
    val note: String
)

/** One entry on the event radar, and the input to the event-window check. */
data class MarketEvent(
    val currency: String,
    val title: String,
    val minutesAway: Int,
    val highImpact: Boolean,
    /** Historical average move, in pips. Null when the calendar does not carry one. */
    val averageMovePips: Double? = null,
    val forecast: String? = null,
    val prior: String? = null,
    /** What to do about it. Blank for events that need nothing. */
    val advice: String = ""
)

/**
 * The instrument, as the scanner needs it.
 *
 * [pipSize] and [pipValuePerLot] are derived from the symbol rather than passed
 * in, because getting them wrong is silent: a JPY pair costed at 0.0001 per pip
 * sizes the position 100x too large and every figure downstream still looks
 * plausible.
 */
data class Instrument(
    val symbol: String,
    val displayName: String,
    val broker: String,
    val price: Double,
    val changePercent: Double,
    val changePips: Double,
    val spreadPips: Double,
    val atrPips: Double,
    val session: String,
    val sessionOpen: Boolean
) {
    private val upper get() = symbol.uppercase()
    private val isJpy get() = upper.contains("JPY")
    private val isMetal get() = upper.startsWith("XAU") || upper.startsWith("XAG")
    private val isIndex get() = upper.any { it.isDigit() }

    /** Formatted the way the venue quotes it: 5 places for FX, 3 for JPY, 2 for metals and indices. */
    val priceDecimals: Int get() = when {
        isIndex -> 2
        isMetal -> 2
        isJpy -> 3
        else -> 5
    }

    val pipSize: Double get() = when {
        isIndex -> 1.0
        isMetal -> 0.1
        isJpy -> 0.01
        else -> 0.0001
    }

    /**
     * USD per pip on one standard lot.
     *
     * Exact only for USD-quoted FX. For JPY-quoted and metals this is the
     * conventional desk approximation, which is what a risk figure on a phone
     * is allowed to be -- the broker prices the fill, not this. It is named and
     * commented rather than buried so nobody later mistakes it for exact.
     */
    val pipValuePerLot: Double get() = when {
        isIndex -> 1.0
        isMetal -> 10.0
        isJpy -> 6.7
        else -> 10.0
    }

    fun formatPrice(value: Double): String = String.format("%.${priceDecimals}f", value)

    /** Signed pip distance from [from] to [to]. */
    fun pipsBetween(from: Double, to: Double): Double = (to - from) / pipSize
}

/**
 * What the chart says, before any of it is scored.
 *
 * This is the vision pass's output shape. Everything in [ConfluenceEngine]
 * consumes it and nothing in it is scored, weighted or judged -- keeping the
 * reading and the scoring apart is what makes the five checks auditable, since
 * a wrong score is then either a wrong reading or a wrong rule, never both at
 * once.
 */
data class ScanReading(
    val direction: Direction,
    val entry: Double,
    val stop: Double,
    /** "H4 bull flag", shown on the pattern check. */
    val pattern: String,
    /** The timeframe whose trend the pattern is being checked against. */
    val trendTimeframe: String,
    val trendBias: Bias,
    /** The nearest structural level, and how well tested it is. */
    val keyLevel: Double,
    val keyLevelTouches: Int,
    val timeframes: List<TimeframeRead>,
    val events: List<MarketEvent>,
    /** One-paragraph plain-English read, shown under the verdict. */
    val narrative: String
)

// -- Ladder -----------------------------------------------------------------

/**
 * How the position is split across the three targets.
 *
 * The three presets are the design's. They are fractions rather than percents
 * so nothing downstream has to remember to divide by 100.
 */
enum class AllocationPreset(
    val label: String,
    val caption: String,
    val splits: List<Double>
) {
    SAFE("60/30/10", "SAFE", listOf(0.60, 0.30, 0.10)),
    BALANCED("50/25/25", "BALANCED", listOf(0.50, 0.25, 0.25)),
    RUNNER("34/33/33", "RUNNER", listOf(0.34, 0.33, 0.33));

    companion object {
        val Default = BALANCED
    }
}

/** One rung: a target, its share of the position, and what it pays. */
data class TradeLeg(
    val name: String,
    val rMultiple: Double,
    val allocation: Double,
    val price: Double,
    val lots: Double,
    val estimatedPl: Double
)

/**
 * The full plan: sizing, the three legs, and the aggregate figures the plan
 * screen prints in its footer.
 */
data class TradePlan(
    val instrument: Instrument,
    val direction: Direction,
    val entry: Double,
    val stop: Double,
    val stopPips: Double,
    val riskAmount: Double,
    val totalLots: Double,
    val legs: List<TradeLeg>,
    val preset: AllocationPreset
) {
    /**
     * Reward-weighted R across the legs.
     *
     * This is the number the first confluence check and the first guardrail
     * both test, and it is the reason multi-TP is not free: taking half the
     * position at 1:1 caps the blended figure well below the 1:3 the third
     * target advertises.
     *
     * Weighted by the lots actually being sent rather than by the preset's
     * fractions. Those differ once each leg is floored to the broker's 0.01
     * increment, and this figure decides a BLOCK -- so it has to describe the
     * basket that will exist, not the one the preset asked for.
     */
    val blendedRR: Double get() {
        val volume = legs.sumOf { it.lots }
        if (volume <= 0.0) return 0.0
        return legs.sumOf { it.lots * it.rMultiple } / volume
    }

    val totalReward: Double get() = legs.sumOf { it.estimatedPl }

    /**
     * What the stop actually costs, as opposed to what was budgeted.
     *
     * [riskAmount] is the intent (balance x risk%). This is the outcome after
     * lot rounding, and it is the number every screen shows: printing the
     * budget beside a position that risks something else is how a risk feature
     * loses the user's trust the first time they check it against the terminal.
     */
    val actualRisk: Double get() = legs.sumOf { it.lots } * stopPips * instrument.pipValuePerLot

    val rewardLabel: String get() = "1:" + String.format("%.2f", blendedRR)
}

/**
 * The R multiples the ladder targets, shallowest first.
 *
 * Fixed at 1:1 / 1:2 / 1:3 to match the design. They are not user-facing yet;
 * when they become editable this is the one place that changes.
 */
private val LADDER_R = listOf(1.0, 2.0, 3.0)

object TradePlanner {

    /**
     * Sizes a position from risk and lays it out across the three targets.
     *
     * Returns null when the stop is degenerate. A zero-pip stop divides to
     * infinite lots, and an "infinite" position that renders on a broker
     * confirmation is worse than a screen that admits it has nothing to show.
     */
    fun build(
        instrument: Instrument,
        reading: ScanReading,
        balance: Double,
        riskPercent: Double,
        preset: AllocationPreset
    ): TradePlan? {
        val stopPips = abs(instrument.pipsBetween(reading.entry, reading.stop))
        if (stopPips < 0.1) return null

        val riskAmount = balance * (riskPercent / 100.0)
        val rawLots = riskAmount / (stopPips * instrument.pipValuePerLot)
        // Brokers quote in 0.01 increments. Rounding here rather than at the
        // point of display keeps every P/L figure on the plan screen consistent
        // with the volume actually sent -- the alternative is a plan whose legs
        // sum to a different position than the one the basket opens.
        val totalLots = floorLots(rawLots)
        if (totalLots <= 0.0) return null

        val sign = if (reading.direction == Direction.BUY) 1 else -1

        val legs = LADDER_R.mapIndexed { index, r ->
            val allocation = preset.splits[index]
            val lots = floorLots(totalLots * allocation)
            val targetPips = stopPips * r
            TradeLeg(
                name = "TP" + (index + 1),
                rMultiple = r,
                allocation = allocation,
                price = reading.entry + sign * targetPips * instrument.pipSize,
                lots = lots,
                estimatedPl = targetPips * instrument.pipValuePerLot * lots
            )
        }

        return TradePlan(
            instrument = instrument,
            direction = reading.direction,
            entry = reading.entry,
            stop = reading.stop,
            stopPips = stopPips,
            riskAmount = riskAmount,
            totalLots = totalLots,
            legs = legs,
            preset = preset
        )
    }

    /**
     * Broker lot granularity: two decimals, rounded **down**.
     *
     * Down, not to nearest. A 1% risk budget on a 16-pip stop sizes to 0.625
     * lots; rounding that to 0.63 risks $100.80 against a $100 limit. Half of
     * all roundings breaching the number the user set is not a rounding policy,
     * it is a broken limit -- so the remainder is always given back.
     *
     * The 0.01 floor is the one case that can still exceed the budget, because
     * it is the smallest position a broker will accept and there is nothing
     * below it to round to. [TradePlan.actualRisk] reports what that really
     * costs rather than repeating the budget back.
     */
    private fun floorLots(value: Double): Double =
        (floor(value * 100) / 100.0).coerceAtLeast(0.01)
}

// -- Confluence -------------------------------------------------------------

/**
 * One of the five checks, after it has run.
 *
 * [detail] is what makes the breakdown auditable rather than decorative: every
 * row states the figure it judged, so a user who disagrees with a score can see
 * which input they disagree with.
 */
data class ConfluenceCheck(
    val title: String,
    val detail: String,
    val passed: Boolean,
    val points: Int
)

enum class Conviction(val label: String) {
    STRONG("STRONG"),
    MODERATE("MODERATE"),
    WEAK("WEAK")
}

data class ConfluenceResult(
    val score: Int,
    val conviction: Conviction,
    val checks: List<ConfluenceCheck>
) {
    val passedCount: Int get() = checks.count { it.passed }
}

/**
 * The five checks, twenty points each.
 *
 * Equal weighting is a deliberate choice, not an unfinished one. A weighted
 * model would score better and explain worse, and this number's entire job is
 * to be explained -- the user is being asked to trust it with a position size.
 * Five twenties also means the arithmetic on the score screen is checkable at a
 * glance, which a 17/23/15/28/17 split would not be.
 */
object ConfluenceEngine {

    private const val POINTS_PER_CHECK = 20

    /** How close to a level still counts as "on" it, as a fraction of the stop distance. */
    private const val KEY_LEVEL_TOLERANCE = 0.85

    /** Below this, a level has not been tested enough to trade off. */
    private const val MIN_LEVEL_TOUCHES = 3

    fun score(
        instrument: Instrument,
        reading: ScanReading,
        plan: TradePlan,
        mode: ScanMode
    ): ConfluenceResult {
        val checks = listOf(
            rewardCheck(plan),
            trendCheck(reading),
            volatilityCheck(instrument, mode),
            keyLevelCheck(instrument, reading, plan),
            eventCheck(reading, mode)
        )
        val score = checks.sumOf { it.points }
        return ConfluenceResult(
            score = score,
            conviction = when {
                score >= 75 -> Conviction.STRONG
                score >= 50 -> Conviction.MODERATE
                else -> Conviction.WEAK
            },
            checks = checks
        )
    }

    private fun rewardCheck(plan: TradePlan): ConfluenceCheck {
        val passed = plan.blendedRR >= 1.5
        return ConfluenceCheck(
            title = "Blended R:R above 1:1.5",
            detail = plan.rewardLabel + " across the three targets",
            passed = passed,
            points = if (passed) POINTS_PER_CHECK else 0
        )
    }

    private fun trendCheck(reading: ScanReading): ConfluenceCheck {
        val passed = reading.trendBias.agreesWith(reading.direction)
        val trend = reading.trendBias.label.lowercase()
        return ConfluenceCheck(
            title = "Pattern aligns with " + reading.trendTimeframe + " trend",
            detail = if (passed) {
                reading.pattern + " inside a " + trend + " " + reading.trendTimeframe
            } else {
                reading.pattern + " against a " + trend + " " + reading.trendTimeframe
            },
            passed = passed,
            points = if (passed) POINTS_PER_CHECK else 0
        )
    }

    private fun volatilityCheck(instrument: Instrument, mode: ScanMode): ConfluenceCheck {
        val atr = instrument.atrPips
        val passed = atr >= mode.atrFloorPips && atr <= mode.atrCeilingPips
        val band = trimmed(mode.atrFloorPips) + "-" + trimmed(mode.atrCeilingPips) + "p"
        return ConfluenceCheck(
            title = "Volatility moderate",
            detail = when {
                passed -> "ATR " + trimmed(atr) + "p inside the " + band + " band"
                atr < mode.atrFloorPips -> "ATR " + trimmed(atr) + "p - too quiet for the " + band + " band"
                else -> "ATR " + trimmed(atr) + "p - above the " + band + " band"
            },
            passed = passed,
            points = if (passed) POINTS_PER_CHECK else 0
        )
    }

    private fun keyLevelCheck(
        instrument: Instrument,
        reading: ScanReading,
        plan: TradePlan
    ): ConfluenceCheck {
        val distancePips = abs(instrument.pipsBetween(reading.entry, reading.keyLevel))
        val tolerance = plan.stopPips * KEY_LEVEL_TOLERANCE
        val passed = distancePips <= tolerance && reading.keyLevelTouches >= MIN_LEVEL_TOUCHES
        val levelLabel = instrument.formatPrice(reading.keyLevel)
        return ConfluenceCheck(
            title = "Entry sits on a key level",
            detail = when {
                passed -> trimmed(distancePips) + "p from " + levelLabel + " - " + reading.keyLevelTouches + " touches"
                reading.keyLevelTouches < MIN_LEVEL_TOUCHES ->
                    levelLabel + " has only " + reading.keyLevelTouches + " touches"
                else -> trimmed(distancePips) + "p from " + levelLabel + " - too far to lean on"
            },
            passed = passed,
            points = if (passed) POINTS_PER_CHECK else 0
        )
    }

    private fun eventCheck(reading: ScanReading, mode: ScanMode): ConfluenceCheck {
        val intruder = reading.events
            .filter { it.highImpact }
            .minByOrNull { it.minutesAway }
            ?.takeIf { it.minutesAway <= mode.windowMinutes }

        return ConfluenceCheck(
            title = "Event window clear",
            detail = if (intruder == null) {
                "Nothing high-impact inside the " + humanDuration(mode.windowMinutes) + " window"
            } else {
                intruder.currency + " " + intruder.title + " in " +
                    humanDuration(intruder.minutesAway) + " - inside the window"
            },
            passed = intruder == null,
            points = if (intruder == null) POINTS_PER_CHECK else 0
        )
    }
}

// -- Guardrails -------------------------------------------------------------

/**
 * How hard a broken rule pushes back.
 *
 * The distinction is the point of the whole feature: a [BLOCK] is the user's
 * own past decision overruling their present one, and the app does not offer to
 * override it inline. The blocked screen says so in as many words -- "Only you
 * can change your rules, in Guardrail settings" -- because a "proceed anyway"
 * button next to a blocker turns a limit into a speed bump.
 */
enum class RuleSeverity { BLOCK, WARN, PASS }

data class GuardrailOutcome(
    val title: String,
    val detail: String,
    val severity: RuleSeverity,
    /** The measured figure, printed on the right of the row. */
    val reading: String
)

/**
 * The user's limits. Defaults are the design's.
 *
 * Held as data rather than as constants because the "EDIT MY RULES" affordance
 * has to lead somewhere, and because the consecutive-loss count is the only
 * input here the scanner cannot re-derive from the plan.
 */
data class GuardrailConfig(
    val minBlendedRR: Double = 1.5,
    val maxStopPips: Double = 50.0,
    val maxConsecutiveLosses: Int = 3,
    val minEventClearanceMinutes: Int = 30
)

data class GuardrailReport(
    val outcomes: List<GuardrailOutcome>
) {
    val blockers: List<GuardrailOutcome> get() = outcomes.filter { it.severity == RuleSeverity.BLOCK }
    val warnings: List<GuardrailOutcome> get() = outcomes.filter { it.severity == RuleSeverity.WARN }
    val passes: List<GuardrailOutcome> get() = outcomes.filter { it.severity == RuleSeverity.PASS }

    val blocked: Boolean get() = blockers.isNotEmpty()
    val allClear: Boolean get() = outcomes.all { it.severity == RuleSeverity.PASS }
}

object Guardrails {

    fun evaluate(
        plan: TradePlan,
        reading: ScanReading,
        config: GuardrailConfig,
        consecutiveLosses: Int
    ): GuardrailReport {
        val minRrLabel = "1:" + trimmed(config.minBlendedRR)

        val rr = GuardrailOutcome(
            title = "Blended R:R >= " + minRrLabel,
            detail = if (plan.blendedRR >= config.minBlendedRR) {
                "Plan gives " + plan.rewardLabel
            } else {
                "Plan gives " + plan.rewardLabel + " - below your minimum"
            },
            // A blocker, not a warning: this is the rule the whole ladder exists
            // to satisfy, so a plan that fails it is not a trade with a caveat.
            severity = if (plan.blendedRR >= config.minBlendedRR) RuleSeverity.PASS else RuleSeverity.BLOCK,
            reading = plan.rewardLabel
        )

        val stop = GuardrailOutcome(
            title = "Stop loss <= " + trimmed(config.maxStopPips) + " pips",
            detail = if (plan.stopPips <= config.maxStopPips) {
                trimmed(plan.stopPips) + "p - inside your limit"
            } else {
                trimmed(plan.stopPips) + "p - " +
                    trimmed(plan.stopPips - config.maxStopPips) + "p wider than your limit"
            },
            // Warn, not block: a wide stop is already paid for by the smaller
            // position the risk sizing hands back, so this is information rather
            // than a veto.
            severity = if (plan.stopPips <= config.maxStopPips) RuleSeverity.PASS else RuleSeverity.WARN,
            reading = trimmed(plan.stopPips) + "p"
        )

        val streak = GuardrailOutcome(
            title = "Consecutive losses < " + config.maxConsecutiveLosses,
            detail = when {
                consecutiveLosses >= config.maxConsecutiveLosses ->
                    consecutiveLosses.toString() + " in a row - the engine is locked"
                consecutiveLosses == config.maxConsecutiveLosses - 1 ->
                    consecutiveLosses.toString() + " of " + config.maxConsecutiveLosses +
                        " - one more locks the engine"
                else -> consecutiveLosses.toString() + " of " + config.maxConsecutiveLosses
            },
            severity = if (consecutiveLosses >= config.maxConsecutiveLosses) RuleSeverity.BLOCK else RuleSeverity.PASS,
            reading = consecutiveLosses.toString() + " of " + config.maxConsecutiveLosses
        )

        val nextHighImpact = reading.events.filter { it.highImpact }.minByOrNull { it.minutesAway }
        val clearance = nextHighImpact?.minutesAway ?: Int.MAX_VALUE
        val event = GuardrailOutcome(
            title = "No high-impact event <= " + config.minEventClearanceMinutes + "m",
            detail = when {
                nextHighImpact == null -> "Nothing high-impact on the calendar"
                clearance <= config.minEventClearanceMinutes ->
                    nextHighImpact.title + " in " + humanDuration(clearance)
                else -> "Next is " + nextHighImpact.title + ", " + humanDuration(clearance) + " out"
            },
            severity = if (clearance <= config.minEventClearanceMinutes) RuleSeverity.WARN else RuleSeverity.PASS,
            reading = if (nextHighImpact == null) "clear" else humanDuration(clearance)
        )

        return GuardrailReport(listOf(rr, stop, streak, event))
    }
}

// -- Formatting -------------------------------------------------------------

/** "14.2", but "50" rather than "50.0" -- a trailing zero on a round limit reads as false precision. */
internal fun trimmed(value: Double): String =
    if (abs(value - value.roundToInt()) < 0.05) value.roundToInt().toString()
    else String.format("%.1f", value)

/**
 * "2h 41m", "18m", "3d". The calendar's own idiom, not a duration library's.
 *
 * A zero remainder is dropped rather than printed: "1d 0h" and "2h 0m" read as
 * a formatter that did not finish, next to countdowns that are otherwise exact.
 */
internal fun humanDuration(minutes: Int): String = when {
    minutes >= 1440 -> {
        val hours = (minutes % 1440) / 60
        (minutes / 1440).toString() + "d" + if (hours > 0) " " + hours + "h" else ""
    }
    minutes >= 60 -> {
        val rest = minutes % 60
        (minutes / 60).toString() + "h" + if (rest > 0) " " + rest + "m" else ""
    }
    else -> minutes.toString() + "m"
}
