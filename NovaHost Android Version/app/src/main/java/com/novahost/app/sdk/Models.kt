package com.novahost.app.sdk

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ChartRequest(
    val imageUrl: String
)

@Serializable
data class ChartAnalysisRequest(
    val imagePath: String? = null, // NovaHost storage path
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
    /**
     * The canonical, upper-cased key as the server recorded it.
     *
     * Stored in preference to whatever the user typed: every device-facing
     * function upper-cases before looking a key up, so a locally-cached lower
     * case copy would mismatch the row it came from.
     */
    val license_key: String? = null,
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
    /**
     * MARKET / LIMIT / STOP, as claim-signals and the realtime broadcast now
     * carry it.
     *
     * Null on a payload from an older server build, and null must mean MARKET:
     * that is how every mentor signal behaved before the portal could express a
     * level, and treating a missing field as "unknown" would strand the signal.
     */
    val order_type: String? = null,
    /** The price a pending order waits at. Null for a market order. */
    val open_price: Double? = null,
    /** Seconds until the broker cancels an unfilled pending order. Null = GTC. */
    val pending_expiry_seconds: Int? = null,
    val status: String? = null,
    val signal_id: String? = null,
    val created_at: String? = null
) {
    /** Normalized direction, tolerating either column. */
    val action: String get() = (side ?: type ?: "BUY").uppercase()
}

/**
 * What `claim-signals` handed this device.
 *
 * The signals here have been claimed server-side and belong to this licence
 * alone -- no other handset on the same key will be given them, and a repeat
 * poll will not return them again. Acting on every entry exactly once is
 * therefore both safe and required: anything dropped on the floor here is
 * dropped permanently.
 */
@Serializable
data class ClaimSignalsResponse(
    val success: Boolean = false,
    val code: String? = null,
    val error: String? = null,
    val signals: List<TradeSignal> = emptyList(),
    /**
     * Claimed but too old to trade, and now retired.
     *
     * A count rather than a list because there is nothing to do with them -- it
     * exists so the terminal feed can say the robot was offline and missed
     * something, instead of a gap the user has no way to notice.
     */
    val stale: Int = 0,
    val window_seconds: Int = 300,
    /**
     * The robot's current symbol allowance, as the server sees it right now.
     *
     * Null on an older server build, which must be treated as "no opinion" and
     * never as "the robot allows nothing" -- clearing a device's allowance
     * because a response lacked a field would empty the Trading Symbols screen
     * and stop every trade.
     */
    val allowed_symbols: List<String>? = null
)

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
    val symbols: List<String>? = emptyList(),
    /**
     * True when the robot has art that was too large to inline in a list row.
     *
     * Mentors upload avatars through the portal and they land in
     * `expert_advisors.avatar_url` as base64 data URIs of 216KB to 3.0MB. A
     * four-robot drawer was an 8.8MB response re-fetched on every resume, so
     * `my-licenses` drops data URIs and sets this instead. The row draws the
     * placeholder; the full art arrives when the robot is actually selected.
     */
    val has_art: Boolean = false
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
    val signal_id: String? = null,
    /**
     * MARKET / LIMIT / STOP. Absent means MARKET, which is how every mentor
     * signal has always behaved.
     *
     * The scanner sets it because a chart entry is rarely where price is
     * standing: "buy the retest at 1.0850" with price at 1.0880 is an order that
     * waits below, and sending it as a market order fills 30 pips away from the
     * level the whole plan was built on.
     */
    val order_type: String? = null,
    /**
     * The price a pending order waits at. Ignored for MARKET.
     *
     * Sent alongside [order_type] rather than folded into it because MetaCopier
     * needs both: a pending `orderType` without an `openPrice` is rejected, and
     * an `openPrice` with a market `orderType` silently makes it pending.
     */
    val open_price: Double? = null,
    /**
     * Seconds until the broker cancels an unfilled pending order.
     *
     * Null or zero means no expiry, which is right for a market order and is
     * the default for a mentor signal. A mentor sending a limit or stop from
     * Normal Trade may now set one, in which case the broker cancels the order
     * if it never fills.
     */
    val pending_expiry_seconds: Int? = null,
    /**
     * Runs the whole executor -- licence, symbol plan, sizing, bracket checks,
     * broker symbol resolution -- and stops one step short of placing the order.
     *
     * The pipeline had no way to be proved without a live position being the
     * test instrument, so nobody proved it, and a break between the mentor
     * portal and the broker looked exactly like a quiet market.
     */
    val dry_run: Boolean = false
)

/**
 * Request for license-status — "is this licence usable, and is an account on it".
 *
 * The app cannot answer this from PostgREST: RLS on `licenses` excludes anon,
 * and the anon key is the only credential a licence-key install has.
 */
@Serializable
data class LicenseStatusRequest(
    val license_key: String
)

/** Response from license-status. Carries no MetaCopier account id by design. */
@Serializable
data class LicenseStatusResponse(
    val success: Boolean = false,
    val active: Boolean = false,
    val linked: Boolean = false,
    val ea_id: String? = null,
    val allowed_symbols: List<String>? = null,
    val broker_server: String? = null,
    val platform: String? = null,
    val connected_at: String? = null,
    /**
     * What `metacopier-connect`'s verify step last recorded: `"connected"` once
     * the broker session is live, `"connecting"` while it is still coming up,
     * null on links made before this was tracked. Null is treated as
     * "assume connected" -- the behaviour that predates it.
     */
    val metacopier_status: String? = null,
    val reason: String? = null,
    val message: String? = null,
    val error: String? = null
)

/**
 * Request for `my-licenses` — "which keys live on this handset".
 *
 * The device is the key, not an email. A licence-key install never supplies an
 * email, `licenses.owner_email` is null on most rows, and there is no auth
 * session for RLS to key off. The android id is the same identity
 * `validate-license` already binds a key against.
 */
@Serializable
data class MyLicensesRequest(
    val android_id: String
)

/** Response from `my-licenses`. */
@Serializable
data class MyLicensesResponse(
    val success: Boolean = false,
    val licenses: List<LicenseRecord> = emptyList(),
    val error: String? = null
)

/** Request for metacopier-connect (registers an MT4/MT5 account). */
@Serializable
data class MetaCopierConnectRequest(
    val license_key: String,
    val account_number: String,
    val password: String,
    val server: String,
    val platform: String = "MT5",
    val region_name: String? = null,
    /**
     * What this broker appends to instrument names -- `.m` on a micro book,
     * `.pro` on a raw-spread one, empty on a standard account.
     *
     * Recorded against the licence at connect time because that is the only
     * moment the app knows which account it is talking about. `metacopier-execute`
     * then decorates every canonical symbol with it: without this, a micro
     * account is sent orders for `XAUUSD` when its book only lists `XAUUSD.m`,
     * and the broker rejects every one.
     *
     * The screen already derived this and passed it to
     * [MetaAPIManager.testBrokerConnection], which dropped it on the floor.
     */
    val symbol_suffix: String = "",
    /** The user's own words for the account type, e.g. "Micro". Kept for support. */
    val account_type: String? = null
)

/** Response from metacopier-connect. */
@Serializable
data class MetaCopierConnectResponse(
    val success: Boolean = false,
    /**
     * Machine-readable outcome, e.g. `WRONG_CREDENTIALS`, `LICENCE_UNKNOWN`,
     * `PROVIDER_UNFUNDED`, `CONNECTED`.
     *
     * [error] is written for the user and will be reworded; this will not. Branch
     * on this, never on the prose.
     */
    val code: String? = null,
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
    /**
     * Machine-readable outcome: `EXECUTED`, `DRY_RUN`, `NO_ACCOUNT_LINKED`,
     * `SYMBOL_DISABLED`, `POSITION_CAP`, `INVALID_STOPS`, `UNKNOWN_SYMBOL`,
     * `INSUFFICIENT_MARGIN`, `TRADING_PROHIBITED`, `ACCOUNT_DISCONNECTED`.
     *
     * [error] is written for the user and will be reworded; this will not.
     * Branch on this, never on the prose.
     */
    val code: String? = null,
    val message: String? = null,
    val requestId: Long? = null,
    val error: String? = null,
    val details: String? = null
)

@Serializable
data class LicenseRecord(
    val id: String,
    /**
     * The key itself. Carried so the drawer can mark the active robot by the
     * thing that actually drives execution.
     *
     * Matching on display name instead (as the picker used to) breaks the
     * moment a user holds two keys for one robot: `expert_advisors` is joined
     * through `ea_id`, so both rows carry the identical name and both light up
     * as active. Two keys for one robot is the ordinary case here, not an edge
     * one -- most accounts in the database look exactly like that.
     */
    val license_key: String? = null,
    // The robot this key unlocks. Needed so the device can filter incoming
    // signals down to its own robot.
    val ea_id: String? = null,
    val status: String? = null,
    /**
     * The licence owner's email.
     *
     * This was `user_email`, a column that has never existed on `licenses`.
     * PostgREST answered 400, the caller's bare catch turned that into an empty
     * list, and the Connected Robots drawer reported "no active licenses found"
     * for accounts holding half a dozen keys.
     */
    val owner_email: String? = null,
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
