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
 * The horizon the user picked.
 *
 * This used to carry an ATR band in pips as well — `4.0..12.0` on a scalp and so
 * on — and [ConfluenceEngine.volatilityCheck] scored against it. Those numbers
 * described an FX major and failed every metal, index and crypto pair outright;
 * the check is a stop-to-ATR ratio now, which needs no per-mode constant because
 * a ratio carries no units. The fields are gone rather than left unread, so
 * nothing can quietly start scoring against them again.
 */
enum class ScanMode(
    val label: String,
    val timeframes: String,
    /** How long the trade is expected to stay open. Feeds the event-window check. */
    val windowMinutes: Int,
    /**
     * The widest chart timeframe this mode still makes sense on, in minutes.
     *
     * Deliberately looser than [timeframes], which is what the rail prints. The
     * band exists to catch a chart that is plainly the wrong horizon -- an M5
     * screenshot scanned as a swing -- and not to nag someone who reads their
     * day trades off M30. A warning that fires on judgement calls is a warning
     * people learn to dismiss, and then it is not there for the real one.
     */
    val chartFloorMinutes: Int,
    val chartCeilingMinutes: Int
) {
    SCALP("Scalp", "M5-M15", 45, chartFloorMinutes = 1, chartCeilingMinutes = 30),
    DAY("Day", "H1-H4", 240, chartFloorMinutes = 15, chartCeilingMinutes = 1440),
    SWING("Swing", "H4-D1", 1440, chartFloorMinutes = 60, chartCeilingMinutes = 100_000)
}

/**
 * What the scan should look for on the chart.
 *
 * Orthogonal to [ScanMode]: the mode says how long the trade is held, this says
 * which school of reading produces it. The same H4 chart genuinely yields
 * different entries under SMC and under a trend-pullback reading, and a scanner
 * that hides that behind one fixed prompt is picking a side for the user.
 *
 * The old endpoint hardcoded "You are a professional SMC trader" into its
 * system prompt, so every scan this product ever ran was an SMC read whether or
 * not that was how the user trades.
 *
 * [brief] is sent to the vision pass and becomes the reading instruction. It is
 * written as guidance rather than a rule list because the model is being asked
 * to read a chart the way a trader of that school would, not to tick boxes.
 */
enum class ScanStrategy(
    val label: String,
    /** One line under the chip. What the user is choosing. */
    val caption: String,
    val brief: String
) {
    SMC(
        "Smart Money",
        "Order blocks, liquidity, ChoCH",
        "Read this as a Smart Money Concepts trader. Look for order blocks, fair value gaps, " +
            "liquidity sweeps above highs or below lows, and a change of character that confirms " +
            "the shift. Enter from the zone price is returning to, not from where it broke."
    ),
    PRICE_ACTION(
        "Price Action",
        "Support, resistance, candles",
        "Read this as a pure price action trader. Work from horizontal support and resistance, " +
            "the candles printing at those levels, and rejection wicks. No indicators. The key " +
            "level is the one price has respected most often in the visible window."
    ),
    CRT(
        "CRT",
        "Sweep, then limit at the 50%",
        "Read this as a Candle Range Theory trader. The range of a completed higher-timeframe " +
            "candle is what matters -- its high and its low are the levels. The model runs in " +
            "three candles: one sets the range, the next sweeps one side of it to take the " +
            "liquidity resting there, and the third distributes toward the opposite side. " +
            "The setup is a sweep that closes back INSIDE the range. " +
            "Entry is the 50% of that sweep candle -- its equilibrium, halfway between its high " +
            "and its low -- placed as a LIMIT, because price has to come back to it. Do not " +
            "enter at the sweep candle's close. Stop goes just beyond the sweep's wick, and the " +
            "target is the opposite end of the range. " +
            "If price has already run past that 50% toward the target, the entry is gone: report " +
            "it as a missed setup with low confidence and say so in the narrative. Do not move " +
            "the entry to wherever price is now to make the setup fit. " +
            "Name the range high and low you are working from in entry_note. " +
            "A sweep that closes OUTSIDE the range is a breakout, not a CRT -- report that as " +
            "low confidence rather than forcing it into the model."
    ),
    BREAKOUT(
        "Breakout",
        "Range breaks and retests",
        "Read this as a breakout trader. Find the consolidation -- range, triangle, flag -- and " +
            "the level that ends it. Prefer the retest entry after the break over chasing the " +
            "candle that broke it, and put the stop back inside the range."
    );

    companion object {
        val Default = SMC

        /** Tolerant of case and of the old wire values. */
        fun from(raw: String?): ScanStrategy =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Default
    }
}

enum class Direction(val label: String) {
    BUY("BUY"),
    SELL("SELL");

    val opposite: Direction get() = if (this == BUY) SELL else BUY
}

/**
 * How the entry is reached: at the market, or waiting at a price.
 *
 * A setup's entry price is rarely where price is standing. "Buy the retest at
 * 1.0850" with price at 1.0880 is an order that waits *below*; "buy the break
 * above 1.0900" from the same place waits *above*. Those are different orders
 * with different fills, and the executor used to send neither -- it hardcoded
 * `openPrice: 0`, MetaCopier's market flag, so every scan filled at whatever
 * price happened to be live and then hung a stop and three targets off a level
 * that was never traded.
 *
 * Which one applies is arithmetic, not opinion:
 *
 * | Direction | entry below price | entry above price |
 * |-----------|-------------------|-------------------|
 * | BUY       | LIMIT (buy dip)   | STOP (buy break)  |
 * | SELL      | STOP (sell break) | LIMIT (sell rally)|
 *
 * The vision pass states its *intent* -- it knows whether it read a retest or a
 * breakout -- and [resolve] checks that intent against the live price. When they
 * disagree, price has moved since the screenshot was taken, and that is worth
 * saying out loud rather than quietly placing the other order.
 */
enum class EntryType(val label: String, val caption: String) {
    MARKET("Market", "fills now, at whatever price is live"),
    LIMIT("Limit", "waits for price to come back to the entry"),
    STOP("Stop", "waits for price to break through the entry");

    companion object {
        /**
         * How close to the live price still counts as "just take it", in pips.
         *
         * A pending order two pips away is a market order with extra steps, and
         * on most books it is rejected outright -- brokers enforce a minimum
         * stop/limit distance from the current price.
         */
        const val MARKET_TOLERANCE_PIPS = 3.0

        fun from(raw: String?): EntryType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MARKET

        /**
         * The order type that actually applies, from the live price.
         *
         * [livePrice] null or zero means nothing was priced -- no linked broker,
         * or the quote failed -- and the honest answer is MARKET: the app cannot
         * claim an entry waits above or below a price it does not have.
         */
        fun resolve(
            direction: Direction,
            entry: Double,
            livePrice: Double?,
            pipSize: Double
        ): EntryType {
            val live = livePrice?.takeIf { it > 0.0 } ?: return MARKET
            if (pipSize <= 0.0) return MARKET

            val distancePips = abs(entry - live) / pipSize
            if (distancePips <= MARKET_TOLERANCE_PIPS) return MARKET

            val entryIsBelow = entry < live
            return when (direction) {
                Direction.BUY -> if (entryIsBelow) LIMIT else STOP
                Direction.SELL -> if (entryIsBelow) STOP else LIMIT
            }
        }
    }
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
    val sessionOpen: Boolean,
    /**
     * The broker's own pip size, when it has been asked.
     *
     * Everything below this is a guess from the symbol's spelling, and the
     * guesses are wrong in ways that do not announce themselves: they cannot
     * tell a 5-digit book from a 4-digit one, they call every symbol containing
     * a digit an index worth 1.0 per pip, and `XAUUSD.m` is not a metal to
     * `startsWith("XAU")` once a suffix moves it. Each of those sizes a position
     * by a factor of ten while every figure on screen still looks plausible.
     *
     * `broker-quote` returns `digits` and `points` off the account's own
     * contract spec, so when this is set it wins outright. Null means nobody
     * asked -- no linked account, or the broker was unreachable -- and the
     * heuristics stand in.
     */
    val brokerPipSize: Double? = null,
    /** The broker's quoted decimal places. Same provenance as [brokerPipSize]. */
    val brokerDigits: Int? = null,
    /** What the broker calls this instrument, e.g. `Gold`, `.US30.`, `XAUUSD.m`. */
    val brokerSymbol: String? = null
) {
    private val upper get() = symbol.uppercase()
    private val isJpy get() = upper.contains("JPY")
    private val isMetal get() = upper.startsWith("XAU") || upper.startsWith("XAG")
    private val isIndex get() = upper.any { it.isDigit() }

    /** Formatted the way the venue quotes it: the broker's digit count when known. */
    val priceDecimals: Int get() = brokerDigits ?: when {
        isIndex -> 2
        isMetal -> 2
        isJpy -> 3
        else -> 5
    }

    val pipSize: Double get() = brokerPipSize?.takeIf { it > 0.0 } ?: when {
        isIndex -> 1.0
        isMetal -> 0.1
        isJpy -> 0.01
        else -> 0.0001
    }

    /** True when the numbers came from the broker rather than from the spelling. */
    val pricedByBroker: Boolean get() = brokerPipSize != null && brokerPipSize > 0.0

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
    /**
     * The timeframe printed on the chart itself. Null when it was cropped out.
     *
     * Carried so [ConfluenceEngine.trendCheck] can tell a real higher-timeframe
     * confirmation from the chart agreeing with itself -- see that function.
     */
    val chartTimeframe: String? = null,
    /** The nearest structural level, and how well tested it is. */
    val keyLevel: Double,
    val keyLevelTouches: Int,
    val timeframes: List<TimeframeRead>,
    val events: List<MarketEvent>,
    /** One-paragraph plain-English read, shown under the verdict. */
    val narrative: String,
    /**
     * The order type the vision pass expected -- a retest reads as LIMIT, a
     * breakout as STOP.
     *
     * Advisory only. [EntryType.resolve] decides what is actually sent, from the
     * live price. This is kept so the two can be compared: when they disagree,
     * price has moved since the screenshot and the user should know before the
     * order goes out.
     *
     * Null when the endpoint did not say.
     */
    val intendedEntryType: EntryType? = null,
    /** The model's one line on why the entry sits where it does. */
    val entryNote: String = "",
    /** The server's staleness verdict. See [ChartVerdict.stale]. */
    val stale: Boolean = false,
    val staleReason: String? = null,
    val chartAgeMinutes: Int? = null
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
    val preset: AllocationPreset,
    /**
     * What money is shown in, and the rate to get there.
     *
     * Every amount on this plan -- [riskAmount], [TradeLeg.estimatedPl],
     * [actualRisk], [totalReward] -- is computed in USD, because that is what
     * the pip value is denominated in. Rendering is the only place the user's
     * own currency belongs, so the conversion lives on [money] and every call
     * site goes through it.
     *
     * Held here rather than passed to each screen because six separate places
     * were formatting a leading "$" by hand, and the seventh would have too.
     */
    val displayCurrency: String = "USD",
    /** Units of [displayCurrency] per 1 USD. */
    val displayRate: Double = 1.0,
    /** Market, or a pending order waiting at [entry]. See [EntryType]. */
    val entryType: EntryType = EntryType.MARKET,
    /**
     * How long a pending order stays alive, in seconds. Zero for a market order.
     *
     * A setup read off a chart is only valid for about as long as the trade was
     * meant to last. Without this, a limit that never fills sits at the broker
     * indefinitely and can trigger days later into a market that has moved on --
     * a position nobody chose, opened against a plan nobody remembers.
     *
     * Taken from [ScanMode.windowMinutes], which already encodes exactly this
     * horizon for the event check: 45 minutes on a scalp, four hours on a day
     * trade, a day on a swing. MetaCopier's `pendingExpirySeconds` hands it to
     * the broker, which does the cancelling itself -- so it holds even if the
     * phone never comes back.
     */
    val pendingExpirySeconds: Int = 0,
    /**
     * True when the vision pass expected a different order type than the live
     * price implies -- price has moved since the screenshot.
     */
    val entryDrifted: Boolean = false
) {
    val currencySymbol: String get() = when (displayCurrency) {
        "ZAR" -> "R"
        "EUR" -> "€"
        else -> "$"
    }

    /**
     * A USD amount, rendered in the user's currency.
     *
     * Takes dollars and returns a string, so a caller cannot convert twice or
     * forget to convert at all -- both of which are silent, and both of which
     * produce a number the user will act on.
     */
    fun money(usd: Double, decimals: Int = 0): String =
        currencySymbol + String.format("%,.${decimals}f", usd * displayRate)

    /**
     * MetaCopier's `orderType` for this plan.
     *
     * Their enum is `Buy, Sell, BuyLimit, SellLimit, BuyStop, SellStop`, and a
     * pending type is only honoured when `openPrice` is also sent -- see
     * [openPriceForBroker].
     */
    val brokerOrderType: String get() {
        val side = if (direction == Direction.BUY) "Buy" else "Sell"
        return when (entryType) {
            EntryType.MARKET -> side
            EntryType.LIMIT -> side + "Limit"
            EntryType.STOP -> side + "Stop"
        }
    }

    /**
     * What to send as `openPrice`.
     *
     * Zero is MetaCopier's "fill at market" flag, so a market order must send
     * exactly that rather than the entry it was planned around -- sending a
     * price with a market type is how an order silently becomes a pending one.
     */
    val openPriceForBroker: Double get() =
        if (entryType == EntryType.MARKET) 0.0 else entry

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
        preset: AllocationPreset,
        /** Sets how long a pending entry stays alive. See [TradePlan.pendingExpirySeconds]. */
        mode: ScanMode,
        /** What to render money in. Sizing stays in USD regardless. */
        displayCurrency: String = "USD",
        displayRate: Double = 1.0
    ): TradePlan? {
        val stopPips = abs(instrument.pipsBetween(reading.entry, reading.stop))
        if (stopPips < 0.1) return null

        // Decided from the live price, not from what the vision pass expected.
        // The screenshot is a moment ago; the order is now.
        val entryType = EntryType.resolve(
            direction = reading.direction,
            entry = reading.entry,
            livePrice = instrument.price.takeIf { it > 0.0 },
            pipSize = instrument.pipSize
        )
        val drifted = reading.intendedEntryType != null && reading.intendedEntryType != entryType

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
            preset = preset,
            displayCurrency = displayCurrency,
            displayRate = displayRate,
            entryType = entryType,
            // Only pending orders carry an expiry; MetaCopier ignores it on a
            // market order and sending one anyway invites confusion later.
            pendingExpirySeconds = if (entryType == EntryType.MARKET) 0 else mode.windowMinutes * 60,
            entryDrifted = drifted
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

    /** Stop-to-ATR bounds. See [volatilityCheck] for why these are a ratio.
     *  Shared with the iOS engine's `STOP_ATR_BAND`, so one chart grades the
     *  same on both handsets. */
    internal const val STOP_ATR_FLOOR = 1.0
    internal const val STOP_ATR_CEILING = 8.0

    /** How close to a level still counts as "on" it, as a fraction of the stop distance. */
    private const val KEY_LEVEL_TOLERANCE = 0.85

    /** Below this, a level has not been tested enough to trade off. */
    private const val MIN_LEVEL_TOUCHES = 3

    /**
     * @param calendarAvailable whether the economic calendar was actually read.
     *        False means the feed failed, which is an unknown rather than an
     *        all-clear -- see [eventCheck].
     */
    fun score(
        instrument: Instrument,
        reading: ScanReading,
        plan: TradePlan,
        mode: ScanMode,
        calendarAvailable: Boolean
    ): ConfluenceResult {
        val checks = listOf(
            rewardCheck(plan),
            trendCheck(reading),
            volatilityCheck(instrument, plan),
            keyLevelCheck(instrument, reading, plan),
            eventCheck(reading, mode, calendarAvailable)
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

    /**
     * Does something the setup cannot see agree with it?
     *
     * The qualifier is the whole check, and it used to be missing. `analyze-chart`
     * defines `trend_bias` as the bias of "the highest timeframe legible on this
     * chart" -- and on a single-timeframe screenshot, which is what people
     * actually send, the highest legible timeframe IS the chart's own. So a
     * bearish Change of Character on M15 was scored against an M15 bearish bias
     * and awarded a full twenty points for agreeing with itself. A live scan
     * printed it plainly: `Change of Character - M15 bearish`, ticked, +20.
     * There is no confirmation in that row. One reading of one chart, counted
     * twice.
     *
     * Confirmation has to come from a wider timeframe than the entry's. Where it
     * does not, this fails and says why -- rather than the alternative, which is
     * awarding the points and letting the user believe two things agreed.
     */
    private fun trendCheck(reading: ScanReading): ConfluenceCheck {
        val chartMinutes = ScanSource.timeframeMinutes(reading.chartTimeframe)
        val biasMinutes = ScanSource.timeframeMinutes(reading.trendTimeframe)

        if (chartMinutes != null && biasMinutes != null && biasMinutes <= chartMinutes) {
            return ConfluenceCheck(
                title = "Pattern aligns with a higher timeframe",
                detail = reading.trendTimeframe + " is the chart's own timeframe - " +
                    "nothing wider confirms this setup",
                passed = false,
                points = 0
            )
        }

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

    /**
     * A negative ATR means nobody measured one.
     *
     * [ScanSource.buildInstrument] uses -1.0 as its "unavailable" marker, and an
     * unmeasured ATR must read as unverified rather than as a quiet market --
     * "too quiet for the band" is a statement about volatility, and this check
     * would be making it without having seen any.
     */
    /**
     * Does the stop have room to breathe, without being so wide the target is
     * unreachable?
     *
     * ## Why this is a ratio and not a pip band
     *
     * This check used to compare the chart's ATR against [ScanMode]'s pip band --
     * 4-12 on a scalp, 10-22 on a day trade, 18-60 on a swing. Every one of those
     * numbers describes an FX major and nothing else. Gold's pip size here is
     * 0.1, so an ordinary $3.50 candle measures 35 pips and fails the Day band;
     * on the iOS engine, where gold's pip is 0.01, the same candle measured 350
     * against the same 10-22 and the check printed `ATR ~350.0p vs the 10-22p
     * band` with a red cross. It could not have passed. Neither could an index
     * or a crypto pair.
     *
     * The stop-to-ATR ratio asks what the band was reaching for, in units that
     * cancel. Both figures are pips of the same instrument, so the ratio is the
     * same number whatever the pip size is -- which removes the whole class of
     * bug rather than retuning a constant per instrument:
     *
     *  - Under [STOP_ATR_FLOOR], the stop sits inside a single candle's ordinary
     *    range. Normal two-way movement takes it out before the setup has been
     *    wrong about anything.
     *  - Over [STOP_ATR_CEILING], a 1R target needs that many candles of one-way
     *    movement. The R:R on paper is real; reaching it inside the trade's
     *    horizon is not.
     */
    private fun volatilityCheck(instrument: Instrument, plan: TradePlan): ConfluenceCheck {
        val atr = instrument.atrPips
        val title = "Stop clears the chart's noise"

        // A negative ATR is ScanSource.buildInstrument's "nobody measured one"
        // marker. An unmeasured ATR must read as unverified rather than as a
        // verdict -- this check would be making a statement about volatility
        // without having seen any.
        if (atr <= 0.0 || plan.stopPips <= 0.0) {
            return ConfluenceCheck(
                title = title,
                detail = "ATR not measured - the chart's candles were not legible enough",
                passed = false,
                points = 0
            )
        }

        val ratio = plan.stopPips / atr
        val passed = ratio >= STOP_ATR_FLOOR && ratio <= STOP_ATR_CEILING
        return ConfluenceCheck(
            title = title,
            detail = "Stop is " + trimmed(ratio) + "x the average candle" + when {
                passed -> ""
                ratio < STOP_ATR_FLOOR -> " - inside the noise"
                else -> " - too wide for the target to be reached"
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

    /**
     * An unread calendar is not a clear calendar.
     *
     * When [calendarAvailable] is false this fails, and says why. The
     * alternative -- passing, because `events` happens to be empty -- is the
     * worst behaviour available here: it awards the full twenty points and
     * prints "nothing high-impact inside the window" at the exact moment the app
     * has no idea what is on the calendar, on the screen a user reads before
     * committing a position.
     *
     * That was the live behaviour for as long as the feed has been down, which
     * is why the distinction is a parameter now rather than an inference from an
     * empty list.
     */
    private fun eventCheck(
        reading: ScanReading,
        mode: ScanMode,
        calendarAvailable: Boolean
    ): ConfluenceCheck {
        if (!calendarAvailable) {
            return ConfluenceCheck(
                title = "Event window clear",
                detail = "Economic calendar unavailable - event risk not checked",
                passed = false,
                points = 0
            )
        }

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
    /**
     * Ceiling on the stop, as a multiple of the chart's own average candle.
     *
     * Replaces a `maxStopPips = 50.0` that only ever described an FX major.
     * Gold's ordinary ten-dollar stop is 100 pips at this app's pip size and
     * every metal, index and crypto plan the scanner ever produced tripped the
     * rule -- a warning that fires on everything is a warning users learn to
     * scroll past, and then it is not there for the real one.
     *
     * Shares [ConfluenceEngine.STOP_ATR_CEILING], so the guardrail and the
     * confluence check cannot drift apart and disagree about the same stop.
     */
    val maxStopAtr: Double = ConfluenceEngine.STOP_ATR_CEILING,
    val maxConsecutiveLosses: Int = 3,
    val minEventClearanceMinutes: Int = 30
) {
    /**
     * The same config with every value forced back into a sane range.
     *
     * Applied on the way out of the editor rather than field by field in the UI,
     * so a half-typed number does not fight the user's keyboard while they are
     * still typing it. The bounds are what the arithmetic downstream can survive,
     * not opinions about good trading:
     *
     *  - A minimum R:R below 1 makes the reward check pass on a plan that risks
     *    more than it targets, which is not a guardrail.
     *  - A stop limit below one candle-range blocks every trade ever; past
     *    thirty it is no longer expressing anything.
     *  - A loss streak of zero locks the engine before the first trade.
     */
    fun sanitised(): GuardrailConfig = copy(
        minBlendedRR = minBlendedRR.coerceIn(1.0, 10.0),
        maxStopAtr = maxStopAtr.coerceIn(1.0, 30.0),
        maxConsecutiveLosses = maxConsecutiveLosses.coerceIn(1, 20),
        minEventClearanceMinutes = minEventClearanceMinutes.coerceIn(0, 240)
    )

    companion object {
        /** What the sheet resets to, and what a fresh install starts on. */
        val Defaults = GuardrailConfig()
    }
}

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

    /**
     * @param calendarAvailable false when the economic feed could not be read.
     *        The event rule warns rather than passing in that case.
     */
    fun evaluate(
        plan: TradePlan,
        reading: ScanReading,
        config: GuardrailConfig,
        /** Null when the broker's trade history could not be read. Not zero. */
        consecutiveLosses: Int?,
        calendarAvailable: Boolean
    ): GuardrailReport {
        val minRrLabel = "1:" + trimmed(config.minBlendedRR)

        /**
         * Is the chart still the market?
         *
         * First in the list because it invalidates every rule under it. A stop
         * distance, an R:R and a key level are all statements about a market
         * that existed when the screenshot was taken, and none of them survives
         * the screenshot being old.
         *
         * A BLOCK, and the only new one here. It has to be: the scan that
         * prompted it read a gold chart whose last candle was three days back,
         * whose terminal had "Market closed" printed on it, and returned a
         * confident SELL that scored MODERATE and armed execute. Price had
         * already rallied ninety dollars, within an hour of that final candle.
         * The reading was a fair account of the chart. The chart was not a fair
         * account of the market, and nothing in the app was looking.
         */
        val freshness = when {
            reading.stale -> GuardrailOutcome(
                title = "Chart is current",
                detail = (reading.staleReason ?: "This chart is not current.") +
                    " Take a fresh screenshot and scan again.",
                severity = RuleSeverity.BLOCK,
                reading = "stale"
            )
            // An unchecked clock is not a clock that said "now". Warns rather
            // than passing, the same rule the calendar and the loss streak
            // follow above.
            reading.chartAgeMinutes == null -> GuardrailOutcome(
                title = "Chart is current",
                detail = "No readable time axis on this chart - its age could not be checked",
                severity = RuleSeverity.WARN,
                reading = "unknown"
            )
            else -> GuardrailOutcome(
                title = "Chart is current",
                detail = "Last candle " + humanDuration(reading.chartAgeMinutes) + " old",
                severity = RuleSeverity.PASS,
                reading = humanDuration(reading.chartAgeMinutes)
            )
        }

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

        // Measured against the chart's own candles rather than a pip count, for
        // the reason GuardrailConfig.maxStopAtr sets out. Unknown when the model
        // could not measure them: there is then no yardstick, and inventing one
        // would warn or clear at random.
        val stopAtr = plan.instrument.atrPips.takeIf { it > 0.0 }?.let { plan.stopPips / it }
        val stop = when {
            stopAtr == null -> GuardrailOutcome(
                title = "Stop <= " + trimmed(config.maxStopAtr) + "x the average candle",
                detail = "The chart's candles could not be measured",
                severity = RuleSeverity.WARN,
                reading = "unknown"
            )
            else -> GuardrailOutcome(
                title = "Stop <= " + trimmed(config.maxStopAtr) + "x the average candle",
                detail = if (stopAtr <= config.maxStopAtr) {
                    trimmed(stopAtr) + "x - inside your limit"
                } else {
                    trimmed(stopAtr) + "x - the target needs more candles than the trade has"
                },
                // Warn, not block: a wide stop is already paid for by the smaller
                // position the risk sizing hands back, so this is information rather
                // than a veto.
                severity = if (stopAtr <= config.maxStopAtr) RuleSeverity.PASS else RuleSeverity.WARN,
                reading = trimmed(stopAtr) + "x"
            )
        }

        // Null is not zero. An unread streak warns rather than passing: this rule
        // spent its whole life being handed a literal 0, so it rendered green,
        // counted toward "rules checked", and could never fire however many
        // trades went against the user.
        val streak = if (consecutiveLosses == null) {
            GuardrailOutcome(
                title = "Consecutive losses < " + config.maxConsecutiveLosses,
                detail = "Trade history unavailable - link a broker account to enforce this",
                severity = RuleSeverity.WARN,
                reading = "unknown"
            )
        } else {
            GuardrailOutcome(
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
        }

        val nextHighImpact = reading.events.filter { it.highImpact }.minByOrNull { it.minutesAway }
        val clearance = nextHighImpact?.minutesAway ?: Int.MAX_VALUE
        val event = if (!calendarAvailable) {
            // Warns rather than passes. The rule's whole promise is that the
            // calendar was looked at, and reporting "clear" off a feed that
            // never answered is the one outcome a risk rule must not produce.
            GuardrailOutcome(
                title = "No high-impact event <= " + config.minEventClearanceMinutes + "m",
                detail = "Economic calendar unavailable - check it yourself before sending",
                severity = RuleSeverity.WARN,
                reading = "unknown"
            )
        } else {
            GuardrailOutcome(
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
        }

        return GuardrailReport(listOf(freshness, rr, stop, streak, event))
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
