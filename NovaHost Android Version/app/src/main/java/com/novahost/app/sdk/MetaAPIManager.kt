package com.novahost.app.sdk

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
import com.novahost.app.BuildConfig
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.plugins.timeout
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

    // No hardcoded broker. NovaHost is broker-agnostic -- the server the user
    // types is passed straight through to MetaCopier.
    const val BROKER_NAME = "your broker"

    // Observable connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * True while [probeLinkStatus] has a call in flight.
     *
     * [isConnected] starts false and, until this existed, was only ever written
     * by [synchronize] -- which runs on ignition. So a cold start on a fully
     * linked account rendered "NOT LINKED" until the user pressed START, and
     * every user who read that as "my broker fell off" was reading a default,
     * not a fact.
     *
     * A boolean cannot say "unknown", which is why the probe needs its own
     * flag: the header shows CHECKING while this is true and only commits to
     * NOT LINKED once the server has actually answered.
     */
    private val _isProbingLink = MutableStateFlow(false)
    val isProbingLink: StateFlow<Boolean> = _isProbingLink.asStateFlow()

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

            val status = fetchLicenseStatus(licenseKey)

            when {
                status == null -> {
                    _isSynchronized.value = false
                    _isConnected.value = false
                    addLog(">> Could not reach the licence server")
                    return false
                }

                !status.success -> {
                    _isSynchronized.value = false
                    _isConnected.value = false
                    addLog(">> Could not verify licence: ${status.error ?: "unknown error"}")
                    false
                }

                !status.active -> {
                    _isSynchronized.value = false
                    _isConnected.value = false
                    addLog(">> ${status.message ?: "Licence is not active"}")
                    false
                }

                !status.linked -> {
                    _isSynchronized.value = false
                    _isConnected.value = false
                    addLog(">> No trading account connected")
                    false
                }

                else -> {
                    _isSynchronized.value = true
                    _isConnected.value = true
                    addLog(">> Trading account linked (${status.broker_server ?: "broker"})")
                    addLog(">> Trade engine activated")
                    true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "[Sync] failed", e)
            _isSynchronized.value = false
            _isConnected.value = false
            addLog(">> Could not verify trading account")
            false
        }
    }

    /**
     * Refreshes the broker-link indicator without touching the trade engine.
     *
     * [synchronize] answers the same question but is an ignition step: it logs
     * to the terminal, and its false return blocks START. This is the passive
     * version, safe to run on every resume -- it reports what the server says
     * and nothing else.
     *
     * A call that fails leaves [isConnected] alone rather than clearing it. The
     * indicator exists to tell the user whether their broker is attached, and
     * a flat network is not evidence that it came off.
     */
    fun probeLinkStatus(context: android.content.Context) {
        if (_isProbingLink.value) return

        managerScope.launch {
            _isProbingLink.value = true
            try {
                val licenseKey = context
                    .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("license_key", null)

                if (licenseKey.isNullOrBlank()) {
                    // No licence is a definite answer, not an unknown one.
                    _isConnected.value = false
                    _isSynchronized.value = false
                    return@launch
                }

                val status = fetchLicenseStatus(licenseKey) ?: return@launch

                val linked = status.success && status.active && status.linked
                _isConnected.value = linked
                if (!linked) _isSynchronized.value = false
            } catch (e: Exception) {
                android.util.Log.w("NovaHost", "[LinkProbe] failed: ${e.message}")
            } finally {
                _isProbingLink.value = false
            }
        }
    }

    /**
     * Asks `license-status` what this key is and whether an account hangs off it.
     *
     * Server-side because RLS on `licenses` has no policy for `anon`, and a
     * licence-key install has no auth session -- see [synchronize].
     *
     * Returns null when the call itself failed, which callers must treat as
     * "unknown", never as "no".
     */
    private suspend fun fetchLicenseStatus(licenseKey: String): LicenseStatusResponse? = try {
        val payload = kotlinx.serialization.json.Json.encodeToString(
            LicenseStatusRequest.serializer(),
            LicenseStatusRequest(license_key = licenseKey.trim().uppercase())
        )

        val response = SupabaseSetup.client.httpClient.post(
            "${BuildConfig.SUPABASE_URL}/functions/v1/license-status"
        ) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            timeout { requestTimeoutMillis = 20_000 }
            setBody(payload)
        }

        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<LicenseStatusResponse>(response.body<String>())
    } catch (e: Exception) {
        android.util.Log.e("NovaHost", "[LicenceStatus] failed", e)
        null
    }

    /**
     * Polls `license-status` for a few seconds waiting for a link to appear.
     *
     * Used after a connect attempt that threw. Registering an account is not
     * atomic from the client's point of view: the edge function can finish
     * writing the link long after the handset has given up on the socket, and
     * every timeout so far has been a *successful* registration that the user
     * was told had failed. Asking the server what actually happened is the only
     * honest way to report the outcome.
     */
    private suspend fun awaitLink(licenseKey: String, attempts: Int = 4): Boolean {
        repeat(attempts) { attempt ->
            if (attempt > 0) kotlinx.coroutines.delay(3_000)
            val status = fetchLicenseStatus(licenseKey)
            if (status?.linked == true) return true
        }
        return false
    }

    suspend fun disconnect() {
        // TODO: connection.close()
        _isSynchronized.value = false
        _isConnected.value = false
        addLog(">> Disconnected from trade server")
    }

    // ── Broker Connection & Sync (Task ID: 1205) ─────────────────
    
    /**
     * How far along a link attempt is. Reported so the screen can show the user
     * something true during a wait that legitimately runs to two minutes.
     *
     * There are only two phases because there are only two things the app
     * actually knows: the request is out, or the request died and we are asking
     * the server what happened. The screen used to animate through three
     * invented stages on a fixed timer, ending on "Verification complete" before
     * the call had returned -- which was a progress bar for a process nobody was
     * watching.
     */
    enum class LinkPhase {
        /** The registration request is in flight; MetaCopier is dialling the broker. */
        REGISTERING,
        /** The socket died. Polling the server to find out whether it landed anyway. */
        VERIFYING
    }

    suspend fun testBrokerConnection(
        context: android.content.Context,
        server: String,
        accountId: String,
        passwordRaw: String,
        platform: String = "mt5",
        symbolSuffix: String = "",
        onPhase: (LinkPhase) -> Unit = {}
    ): Result<String> {
        return try {
            onPhase(LinkPhase.REGISTERING)
            val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)

            // No placeholder key. This used to fall back to "DEMO-1234", which
            // the server correctly refuses -- so an unactivated device was told
            // "Licence not recognised" while standing on a broker form, and read
            // that as the broker link being broken. Every other call site in
            // this file already treats a missing key as a stop.
            val licenseKey = prefs.getString("license_key", null)?.trim()?.uppercase()
            if (licenseKey.isNullOrBlank()) {
                return Result.failure(
                    IllegalStateException(
                        "This device is not activated yet. Enter your licence key first, then connect your broker."
                    )
                )
            }

            // MetaTrader accepts none of these with surrounding whitespace, and
            // all three are pasted out of a broker email more often than typed.
            // An invisible trailing newline on the password is otherwise
            // indistinguishable from a wrong password.
            val cleanAccount = accountId.filterNot { it.isWhitespace() }
            val cleanServer = server.trim()
            val cleanPassword = passwordRaw.trim()

            // Registers the account with MetaCopier and binds the returned
            // MetaCopier account id to this licence, server-side.
            val request = MetaCopierConnectRequest(
                license_key = licenseKey,
                account_number = cleanAccount,
                password = cleanPassword,
                server = cleanServer,
                platform = platform.uppercase()
            )

            val httpResponse = SupabaseSetup.client.httpClient.post(
                "${BuildConfig.SUPABASE_URL}/functions/v1/metacopier-connect"
            ) {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                }
                // Registering an account makes MetaCopier dial the broker and
                // authenticate, which routinely runs past the client defaults.
                // All three limits are raised, not just the request one: the
                // failure users actually hit was `connect_timeout`, which fires
                // before a request timeout can.
                timeout {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 30_000
                    socketTimeoutMillis = 120_000
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
                _isSynchronized.value = true
                addLog(">> Trading account linked ($cleanServer)")
                android.util.Log.i("NovaHost", "[BrokerConn] Connected via MetaCopier (${parsed.platform ?: platform})")
                Result.success(parsed.account_id)
            } else {
                // Deliberately does NOT clear isConnected. A rejected *new*
                // attempt says nothing about an account already bound to this
                // licence, and forcing false here is what flipped a working
                // install to NOT LINKED the moment a user mistyped a password on
                // a reconnect. probeLinkStatus owns that flag; this only sets it
                // on a confirmed success.
                val reason = parsed.error ?: "Connection failed (${httpResponse.status.value})"
                android.util.Log.e(
                    "NovaHost",
                    "[BrokerConn] ${parsed.code ?: "NO_CODE"}: $reason ${parsed.details ?: ""}"
                )
                addLog(">> Broker link refused (${parsed.code ?: httpResponse.status.value})")
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            // The socket died, which says nothing about whether the account was
            // registered. It usually was: the edge function finishes writing the
            // link well after a handset on mobile data has given up waiting, and
            // every timeout observed in testing was a successful registration
            // reported to the user as a failure.
            //
            // So ask the server what actually happened instead of guessing from
            // the transport.
            android.util.Log.w("NovaHost", "[BrokerConn] transport failed (${e.message}); verifying server-side")
            addLog(">> Connection slow -- checking whether the account linked")
            onPhase(LinkPhase.VERIFYING)

            val licenseKey = context
                .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                .getString("license_key", null)

            if (!licenseKey.isNullOrBlank() && awaitLink(licenseKey)) {
                _isConnected.value = true
                _isSynchronized.value = true
                android.util.Log.i("NovaHost", "[BrokerConn] Link confirmed server-side after transport failure")
                addLog(">> Trading account linked")
                Result.success("linked")
            } else {
                // Left alone for the same reason as the rejection branch above:
                // a dead socket is not evidence that an existing link came off.
                android.util.Log.e("NovaHost", "[BrokerConn] Connection Failed: ${e.message}", e)
                Result.failure(
                    Exception("Could not reach the server. Check your connection and try again.")
                )
            }
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
                android.util.Log.e("NovaHost", "[BrokerConn] Balance Sync failed: ${e.message}", e)
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
                    // A market order goes broker-side before it answers. 15s is
                    // not enough margin to distinguish "slow fill" from "failed".
                    timeout { requestTimeoutMillis = 45_000 }
                    setBody(request)
                }

            val bodyText = response.body<String>()
            val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<MetaCopierTradeResponse>(bodyText)

            // A 2xx with success=false is still a failure -- never report a
            // trade as placed unless the broker actually accepted it.
            if (response.status.value in 200..299 && parsed.success) {
                android.util.Log.i("NovaHost", "[Trade] ${parsed.message}")
                Result.success(parsed.message ?: "Trade sent.")
            } else {
                val reason = parsed.error ?: "Execution failed (${response.status.value})"
                android.util.Log.e("NovaHost", "[Trade] $reason ${parsed.details ?: ""}")
                Result.failure(Exception(reason))
            }
        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "[Trade] Execution failed", e)
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
                    android.util.Log.e("NovaHost", "Heartbeat pulse failed", e)
                }
                kotlinx.coroutines.delay(60000) // Pulse every 60 seconds
            }
        }
    }
}
