package com.novahost.app.ui.scanner

import com.novahost.app.BuildConfig
import com.novahost.app.sdk.EconomicEvent
import com.novahost.app.sdk.NovaHostBackend
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Where the scanner's inputs come from.
 *
 * Kept apart from [ConfluenceEngine] on purpose: the engine is fixed rules over
 * a fixed shape, and this file is the shifting part -- the edge function's
 * contract, the price feed's symbol spelling, the calendar's date format. When
 * a real technical-analysis feed lands, this is the only file that changes.
 *
 * ## What is live today, and what is not
 *
 * The vision pass (`/functions/v1/analyze-chart`, Claude with a JSON schema on
 * the response) returns direction, entry, sl, tp, confidence, patterns, the
 * higher-timeframe bias, the key level with its touch count, and a per-timeframe
 * read. That is four of the five confluence checks:
 *
 * | Check                        | Source                          | Live |
 * |------------------------------|---------------------------------|------|
 * | Blended R:R above 1:1.5      | entry + sl, through the ladder  | yes  |
 * | Pattern aligns with trend    | trend_bias from the vision pass | yes  |
 * | Entry sits on a key level    | key_level + touches             | yes  |
 * | Event window clear           | economic calendar               | NO   |
 * | Volatility moderate          | ATR                             | NO   |
 *
 * The two unavailable checks are reported as failures with a detail line that
 * says why, rather than being quietly passed or dropped from the total. That is
 * a deliberate call: a confluence factor nobody verified has not been confirmed,
 * and a scoring engine that awards points for data it never received is exactly
 * the thing the "auditable" claim on the score screen is promising it is not.
 *
 * Both gaps are the same gap -- a market data feed. Finnhub's plan here excludes
 * forex, so no price and no ATR; FMP's `economic_calendar` endpoint was retired
 * and answers with a legacy notice, so the radar is permanently empty. The fix
 * for both is to read quotes and specs from MetaCopier, which this app already
 * pays for and which prices the user's own broker rather than a proxy.
 *
 * Until then a live scan tops out at 60/100. That ceiling is real and should
 * stay visible: it is the difference between a score that is low and a score
 * that is uninformed.
 */

// -- Vision pass ------------------------------------------------------------

/** One row of the model's own timeframe read. */
@Serializable
data class VerdictTimeframe(
    val timeframe: String = "",
    val bias: String = "",
    val note: String = ""
)

/**
 * The analyze-chart response.
 *
 * Every field is nullable with a default because a vision model returning
 * partial JSON is a normal Tuesday, and a parse failure here would take out the
 * whole scan rather than the one field that went missing. The endpoint now
 * enforces the shape server-side with a JSON schema, so this is belt and braces
 * rather than the only line of defence it used to be.
 */
@Serializable
data class ChartVerdict(
    val direction: String? = null,
    val confidence: Double? = null,
    val entry: Double? = null,
    val tp: Double? = null,
    val sl: Double? = null,
    val patterns: List<String> = emptyList(),
    @SerialName("trend_bias") val trendBias: String? = null,
    @SerialName("trend_timeframe") val trendTimeframe: String? = null,
    @SerialName("key_level") val keyLevel: Double? = null,
    @SerialName("key_level_touches") val keyLevelTouches: Int? = null,
    /**
     * Typical candle range over ~14 candles, in the chart's price scale.
     *
     * The volatility input, read off the chart because there is nowhere else to
     * get it: MetaCopier is the only broker connection this app has and its API
     * carries no candle, bar or OHLC endpoint -- accounts, positions, symbols
     * and one live quote is the whole surface. Finnhub, which would have had
     * them, does not serve forex on this plan.
     *
     * Null when the candles were too compressed to measure. That stays null all
     * the way to the volatility check, which scores it as unverified rather than
     * substituting a plausible figure -- twenty points for an invented ATR is
     * exactly what the score screen's "auditable" claim is promising it is not.
     */
    @SerialName("average_candle_range") val averageCandleRange: Double? = null,
    /**
     * MARKET / LIMIT / STOP -- how the model expects the entry to be reached.
     *
     * Advisory. The order type actually sent is derived from the live price by
     * [EntryType.resolve]; this is compared against it so a setup whose price
     * has moved since the screenshot can be flagged rather than silently
     * becoming the opposite kind of order.
     */
    @SerialName("entry_type") val entryType: String? = null,
    @SerialName("entry_note") val entryNote: String? = null,
    /** The model's per-timeframe read. Empty when it could only see one. */
    val timeframes: List<VerdictTimeframe> = emptyList(),
    val narrative: String? = null,
    /**
     * The instrument printed on the chart, verbatim.
     *
     * Carried so the screen can catch the mistake that costs the most: scanning
     * a gold chart with EURUSD selected sizes and executes the euro, because
     * every downstream step uses the *selected* symbol and not this one.
     */
    @SerialName("symbol_on_chart") val symbolOnChart: String? = null,
    @SerialName("timeframe_on_chart") val timeframeOnChart: String? = null,
    /** Server-side error envelope. Present instead of a reading, never alongside one. */
    val error: String? = null,
    val code: String? = null
)

@Serializable
private data class AnalyzeChartRequest(
    val imageBase64: String,
    val pair: String,
    val mode: String,
    /** [ScanStrategy] name -- SMC / PRICE_ACTION / TREND / BREAKOUT. */
    val strategy: String,
    val email: String,
    @SerialName("android_id") val android_id: String
)

/**
 * A scan the server declined, with the reason it gave.
 *
 * Separate from a transport failure so the screen can show the server's own
 * sentence -- "The AI chart scanner is not unlocked on this email", "That image
 * is not a readable price chart" -- instead of a status code. [code] is the
 * machine-readable half: SCANNER_LOCKED, DEVICE_MISMATCH, NOT_A_CHART,
 * BAD_BRACKET.
 */
class ScanRefused(val code: String?, message: String) : Exception(message)

object ScanSource {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sends a chart to the vision pass.
     *
     * [imageBase64] is the full data URL. The endpoint accepts a bare payload
     * too, but sending the URL keeps the media type with the image -- an Android
     * screenshot is usually PNG, and the previous endpoint hardcoded JPEG.
     *
     * The body is hand-serialized to a String rather than handed to `setBody`
     * as an object. The NovaHost backend client this app builds installs no
     * ContentNegotiation, so passing a `@Serializable` object throws *before a
     * request is ever sent* -- which the user sees as a failed scan on a working
     * network. `Entitlements.kt` documents the same trap; this function was
     * still walking into it, which meant the scan never reached the server at
     * all, on top of the three contract faults waiting for it when it did.
     */
    suspend fun analyzeChart(
        imageBase64: String,
        pair: String,
        mode: ScanMode,
        strategy: ScanStrategy,
        email: String,
        deviceId: String
    ): Result<ChartVerdict> = try {
        val payload = json.encodeToString(
            AnalyzeChartRequest.serializer(),
            AnalyzeChartRequest(
                imageBase64 = imageBase64,
                pair = pair,
                mode = mode.label,
                // The name, not the brief. The reading instruction lives
                // server-side so it can be tuned without shipping an APK, and so
                // a tampered client cannot rewrite the prompt it is billed for.
                strategy = strategy.name,
                email = email,
                android_id = deviceId
            )
        )

        val response: HttpResponse = NovaHostBackend.client.httpClient.post(
            BuildConfig.NOVAHOST_API_URL.removeSuffix("/") + "/functions/v1/analyze-chart"
        ) {
            header(HttpHeaders.Authorization, "Bearer " + BuildConfig.NOVAHOST_API_KEY)
            header("apikey", BuildConfig.NOVAHOST_API_KEY)
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            // A vision read on a full-size screenshot runs well past the
            // client's 60s default when the model is thinking.
            timeout { requestTimeoutMillis = 120_000 }
            setBody(payload)
        }

        // Read the body whatever the status: the function returns its reason in
        // the same envelope, and that sentence is what the user needs to see.
        // "Analysis node returned 403" tells them nothing they can act on.
        val verdict = json.decodeFromString<ChartVerdict>(response.body<String>())

        when {
            verdict.error != null -> Result.failure(ScanRefused(verdict.code, verdict.error))

            response.status.value !in 200..299 ->
                Result.failure(Exception("The scan did not complete (" + response.status.value + ")."))

            // A verdict without an entry and a stop cannot be sized, scored or
            // executed. Failing here beats rendering a plan around nulls.
            verdict.entry == null || verdict.sl == null || verdict.direction == null ->
                Result.failure(Exception("The scan came back without an entry and stop."))

            else -> Result.success(verdict)
        }
    } catch (e: Exception) {
        android.util.Log.e("NovaHost", "[Scanner] analyze-chart failed", e)
        Result.failure(e)
    }

    // -- Broker quote -------------------------------------------------------

    /**
     * What the user's own broker says about an instrument, right now.
     *
     * Replaces the Finnhub path for the scanner. Two reasons, and the second is
     * the one that matters: Finnhub's plan here does not serve OANDA forex at
     * all, and even if it did, a proxy feed is not what the order fills against.
     * The spread the user pays, the digits their broker quotes and the minimum
     * lot it accepts are all properties of their account.
     */
    @Serializable
    data class BrokerQuote(
        val success: Boolean = false,
        val symbol: String? = null,
        @SerialName("broker_symbol") val brokerSymbol: String? = null,
        val bid: Double? = null,
        val ask: Double? = null,
        val price: Double? = null,
        @SerialName("spread_pips") val spreadPips: Double? = null,
        val digits: Int? = null,
        val point: Double? = null,
        /** The broker's own pip size. Authoritative over any guess from the spelling. */
        @SerialName("pip_size") val pipSize: Double? = null,
        @SerialName("contract_size") val contractSize: Double? = null,
        @SerialName("min_volume") val minVolume: Double? = null,
        @SerialName("volume_step") val volumeStep: Double? = null,
        val tradeable: Boolean = true,
        val code: String? = null,
        val error: String? = null
    )

    @Serializable
    private data class BrokerQuoteRequest(
        @SerialName("license_key") val license_key: String,
        val symbol: String
    )

    @Serializable
    private data class BrokerHistoryRequest(
        @SerialName("license_key") val license_key: String
    )

    /**
     * Prices [symbol] against the licence's linked broker account.
     *
     * Returns null rather than failing the scan: a quote is an improvement on
     * the instrument, not a precondition for reading a chart. The screen shows
     * "--" and the scan still runs, which is the behaviour a user on a
     * disconnected broker needs.
     */
    suspend fun brokerQuote(context: android.content.Context, symbol: String): BrokerQuote? = try {
        val licenseKey = context
            .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
            .getString("license_key", null)

        if (licenseKey.isNullOrBlank()) {
            null
        } else {
            val payload = json.encodeToString(
                BrokerQuoteRequest.serializer(),
                BrokerQuoteRequest(license_key = licenseKey, symbol = symbol)
            )

            val response: HttpResponse = NovaHostBackend.client.httpClient.post(
                BuildConfig.NOVAHOST_API_URL.removeSuffix("/") + "/functions/v1/broker-quote"
            ) {
                header(HttpHeaders.Authorization, "Bearer " + BuildConfig.NOVAHOST_API_KEY)
                header("apikey", BuildConfig.NOVAHOST_API_KEY)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(payload)
            }

            val quote = json.decodeFromString<BrokerQuote>(response.body<String>())
            if (quote.success) quote else {
                android.util.Log.w("NovaHost", "[Scanner] no broker quote: " + (quote.code ?: "?"))
                null
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("NovaHost", "[Scanner] broker quote failed", e)
        null
    }

    // -- Trade history ------------------------------------------------------

    @Serializable
    private data class LossStreakResponse(
        val success: Boolean = false,
        @SerialName("consecutive_losses") val consecutiveLosses: Int = 0,
        @SerialName("closed_positions") val closedPositions: Int = 0,
        val code: String? = null
    )

    /**
     * How many losses in a row the linked account is currently on.
     *
     * Returns null when it could not be established -- no licence, no linked
     * broker, or the history could not be read. Null is not zero: the guardrail
     * reports an unmeasured streak as unknown rather than as a clean slate,
     * which is the same distinction the calendar rule now makes.
     *
     * This rule used to be passed a literal `0`, so it rendered as passing, was
     * counted in "3 of 4 rules checked", and could never fire however many
     * trades went against the user.
     */
    suspend fun lossStreak(context: android.content.Context): Int? = try {
        val licenseKey = context
            .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
            .getString("license_key", null)

        if (licenseKey.isNullOrBlank()) {
            null
        } else {
            val payload = json.encodeToString(
                BrokerHistoryRequest.serializer(),
                BrokerHistoryRequest(license_key = licenseKey)
            )

            val response: HttpResponse = NovaHostBackend.client.httpClient.post(
                BuildConfig.NOVAHOST_API_URL.removeSuffix("/") + "/functions/v1/broker-history"
            ) {
                header(HttpHeaders.Authorization, "Bearer " + BuildConfig.NOVAHOST_API_KEY)
                header("apikey", BuildConfig.NOVAHOST_API_KEY)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(payload)
            }

            val decoded = json.decodeFromString<LossStreakResponse>(response.body<String>())
            if (decoded.success) decoded.consecutiveLosses else {
                android.util.Log.w("NovaHost", "[Scanner] loss streak unavailable: " + (decoded.code ?: "?"))
                null
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("NovaHost", "[Scanner] loss streak failed", e)
        null
    }

    // -- Instrument ---------------------------------------------------------

    /** Display symbol to the price feed's spelling, e.g. EUR/USD -> OANDA:EUR_USD. */
    fun feedKey(symbol: String): String {
        val bare = symbol.replace("/", "").uppercase()
        if (bare.length < 6) return "OANDA:" + bare
        return "OANDA:" + bare.substring(0, 3) + "_" + bare.substring(3)
    }

    /**
     * Assembles the instrument from whatever the feed currently holds.
     *
     * Spread and ATR are not on the Finnhub trade stream, so they arrive as
     * nulls and the screen shows them as unavailable rather than inventing a
     * number. An invented ATR is worse than a blank one: the volatility check
     * scores off it, so a made-up figure becomes a made-up twenty points.
     */
    fun buildInstrument(
        symbol: String,
        livePrice: Double?,
        broker: String,
        session: String,
        sessionOpen: Boolean,
        spreadPips: Double?,
        atrPips: Double?,
        /** The broker's answer, when there is a linked account to ask. */
        quote: BrokerQuote? = null
    ): Instrument = Instrument(
        symbol = symbol,
        displayName = displayNameFor(symbol),
        broker = quote?.brokerSymbol?.takeIf { it.isNotBlank() } ?: broker,
        // The broker's mid beats any other price on offer: it is the book the
        // order will actually reach.
        price = quote?.price ?: livePrice ?: 0.0,
        changePercent = 0.0,
        changePips = 0.0,
        spreadPips = quote?.spreadPips ?: spreadPips ?: -1.0,
        atrPips = atrPips ?: -1.0,
        session = session,
        sessionOpen = sessionOpen,
        brokerPipSize = quote?.pipSize,
        brokerDigits = quote?.digits,
        brokerSymbol = quote?.brokerSymbol
    )

    /**
     * Fills in the ATR the vision pass measured, converted to pips.
     *
     * Applied after the scan rather than at [buildInstrument] because the figure
     * comes back with the reading -- the instrument is assembled before the
     * chart has been looked at.
     *
     * The conversion uses the instrument's own pip size, so it inherits whatever
     * accuracy that has. On a broker-sourced pip size it is exact; on the
     * symbol-spelling heuristic it is as good as the guess. Left null when the
     * model could not measure the candles, which the volatility check reports as
     * unverified.
     */
    fun withMeasuredAtr(instrument: Instrument, verdict: ChartVerdict): Instrument {
        val range = verdict.averageCandleRange ?: return instrument
        if (range <= 0.0 || instrument.pipSize <= 0.0) return instrument
        return instrument.copy(atrPips = range / instrument.pipSize)
    }

    private fun displayNameFor(symbol: String): String {
        val bare = symbol.replace("/", "").uppercase()
        val names = mapOf(
            "EUR" to "Euro", "USD" to "US Dollar", "GBP" to "Pound Sterling",
            "JPY" to "Japanese Yen", "AUD" to "Australian Dollar",
            "NZD" to "New Zealand Dollar", "CAD" to "Canadian Dollar",
            "CHF" to "Swiss Franc", "XAU" to "Gold", "XAG" to "Silver"
        )
        if (bare.length < 6) return symbol
        val base = names[bare.substring(0, 3)]
        val quote = names[bare.substring(3, 6)]
        return if (base != null && quote != null) base + " / " + quote else symbol
    }

    // -- Calendar -----------------------------------------------------------

    private val fmpFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        // FMP publishes in UTC. Parsing in device-local time shifts every event
        // by the user's offset, which silently breaks the event-window check for
        // anyone outside UTC -- the exact users a session-aware scanner has.
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * The calendar entries that touch this pair, nearest first.
     *
     * Filtered to the two currencies in the symbol: a scanner that warns about
     * AUD retail sales while you are sizing EUR/USD trains the user to ignore
     * the radar entirely.
     */
    fun buildEvents(symbol: String, calendar: List<EconomicEvent>, now: Long = System.currentTimeMillis()): List<MarketEvent> {
        val bare = symbol.replace("/", "").uppercase()
        val currencies = if (bare.length >= 6) {
            setOf(bare.substring(0, 3), bare.substring(3, 6))
        } else {
            setOf(bare)
        }

        return calendar.asSequence()
            .filter { it.currency.uppercase() in currencies }
            .mapNotNull { event ->
                val at = parseUtc(event.date) ?: return@mapNotNull null
                val minutesAway = ((at - now) / 60_000L).toInt()
                // Past events are dropped rather than shown with a negative
                // countdown; the radar is about what is still coming.
                if (minutesAway < 0) return@mapNotNull null
                MarketEvent(
                    currency = event.currency.uppercase(),
                    title = event.event,
                    minutesAway = minutesAway,
                    highImpact = event.impact.equals("High", ignoreCase = true),
                    forecast = event.estimate?.toString(),
                    prior = event.previous?.toString()
                )
            }
            .sortedBy { it.minutesAway }
            .take(4)
            .toList()
    }

    private fun parseUtc(value: String): Long? = try {
        fmpFormat.parse(value)?.time
    } catch (e: Exception) {
        null
    }

    // -- Reading ------------------------------------------------------------

    private fun biasOf(raw: String?): Bias = when (raw?.uppercase()) {
        "BULLISH", "BUY", "UP" -> Bias.BULLISH
        "BEARISH", "SELL", "DOWN" -> Bias.BEARISH
        else -> Bias.NEUTRAL
    }

    /**
     * Turns a vision verdict into the shape the engine scores.
     *
     * Every field the engine reads now comes from the endpoint. The two that
     * used to arrive as permanent unknowns -- the higher-timeframe bias and the
     * key level's touch count -- are part of the response schema, so the trend
     * and key-level checks can pass on their merits instead of failing on a
     * missing input. The remaining structural failure is volatility, which needs
     * an ATR the price feed does not carry.
     *
     * NEUTRAL and a zero touch count are still honest answers rather than
     * absent ones: a range genuinely is neutral, and a level with no visible
     * touches genuinely has not been tested. Those score as failures, correctly.
     */
    fun toReading(
        verdict: ChartVerdict,
        instrument: Instrument,
        events: List<MarketEvent>
    ): ScanReading {
        val direction = if (verdict.direction?.uppercase()?.startsWith("BEAR") == true ||
            verdict.direction?.uppercase() == "SELL"
        ) Direction.SELL else Direction.BUY

        val entry = verdict.entry ?: instrument.price
        val stop = verdict.sl ?: entry
        val pattern = verdict.patterns.firstOrNull() ?: "Pattern not named"
        val trendBias = biasOf(verdict.trendBias)

        // The model's own rows when it could read more than one timeframe;
        // otherwise a single row carrying the trend it did read. Padding one
        // read out into a grid of blanks would look like analysis while carrying
        // none of it.
        val timeframes = verdict.timeframes
            .filter { it.timeframe.isNotBlank() }
            .map { TimeframeRead(it.timeframe, biasOf(it.bias), it.note) }
            .ifEmpty {
                listOf(
                    TimeframeRead(
                        timeframe = verdict.trendTimeframe ?: "Trend",
                        bias = trendBias,
                        note = if (trendBias == Bias.NEUTRAL) {
                            "No higher-timeframe read from this scan"
                        } else {
                            pattern
                        }
                    )
                )
            }

        val confidence = verdict.confidence
        val confidenceNote = if (confidence != null) {
            " The model put " + confidence.toInt() + "% confidence on the read."
        } else ""

        // The model's paragraph when it wrote one, because it saw the chart and
        // this function did not. The assembled sentence is the fallback.
        val narrative = verdict.narrative?.takeIf { it.isNotBlank() }
            ?: (buildNarrative(direction, instrument, entry, stop, pattern) + confidenceNote)

        return ScanReading(
            direction = direction,
            entry = entry,
            stop = stop,
            pattern = pattern,
            trendTimeframe = verdict.trendTimeframe ?: "higher-timeframe",
            trendBias = trendBias,
            keyLevel = verdict.keyLevel ?: entry,
            keyLevelTouches = verdict.keyLevelTouches ?: 0,
            timeframes = timeframes,
            events = events,
            narrative = narrative,
            intendedEntryType = verdict.entryType?.let { EntryType.from(it) },
            entryNote = verdict.entryNote.orEmpty()
        )
    }

    /**
     * Does the chart show the instrument the user selected?
     *
     * Returns the chart's own symbol when it disagrees with [selected], and null
     * when they match or the chart carries no legible label.
     *
     * This is the one mismatch worth interrupting for. Everything downstream --
     * the pip size, the lot maths, the order that reaches the broker -- keys off
     * the *selected* symbol, so scanning a gold screenshot with EURUSD selected
     * produces a euro position sized off gold's price levels. Nothing else in
     * the pipeline can notice.
     */
    fun symbolMismatch(verdict: ChartVerdict, selected: String): String? {
        val onChart = verdict.symbolOnChart?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val bare = { s: String -> s.filter { it.isLetterOrDigit() }.uppercase() }
        val a = bare(onChart)
        val b = bare(selected)
        if (a.isEmpty() || b.isEmpty()) return null
        // Broker decoration -- XAUUSD.m, .US30., GOLDpro -- is a match, not a
        // mismatch. Only a genuinely different instrument is worth a warning.
        return if (a.startsWith(b) || b.startsWith(a)) null else onChart
    }

    /**
     * A chart timeframe label, in minutes.
     *
     * Handles the spellings a charting package actually prints: `M5`, `5M`,
     * `H4`, `4H`, `D1`, `1D`, `W1`, `MN1`, and a bare `15`. Returns null when
     * the label is missing or unrecognised, which callers must treat as "no
     * opinion" -- refusing to score a chart because its timeframe label was
     * cropped out of the screenshot would be worse than not checking.
     *
     * `MN` is checked before `M`. In MetaTrader `M` is minutes and `MN` is
     * months, so reading `MN1` as one minute would turn the longest timeframe
     * there is into the shortest.
     */
    fun timeframeMinutes(raw: String?): Int? {
        val s = raw?.trim()?.uppercase()?.replace(" ", "") ?: return null
        if (s.isEmpty()) return null

        val digits = s.filter { it.isDigit() }.toIntOrNull() ?: return null
        if (digits <= 0) return null

        return when {
            s.contains("MN") -> digits * 43_200      // months
            s.contains("W") -> digits * 10_080
            s.contains("D") -> digits * 1_440
            s.contains("H") -> digits * 60
            s.contains("M") -> digits
            // A bare number is minutes, which is how most platforms label them.
            s.all { it.isDigit() } -> digits
            else -> null
        }
    }

    /**
     * Does the chart's timeframe suit the mode the user picked?
     *
     * Returns the chart's own timeframe label when it does not, and null when it
     * does or when the chart carries none.
     *
     * Worth checking because the failure is quiet and reads as a market
     * condition rather than a mistake. An M5 chart scanned in Day mode produces
     * a three-pip stop measured against Day's 10-22 pip volatility band, so the
     * volatility check fails with "too quiet" -- a true sentence about the wrong
     * thing. The market is not quiet; the mode does not match the chart. Nothing
     * else in the pipeline can tell those apart.
     */
    fun timeframeMismatch(verdict: ChartVerdict, mode: ScanMode): String? {
        val label = verdict.timeframeOnChart?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val minutes = timeframeMinutes(label) ?: return null
        val suits = minutes >= mode.chartFloorMinutes && minutes <= mode.chartCeilingMinutes
        return if (suits) null else label
    }

    private fun buildNarrative(
        direction: Direction,
        instrument: Instrument,
        entry: Double,
        stop: Double,
        pattern: String
    ): String {
        val stopPips = abs(instrument.pipsBetween(entry, stop))
        return "The scan reads " + pattern.lowercase() + " on " + instrument.symbol +
            " and wants a " + direction.label.lowercase() + " from " +
            instrument.formatPrice(entry) + ", risking " + trimmed(stopPips) +
            " pips to " + instrument.formatPrice(stop) + "."
    }
}

/**
 * A stand-in reading for previewing the flow without spending a scan.
 *
 * This exists so the five states can be walked and judged -- by the person
 * building them and by anyone reviewing the redesign -- without a chart, a
 * network round trip or a live account. It is deliberately fenced:
 * [ScannerUiState.isSample] rides along with it and the execute button refuses
 * to arm while it is set, so a preview can never reach a broker.
 *
 * The figures are the design's own, which is what makes it useful for checking
 * the implementation against the artboards.
 */
object SampleScan {

    val instrument = Instrument(
        symbol = "EUR/USD",
        displayName = "Euro / US Dollar",
        broker = "Sample data",
        price = 1.08640,
        changePercent = 0.18,
        changePips = 19.4,
        spreadPips = 0.6,
        atrPips = 14.2,
        session = "London + NY",
        sessionOpen = true
    )

    val reading = ScanReading(
        direction = Direction.BUY,
        entry = 1.08640,
        stop = 1.08480,
        pattern = "H4 bull flag",
        trendTimeframe = "D1",
        trendBias = Bias.BULLISH,
        keyLevel = 1.08510,
        keyLevelTouches = 4,
        timeframes = listOf(
            TimeframeRead("M15", Bias.BULLISH, "Higher lows, momentum up"),
            TimeframeRead("H1", Bias.BULLISH, "1.0858 broke and retested"),
            TimeframeRead("H4", Bias.BULLISH, "Ascending channel intact"),
            TimeframeRead("D1", Bias.NEUTRAL, "Range 1.0790-1.0910")
        ),
        events = listOf(
            MarketEvent(
                currency = "USD",
                title = "CPI m/m",
                minutesAway = 161,
                highImpact = true,
                averageMovePips = 38.0,
                forecast = "0.3%",
                prior = "0.2%",
                advice = "Take TP1 and TP2 off before the print and let TP3 run with the stop at break-even."
            ),
            MarketEvent(
                currency = "EUR",
                title = "ECB Lane speech",
                minutesAway = 251,
                highImpact = false,
                advice = "Low historical impact on EUR/USD. No action needed."
            )
        ),
        narrative = "Price reclaimed 1.0858 and is holding above the H4 demand zone while the daily trend stays up. One check failed: US CPI lands inside the trade window."
    )
}
