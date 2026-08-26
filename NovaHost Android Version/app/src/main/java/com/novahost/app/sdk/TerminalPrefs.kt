package com.novaedge.app.sdk

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedBotProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val backgroundImageUrl: String?,
    val symbols: List<String>
)

object TerminalPrefs {
    private const val PREFS_NAME = "secure_terminal_prefs"
    private const val KEY_SERVER = "terminal_server"
    private const val KEY_ACCOUNT = "terminal_account"
    private const val KEY_TOKEN = "terminal_token"
    private const val KEY_VALIDATED_BOTS = "validated_bots_list"
    private const val KEY_GLOW_MODE = "user_glow_mode"
    private const val KEY_SEC_BG_COLOR = "secondary_bg_color"
    private const val KEY_THEME_COLOR = "user_theme_color"
    private const val KEY_IMMERSIVE_MODE = "immersive_mode"
    private const val KEY_ROBOT_FONT = "robot_font_style"
    private const val KEY_HOME_BUTTON_SHAPE = "home_button_shape"
    private const val KEY_TRADE_CALCULATOR_ENABLED = "trade_calculator_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveConfig(context: Context, config: TerminalConfig) {
        getPrefs(context).edit().apply {
            putString(KEY_SERVER, config.server)
            putString(KEY_ACCOUNT, config.account)
            putString(KEY_TOKEN, config.token)
            apply()
        }
    }

    fun getConfig(context: Context): TerminalConfig? {
        val prefs = getPrefs(context)
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val account = prefs.getString(KEY_ACCOUNT, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return TerminalConfig(server, account, token)
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun saveBotProfile(context: Context, profile: SavedBotProfile) {
        val prefs = getPrefs(context)
        val currentListJson = prefs.getString(KEY_VALIDATED_BOTS, "[]") ?: "[]"
        val currentList = try {
            Json.decodeFromString<List<SavedBotProfile>>(currentListJson)
        } catch (e: Exception) {
            emptyList()
        }
        val newList = currentList.filter { it.id != profile.id } + profile
        prefs.edit().putString(KEY_VALIDATED_BOTS, Json.encodeToString(newList)).apply()
    }

    fun getSavedBotProfiles(context: Context): List<SavedBotProfile> {
        val prefs = getPrefs(context)
        val currentListJson = prefs.getString(KEY_VALIDATED_BOTS, "[]") ?: "[]"
        return try {
            Json.decodeFromString<List<SavedBotProfile>>(currentListJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getGlowMode(context: Context): String {
        return getPrefs(context).getString(KEY_GLOW_MODE, "MEDIUM") ?: "MEDIUM"
    }

    fun setGlowMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_GLOW_MODE, mode).apply()
    }

    fun getSecondaryBgColor(context: Context): Long? {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_SEC_BG_COLOR)) prefs.getLong(KEY_SEC_BG_COLOR, 0L) else null
    }

    fun setSecondaryBgColor(context: Context, colorValue: Long) {
        getPrefs(context).edit().putLong(KEY_SEC_BG_COLOR, colorValue).apply()
    }

    fun getThemeColorValue(context: Context): Long? {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_THEME_COLOR)) prefs.getLong(KEY_THEME_COLOR, 0L) else null
    }

    fun setThemeColorValue(context: Context, colorValue: Long) {
        getPrefs(context).edit().putLong(KEY_THEME_COLOR, colorValue).apply()
    }

    /** @description Persist the immersive (card-free) mode toggle. */
    fun getImmersiveMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IMMERSIVE_MODE, false)
    }

    fun setImmersiveMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IMMERSIVE_MODE, enabled).apply()
    }

    /** @description Persist the user's chosen robot name font style. */
    fun getRobotFontStyle(context: Context): String {
        return getPrefs(context).getString(KEY_ROBOT_FONT, "DEFAULT_SYSTEM") ?: "DEFAULT_SYSTEM"
    }

    fun setRobotFontStyle(context: Context, style: String) {
        getPrefs(context).edit().putString(KEY_ROBOT_FONT, style).apply()
    }

    fun getHomeButtonShape(context: Context): String {
        return getPrefs(context).getString(KEY_HOME_BUTTON_SHAPE, "CIRCLE") ?: "CIRCLE"
    }

    fun setHomeButtonShape(context: Context, shape: String) {
        getPrefs(context).edit().putString(KEY_HOME_BUTTON_SHAPE, shape).apply()
    }

    fun getTradeCalculatorEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TRADE_CALCULATOR_ENABLED, true)
    }

    fun setTradeCalculatorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TRADE_CALCULATOR_ENABLED, enabled).apply()
    }
}
