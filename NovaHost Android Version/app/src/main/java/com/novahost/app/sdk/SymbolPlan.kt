package com.novahost.app.sdk

import android.content.Context
import com.novahost.app.BuildConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What one symbol is allowed to do on this device.
 *
 * [enabled] is the user's tick, not the robot's. The two are different
 * permissions and conflating them is the bug this file exists to end: the
 * robot's allowance arrives with the licence and is the mentor's decision,
 * while this is the subscriber saying "yes, trade that one, this size".
 */
@Serializable
data class SymbolConfig(
    val symbol: String,
    /**
     * Defaults to on, which is also the migration path.
     *
     * Before this file existed, every symbol in the robot's allowance traded.
     * An install upgrading into the new screen has no stored plan, so every
     * allowance symbol is reconciled in fresh -- and defaulting those to *off*
     * would silently stop a working robot until the user found the screen and
     * ticked six boxes. The mentor already permitted them; on is the honest
     * default and the user opts out, not in.
     */
    val enabled: Boolean = true,
    /** Lots per trade when [smartLot] is off. */
    val lot: Double = SymbolPlanStore.DEFAULT_LOT,
    /** How many positions on this symbol may be open at once. */
    val maxTrades: Int = SymbolPlanStore.DEFAULT_TRADES,
    /** True when the size comes from the trade calculator rather than the stepper. */
    val smartLot: Boolean = true
)

/**
 * The whole Trading Symbols screen, in one serialisable value.
 *
 * The calculator inputs live here rather than in loose prefs keys because the
 * lot the executor eventually uses is derived from them, and a number the user
 * cannot trace back to what they typed is a number they will not trust.
 */
@Serializable
data class SymbolPlan(
    val symbols: List<SymbolConfig> = emptyList(),
    val balance: Double = 0.0,
    val riskPercent: Double = 1.0,
    val riskTrades: Int = 1,
    /** The calculator's answer, cached so the executor does not re-derive it. */
    val smartLotSize: Double = 0.0,
    /**
     * What [balance] is denominated in.
     *
     * Sizing is done in dollars because the pip value the maths uses is a dollar
     * figure. An account funded in rand needs its risk budget converted first --
     * without that, a R10,000 balance is treated as $10,000 and every position
     * comes out about eighteen times too big.
     */
    val currency: String = "USD",
    /** USD -> [currency]. Cached with the plan so sizing works offline. */
    val fxRate: Double = 1.0
) {
    fun configFor(symbol: String): SymbolConfig? = symbols.firstOrNull { it.symbol == symbol }

    val selected: List<SymbolConfig> get() = symbols.filter { it.enabled }
}

/**
 * Reads and writes the user's per-symbol trading plan, and pushes it to the
 * server that actually places the trades.
 *
 * ## Why this is not just `allowed_symbols`
 *
 * `allowed_symbols` is the robot's allowance. It is written once, by licence
 * activation, from what the mentor configured, and [metacopier-execute] rejects
 * anything outside it server-side. The Quotes screen used to write the user's
 * ticks back into that same key, which meant unticking a symbol deleted it from
 * the robot's allowance -- the grid is built from that key, so the symbol
 * vanished from the screen on the next visit and could never be turned back on.
 * A user who unticked all six had an app that would never trade again and no way
 * to undo it.
 *
 * So the allowance stays read-only here, and the user's selection lives under
 * its own key.
 */
object SymbolPlanStore {

    private const val PREFS = "metahost_prefs"
    private const val KEY_PLAN = "symbol_plan"

    /** The mentor's allowance. Read-only from this object -- see the class doc. */
    private const val KEY_ROBOT_ALLOWANCE = "allowed_symbols"

    /** Kept in step with [SymbolPlan.smartLotSize] for the scanner, which still reads it. */
    private const val KEY_SMART_LOT = "smart_lot_size"
    private const val KEY_SMART_RISK = "smart_risk_pct"

    const val DEFAULT_LOT = 0.05
    const val DEFAULT_TRADES = 2

    const val MIN_LOT = 0.01
    const val MAX_LOT = 50.0
    const val LOT_STEP = 0.01
    const val MIN_TRADES = 1
    const val MAX_TRADES = 5

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The symbols the licence permits. Empty means the licence carried none. */
    fun robotAllowance(context: Context): List<String> =
        (prefs(context).getString(KEY_ROBOT_ALLOWANCE, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    /**
     * The stored plan, reconciled against the current allowance.
     *
     * Reconciling on read rather than on write is deliberate: the allowance can
     * change under the app when a mentor edits the robot and the licence
     * refreshes, and a plan holding a symbol that is no longer permitted would
     * otherwise keep sizing trades the server will reject.
     */
    fun load(context: Context): SymbolPlan {
        val raw = prefs(context).getString(KEY_PLAN, null)
        val stored = if (raw.isNullOrBlank()) {
            SymbolPlan()
        } else {
            runCatching { json.decodeFromString<SymbolPlan>(raw) }.getOrElse { SymbolPlan() }
        }

        val allowance = robotAllowance(context)
        if (allowance.isEmpty()) return stored

        // Keep one entry per allowed symbol, preserving whatever the user set.
        val reconciled = allowance.map { sym ->
            stored.configFor(sym) ?: SymbolConfig(symbol = sym)
        }
        return stored.copy(symbols = reconciled)
    }

    fun save(context: Context, plan: SymbolPlan) {
        prefs(context).edit()
            .putString(KEY_PLAN, json.encodeToString(SymbolPlan.serializer(), plan))
            // The scanner's TradePlanner still reads these two directly.
            .putFloat(KEY_SMART_LOT, plan.smartLotSize.toFloat())
            .putFloat(KEY_SMART_RISK, perTradeRiskPercent(plan).toFloat())
            .apply()
    }

    /** Total risk split across the planned number of trades. */
    fun perTradeRiskPercent(plan: SymbolPlan): Double {
        val n = plan.riskTrades.coerceAtLeast(1)
        return plan.riskPercent.coerceIn(0.0, 100.0) / n
    }

    /**
     * The calculator's suggested lot for one trade.
     *
     * A 20-pip stop and a $10-per-pip standard lot, the same two assumptions the
     * scanner's calculator states on screen. They are named rather than buried
     * because a lot size quoted off an assumption the user cannot see is a
     * number they will size a real position with and not know why.
     */
    fun suggestedLot(
        balance: Double,
        riskPercent: Double,
        trades: Int,
        /**
         * USD -> account currency. The balance and the risk budget are in the
         * account's currency; [PIP_VALUE_PER_LOT] is in dollars. Dividing one by
         * the other without converting is what made a rand account size every
         * position roughly eighteen times too large.
         */
        fxRate: Double = 1.0
    ): Double {
        val n = trades.coerceAtLeast(1)
        val perTradeAmount = balance * riskPercent.coerceIn(0.0, 100.0) / 100.0 / n
        if (perTradeAmount <= 0.0) return 0.0

        val rate = if (fxRate > 0.0) fxRate else 1.0
        // The pip value expressed in the account's own currency.
        val pipValueInAccountCurrency = PIP_VALUE_PER_LOT * rate

        return maxOf(MIN_LOT, perTradeAmount / (STOP_PIPS * pipValueInAccountCurrency))
    }

    const val STOP_PIPS = 20.0
    const val PIP_VALUE_PER_LOT = 10.0

    /**
     * The volume to send for a signal on [symbol], or null to drop the signal.
     *
     * Precedence, highest first:
     *
     *  1. **Not selected** -> null. The user did not tick it, so it does not trade,
     *     whatever the robot is allowed to do.
     *  2. **Smart lot on** -> the calculator's figure. The user asked for their
     *     risk budget to decide the size, so the mentor's volume is advisory only.
     *  3. **Manual lot** -> exactly what the stepper says. An explicitly typed
     *     size is an instruction, not a hint, and scaling it would silently
     *     override someone who set 0.02 on purpose.
     *  4. **Neither** -> mentor-relative scaling, the original behaviour: copy the
     *     mentor's size in proportion to the two account balances.
     */
    fun resolveLot(
        plan: SymbolPlan,
        symbol: String,
        signalLot: Double,
        userBalance: Double,
        adminBalance: Double
    ): Double? {
        val cfg = plan.configFor(symbol) ?: return null
        if (!cfg.enabled) return null

        val raw = when {
            cfg.smartLot && plan.smartLotSize > 0.0 -> plan.smartLotSize
            !cfg.smartLot && cfg.lot > 0.0 -> cfg.lot
            adminBalance > 0.0 && userBalance > 0.0 -> (userBalance / adminBalance) * signalLot
            else -> MIN_LOT
        }

        val clamped = raw.coerceIn(MIN_LOT, MAX_LOT)
        return Math.round(clamped * 100.0) / 100.0
    }

    // ── Server sync ────────────────────────────────────────────────────────

    @Serializable
    private data class SyncSymbol(
        val symbol: String,
        val enabled: Boolean,
        val lot: Double,
        val max_trades: Int,
        val smart_lot: Boolean
    )

    @Serializable
    private data class SyncRequest(
        val license_key: String,
        val balance: Double,
        val currency: String,
        val risk_percent: Double,
        val risk_trades: Int,
        val smart_lot_size: Double,
        val symbols: List<SyncSymbol>
    )

    @Serializable
    private data class SyncResponse(
        val success: Boolean = false,
        val error: String? = null,
        val synced: Int? = null
    )

    /**
     * Pushes the plan to `sync-symbol-config` so the executor can enforce it.
     *
     * Server-side enforcement is the point. The device deciding its own lot size
     * is fine right up until the device is compromised, wound back, or simply
     * out of date -- the caps a user sets have to be checked where the order is
     * actually placed, which is the edge function, not here.
     *
     * Failure is not fatal and must not be presented as one: the plan is already
     * saved locally and the executor falls back to the device's own numbers, so
     * a sync that cannot reach the network costs enforcement, not function.
     */
    suspend fun sync(context: Context): Result<Int> {
        val licenseKey = prefs(context).getString("license_key", null)
        if (licenseKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No licence key on this device."))
        }

        val plan = load(context)

        return try {
            val request = SyncRequest(
                license_key = licenseKey.trim().uppercase(),
                balance = plan.balance,
                currency = plan.currency,
                risk_percent = plan.riskPercent,
                risk_trades = plan.riskTrades,
                smart_lot_size = plan.smartLotSize,
                symbols = plan.symbols.map {
                    SyncSymbol(
                        symbol = it.symbol,
                        enabled = it.enabled,
                        lot = it.lot,
                        max_trades = it.maxTrades,
                        smart_lot = it.smartLot
                    )
                }
            )

            val response = SupabaseSetup.client.httpClient.post(
                "${BuildConfig.SUPABASE_URL}/functions/v1/sync-symbol-config"
            ) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                timeout { requestTimeoutMillis = 20_000 }
                setBody(request)
            }

            val parsed = json.decodeFromString<SyncResponse>(response.body<String>())

            if (response.status.value in 200..299 && parsed.success) {
                android.util.Log.i("NovaHost", "[SymbolPlan] synced ${parsed.synced ?: 0} symbols")
                Result.success(parsed.synced ?: 0)
            } else {
                val reason = parsed.error ?: "Sync failed (${response.status.value})"
                android.util.Log.w("NovaHost", "[SymbolPlan] $reason")
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            android.util.Log.w("NovaHost", "[SymbolPlan] sync failed: ${e.message}")
            Result.failure(e)
        }
    }
}
