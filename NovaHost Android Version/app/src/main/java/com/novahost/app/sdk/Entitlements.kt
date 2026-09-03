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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the user has paid for, and how the app finds out.
 *
 * Two once-off products, plus a paid move between handsets:
 *
 *   R599  app access       -> [Entitlement.appAccess]
 *   R349  AI chart scanner -> [Entitlement.scanner]
 *   R150  device move      -> [requestMove] then [checkout] with the emailed code
 *
 * All of it is answered by the `check-subscription-status` edge function, which
 * is the only thing allowed to decide entitlement -- it also owns the one-email-
 * one-device rule and fails closed.
 *
 * Every call here goes out through the raw Ktor client with a hand-serialized
 * body rather than `functions.invoke { setBody(obj) }`. The NovaHost backend client this
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

    /** The device session the server issued. Sent back on every check. */
    private const val KEY_TOKEN = "ent_session_token"

    /** When the server last answered, and how long that answer is good for. */
    private const val KEY_CHECKED_AT = "ent_checked_at"
    private const val KEY_MAX_OFFLINE_DAYS = "ent_max_offline_days"

    /** Used only until the first server answer arrives. */
    private const val DEFAULT_MAX_OFFLINE_DAYS = 5

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private val json = Json { ignoreUnknownKeys = true }

    // ── Model ──────────────────────────────────────────────────────────────

    /**
     * @param appAccess the R599 purchase is paid and bound to this handset.
     * @param scanner   the R349 scanner purchase is paid.
     * @param reason    machine-readable denial: no_purchase / not_paid / expired /
     *                  device_mismatch / stale / active. Null when the call itself failed.
     * @param message   the sentence to show the user. Always populated on failure.
     * @param move      when [reason] is device_mismatch, whether a R150 move is
     *                  available and when. Null otherwise.
     */
    data class Entitlement(
        val appAccess: Boolean,
        val scanner: Boolean,
        val reason: String? = null,
        val message: String? = null,
        val move: MoveStatus? = null
    ) {
        /** True when the server answered at all, whatever the answer was. */
        val answered: Boolean get() = reason != null
    }

    /**
     * Whether the licence on this email can be moved here, and when.
     *
     * @param reason ok / cooldown / limit_reached.
     */
    data class MoveStatus(
        val eligible: Boolean,
        val reason: String,
        val availableAt: String?,
        val movesUsed: Int,
        val movesAllowed: Int
    ) {
        /** "3 October", for putting a date in front of the user. */
        val availableOnLabel: String?
            get() = availableAt?.let {
                runCatching {
                    DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.parse(it))
                }.getOrNull()
            }
    }

    /** Which product a checkout is for. Mirrors the edge function's `product`. */
    enum class Product(val wire: String) {
        APP("APP"),
        SCANNER("SCANNER"),
        REACTIVATE("REACTIVATE")
    }

    /**
     * @param checkoutUrl where to send the user to pay. Null when nothing is owed.
     * @param route       ACTIVE_SAME_DEVICE / SCANNER_OWNED mean already paid.
     *                    MOVE_CODE_REQUIRED / MOVE_CODE_INVALID / MOVE_COOLDOWN /
     *                    MOVE_LIMIT_REACHED are the device-move refusals.
     */
    data class Checkout(
        val route: String,
        val checkoutUrl: String?,
        val amount: String?,
        val message: String?,
        val move: MoveStatus? = null
    )

    /** The result of asking for a move code by email. */
    data class MoveRequest(
        val sent: Boolean,
        val message: String?,
        val code: String?
    )

    // ── Wire types ─────────────────────────────────────────────────────────

    @Serializable
    private data class MoveWire(
        val eligible: Boolean = false,
        val reason: String? = null,
        @SerialName("available_at") val availableAt: String? = null,
        @SerialName("moves_used") val movesUsed: Int = 0,
        @SerialName("moves_allowed") val movesAllowed: Int = 2
    ) {
        fun toStatus() = MoveStatus(
            eligible = eligible,
            reason = reason ?: "ok",
            availableAt = availableAt,
            movesUsed = movesUsed,
            movesAllowed = movesAllowed
        )
    }

    @Serializable
    private data class StatusRequest(
        @SerialName("email") val email: String,
        @SerialName("android_id") val android_id: String,
        @SerialName("token") val token: String? = null
    )

    @Serializable
    private data class StatusResponse(
        val success: Boolean = false,
        @SerialName("is_premium") val isPremium: Boolean = false,
        @SerialName("is_lifetime") val isLifetime: Boolean = false,
        @SerialName("has_scanner") val hasScanner: Boolean = false,
        val reason: String? = null,
        val message: String? = null,
        val error: String? = null,
        val token: String? = null,
        @SerialName("max_offline_days") val maxOfflineDays: Int? = null,
        val move: MoveWire? = null
    )

    @Serializable
    private data class CheckoutRequest(
        @SerialName("email") val email: String,
        @SerialName("android_id") val android_id: String,
        @SerialName("product") val product: String,
        @SerialName("move_code") val move_code: String? = null
    )

    @Serializable
    private data class CheckoutResponse(
        val route: String? = null,
        val product: String? = null,
        val amount: String? = null,
        @SerialName("item_name") val itemName: String? = null,
        @SerialName("checkout_url") val checkoutUrl: String? = null,
        val message: String? = null,
        val error: String? = null,
        val move: MoveWire? = null
    )

    @Serializable
    private data class MoveCodeRequest(
        @SerialName("email") val email: String,
        @SerialName("android_id") val android_id: String
    )

    @Serializable
    private data class MoveCodeResponse(
        val success: Boolean = false,
        val sent: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        val code: String? = null
    )

    // ── Calls ──────────────────────────────────────────────────────────────

    private fun functionUrl(name: String): String =
        "${com.novahost.app.BuildConfig.NOVAHOST_API_URL.removeSuffix("/")}/functions/v1/$name"

    private fun io.ktor.client.request.HttpRequestBuilder.novaHeaders() {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        header(HttpHeaders.Authorization, "Bearer ${com.novahost.app.BuildConfig.NOVAHOST_API_KEY}")
        header("apikey", com.novahost.app.BuildConfig.NOVAHOST_API_KEY)
    }

    /**
     * Asks the server what [email] is entitled to on this handset.
     *
     * A successful answer is cached along with the moment it arrived. A failed
     * *call* is not -- a flat network must never revoke access the user has
     * already paid for on the spot, so the caller is told the call failed and
     * the cached answer stands until it goes stale. See [cached].
     */
    suspend fun check(context: Context, email: String): Entitlement {
        val cleaned = email.trim().lowercase()
        if (cleaned.isEmpty()) {
            return Entitlement(false, false, "no_email", "Enter the email you paid with.")
        }

        return try {
            val payload = json.encodeToString(
                StatusRequest.serializer(),
                StatusRequest(
                    email = cleaned,
                    android_id = DeviceSecurityHelper.getDeviceId(context),
                    token = storedToken(context)
                )
            )

            val response = NovaHostBackend.client.httpClient.post(functionUrl("check-subscription-status")) {
                novaHeaders()
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
                message = decoded.message,
                move = decoded.move?.toStatus()
            )

            persist(context, cleaned, result, decoded.token, decoded.maxOfflineDays)
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
     * Asks the server to email a six-digit move code to [email].
     *
     * Step one of moving a paid licence onto this handset. The code proves
     * whoever is paying can read the mailbox the licence belongs to -- without
     * it, R150 would buy the eviction of a stranger.
     */
    suspend fun requestMove(context: Context, email: String): MoveRequest {
        val cleaned = email.trim().lowercase()
        if (cleaned.isEmpty()) {
            return MoveRequest(false, "Enter the email you paid with.", null)
        }

        return try {
            val payload = json.encodeToString(
                MoveCodeRequest.serializer(),
                MoveCodeRequest(
                    email = cleaned,
                    android_id = DeviceSecurityHelper.getDeviceId(context)
                )
            )

            val response = NovaHostBackend.client.httpClient.post(functionUrl("request-device-move")) {
                novaHeaders()
                setBody(payload)
            }

            val decoded = json.decodeFromString<MoveCodeResponse>(response.body<String>())

            MoveRequest(
                sent = decoded.success && decoded.sent,
                message = decoded.message ?: decoded.error,
                code = decoded.code
            )

        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "Move code request failed", e)
            MoveRequest(false, "Could not reach the server. Check your connection and try again.", null)
        }
    }

    /**
     * Builds a Payfast checkout for [product].
     *
     * Returns null only when the call itself failed; a server that says the user
     * already owns the product comes back as a [Checkout] with a null URL and a
     * route of ACTIVE_SAME_DEVICE or SCANNER_OWNED. A move that is refused comes
     * back the same way with a MOVE_* route -- those are answers, not errors,
     * and each needs its own sentence in front of the user.
     *
     * @param moveCode the six digits from [requestMove]. Only a REACTIVATE needs one.
     */
    suspend fun checkout(
        context: Context,
        email: String,
        product: Product,
        moveCode: String? = null
    ): Checkout? {
        val cleaned = email.trim().lowercase()
        if (cleaned.isEmpty()) return null

        return try {
            val payload = json.encodeToString(
                CheckoutRequest.serializer(),
                CheckoutRequest(
                    email = cleaned,
                    android_id = DeviceSecurityHelper.getDeviceId(context),
                    product = product.wire,
                    move_code = moveCode?.trim()?.takeIf { it.isNotEmpty() }
                )
            )

            val response = NovaHostBackend.client.httpClient.post(functionUrl("generate-payfast-checkout")) {
                novaHeaders()
                setBody(payload)
            }

            val decoded = json.decodeFromString<CheckoutResponse>(response.body<String>())

            if (decoded.error != null) {
                return Checkout("ERROR", null, null, decoded.error)
            }

            Checkout(
                route = decoded.route ?: "ERROR",
                checkoutUrl = decoded.checkoutUrl,
                amount = decoded.amount,
                message = decoded.message,
                move = decoded.move?.toStatus()
            )

        } catch (e: Exception) {
            android.util.Log.e("NovaHost", "Checkout build failed", e)
            null
        }
    }

    // ── Local cache ────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storedToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun persist(
        context: Context,
        email: String,
        entitlement: Entitlement,
        token: String?,
        maxOfflineDays: Int?
    ) {
        prefs(context).edit().apply {
            putString(KEY_EMAIL, email)
            putBoolean(KEY_APP_ACCESS, entitlement.appAccess)
            putBoolean(KEY_SCANNER, entitlement.scanner)
            putLong(KEY_CHECKED_AT, System.currentTimeMillis())
            if (token != null) putString(KEY_TOKEN, token)
            if (maxOfflineDays != null) putInt(KEY_MAX_OFFLINE_DAYS, maxOfflineDays)
        }.apply()
    }

    /**
     * The last answer the server gave, for use before the first call of a session.
     *
     * **This is the offline lock, and it is the whole reason the device rule is
     * worth anything.** Without it, the exploit is: buy the app on phone A, pay
     * R150 to move the licence to phone B, then keep phone A permanently offline.
     * A never learns it was evicted, keeps `ent_app_access = true` forever, and
     * two handsets run on one R599 plus one R150.
     *
     * So a cached answer expires. Past `max_offline_days` -- which the server
     * sends, so the window can be changed without shipping an APK -- this
     * reports no access with reason "stale", and the gate comes up until a real
     * server answer arrives. A paying customer who opens the app online even
     * once a week never sees it.
     *
     * A cache with no timestamp at all is treated as stale rather than valid.
     * That fails closed: an install that has never had a server answer has not
     * established anything worth trusting.
     *
     * The clock is checked for going backwards, too. The elapsed-time comparison
     * is against the device's own clock, so winding the date back would
     * otherwise make a stale cache look fresh again -- cheaper than staying
     * online, and the one obvious way around this. A timestamp in the future
     * means the clock moved, and a cache that cannot be aged is not trusted.
     */
    fun cached(context: Context): Entitlement = with(prefs(context)) {
        val appAccess = getBoolean(KEY_APP_ACCESS, false)
        val scanner = getBoolean(KEY_SCANNER, false)

        if (!appAccess && !scanner) {
            return Entitlement(appAccess = false, scanner = false)
        }

        val checkedAt = getLong(KEY_CHECKED_AT, 0L)
        val maxDays = getInt(KEY_MAX_OFFLINE_DAYS, DEFAULT_MAX_OFFLINE_DAYS)
        val now = System.currentTimeMillis()

        val neverChecked = checkedAt <= 0L
        val clockWentBackwards = now < checkedAt
        val tooOld = now - checkedAt > maxDays * DAY_MS

        if (neverChecked || clockWentBackwards || tooOld) {
            return Entitlement(
                appAccess = false,
                scanner = false,
                reason = "stale",
                message = "Connect to the internet to confirm your access."
            )
        }

        Entitlement(appAccess = appAccess, scanner = scanner)
    }

    /** True when a cached answer has aged out and only the server can help. */
    fun isStale(context: Context): Boolean = cached(context).reason == "stale"

    /** The email the last successful check was made against. Empty if never checked. */
    fun savedEmail(context: Context): String =
        prefs(context).getString(KEY_EMAIL, "").orEmpty()

    /**
     * Grants access locally after a payment this device just completed.
     *
     * Used by the deep-link return so the user is not held at the gate while the
     * Payfast ITN lands. The next [check] re-reads the truth from the server.
     *
     * Stamps the check time as well as the flags. Without that the grant would
     * be born stale and the gate would reappear on the next launch, which is a
     * miserable thing to do to somebody who has just paid.
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
            ),
            token = null,
            maxOfflineDays = null
        )
    }

    /**
     * Clears the cache. Used when the user switches to a different email.
     *
     * The session token goes too. It belongs to the email that was signed in,
     * and presenting one account's session while asking about another is a
     * question with no sensible answer.
     */
    fun forget(context: Context) {
        prefs(context).edit()
            .remove(KEY_APP_ACCESS)
            .remove(KEY_SCANNER)
            .remove(KEY_TOKEN)
            .remove(KEY_CHECKED_AT)
            .apply()
    }
}
