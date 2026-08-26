package com.novahost.app.sdk

import android.content.Context
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the user has paid for, and how the app finds out.
 *
 * Two once-off products:
 *
 *   R599  app access      -> [Entitlement.appAccess]
 *   R349  AI chart scanner -> [Entitlement.scanner]
 *
 * Both are answered by the `check-subscription-status` edge function, which is
 * the only thing allowed to decide entitlement -- it also owns the one-email-
 * one-device rule and fails closed.
 *
 * Every call here goes out through the raw Ktor client with a hand-serialized
 * body rather than `functions.invoke { setBody(obj) }`. The Supabase client this
 * app builds does not install ContentNegotiation, so handing `setBody` a
 * `@Serializable` object throws before a request is ever sent -- which the user
 * sees as "check your connection" on a perfectly good network. The licence
 * activation path already learned this; this follows it.
 */
object Entitlements {

    private const val PREFS = "metahost_prefs"

    /** Last answer from the server, cached so a cold start is not a network gate. */
    private const val KEY_APP_ACCESS = "ent_app_access"
    private const val KEY_SCANNER = "ent_scanner"
    private const val KEY_EMAIL = "user_email"

    private val json = Json { ignoreUnknownKeys = true }

    // ── Model ──────────────────────────────────────────────────────────────

    /**
     * @param appAccess the R599 purchase is paid and bound to this handset.
     * @param scanner   the R349 scanner purchase is paid.
     * @param reason    machine-readable denial: no_purchase / not_paid / expired /
     *                  device_mismatch / active. Null when the call itself failed.
     * @param message   the sentence to show the user. Always populated on failure.
     */
    data class Entitlement(
        val appAccess: Boolean,
        val scanner: Boolean,
        val reason: String? = null,
        val message: String? = null
    ) {
        /** True when the server answered at all, whatever the answer was. */
        val answered: Boolean get() = reason != null
    }

    /** Which product a checkout is for. Mirrors the edge function's `product`. */
    enum class Product(val wire: String) { APP("APP"), SCANNER("SCANNER") }

    /**
     * @param checkoutUrl where to send the user to pay. Null when nothing is owed.
     * @param route       ACTIVE_SAME_DEVICE / SCANNER_OWNED mean already paid.
     */
    data class Checkout(
        val route: String,
        val checkoutUrl: String?,
        val amount: String?,
        val message: String?
    )

    // ── Wire types ─────────────────────────────────────────────────────────

    @Serializable
    private data class StatusRequest(
        @SerialName("email") val email: String,
        @SerialName("android_id") val android_id: String
    )

    @Serializable
    private data class StatusResponse(
        val success: Boolean = false,
        @SerialName("is_premium") val isPremium: Boolean = false,
        @SerialName("is_lifetime") val isLifetime: Boolean = false,
        @SerialName("has_scanner") val hasScanner: Boolean = false,
        val reason: String? = null,
        val message: String? = null,
        val error: String? = null
    )

    @Serializable
    private data class CheckoutRequest(
        @SerialName("email") val email: String,
        @SerialName("android_id") val android_id: String,
        @SerialName("product") val product: String
    )

    @Serializable
    private data class CheckoutResponse(
        val route: String? = null,
        val product: String? = null,
        val amount: String? = null,
        @SerialName("item_name") val itemName: String? = null,
        @SerialName("checkout_url") val checkoutUrl: String? = null,
        val message: String? = null,
        val error: String? = null
    )

    // ── Calls ──────────────────────────────────────────────────────────────

    /**
     * Asks the server what [email] is entitled to on this handset.
     *
     * A successful answer is cached. A failed *call* is not -- a flat network
     * must never revoke access the user has already paid for, so the caller is
     * told the call failed and the cached answer stands.
     */
    suspend fun check(context: Context, email: String): Entitlement {
        val cleaned = email.trim().lowercase()
        if (cleaned.isEmpty()) {
            return Entitlement(false, false, "no_email", "Enter the email you paid with.")
        }

        return try {
            val payload = json.encodeToString(
                StatusRequest.serializer(),
                StatusRequest(email = cleaned, android_id = DeviceSecurityHelper.getDeviceId(context))
            )

            val response = SupabaseSetup.client.httpClient.post(
                "${com.novahost.app.BuildConfig.SUPABASE_URL.removeSuffix("/")}/functions/v1/check-subscription-status"
            ) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer ${com.novahost.app.BuildConfig.SUPABASE_ANON_KEY}")
                header("apikey", com.novahost.app.BuildConfig.SUPABASE_ANON_KEY)
                setBody(payload)
            }

            // Read the body whatever the status: the function returns its reason
            // with a 4xx, and that reason is exactly what the user needs.
            val decoded = json.decodeFromString<StatusResponse>(response.body<String>())

            if (!decoded.success) {
                return Entitlement(
                    appAccess = false,
                    scanner = false,
                    reason = "error",
                    message = decoded.error ?: "Could not check your access. Try again."
                )
            }

            val result = Entitlement(
                appAccess = decoded.isPremium,
                scanner = decoded.hasScanner,
                reason = decoded.reason ?: "active",
                message = decoded.message
            )

            persist(context, cleaned, result)
            result

        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "Entitlement check failed", e)
            // Call failed, not "user is unpaid". Reason stays null so the caller
            // can tell the difference and keep whatever it had cached.
            Entitlement(
                appAccess = false,
                scanner = false,
                reason = null,
                message = "Could not reach the server. Check your connection and try again."
            )
        }
    }

    /**
     * Builds a Payfast checkout for [product].
     *
     * Returns null only when the call itself failed; a server that says the user
     * already owns the product comes back as a [Checkout] with a null URL and a
     * route of ACTIVE_SAME_DEVICE or SCANNER_OWNED.
     */
    suspend fun checkout(context: Context, email: String, product: Product): Checkout? {
        val cleaned = email.trim().lowercase()
        if (cleaned.isEmpty()) return null

        return try {
            val payload = json.encodeToString(
                CheckoutRequest.serializer(),
                CheckoutRequest(
                    email = cleaned,
                    android_id = DeviceSecurityHelper.getDeviceId(context),
                    product = product.wire
                )
            )

            val response = SupabaseSetup.client.httpClient.post(
                "${com.novahost.app.BuildConfig.SUPABASE_URL.removeSuffix("/")}/functions/v1/generate-payfast-checkout"
            ) {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer ${com.novahost.app.BuildConfig.SUPABASE_ANON_KEY}")
                header("apikey", com.novahost.app.BuildConfig.SUPABASE_ANON_KEY)
                setBody(payload)
            }

            val decoded = json.decodeFromString<CheckoutResponse>(response.body<String>())

            if (decoded.error != null) {
                return Checkout(route = "ERROR", checkoutUrl = null, amount = null, message = decoded.error)
            }

            Checkout(
                route = decoded.route ?: "ERROR",
                checkoutUrl = decoded.checkoutUrl,
                amount = decoded.amount,
                message = decoded.message
            )

        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "Checkout build failed", e)
            null
        }
    }

    // ── Local cache ────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persist(context: Context, email: String, entitlement: Entitlement) {
        prefs(context).edit().apply {
            putString(KEY_EMAIL, email)
            putBoolean(KEY_APP_ACCESS, entitlement.appAccess)
            putBoolean(KEY_SCANNER, entitlement.scanner)
        }.apply()
    }

    /** The last answer the server gave, for use before the first call of a session. */
    fun cached(context: Context): Entitlement = with(prefs(context)) {
        Entitlement(
            appAccess = getBoolean(KEY_APP_ACCESS, false),
            scanner = getBoolean(KEY_SCANNER, false),
            reason = null,
            message = null
        )
    }

    /** The email the last successful check was made against. Empty if never checked. */
    fun savedEmail(context: Context): String =
        prefs(context).getString(KEY_EMAIL, "").orEmpty()

    /**
     * Grants access locally after a payment this device just completed.
     *
     * Used by the deep-link return so the user is not held at the gate while the
     * Payfast ITN lands. The next [check] re-reads the truth from the server.
     */
    fun grantLocally(context: Context, email: String, appAccess: Boolean, scanner: Boolean) {
        val current = cached(context)
        persist(
            context,
            email.trim().lowercase(),
            Entitlement(
                appAccess = current.appAccess || appAccess,
                scanner = current.scanner || scanner,
                reason = "active",
                message = null
            )
        )
    }

    /** Clears the cache. Used when the user switches to a different email. */
    fun forget(context: Context) {
        prefs(context).edit()
            .remove(KEY_APP_ACCESS)
            .remove(KEY_SCANNER)
            .apply()
    }
}
