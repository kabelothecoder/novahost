package com.novaedge.app.sdk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.novaedge.app.BuildConfig
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.github.jan.supabase.postgrest.from

/**
 * MetaAPIManager
 *
 * Wraps the MetaAPI Java SDK for:
 *   - synchronize(subscriberId)  → Screen 5 Start/Stop trigger
 *   - synchronize(context)       → verifies a MetaCopier account is linked
 *   - Account prefix validation  → Stitch intercepts before connect
 *
 * Integration point: replace the placeholder calls with actual
 * cloud.metaapi.sdk.MetaApi / MetaTraderAccount calls.
 */
object MetaAPIManager {

    // No hardcoded broker. Nova Edge is broker-agnostic -- the server the user
    // types is passed straight through to MetaCopier.
    const val BROKER_NAME = "your broker"

    // Observable connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val botStatus = MutableStateFlow(BotStatus.IDLE)

    private val _isSynchronized = MutableStateFlow(false)
    val isSynchronized: StateFlow<Boolean> = _isSynchronized

    private val _balance = MutableStateFlow(0.0)
    val balance: StateFlow<Double> = _balance

    private val _equity = MutableStateFlow(0.0)
    val equity: StateFlow<Double> = _equity

    private val _logs = MutableStateFlow<List<String>>(listOf(">> System idle..."))
    val logs: StateFlow<List<String>> = _logs

    fun addLog(msg: String) {
        _logs.value = _logs.value.takeLast(49) + msg
    }

    private val managerScope = CoroutineScope(Dispatchers.IO)

    // ── Start/Stop toggle (Screen 5) ──────────────────────────────
    /**
     * Confirms this device actually has a trading account wired up before the
     * robot reports itself as running.
     *
     * This used to unconditionally set both flags true and log "Connected to
     * trade server" without contacting anything, so the UI showed a live engine
     * even when no broker was attached. It now checks that the licence has a
     * MetaCopier account bound to it, and reports honestly when it does not.
     */
    suspend fun synchronize(context: android.content.Context): Boolean {
        return try {
            val licenseKey = context
                .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                .getString("license_key", null)

            if (licenseKey.isNullOrBlank()) {
                _isSynchronized.value = false
                _isConnected.value = false
                addLog(">> No licence on this device")
                return false
            }

            val rows = SupabaseSetup.client
                .from("licenses")
                .select(io.github.jan.supabase.postgrest.query.Columns.raw("metadata")) {
                    filter { eq("license_key", licenseKey.trim().uppercase()) }
                }
                .decodeList<kotlinx.serialization.json.JsonObject>()

            val accountId = rows.firstOrNull()
                ?.get("metadata")?.let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("metacopier_account_id")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.takeIf { it.isNotBlank() && it != "null" }

            if (accountId.isNullOrBlank()) {
                _isSynchronized.value = false
                _isConnected.value = false
                addLog(">> No trading account connected")
                false
            } else {
                _isSynchronized.value = true
                _isConnected.value = true
                addLog(">> Trading account linked")
                addLog(">> Trade engine activated")
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("Nova Edge", "[Sync] failed", e)
            _isSynchronized.value = false
            _isConnected.value = false
            addLog(">> Could not verify trading account")
            false
        }
    }

    suspend fun disconnect(subscriberId: String) {
        // TODO: connection.close()
        _isSynchronized.value = false
        _isConnected.value = false
        addLog(">> Disconnected from trade server")
    }

    // ── Broker Connection & Sync (Task ID: 1205) ─────────────────
    
    suspend fun testBrokerConnection(
        context: android.content.Context,
        server: String,
        accountId: String,
        passwordRaw: String,
        platform: String = "mt5",
        accountType: String = "Standard — No Bonus",
        symbolSuffix: String = ""
    ): Result<String> {
        return try {
            val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
            val licenseKey = prefs.getString("license_key", "DEMO-1234") ?: "DEMO-1234"

            // Registers the account with MetaCopier and binds the returned
            // MetaCopier account id to this licence, server-side.
            val request = MetaCopierConnectRequest(
                license_key = licenseKey,
                account_number = accountId,
                password = passwordRaw,
                server = server,
                platform = platform.uppercase()
            )

            val httpResponse = SupabaseSetup.client.httpClient.post(
                "${BuildConfig.SUPABASE_URL}/functions/v1/metacopier-connect"
            ) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                }
                setBody(request)
            }

            val bodyText = httpResponse.body<String>()
            val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<MetaCopierConnectResponse>(bodyText)

            // Trust the success flag, not the status code -- the old code did a
            // substring check for "error" on the raw body, which misfired on any
            // payload that merely contained that word.
            if (httpResponse.status.value in 200..299 && parsed.success && parsed.account_id != null) {
                _isConnected.value = true
                android.util.Log.i("Nova Edge", "[BrokerConn] Connected via MetaCopier (${parsed.platform ?: platform})")
                Result.success(parsed.account_id)
            } else {
                _isConnected.value = false
                val reason = parsed.error ?: "Connection failed (${httpResponse.status.value})"
                android.util.Log.e("Nova Edge", "[BrokerConn] $reason ${parsed.details ?: ""}")
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            android.util.Log.e("Nova Edge", "[BrokerConn] Connection Failed: ${e.message}", e)
            _isConnected.value = false
            Result.failure(e)
        }
    }


    fun startBalanceSync(accountId: String) {
        managerScope.launch {
            try {
                val channel = SupabaseSetup.client.realtime.channel("broker-$accountId")
                val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "broker_accounts"
                    // Assuming account_id is the primary key or unique identifier
                }
                
                channel.subscribe()
                
                changes.collect { action ->
                    val record = action.record
                    val newBalance = record["balance"]?.toString()?.toDoubleOrNull() ?: 0.0
                    val newEquity = record["equity"]?.toString()?.toDoubleOrNull() ?: 0.0
                    
                    // Filter by accountId if multiple accounts could exist in one channel
                    if (record["account_id"]?.toString() == accountId) {
                        _balance.value = newBalance
                        _equity.value = newEquity
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Nova Edge", "[BrokerConn] Balance Sync failed: ${e.message}", e)
            }
        }
    }

    // ── Account validation ─────────────────────────────────────────
    /**
     * Broker-agnostic sanity check. Replaces validateTrade245Account(), which
     * demanded a "245" prefix and so rejected Exness, Deriv, XM and every other
     * broker. Real validation happens when MetaCopier attempts the login -- the
     * client should not pretend to know each broker's numbering scheme.
     */
    fun isPlausibleAccountNumber(accountNumber: String): Boolean {
        val trimmed = accountNumber.trim()
        return trimmed.length in 4..20 && trimmed.all { it.isDigit() }
    }

    private fun getLocalSymbol(symbol: String): String {
        return symbol.replace(".pro", "").replace("-", "")
    }

    // ── Trade execution (MetaCopier) ─────────────────────────────
    /**
     * Places a market order through metacopier-execute.
     *
     * The licence is the credential: the edge function verifies it is active and
     * resolves the MetaCopier account from it, so no broker credentials or
     * account ids leave the device.
     */
    suspend fun executeTrade(
        context: android.content.Context,
        pair: String,
        side: String,
        volume: Double,
        sl: Double? = 0.0,
        tp: Double? = 0.0,
        signalId: String? = null
    ): Result<String> {
        return try {
            val licenseKey = context
                .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                .getString("license_key", null)

            if (licenseKey.isNullOrBlank()) {
                return Result.failure(IllegalStateException("No licence key on this device."))
            }

            val request = MetaCopierTradeRequest(
                license_key = licenseKey,
                pair = pair,
                side = side.uppercase(),
                volume = volume,
                sl = sl,
                tp = tp,
                signal_id = signalId
            )

            val response: io.ktor.client.statement.HttpResponse =
                SupabaseSetup.client.httpClient.post(
                    "${BuildConfig.SUPABASE_URL}/functions/v1/metacopier-execute"
                ) {
                    header(io.ktor.http.HttpHeaders.Authorization, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    header(io.ktor.http.HttpHeaders.ContentType, io.ktor.http.ContentType.Application.Json)
                    setBody(request)
                }

            val bodyText = response.body<String>()
            val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<MetaCopierTradeResponse>(bodyText)

            // A 2xx with success=false is still a failure -- never report a
            // trade as placed unless the broker actually accepted it.
            if (response.status.value in 200..299 && parsed.success) {
                android.util.Log.i("Nova Edge", "[Trade] ${parsed.message}")
                Result.success(parsed.message ?: "Trade sent.")
            } else {
                val reason = parsed.error ?: "Execution failed (${response.status.value})"
                android.util.Log.e("Nova Edge", "[Trade] $reason ${parsed.details ?: ""}")
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            android.util.Log.e("Nova Edge", "[Trade] Execution failed", e)
            Result.failure(e)
        }
    }

    // ── Heartbeat Pulse ──────────────────────────────────────────
    fun startHeartbeatPulse(context: android.content.Context, deviceId: String) {
        managerScope.launch {
            val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
            val licenseKey = prefs.getString("license_key", null) ?: return@launch

            while (true) {
                try {
                    SupabaseSetup.client
                        .from("device_activations")
                        .update(mapOf("last_seen_at" to java.time.Instant.now().toString())) {
                            filter {
                                eq("device_id", deviceId)
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("Nova Edge", "Heartbeat pulse failed", e)
                }
                kotlinx.coroutines.delay(60000) // Pulse every 60 seconds
            }
        }
    }
}
