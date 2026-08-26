package com.novaedge.app.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novaedge.app.sdk.DeviceSecurityHelper
import com.novaedge.app.sdk.SupabaseSetup
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.client.request.headers
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.novaedge.app.sdk.TerminalPrefs

enum class SubscriptionRoute { IDLE, CHECKING, NEW_USER, ACTIVE_SAME_DEVICE, ACTIVE_NEW_DEVICE, ERROR, VERIFYING_PAYMENT, PAYMENT_VERIFIED }

@Serializable
data class SubscriptionStatusRequest(
    @SerialName("email") val email: String, 
    @SerialName("android_id") val android_id: String
)

@Serializable
data class SubscriptionStatusResponse(val route: String, val checkout_url: String? = null, val error: String? = null)

@Serializable
data class SubscriptionRecord(
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("admin_display_name") val adminDisplayName: String = "SYSTEM_ADMIN",
    @SerialName("background_media_url") val backgroundMediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String? = "video",
    @SerialName("robot_name") val robotName: String = "ALGO_CORE :: QUANTUM_BREAKER"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
    
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _adminDisplayName = MutableStateFlow(prefs.getString("admin_display_name", "SYSTEM_ADMIN") ?: "SYSTEM_ADMIN")
    val adminDisplayName: StateFlow<String> = _adminDisplayName.asStateFlow()

    private val _backgroundMediaUrl = MutableStateFlow(prefs.getString("background_image_url", null))
    val backgroundMediaUrl: StateFlow<String?> = _backgroundMediaUrl.asStateFlow()

    private val _mediaType = MutableStateFlow(prefs.getString("media_type", "video"))
    val mediaType: StateFlow<String?> = _mediaType.asStateFlow()

    private val _robotName = MutableStateFlow(prefs.getString("robot_name", "ALGO_CORE :: QUANTUM_BREAKER") ?: "ALGO_CORE :: QUANTUM_BREAKER")
    val robotName: StateFlow<String> = _robotName.asStateFlow()

    private val _subscriptionRoute = MutableStateFlow(SubscriptionRoute.IDLE)
    val subscriptionRoute: StateFlow<SubscriptionRoute> = _subscriptionRoute.asStateFlow()

    private val _checkoutUrl = MutableStateFlow<String?>(null)
    val checkoutUrl: StateFlow<String?> = _checkoutUrl.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _userLicenses = MutableStateFlow<List<com.novaedge.app.sdk.LicenseRecord>>(emptyList())
    val userLicenses: StateFlow<List<com.novaedge.app.sdk.LicenseRecord>> = _userLicenses.asStateFlow()

    init {
        validateJwtToken()
    }

    private fun validateJwtToken() {
        val config = TerminalPrefs.getConfig(getApplication())
        val token = config?.token
        if (token.isNullOrBlank()) {
            _isPremium.value = false
            return
        }
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payloadStr = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(payloadStr).jsonObject
                val exp = json["exp"]?.jsonPrimitive?.content?.toLongOrNull()
                val email = json["email"]?.jsonPrimitive?.content
                if (!email.isNullOrBlank() && _adminDisplayName.value == "SYSTEM_ADMIN") {
                    _adminDisplayName.value = email.substringBefore("@")
                }
                if (exp != null) {
                    val currentUnixTime = System.currentTimeMillis() / 1000
                    if (currentUnixTime < exp) {
                        _isPremium.value = true
                        return
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPremium.value = false
    }

    fun fetchUserLicenses(email: String) {
        viewModelScope.launch {
            try {
                // Join expert_advisors so each license carries its robot's
                // identity (name, art, symbols) -- those columns live on the
                // robot, not the license.
                val results = SupabaseSetup.client.postgrest
                    .from("licenses")
                    .select(
                        io.github.jan.supabase.postgrest.query.Columns.raw(
                            "id, ea_id, status, user_email, allowed_symbols, " +
                            "expert_advisors:expert_advisors!licenses_ea_id_fkey(" +
                            "id, name, display_name, avatar_url, accent_color, " +
                            "background_video_url, tts_script, symbols)"
                        )
                    ) {
                        filter {
                            eq("user_email", email.trim().lowercase())
                        }
                    }
                    .decodeList<com.novaedge.app.sdk.LicenseRecord>()
                _userLicenses.value = results
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkSubscriptionStatus(email: String) {
        viewModelScope.launch {
            _subscriptionRoute.value = SubscriptionRoute.CHECKING
            _errorMessage.value = null
            try {
                val androidId = DeviceSecurityHelper.getDeviceId(getApplication())
                val request = SubscriptionStatusRequest(email = email.trim(), android_id = androidId)
                
                val response = SupabaseSetup.client.functions.invoke("generate-payfast-checkout") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    }
                    setBody(request)
                }.body<SubscriptionStatusResponse>()

                if (response.error != null) {
                    _errorMessage.value = response.error
                    _subscriptionRoute.value = SubscriptionRoute.ERROR
                } else {
                    _checkoutUrl.value = response.checkout_url
                    _subscriptionRoute.value = when (response.route) {
                        "NEW_USER" -> SubscriptionRoute.NEW_USER
                        "ACTIVE_SAME_DEVICE" -> SubscriptionRoute.ACTIVE_SAME_DEVICE
                        "ACTIVE_NEW_DEVICE" -> SubscriptionRoute.ACTIVE_NEW_DEVICE
                        else -> {
                            _errorMessage.value = "Unknown route."
                            SubscriptionRoute.ERROR
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = "Unable to reach payment server. Check your connection."
                _subscriptionRoute.value = SubscriptionRoute.ERROR
            }
        }
    }

    fun unlockApp() {
        _isPremium.value = true
        _subscriptionRoute.value = SubscriptionRoute.IDLE
    }

    /**
     * @description Called immediately when the metahost://payment/success deep link fires.
     * Payfast's ITN webhook takes 2-3 seconds to reach Supabase after the browser redirects.
     * This function polls the subscriptions table up to 3 times with a 2-second delay between
     * retries, giving the webhook time to land before we consider it a failure.
     */
    fun verifyPaymentStatus(email: String) {
        viewModelScope.launch {
            _subscriptionRoute.value = SubscriptionRoute.VERIFYING_PAYMENT
            val maxRetries = 3
            var attempt = 0
            var isVerified = false

            while (attempt < maxRetries && !isVerified) {
                attempt++
                try {
                    // Wait 2 seconds before each check to let the ITN arrive
                    if (attempt > 1) delay(2000L)

                    val result = SupabaseSetup.client.postgrest
                        .from("subscriptions")
                        .select {
                            filter {
                                eq("email", email.trim().lowercase())
                            }
                        }
                        .decodeSingle<SubscriptionRecord>()

                    if (result.isPremium) {
                        isVerified = true
                        _adminDisplayName.value = result.adminDisplayName
                        _backgroundMediaUrl.value = result.backgroundMediaUrl
                        _mediaType.value = result.mediaType
                        _robotName.value = result.robotName
                        
                        prefs.edit().apply {
                            putString("admin_display_name", result.adminDisplayName)
                            putString("background_image_url", result.backgroundMediaUrl)
                            putString("media_type", result.mediaType)
                            putString("robot_name", result.robotName)
                        }.apply()

                        android.util.Log.i("Nova Edge", "[Payment] Verified is_premium=true on attempt $attempt")
                    } else {
                        android.util.Log.w("Nova Edge", "[Payment] is_premium still false on attempt $attempt, retrying...")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Nova Edge", "[Payment] DB query failed on attempt $attempt: ${e.message}")
                }
            }

            if (isVerified) {
                validateJwtToken()
                _subscriptionRoute.value = SubscriptionRoute.PAYMENT_VERIFIED
            } else {
                _errorMessage.value = "Payment received but verification timed out. Please restart the app or tap Check Status again."
                _subscriptionRoute.value = SubscriptionRoute.ERROR
            }
        }
    }

    fun resetSubscriptionState() {
        _subscriptionRoute.value = SubscriptionRoute.IDLE
        _errorMessage.value = null
        _checkoutUrl.value = null
    }

    fun saveSymbolPreferences(symbol: String, lotSize: Double) {
        viewModelScope.launch {
            try {
                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotEmpty()) {
                    val data = mapOf(
                        "email" to email,
                        "symbol" to symbol,
                        "lot_size" to lotSize
                    )
                    SupabaseSetup.client.postgrest
                        .from("symbol_preferences")
                        .upsert(data)
                        
                    android.util.Log.i("Nova Edge", "Saved preferences for $symbol: Lot Size $lotSize")
                }
            } catch (e: Exception) {
                android.util.Log.e("Nova Edge", "Failed to save symbol preferences: ${e.message}")
            }
        }
    }
}

