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
    val smartLot: Boolean = true,
    /**
     * What THIS user's broker calls the instrument, e.g. `XAUUSD` -> `Gold`.
     *
     * NovaHost trades canonical names because that is what the mentor picks and
     * what the licence allows. Brokers rarely agree: one live account lists gold
     * as `Gold`, the Nasdaq as `.USTECH.` and the Dow as `.US30.`, and an order
     * naming a symbol the broker does not carry is rejected outright.
     *
     * Blank means "not set", and the server falls back to its own discovery. It
     * is not something the user should normally have to fill in -- [discover]
     * reads the broker's real symbol list and pre-fills this -- but it stays
     * editable, because the one account whose spelling nothing predicts is the
     * account that would otherwise be stuck.
     */
    val brokerSymbol: String = ""
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

    /**
     * The pre-licence plan key, kept only so an existing install can be migrated
     * off it. Nothing writes here any more -- see [planKey].
     */
    private const val KEY_PLAN_LEGACY = "symbol_plan"

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
     * Bumped whenever the stored allowance actually changes.
     *
     * The allowance lives in SharedPreferences, which nothing can observe, so a
     * screen built from it has no way to know it went stale. Screens collect
     * this and re-read.
     */
    private val _allowanceRevision = kotlinx.coroutines.flow.MutableStateFlow(0)
    val allowanceRevision: kotlinx.coroutines.flow.StateFlow<Int> = _allowanceRevision

    /**
     * Records a new allowance handed down by the server.
     *
     * The allowance used to be written exactly once, during licence activation,
     * and never again. A mentor who added a symbol to their robot updated the
     * database, the licence and the portal -- and every handset in the field
     * carried on offering the old list forever, because nothing ever asked
     * again. The only cure was re-activating the licence, which nobody would
     * think to do and nothing prompted.
     *
     * `claim-signals` now carries it on every poll, so this is called roughly
     * every twenty seconds. It writes only on a real change, and returns whether
     * it wrote so the caller can say so in the terminal feed.
     *
     * An empty or absent list is ignored rather than applied. "The server told
     * us nothing" and "the robot allows nothing" are different statements, and
     * treating the first as the second would empty the Trading Symbols screen
     * and stop the robot trading on a transient response.
     */
    fun updateAllowance(context: Context, incoming: List<String>?): Boolean {
        val next = incoming
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: return false

        if (next.isEmpty()) return false
        if (next == robotAllowance(context)) return false

        prefs(context).edit().putString(KEY_ROBOT_ALLOWANCE, next.joinToString(",")).apply()
        _allowanceRevision.value += 1

        android.util.Log.i("NovaHost", "[SymbolPlan] allowance updated -> ${next.joinToString(",")}")
        return true
    }

    /**
     * The stored plan, reconciled against the current allowance.
     *
     * Reconciling on read rather than on write is deliberate: the allowance can
     * change under the app when a mentor edits the robot and the licence
     * refreshes, and a plan holding a symbol that is no longer permitted would
     * otherwise keep sizing trades the server will reject.
     */
    /**
     * Where this licence's plan is stored.
     *
     * The plan used to live under one device-wide key, which quietly assumed a
     * handset only ever holds one licence. It does not -- the vault lets a user
     * switch between keys, and a single device in this project has activated
     * four. Everything in the plan is licence-specific: the symbols come from
     * that robot's allowance, and `brokerSymbol` is the spelling used by the
     * broker account bound to that licence.
     *
     * So a switch carried the previous licence's answers into the new one. Gold
     * saved as `Gold` for a Trade245 licence went on being sent as `Gold` after
     * a switch to a licence on a broker that lists `XAUUSD` -- an order rejected
     * on arrival, recovered only by the fallback chain, and only after burning
     * one signal.
     *
     * Falls back to the legacy key when there is no licence yet, so an install
     * mid-activation still has somewhere coherent to read and write.
     */
    private fun planKey(context: Context): String {
        val licence = prefs(context).getString("license_key", null)?.trim()?.uppercase()
        return if (licence.isNullOrBlank()) KEY_PLAN_LEGACY else "symbol_plan:$licence"
    }

    /**
     * Moves a pre-licence plan onto the active licence, once.
     *
     * The legacy entry is REMOVED after being adopted, and that removal is the
     * point rather than tidiness: leaving it would let the next licence adopt
     * the same plan too, which is precisely the bleed this change exists to
     * stop. The first licence to look keeps the settings, everything after it
     * starts clean.
     */
    private fun migrateLegacyPlan(context: Context, key: String) {
        if (key == KEY_PLAN_LEGACY) return

        val p = prefs(context)
        val legacy = p.getString(KEY_PLAN_LEGACY, null)
        if (legacy.isNullOrBlank()) return

        p.edit().putString(key, legacy).remove(KEY_PLAN_LEGACY).apply()
        android.util.Log.i("NovaHost", "[SymbolPlan] migrated device plan onto $key")
    }

    fun load(context: Context): SymbolPlan {
        val key = planKey(context)
        if (prefs(context).getString(key, null).isNullOrBlank()) {
            migrateLegacyPlan(context, key)
        }

        val raw = prefs(context).getString(key, null)
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
            .putString(planKey(context), json.encodeToString(SymbolPlan.serializer(), plan))
            // The scanner's TradePlanner still reads these two directly, and they
            // stay device-wide on purpose: they describe whichever licence is
            // active, and they are rewritten on every save.
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
        val smart_lot: Boolean,
        /** Null rather than "" when unset -- see [SymbolConfig.brokerSymbol]. */
        val broker_symbol: String? = null
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
                        smart_lot = it.smartLot,
                        broker_symbol = it.brokerSymbol.trim().ifBlank { null }
                    )
                }
            )

            val response = NovaHostBackend.client.httpClient.post(
                "${BuildConfig.NOVAHOST_API_URL}/functions/v1/sync-symbol-config"
            ) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer ${BuildConfig.NOVAHOST_API_KEY}")
                header("apikey", BuildConfig.NOVAHOST_API_KEY)
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

    // ── Broker symbol discovery ────────────────────────────────────────────

    @Serializable
    private data class MappingDto(
        val symbol: String,
        val current: String? = null,
        val suggested: String? = null,
        val confidence: String? = null,
        val current_is_valid: Boolean? = null
    )

    @Serializable
    private data class DiscoverResponse(
        val success: Boolean = false,
        val code: String? = null,
        val error: String? = null,
        val broker_symbols: List<String> = emptyList(),
        val mappings: List<MappingDto> = emptyList()
    )

    /**
     * What the broker's own symbol list said, after it was applied to the plan.
     *
     * [unmatched] is the part that matters to the user: those are instruments
     * their robot is allowed to trade and their broker does not appear to carry
     * under any name, which is the one case that still needs a human.
     */
    data class Discovery(
        /** Every name the broker lists, for the picker. */
        val brokerSymbols: List<String>,
        /** Canonical symbols that were resolved to a broker name. */
        val matched: List<String>,
        /** Canonical symbols the broker does not appear to offer. */
        val unmatched: List<String>
    )

    /**
     * Reads the instrument list off the user's own broker and fills in the names.
     *
     * This is the difference between asking someone to know that their broker
     * spells the Nasdaq `.USTECH.` and simply telling them. `broker-symbols`
     * returns the account's real Market Watch plus a best match for each symbol
     * on the licence; this applies those matches, saves, and hands back what
     * could not be resolved.
     *
     * A name the user has already set is never overwritten. Discovery fills
     * blanks and reports conflicts -- it does not overrule a person who has
     * looked at their own terminal.
     *
     * Failure is not fatal: the plan is untouched and the server still falls
     * back to its own discovery at execution time, so a user who never opens
     * this screen still trades.
     */
    suspend fun discover(context: Context): Result<Discovery> {
        val licenseKey = prefs(context).getString("license_key", null)
        if (licenseKey.isNullOrBlank()) {
            return Result.failure(IllegalStateException("No licence key on this device."))
        }

        return try {
            val response = NovaHostBackend.client.httpClient.post(
                "${BuildConfig.NOVAHOST_API_URL}/functions/v1/broker-symbols"
            ) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer ${BuildConfig.NOVAHOST_API_KEY}")
                header("apikey", BuildConfig.NOVAHOST_API_KEY)
                // The broker is queried live, so this is slower than a plain
                // database read and must not be cut off mid-answer.
                timeout { requestTimeoutMillis = 30_000 }
                setBody(mapOf("license_key" to licenseKey.trim().uppercase()))
            }

            val parsed = json.decodeFromString<DiscoverResponse>(response.body<String>())

            if (response.status.value !in 200..299 || !parsed.success) {
                val reason = parsed.error ?: "Could not read your broker's symbols."
                android.util.Log.w("NovaHost", "[SymbolPlan] discover: ${parsed.code} $reason")
                return Result.failure(Exception(reason))
            }

            val plan = load(context)
            val byCanonical = parsed.mappings.associateBy { it.symbol }

            val applied = plan.symbols.map { cfg ->
                val hit = byCanonical[cfg.symbol]
                // Only ever fills a blank. `current` is what the server already
                // holds, which is either a previous discovery or this user's own
                // typing -- neither is ours to replace with a fresh guess.
                val resolved = when {
                    cfg.brokerSymbol.isNotBlank() -> cfg.brokerSymbol
                    !hit?.current.isNullOrBlank() -> hit.current
                    !hit?.suggested.isNullOrBlank() -> hit.suggested
                    else -> ""
                }
                cfg.copy(brokerSymbol = resolved ?: "")
            }

            val updated = plan.copy(symbols = applied)
            save(context, updated)

            val matched = applied.filter { it.brokerSymbol.isNotBlank() }.map { it.symbol }
            val unmatched = applied.filter { it.brokerSymbol.isBlank() }.map { it.symbol }

            android.util.Log.i(
                "NovaHost",
                "[SymbolPlan] discovered ${matched.size}/${applied.size} broker symbols"
            )

            Result.success(
                Discovery(
                    brokerSymbols = parsed.broker_symbols,
                    matched = matched,
                    unmatched = unmatched
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("NovaHost", "[SymbolPlan] discover failed: ${e.message}")
            Result.failure(e)
        }
    }
}
