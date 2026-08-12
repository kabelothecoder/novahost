package com.novaedge.app.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ChartRequest(
    val imageUrl: String
)

@Serializable
data class ChartAnalysisRequest(
    val imagePath: String? = null, // Supabase Storage Path
    val pair: String? = null
)

@Serializable
data class BrokerConnectionRequest(
    @SerialName("account_id") val account_id: String,
    @SerialName("password") val password: String,
    @SerialName("server") val server: String,
    @SerialName("platform") val platform: String = "mt5",
    @SerialName("license_key") val license_key: String,
    @SerialName("account_type") val account_type: String = "Standard",
    @SerialName("symbol_suffix") val symbol_suffix: String = ""
)

enum class BotStatus { IDLE, RUNNING, STOPPED }

@Serializable
data class LicenseValidationRequest(
    @SerialName("licenseKey") val licenseKey: String,
    @SerialName("deviceId") val deviceId: String
)

/**
 * Request shape validate-license expects from an Android client. Sending
 * `license_key` / `android_id` is what selects the Android response branch and
 * triggers device locking.
 */
@Serializable
data class LicenseActivationRequest(
    val license_key: String,
    val android_id: String
)

/** Android branch response from validate-license. */
@Serializable
data class LicenseActivationResponse(
    val success: Boolean = false,
    val error: String? = null,
    val ea_id: String? = null,
    val product_name: String? = null,
    val display_name: String? = null,
    val product_code: String? = null,
    val avatar_url: String? = null,
    val background_video_url: String? = null,
    val accent_color: String? = null,
    val symbols: List<String>? = emptyList(),
    val tts_script: String? = null,
    val allowed_symbols: List<String>? = emptyList()
)

@Serializable
data class ChartAnalysisResponse(
    val pair: String? = null,
    val action: String? = null,
    val entry: String? = null,
    val sl: String? = null,
    val tp: String? = null,
    val confidence: String? = null,
    val patterns: List<String> = emptyList(),
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class DispatchSignalRequest(
    val pair: String,
    val action: String,
    val entry: Double,
    val sl: Double,
    val tp: Double,
    val terminal: TerminalConfig
)

@Serializable
data class TerminalConfig(
    val server: String,
    val account: String,
    val token: String
)

@Serializable
data class DispatchSignalResponse(
    val success: Boolean,
    val signalId: String? = null,
    val error: String? = null
)

@Serializable
data class TradeSignal(
    val id: String? = null,
    // Which robot this signal belongs to. Every device must ignore signals for
    // robots its license isn't tied to, otherwise one mentor's calls execute on
    // another mentor's subscribers.
    val ea_id: String? = null,
    val pair: String,
    // Direction lives in `side` on the signals table. `type` is the legacy
    // column kept for back-compat. There is NO `action` column -- declaring one
    // as a required field made every decodeRecord<TradeSignal>() throw, which is
    // why no signal ever reached execution.
    val side: String? = null,
    val type: String? = null,
    val price: Double? = null,
    val entry: Double? = null,
    val sl: Double? = null,
    val tp: Double? = null,
    val adminBalance: Double? = null,
    val lot: Double? = 0.01,
    val status: String? = null,
    val signal_id: String? = null,
    val created_at: String? = null
) {
    /** Normalized direction, tolerating either column. */
    val action: String get() = (side ?: type ?: "BUY").uppercase()
}

/**
 * A robot's presentation identity, carried from expert_advisors via the license
 * key. This is what makes the app feel like the robot -- not the mentor.
 */
@Serializable
data class RobotIdentity(
    val id: String,
    val name: String? = null,
    val display_name: String? = null,
    val avatar_url: String? = null,
    val accent_color: String? = null,
    val background_video_url: String? = null,
    val tts_script: String? = null,
    val symbols: List<String>? = emptyList()
)

@Serializable
data class TradeExecutionRequest(
    val pair: String,
    val type: String,
    val volume: Double,
    val sl: Double? = 0.0,
    val tp: Double? = 0.0,
    val terminal: TerminalConfig,
    val license_key: String
)

/**
 * Request for metacopier-execute.
 *
 * Note what is NOT here: broker credentials and the MetaCopier account id. The
 * account is resolved server-side from the licence, so a tampered client cannot
 * aim a trade at somebody else's trading account.
 */
@Serializable
data class MetaCopierTradeRequest(
    val license_key: String,
    val pair: String,
    val side: String,
    val volume: Double,
    val sl: Double? = 0.0,
    val tp: Double? = 0.0,
    val signal_id: String? = null
)

/** Request for metacopier-connect (registers an MT4/MT5 account). */
@Serializable
data class MetaCopierConnectRequest(
    val license_key: String,
    val account_number: String,
    val password: String,
    val server: String,
    val platform: String = "MT5",
    val region_name: String? = null
)

/** Response from metacopier-connect. */
@Serializable
data class MetaCopierConnectResponse(
    val success: Boolean = false,
    val account_id: String? = null,
    val platform: String? = null,
    val region: String? = null,
    val message: String? = null,
    val error: String? = null,
    val details: String? = null
)

/** Response from metacopier-execute. */
@Serializable
data class MetaCopierTradeResponse(
    val success: Boolean = false,
    val message: String? = null,
    val requestId: Long? = null,
    val error: String? = null,
    val details: String? = null
)

@Serializable
data class LicenseRecord(
    val id: String,
    // The robot this key unlocks. Needed so the device can filter incoming
    // signals down to its own robot.
    val ea_id: String? = null,
    val status: String? = null,
    val user_email: String? = null,
    val allowed_symbols: List<String>? = emptyList(),
    // Joined from expert_advisors. display_name/avatar/tts/symbols are columns
    // on THAT table, not on licenses -- reading them off the license row always
    // yielded null, which is why robots rendered as "TRADING BOT" with no art.
    @SerialName("expert_advisors") val robot: RobotIdentity? = null
) {
    val display_name: String? get() = robot?.display_name ?: robot?.name
    val avatar_url: String? get() = robot?.avatar_url
    val accent_color: String? get() = robot?.accent_color
    val tts_script: String? get() = robot?.tts_script
    /** Symbols the license restricts to, else the robot's full symbol set. */
    val symbols: List<String>? get() =
        allowed_symbols?.takeIf { it.isNotEmpty() } ?: robot?.symbols
}

@Serializable
data class BrokerConnectionResponse(
    val success: Boolean,
    val token: String? = null,
    val error: String? = null
)
