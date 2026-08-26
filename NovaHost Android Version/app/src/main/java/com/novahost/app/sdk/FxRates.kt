package com.novahost.app.sdk

import android.content.Context
import com.novahost.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * How many units of an account's currency one US dollar buys.
 *
 * The lot calculator works in dollars whether or not the user does: a standard
 * lot on a USD-quoted pair moves roughly $10 per pip, and that is the only
 * figure the sizing maths has. An account funded in rand has a rand balance and
 * a rand risk budget, so sizing it against a dollar pip value without
 * converting overstates every position by about eighteen times. That is not a
 * display bug; it is a position-sizing bug that spends real money.
 *
 * Rates come from FinancialModelingPrep, which the app already calls for the
 * economic calendar, so this adds no new credential and no new vendor.
 *
 * ## On staleness
 *
 * A cached rate is used when the network is unavailable, and its age is exposed
 * so the screen can say what it sized against. A rate that is hours old is
 * fine for position sizing -- currencies do not move far enough in a day to
 * change a lot size meaningfully -- but a rate of unknown provenance is not, so
 * [Quote.fetchedAt] is never faked.
 */
object FxRates {

    private const val PREFS = "nova_fx"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Currencies the account-currency picker offers. */
    val SUPPORTED = listOf("USD", "ZAR", "EUR")

    /**
     * Last-resort rates, used only when nothing has ever been fetched and the
     * network is down. Deliberately round numbers: they exist so the calculator
     * produces a sane figure rather than a zero, and the UI marks any sizing
     * done on them as an estimate.
     */
    private val FALLBACK = mapOf("USD" to 1.0, "ZAR" to 18.0, "EUR" to 0.92)

    /**
     * @param rate      units of [currency] per 1 USD.
     * @param fetchedAt epoch millis the rate was retrieved, 0 when it is a fallback.
     */
    data class Quote(val currency: String, val rate: Double, val fetchedAt: Long) {
        val isEstimate: Boolean get() = fetchedAt == 0L
        val ageMillis: Long get() = if (fetchedAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - fetchedAt
    }

    /** Six hours. FX does not move fast enough to justify asking more often. */
    private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    /**
     * The USD -> [currency] rate, from cache when it is fresh enough.
     *
     * Never throws: a failed fetch falls back to the cached value, then to
     * [FALLBACK], because a calculator that shows nothing is worse than one that
     * shows a figure it has labelled as an estimate.
     */
    suspend fun usdTo(context: Context, currency: String): Quote {
        val code = currency.uppercase()
        if (code == "USD") return Quote("USD", 1.0, System.currentTimeMillis())

        cached(context, code)?.let { if (it.ageMillis < MAX_AGE_MS) return it }

        val fetched = fetch(code)
        if (fetched != null) {
            store(context, code, fetched)
            return Quote(code, fetched, System.currentTimeMillis())
        }

        // Stale beats absent.
        cached(context, code)?.let { return it }

        return Quote(code, FALLBACK[code] ?: 1.0, 0L)
    }

    private suspend fun fetch(code: String): Double? = withContext(Dispatchers.IO) {
        val key = BuildConfig.FMP_API_KEY
        if (key.isBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url("https://financialmodelingprep.com/api/v3/quote/USD$code?apikey=$key")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null

                val element = json.parseToJsonElement(body)
                val first = element.jsonArray.firstOrNull()?.jsonObject ?: return@withContext null
                val price = first["price"]?.jsonPrimitive?.content?.toDoubleOrNull()

                price?.takeIf { it > 0.0 }
            }
        } catch (e: Exception) {
            android.util.Log.w("NovaHost", "[FX] USD$code fetch failed: ${e.message}")
            null
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cached(context: Context, code: String): Quote? {
        val p = prefs(context)
        val rate = p.getFloat("rate_$code", 0f).toDouble()
        val at = p.getLong("at_$code", 0L)
        return if (rate > 0.0 && at > 0L) Quote(code, rate, at) else null
    }

    private fun store(context: Context, code: String, rate: Double) {
        prefs(context).edit()
            .putFloat("rate_$code", rate.toFloat())
            .putLong("at_$code", System.currentTimeMillis())
            .apply()
    }

    /** The symbol to put in front of an amount. */
    fun symbolFor(currency: String): String = when (currency.uppercase()) {
        "ZAR" -> "R"
        "EUR" -> "€"
        else -> "$"
    }
}
