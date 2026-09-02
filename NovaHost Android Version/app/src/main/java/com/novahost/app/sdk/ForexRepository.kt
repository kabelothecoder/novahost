package com.novahost.app.sdk

import com.novahost.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.IOException
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * @description Data class representing a live price update from Finnhub.
 */
@Serializable
data class Trade(
    val p: Double = 0.0,
    val s: String = "",
    val t: Long = 0L,
    val v: Double = 0.0
)

/**
 * @description Data class representing the Finnhub WebSocket message.
 */
@Serializable
data class FinnhubMessage(
    val data: List<Trade> = emptyList(),
    val type: String = ""
)

/**
 * @description Data class representing an economic calendar event from FMP.
 */
@Serializable
data class EconomicEvent(
    val date: String = "",
    val country: String = "",
    val event: String = "",
    val currency: String = "",
    val estimate: Double? = null,
    val previous: Double? = null,
    val actual: Double? = null,
    val impact: String = ""
)

/**
 * @description Data class representing the state of a market session.
 */
data class MarketSession(
    val name: String = "",
    val isOpen: Boolean = false,
    val openTime: String = "",
    val closeTime: String = ""
)

/**
 * @description Singleton object managing market data connections and state.
 */
object ForexRepository {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val json = Json { ignoreUnknownKeys = true }

    private val _livePrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    val livePrices: StateFlow<Map<String, Double>> = _livePrices.asStateFlow()

    private val _liveHighs = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _liveLows = MutableStateFlow<Map<String, Double>>(emptyMap())

    private val _bullishMomentum = MutableStateFlow<Map<String, Float>>(emptyMap())
    val bullishMomentum: StateFlow<Map<String, Float>> = _bullishMomentum.asStateFlow()

    private val _economicCalendar = MutableStateFlow<List<EconomicEvent>>(emptyList())
    val economicCalendar: StateFlow<List<EconomicEvent>> = _economicCalendar.asStateFlow()

    /**
     * Whether the calendar was actually read, as opposed to simply being empty.
     *
     * These are different facts and the scanner scores them differently. "No
     * high-impact events today" is a genuine all-clear worth twenty points;
     * "the calendar endpoint answered with an error" is an unknown, and awarding
     * points for it would mean the event check passes hardest exactly when it
     * knows least.
     *
     * It has been the second case for some time without anyone being able to
     * tell: FMP retired `/api/v3/economic_calendar` for non-legacy keys, the
     * response no longer parses as a list of events, and the catch block that
     * swallows the failure is empty. The Event Radar has been showing "no
     * events" and the scan has been collecting the twenty points for it.
     */
    private val _calendarAvailable = MutableStateFlow(false)
    val calendarAvailable: StateFlow<Boolean> = _calendarAvailable.asStateFlow()

    private val _marketSessions = MutableStateFlow<List<MarketSession>>(emptyList())
    val marketSessions: StateFlow<List<MarketSession>> = _marketSessions.asStateFlow()

    /**
     * @description Initializes the repository, connects to WebSocket and fetches initial data.
     */
    fun initialize() {
        connectFinnhubWebSocket()
        fetchEconomicCalendar()
        updateMarketSessions()
    }

    /**
     * @description Connects to the Finnhub WebSocket for real-time prices.
     */
    private fun connectFinnhubWebSocket() {
        val request = Request.Builder()
            .url("wss://ws.finnhub.io?token=${BuildConfig.FINNHUB_API_KEY}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Subscribe to default symbols
                val symbols = listOf("OANDA:XAU_USD", "OANDA:EUR_USD", "OANDA:GBP_USD", "BINANCE:BTCUSDT")
                symbols.forEach { symbol ->
                    webSocket.send("{\"type\":\"subscribe\",\"symbol\":\"$symbol\"}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = json.decodeFromString<FinnhubMessage>(text)
                    if (message.type == "trade") {
                        val newPrices = message.data.associate { it.s to it.p }
                        _livePrices.update { currentPrices ->
                            currentPrices + newPrices
                        }
                        
                        _liveHighs.update { currentHighs ->
                            val map = currentHighs.toMutableMap()
                            newPrices.forEach { (symbol, price) ->
                                val high = map[symbol] ?: price
                                map[symbol] = maxOf(high, price)
                            }
                            map
                        }
                        
                        _liveLows.update { currentLows ->
                            val map = currentLows.toMutableMap()
                            newPrices.forEach { (symbol, price) ->
                                val low = map[symbol] ?: price
                                map[symbol] = minOf(low, price)
                            }
                            map
                        }
                        
                        _bullishMomentum.update { currentMomentum ->
                            val map = currentMomentum.toMutableMap()
                            newPrices.forEach { (symbol, price) ->
                                val high = _liveHighs.value[symbol] ?: price
                                val low = _liveLows.value[symbol] ?: price
                                val diff = high - low
                                val momentum = if (diff > 0.0) {
                                    ((price - low) / diff * 100.0).toFloat()
                                } else {
                                    50f // Default neutral if no range yet
                                }
                                map[symbol] = momentum
                            }
                            map
                        }
                    }
                } catch (e: Exception) {
                    // Handle serialization errors or unmapped types
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Handle WebSocket failure
            }
        })
    }

    /**
     * @description Fetches the economic calendar from Financial Modeling Prep via REST API.
     */
    private fun fetchEconomicCalendar() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val request = Request.Builder()
            .url("https://financialmodelingprep.com/api/v3/economic_calendar?from=$today&to=$today&apikey=${BuildConfig.FMP_API_KEY}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Logged rather than swallowed. An empty catch here is how the
                // calendar came to be permanently unavailable without anything
                // in the app saying so.
                android.util.Log.w("NovaHost", "[Calendar] fetch failed: " + e.message)
                _calendarAvailable.value = false
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                if (bodyString == null) {
                    _calendarAvailable.value = false
                    return
                }
                try {
                    val events = json.decodeFromString<List<EconomicEvent>>(bodyString)
                    _economicCalendar.value = events
                    _calendarAvailable.value = true
                } catch (e: Exception) {
                    // A retired endpoint answers with an object, not a list, so
                    // this is where FMP's "Legacy Endpoint" notice lands. The
                    // first 200 characters go to the log because the message is
                    // the whole diagnosis.
                    android.util.Log.w(
                        "NovaHost",
                        "[Calendar] unreadable response: " + bodyString.take(200)
                    )
                    _economicCalendar.value = emptyList()
                    _calendarAvailable.value = false
                }
            }
        })
    }

    /**
     * @description Calculates and updates the current state of major market sessions based on UTC time.
     */
    fun updateMarketSessions() {
        val nowUtc = ZonedDateTime.now(ZoneId.of("UTC")).toLocalTime()
        val isWeekend = ZonedDateTime.now(ZoneId.of("UTC")).dayOfWeek.value in 6..7

        val sessions = listOf(
            MarketSession("Sydney", !isWeekend && isBetween(nowUtc, LocalTime.of(22, 0), LocalTime.of(7, 0)), "22:00 UTC", "07:00 UTC"),
            MarketSession("Tokyo", !isWeekend && isBetween(nowUtc, LocalTime.of(0, 0), LocalTime.of(9, 0)), "00:00 UTC", "09:00 UTC"),
            MarketSession("London", !isWeekend && isBetween(nowUtc, LocalTime.of(8, 0), LocalTime.of(17, 0)), "08:00 UTC", "17:00 UTC"),
            MarketSession("New York", !isWeekend && isBetween(nowUtc, LocalTime.of(13, 0), LocalTime.of(22, 0)), "13:00 UTC", "22:00 UTC")
        )
        
        _marketSessions.value = sessions
    }

    /**
     * @description Checks if a specific time is between a start and end time, accounting for midnight crossing.
     */
    private fun isBetween(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start.isBefore(end)) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            !time.isBefore(start) || time.isBefore(end)
        }
    }
}
