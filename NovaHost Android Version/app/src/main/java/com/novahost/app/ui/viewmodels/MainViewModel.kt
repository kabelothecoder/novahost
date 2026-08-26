package com.novahost.app.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novahost.app.sdk.Entitlements
import com.novahost.app.sdk.SupabaseSetup
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the app gate is in its conversation with the user.
 *
 * This replaced a `SubscriptionRoute` enum that mixed two different questions --
 * "what is the user entitled to" and "what should the paywall render" -- into
 * one value. Entitlement now lives in [MainViewModel.isPremium] and
 * [MainViewModel.hasScanner]; this is only ever about the screen.
 */
enum class GateStage {
    /** Waiting for an email. */
    IDLE,
    /** A check is in flight. */
    CHECKING,
    /** The server answered and the answer was no. [GateState.message] says why. */
    DENIED,
    /** Checkout URL is ready; the browser is about to open. */
    CHECKOUT_READY,
    /** Back from Payfast, polling for the ITN to land. */
    VERIFYING,
    /** Paid and bound to this device. */
    GRANTED
}

/**
 * @param message  the sentence to put in front of the user, if any.
 * @param checkout the Payfast URL to open, set only in [GateStage.CHECKOUT_READY].
 * @param reason   the server's machine-readable denial: no_purchase / not_paid /
 *                 expired / device_mismatch. The gate needs this and not just
 *                 [message], because a device mismatch is a R150 move rather
 *                 than a R599 purchase and the button has to say so.
 */
data class GateState(
    val stage: GateStage = GateStage.IDLE,
    val message: String? = null,
    val checkout: String? = null,
    val reason: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)

    // ── Entitlements ───────────────────────────────────────────────────────
    // Seeded from the local cache so a cold start on a flat network does not
    // lock a paying user out of an app they have already bought.

    private val _isPremium = MutableStateFlow(Entitlements.cached(application).appAccess)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _hasScanner = MutableStateFlow(Entitlements.cached(application).scanner)
    val hasScanner: StateFlow<Boolean> = _hasScanner.asStateFlow()

    /** The email the user last checked with. Pre-fills both paygates. */
    private val _userEmail = MutableStateFlow(Entitlements.savedEmail(application))
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    // ── Gate screens ───────────────────────────────────────────────────────

    private val _appGate = MutableStateFlow(GateState())
    val appGate: StateFlow<GateState> = _appGate.asStateFlow()

    private val _scannerGate = MutableStateFlow(GateState())
    val scannerGate: StateFlow<GateState> = _scannerGate.asStateFlow()

    // ── Robot identity ─────────────────────────────────────────────────────

    private val _adminDisplayName = MutableStateFlow(prefs.getString("admin_display_name", "SYSTEM_ADMIN") ?: "SYSTEM_ADMIN")
    val adminDisplayName: StateFlow<String> = _adminDisplayName.asStateFlow()

    // Robot identity is written by licence activation and by the robot picker,
    // under the keys display_name / avatar_url / background_video_url. Reading
    // any other key (as this used to) means the real robot never appears.
    private val _backgroundMediaUrl = MutableStateFlow(prefs.getString("background_video_url", null))
    val backgroundMediaUrl: StateFlow<String?> = _backgroundMediaUrl.asStateFlow()

    private val _mediaType = MutableStateFlow(prefs.getString("media_type", "image"))
    val mediaType: StateFlow<String?> = _mediaType.asStateFlow()

    private val _robotName = MutableStateFlow(prefs.getString("display_name", "") ?: "")
    val robotName: StateFlow<String> = _robotName.asStateFlow()

    private val _robotAvatarUrl = MutableStateFlow(prefs.getString("avatar_url", null))
    val robotAvatarUrl: StateFlow<String?> = _robotAvatarUrl.asStateFlow()

    /**
     * The key the app is currently trading under.
     *
     * Surfaced because the dashboard never showed it. A user who entered a key
     * on the portal had no way to confirm the handset had picked up that key
     * rather than an older one -- and with several keys on one robot, the robot
     * name alone cannot tell them apart.
     */
    private val _activeLicenseKey = MutableStateFlow(prefs.getString("license_key", "") ?: "")
    val activeLicenseKey: StateFlow<String> = _activeLicenseKey.asStateFlow()

    private val _userLicenses = MutableStateFlow<List<com.novahost.app.sdk.LicenseRecord>>(emptyList())
    val userLicenses: StateFlow<List<com.novahost.app.sdk.LicenseRecord>> = _userLicenses.asStateFlow()

    /**
     * Why the licence list is empty, when it is empty for a reason.
     *
     * "You have no keys on this device" and "we could not reach the server" are
     * different sentences and lead the user somewhere different. The old code
     * could not tell them apart: every failure went through a bare
     * `catch { printStackTrace() }` and rendered as the same empty state, which
     * is how a broken query spent weeks looking like an empty account.
     */
    private val _licenseError = MutableStateFlow<String?>(null)
    val licenseError: StateFlow<String?> = _licenseError.asStateFlow()

    private val _licensesLoading = MutableStateFlow(false)
    val licensesLoading: StateFlow<Boolean> = _licensesLoading.asStateFlow()

    init {
        // Re-check in the background against whatever email is on file, so a
        // refund or a device move is picked up without the user doing anything.
        val known = Entitlements.savedEmail(application)
        if (known.isNotEmpty()) refreshEntitlements(known)
    }

    /**
     * Re-reads the active robot from preferences. Call after activating a licence
     * or switching robots so the dashboard reflects the change immediately.
     */
    fun refreshRobotIdentity() {
        _robotName.value = prefs.getString("display_name", "") ?: ""
        _robotAvatarUrl.value = prefs.getString("avatar_url", null)
        _backgroundMediaUrl.value = prefs.getString("background_video_url", null)
        _activeLicenseKey.value = prefs.getString("license_key", "") ?: ""
    }

    // ── App access (R599, once-off) ────────────────────────────────────────

    /**
     * Silent re-check. Never downgrades on a failed call and never puts a
     * message on screen -- it runs without the user having asked for it.
     */
    fun refreshEntitlements(email: String) {
        viewModelScope.launch {
            val result = Entitlements.check(getApplication(), email)
            if (!result.answered) return@launch
            _isPremium.value = result.appAccess
            _hasScanner.value = result.scanner
        }
    }

    /** The paygate's "check my access" button. */
    fun checkAppAccess(email: String) {
        val cleaned = email.trim()
        if (cleaned.isEmpty()) {
            _appGate.value = GateState(GateStage.DENIED, "Enter the email you paid with.")
            return
        }

        viewModelScope.launch {
            _appGate.value = GateState(GateStage.CHECKING)
            val result = Entitlements.check(getApplication(), cleaned)

            _userEmail.value = cleaned.lowercase()

            if (!result.answered) {
                _appGate.value = GateState(GateStage.DENIED, result.message)
                return@launch
            }

            _hasScanner.value = result.scanner

            if (result.appAccess) {
                _isPremium.value = true
                _appGate.value = GateState(GateStage.GRANTED)
            } else {
                _appGate.value = GateState(
                    stage = GateStage.DENIED,
                    message = result.message ?: "No app access found for that email.",
                    reason = result.reason
                )
            }
        }
    }

    /** The paygate's "Buy app access — R599" button. */
    fun buyAppAccess(email: String) {
        val cleaned = email.trim().ifEmpty { _userEmail.value }
        if (cleaned.isEmpty()) {
            _appGate.value = GateState(GateStage.DENIED, "Enter your email first so your purchase can be found again.")
            return
        }

        viewModelScope.launch {
            _appGate.value = GateState(GateStage.CHECKING)
            // Remembered before the browser opens: the deep link comes back into
            // a fresh process and this is how it knows who paid.
            prefs.edit().putString("pending_payment_email", cleaned.lowercase()).apply()

            val checkout = Entitlements.checkout(getApplication(), cleaned, Entitlements.Product.APP)

            _appGate.value = when {
                checkout == null ->
                    GateState(GateStage.DENIED, "Could not reach the payment server. Check your connection.")

                checkout.route == "ACTIVE_SAME_DEVICE" -> {
                    _isPremium.value = true
                    GateState(GateStage.GRANTED)
                }

                checkout.checkoutUrl != null ->
                    GateState(GateStage.CHECKOUT_READY, checkout.message, checkout.checkoutUrl)

                else ->
                    GateState(GateStage.DENIED, checkout.message ?: "That purchase could not be started.")
            }
        }
    }

    // ── Chart scanner (R349, once-off) ─────────────────────────────────────

    /** "Restore purchase on this device" on the scanner sheet. */
    fun checkScannerAccess() {
        val email = _userEmail.value.ifEmpty { Entitlements.savedEmail(getApplication()) }
        if (email.isEmpty()) {
            _scannerGate.value = GateState(GateStage.DENIED, "Unlock the app first — the scanner is tied to the same email.")
            return
        }

        viewModelScope.launch {
            _scannerGate.value = GateState(GateStage.CHECKING)
            val result = Entitlements.check(getApplication(), email)

            if (!result.answered) {
                _scannerGate.value = GateState(GateStage.DENIED, result.message)
                return@launch
            }

            _isPremium.value = result.appAccess

            if (result.scanner) {
                _hasScanner.value = true
                _scannerGate.value = GateState(GateStage.GRANTED)
            } else {
                _scannerGate.value = GateState(
                    GateStage.DENIED,
                    "No scanner purchase found for " + email + "."
                )
            }
        }
    }

    /** The scanner sheet's unlock button. */
    fun buyScanner() {
        val email = _userEmail.value.ifEmpty { Entitlements.savedEmail(getApplication()) }
        if (email.isEmpty()) {
            _scannerGate.value = GateState(GateStage.DENIED, "Unlock the app first — the scanner is tied to the same email.")
            return
        }

        viewModelScope.launch {
            _scannerGate.value = GateState(GateStage.CHECKING)
            prefs.edit().putString("pending_payment_email", email).apply()

            val checkout = Entitlements.checkout(getApplication(), email, Entitlements.Product.SCANNER)

            _scannerGate.value = when {
                checkout == null ->
                    GateState(GateStage.DENIED, "Could not reach the payment server. Check your connection.")

                checkout.route == "SCANNER_OWNED" -> {
                    _hasScanner.value = true
                    GateState(GateStage.GRANTED)
                }

                checkout.checkoutUrl != null ->
                    GateState(GateStage.CHECKOUT_READY, checkout.message, checkout.checkoutUrl)

                else ->
                    GateState(GateStage.DENIED, checkout.message ?: "That purchase could not be started.")
            }
        }
    }

    // ── Payment return ─────────────────────────────────────────────────────

    /**
     * Called when the metahost://payment/success deep link fires.
     *
     * Payfast's ITN reaches Supabase a second or three after the browser
     * redirects, so this polls rather than reading once. It asks
     * check-subscription-status -- the same function the gate uses -- instead of
     * reading the table directly, so the device-binding rule is applied to the
     * purchase the moment it lands.
     */
    fun verifyPaymentStatus(email: String) {
        val cleaned = email.trim().lowercase()
        if (cleaned.isEmpty()) return

        viewModelScope.launch {
            _appGate.value = GateState(GateStage.VERIFYING)

            var attempt = 0
            while (attempt < 5) {
                attempt++
                if (attempt > 1) delay(2000L)

                val result = Entitlements.check(getApplication(), cleaned)
                if (!result.answered) continue

                _userEmail.value = cleaned
                _hasScanner.value = result.scanner
                _isPremium.value = result.appAccess

                // Either entitlement landing counts as the payment arriving --
                // this same return path serves the R599 and the R349 purchase.
                if (result.appAccess || result.scanner) {
                    android.util.Log.i("NovaHost", "[Payment] Verified on attempt " + attempt)
                    _appGate.value = GateState(GateStage.GRANTED)
                    _scannerGate.value = GateState(GateStage.GRANTED)
                    return@launch
                }
            }

            _appGate.value = GateState(
                GateStage.DENIED,
                "Payment received but not confirmed yet. Give it a minute, then tap Check access again."
            )
        }
    }

    /** Dismisses the gate after a grant the screen has already acted on. */
    fun unlockApp() {
        _isPremium.value = true
        _appGate.value = GateState(GateStage.IDLE)
    }

    fun resetAppGate() {
        _appGate.value = GateState(GateStage.IDLE)
    }

    fun resetScannerGate() {
        _scannerGate.value = GateState(GateStage.IDLE)
    }

    /** Forgets the cached entitlement so a different email can be tried. */
    fun useDifferentEmail() {
        Entitlements.forget(getApplication())
        _isPremium.value = false
        _hasScanner.value = false
        _userEmail.value = ""
        _appGate.value = GateState(GateStage.IDLE)
    }

    // ── Licences ───────────────────────────────────────────────────────────

    /**
     * Every licence activated on this handset, for the Connected Robots drawer.
     *
     * ## What this replaced, and why none of it could ever have worked
     *
     * The previous implementation read `licenses` straight through PostgREST,
     * filtered on `user_email`. Three independent failures, any one of which
     * returns an empty list on its own:
     *
     *  1. `licenses` has no `user_email` column -- it is `owner_email`. Naming a
     *     column that does not exist makes PostgREST answer 400, and the bare
     *     `catch { printStackTrace() }` here turned that into "no licences".
     *  2. Even spelled correctly it would return nothing. The app holds the anon
     *     key and no auth session, and RLS on `licenses` has no `anon` policy:
     *     `auth.uid()` is null, so every row is filtered out.
     *     [MetaAPIManager] already documents this and routes around it.
     *  3. The caller only ran this when `user_email` was set in preferences, and
     *     licence activation never writes that key -- only the Payfast paygate
     *     does. A user who activated with a key and never hit the paywall had no
     *     email at all, so the fetch did not fire in the first place.
     *
     * Email was never a viable key regardless: `owner_email` is null on most
     * rows in the database.
     *
     * So the lookup is by device, through an edge function, like every other
     * device-facing call here. Nothing is silent -- a failure sets
     * [licenseError] rather than being swallowed into an empty list.
     */
    fun fetchUserLicenses() {
        viewModelScope.launch {
            _licensesLoading.value = true
            try {
                val androidId = android.provider.Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ).orEmpty()

                if (androidId.isBlank()) {
                    _licenseError.value = "This device has no hardware id, so licences cannot be matched to it."
                    return@launch
                }

                val payload = json.encodeToString(
                    com.novahost.app.sdk.MyLicensesRequest.serializer(),
                    com.novahost.app.sdk.MyLicensesRequest(android_id = androidId)
                )

                val response = SupabaseSetup.client.httpClient.post(
                    "${com.novahost.app.BuildConfig.SUPABASE_URL}/functions/v1/my-licenses"
                ) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer ${com.novahost.app.BuildConfig.SUPABASE_ANON_KEY}")
                    header("apikey", com.novahost.app.BuildConfig.SUPABASE_ANON_KEY)
                    timeout { requestTimeoutMillis = 20_000 }
                    setBody(payload)
                }

                val parsed = json.decodeFromString<com.novahost.app.sdk.MyLicensesResponse>(
                    response.body<String>()
                )

                if (parsed.success) {
                    _userLicenses.value = parsed.licenses
                    _licenseError.value = null
                } else {
                    // The list is left alone on failure. A user who could see
                    // their robots a moment ago should not watch them vanish
                    // because one refresh timed out.
                    _licenseError.value = parsed.error ?: "Could not read your licences."
                }
            } catch (e: Exception) {
                android.util.Log.w("NovaHost", "[Licences] fetch failed: ${e.message}")
                _licenseError.value = "Could not reach the licence server."
            } finally {
                _licensesLoading.value = false
            }
        }
    }

    /**
     * Points the handset at a different robot it already holds a key for.
     *
     * Re-runs activation for the chosen key rather than copying the list row
     * across, for three reasons:
     *
     *  - The list row carries no art. `my-licenses` strips the base64 avatar
     *    blobs (see [com.novahost.app.sdk.RobotIdentity.has_art]); activation
     *    returns the full identity for one robot.
     *  - It re-binds the device seat, so the licence the user is now trading
     *    under is the one recorded against this handset.
     *  - It fails loudly. A key that has been suspended on the portal since the
     *    list was fetched is refused here, instead of silently re-branding the
     *    dashboard to a robot that can no longer trade.
     *
     * [onDone] receives null on success or a sentence to put in front of the
     * user on failure.
     */
    fun switchRobot(license: com.novahost.app.sdk.LicenseRecord, onDone: (String?) -> Unit) {
        val key = license.license_key
        if (key.isNullOrBlank()) {
            onDone("That robot has no usable licence key.")
            return
        }

        viewModelScope.launch {
            try {
                val androidId = android.provider.Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ).orEmpty()

                val payload = json.encodeToString(
                    com.novahost.app.sdk.LicenseActivationRequest.serializer(),
                    com.novahost.app.sdk.LicenseActivationRequest(
                        license_key = key.trim().uppercase(),
                        android_id = androidId
                    )
                )

                val response = SupabaseSetup.client.httpClient.post(
                    "${com.novahost.app.BuildConfig.SUPABASE_URL}/functions/v1/validate-license"
                ) {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header(HttpHeaders.Authorization, "Bearer ${com.novahost.app.BuildConfig.SUPABASE_ANON_KEY}")
                    header("apikey", com.novahost.app.BuildConfig.SUPABASE_ANON_KEY)
                    timeout { requestTimeoutMillis = 30_000 }
                    setBody(payload)
                }

                // Read the body whatever the status: the function returns its
                // reason ("Device mismatch", "License is inactive") with a 401
                // or 403, and that reason is exactly what the user needs.
                val result = json.decodeFromString<com.novahost.app.sdk.LicenseActivationResponse>(
                    response.body<String>()
                )

                if (!result.success) {
                    onDone(result.error ?: "That licence could not be activated.")
                    return@launch
                }

                val symbols = result.allowed_symbols?.takeIf { it.isNotEmpty() }
                    ?: result.symbols
                    ?: emptyList()

                // license_key must be written here, not just the branding. It is
                // what sync-symbol-config and metacopier-execute key off, so a
                // swap that re-brands without rewriting it leaves the dashboard
                // showing one robot while trades execute under another.
                prefs.edit().apply {
                    putString("license_key", result.license_key ?: key.trim().uppercase())
                    putString("active_ea_id", result.ea_id ?: license.ea_id ?: "")
                    putString("display_name", result.display_name ?: result.product_name ?: "TRADING BOT")
                    putString("avatar_url", result.avatar_url)
                    putString("accent_color", result.accent_color)
                    putString("background_video_url", result.background_video_url)
                    putString("tts_script", result.tts_script)
                    putString("allowed_symbols", symbols.joinToString(","))
                }.apply()

                // The stored plan reconciles itself against the new allowance on
                // read, so the push is what makes the server agree with it.
                // Enforcement, not function -- a failure here is not surfaced.
                com.novahost.app.sdk.SymbolPlanStore.sync(getApplication())

                refreshRobotIdentity()
                onDone(null)
            } catch (e: Exception) {
                android.util.Log.w("NovaHost", "[Licences] switch failed: ${e.message}")
                onDone("Could not reach the licence server.")
            }
        }
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
}
