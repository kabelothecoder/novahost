package com.novahost.app.ui.scanner

import com.novahost.app.BuildConfig
import com.novahost.app.sdk.EconomicEvent
import com.novahost.app.sdk.SupabaseSetup
import io.ktor.client.call.body
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
 * The vision pass (`/functions/v1/analyze-chart`) returns direction, entry, tp,
 * sl, confidence and patterns -- the contract the iOS client already calls. That
 * covers three of the five confluence checks outright:
 *
 * | Check                        | Source                          | Live |
 * |------------------------------|---------------------------------|------|
 * | Blended R:R above 1:1.5      | entry + sl, through the ladder  | yes  |
 * | Volatility moderate          | ATR, from the price feed        | yes  |
 * | Event window clear           | FMP economic calendar           | yes  |
 * | Pattern aligns with trend    | needs a higher-timeframe bias   | NO   |
 * | Entry sits on a key level    | needs level + touch count       | NO   |
 *
 * The two unavailable checks are reported as failures with a detail line that
 * says why, rather than being quietly passed or dropped from the total. That is
 * a deliberate call: a confluence factor nobody verified has not been confirmed,
 * and a scoring engine that awards points for data it never received is exactly
 * the thing the "auditable" claim on the score screen is promising it is not.
 *
 * The practical consequence is that a live scan today tops out at 60/100 rather
 * than the design's 80. Closing that gap is a change to the edge function -- it
 * needs to return a trend bias and the nearest level with its touch count -- and
 * then to [toReading] here. Nothing in the UI or the engine has to move.
 */

// -- Vision pass ------------------------------------------------------------

/**
 * The analyze-chart response.
 *
 * Field names match what the iOS client reads off the same endpoint. Every
 * field is nullable with a default because a vision model returning partial
 * JSON is a normal Tuesday, and a parse failure here would take out the whole
 * scan rather than the one field that went missing.
 */
@Serializable
data class ChartVerdict(
    val direction: String? = null,
    val confidence: Double? = null,
    val entry: Double? = null,
    val tp: Double? = null,
    val sl: Double? = null,
    val patterns: List<String> = emptyList(),
    /** Present only once the function is extended; see the table above. */
    @SerialName("trend_bias") val trendBias: String? = null,
    @SerialName("trend_timeframe") val trendTimeframe: String? = null,
    @SerialName("key_level") val keyLevel: Double? = null,
    @SerialName("key_level_touches") val keyLevelTouches: Int? = null
)

@Serializable
private data class AnalyzeChartRequest(val imageBase64: String)

object ScanSource {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sends a chart to the vision pass.
     *
     * [imageBase64] is the full data URL, matching what the endpoint already
     * receives from iOS -- re-encoding it to a bare payload here would make the
     * two clients disagree about the contract.
     */
    suspend fun analyzeChart(imageBase64: String): Result<ChartVerdict> = try {
        val response: HttpResponse = SupabaseSetup.client.httpClient.post(
            BuildConfig.SUPABASE_URL + "/functions/v1/analyze-chart"
        ) {
            header(HttpHeaders.Authorization, "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(AnalyzeChartRequest(imageBase64))
        }

        if (response.status.value !in 200..299) {
            Result.failure(Exception("Analysis node returned " + response.status.value))
        } else {
            val verdict = json.decodeFromString<ChartVerdict>(response.body<String>())
            if (verdict.entry == null || verdict.sl == null || verdict.direction == null) {
                // A verdict without an entry and a stop cannot be sized, scored
                // or executed. Failing here beats rendering a plan around nulls.
                Result.failure(Exception("The scan came back without an entry and stop."))
            } else {
                Result.success(verdict)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("NovaHost", "[Scanner] analyze-chart failed", e)
        Result.failure(e)
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
        atrPips: Double?
    ): Instrument = Instrument(
        symbol = symbol,
        displayName = displayNameFor(symbol),
        broker = broker,
        price = livePrice ?: 0.0,
        changePercent = 0.0,
        changePips = 0.0,
        spreadPips = spreadPips ?: -1.0,
        atrPips = atrPips ?: -1.0,
        session = session,
        sessionOpen = sessionOpen
    )

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

    /**
     * Turns a vision verdict into the shape the engine scores.
     *
     * The two fields the endpoint does not yet return become [Bias.NEUTRAL] and
     * a zero touch count. Both are honest "unknown" values that the engine will
     * score as failures -- see the table at the top of this file for why that is
     * the intended behaviour rather than a gap to paper over.
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

        val trendBias = when (verdict.trendBias?.uppercase()) {
            "BULLISH", "BUY", "UP" -> Bias.BULLISH
            "BEARISH", "SELL", "DOWN" -> Bias.BEARISH
            else -> Bias.NEUTRAL
        }

        val confidence = verdict.confidence
        val confidenceNote = if (confidence != null) {
            " The model put " + confidence.toInt() + "% confidence on the read."
        } else ""

        return ScanReading(
            direction = direction,
            entry = entry,
            stop = stop,
            pattern = pattern,
            trendTimeframe = verdict.trendTimeframe ?: "higher-timeframe",
            trendBias = trendBias,
            keyLevel = verdict.keyLevel ?: entry,
            keyLevelTouches = verdict.keyLevelTouches ?: 0,
            // Timeframe alignment is one row, not four, until the endpoint
            // returns a per-timeframe read. Four rows of the same unknown would
            // look like analysis while carrying none.
            timeframes = listOf(
                TimeframeRead(
                    timeframe = verdict.trendTimeframe ?: "Trend",
                    bias = trendBias,
                    note = if (trendBias == Bias.NEUTRAL) {
                        "No higher-timeframe read from this scan"
                    } else {
                        pattern
                    }
                )
            ),
            events = events,
            narrative = buildNarrative(direction, instrument, entry, stop, pattern) + confidenceNote
        )
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
