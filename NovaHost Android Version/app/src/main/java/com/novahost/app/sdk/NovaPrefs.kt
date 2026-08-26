package com.novahost.app.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * The one home for appearance and layout preferences.
 *
 * Before this existed the same category of value lived in two stores: theme
 * colours, glow and shape sat in the encrypted `secure_terminal_prefs`
 * alongside broker credentials, while home button scale and robot-name
 * typography sat in raw `metahost_prefs`. Nothing decided which store a new
 * appearance value belonged in, so "where is the theme saved" had two answers
 * and the home layout preset would have made a third.
 *
 * The split is now by *sensitivity*, not by accident:
 *   - [TerminalPrefs] — encrypted. Broker credentials, session token, bot profiles.
 *   - [NovaPrefs]     — plain. Everything about how the app looks.
 *   - `metahost_prefs` — the active robot's identity as written by licence
 *                        activation (display_name, avatar_url, accent_color).
 *
 * Appearance values are deliberately NOT in the encrypted store. That store
 * rebuilds itself from scratch when its ciphertext fails to authenticate, which
 * is the right call for a session token and the wrong one for a layout the user
 * spent time arranging.
 *
 * These are device-local. Reinstalling the APK clears them; nothing here syncs.
 */
object NovaPrefs {
    private const val PREFS_NAME = "nova_appearance"

    /** Set once the legacy values have been pulled across. */
    private const val KEY_MIGRATED = "migrated_from_split_stores"

    // ── Appearance ─────────────────────────────────────────────────────────
    private const val KEY_ACCENT = "user_theme_color"
    private const val KEY_SECONDARY_ACCENT = "secondary_accent_color"
    private const val KEY_SEC_BG_COLOR = "secondary_bg_color"
    private const val KEY_GLOSS_THEME = "gloss_theme_enabled"
    private const val KEY_GLOW_MODE = "user_glow_mode"
    private const val KEY_NOVA_GLOW = "nova_glow_intensity"
    private const val KEY_APP_THEME = "interface_preset"
    private const val KEY_IMMERSIVE_MODE = "immersive_mode"
    private const val KEY_ROBOT_FONT = "robot_font_style"
    private const val KEY_HOME_BUTTON_SHAPE = "home_button_shape"
    private const val KEY_HOME_BUTTON_SCALE = "home_button_scale"
    private const val KEY_ROBOT_NAME_FONT_SIZE = "robot_name_font_size"
    private const val KEY_ROBOT_NAME_FONT_COLOR = "robot_name_font_color"
    private const val KEY_ROBOT_ACCENT_PREFIX = "robot_accent_override_"

    /**
     * Light is an explicit opt-in. Dark is the real scheme: every screen in the
     * app paints dark surfaces, and the theme used to hand them a light
     * MaterialTheme underneath, so any Material default that showed through
     * (ripples, disabled text, unstyled dividers) came out of the wrong palette.
     */
    private const val KEY_USE_LIGHT_SCHEME = "use_light_scheme"

    // ── Home layout ────────────────────────────────────────────────────────
    private const val KEY_HOME_LAYOUT = "home_layout"
    private const val KEY_ART_MODE_PREFIX = "home_art_mode_"
    private const val KEY_WIDGET_ORDER_PREFIX = "home_widget_order_"
    private const val KEY_WIDGET_HIDDEN_PREFIX = "home_widget_hidden_"

    private fun prefs(context: Context): SharedPreferences {
        val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!store.getBoolean(KEY_MIGRATED, false)) migrate(context, store)
        return store
    }

    /**
     * Pulls the appearance values out of the two stores that used to share them.
     *
     * Reads are best-effort on purpose: the encrypted store can legitimately be
     * unreadable on a restored install, and losing a saved accent colour is not
     * a reason to fail. The legacy keys are left in place rather than deleted --
     * a half-migrated user who downgrades still finds their theme.
     */
    private fun migrate(context: Context, store: SharedPreferences) {
        val edit = store.edit()

        runCatching {
            TerminalPrefs.legacyAppearanceStore(context)?.let { old ->
                if (old.contains(KEY_ACCENT)) edit.putLong(KEY_ACCENT, old.getLong(KEY_ACCENT, 0L))
                if (old.contains(KEY_SECONDARY_ACCENT)) edit.putLong(KEY_SECONDARY_ACCENT, old.getLong(KEY_SECONDARY_ACCENT, 0L))
                if (old.contains(KEY_SEC_BG_COLOR)) edit.putLong(KEY_SEC_BG_COLOR, old.getLong(KEY_SEC_BG_COLOR, 0L))
                if (old.contains(KEY_GLOSS_THEME)) edit.putBoolean(KEY_GLOSS_THEME, old.getBoolean(KEY_GLOSS_THEME, false))
                if (old.contains(KEY_GLOW_MODE)) edit.putString(KEY_GLOW_MODE, old.getString(KEY_GLOW_MODE, null))
                if (old.contains(KEY_NOVA_GLOW)) edit.putString(KEY_NOVA_GLOW, old.getString(KEY_NOVA_GLOW, null))
                if (old.contains(KEY_APP_THEME)) edit.putString(KEY_APP_THEME, old.getString(KEY_APP_THEME, null))
                if (old.contains(KEY_IMMERSIVE_MODE)) edit.putBoolean(KEY_IMMERSIVE_MODE, old.getBoolean(KEY_IMMERSIVE_MODE, false))
                if (old.contains(KEY_ROBOT_FONT)) edit.putString(KEY_ROBOT_FONT, old.getString(KEY_ROBOT_FONT, null))
                if (old.contains(KEY_HOME_BUTTON_SHAPE)) edit.putString(KEY_HOME_BUTTON_SHAPE, old.getString(KEY_HOME_BUTTON_SHAPE, null))
                // Per-robot accent overrides are an open-ended key space.
                old.all.keys.filter { it.startsWith(KEY_ROBOT_ACCENT_PREFIX) }.forEach { key ->
                    edit.putLong(key, old.getLong(key, 0L))
                }
            }
        }.onFailure {
            android.util.Log.w("NovaPrefs", "Encrypted appearance values unreadable; starting from defaults", it)
        }

        runCatching {
            val legacy = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
            if (legacy.contains(KEY_HOME_BUTTON_SCALE)) edit.putFloat(KEY_HOME_BUTTON_SCALE, legacy.getFloat(KEY_HOME_BUTTON_SCALE, 1.0f))
            if (legacy.contains(KEY_ROBOT_NAME_FONT_SIZE)) edit.putFloat(KEY_ROBOT_NAME_FONT_SIZE, legacy.getFloat(KEY_ROBOT_NAME_FONT_SIZE, 24f))
            if (legacy.contains(KEY_ROBOT_NAME_FONT_COLOR)) edit.putLong(KEY_ROBOT_NAME_FONT_COLOR, legacy.getLong(KEY_ROBOT_NAME_FONT_COLOR, -1L))
        }

        edit.putBoolean(KEY_MIGRATED, true).apply()
    }

    // ── Accent ─────────────────────────────────────────────────────────────

    fun getAccentColor(context: Context): Long? =
        prefs(context).let { if (it.contains(KEY_ACCENT)) it.getLong(KEY_ACCENT, 0L) else null }

    fun setAccentColor(context: Context, colorValue: Long) {
        prefs(context).edit().putLong(KEY_ACCENT, colorValue).apply()
    }

    fun getSecondaryAccent(context: Context): Long? =
        prefs(context).let { if (it.contains(KEY_SECONDARY_ACCENT)) it.getLong(KEY_SECONDARY_ACCENT, 0L) else null }

    fun setSecondaryAccent(context: Context, colorValue: Long) {
        prefs(context).edit().putLong(KEY_SECONDARY_ACCENT, colorValue).apply()
    }

    fun getSecondaryBgColor(context: Context): Long? =
        prefs(context).let { if (it.contains(KEY_SEC_BG_COLOR)) it.getLong(KEY_SEC_BG_COLOR, 0L) else null }

    fun setSecondaryBgColor(context: Context, colorValue: Long) {
        prefs(context).edit().putLong(KEY_SEC_BG_COLOR, colorValue).apply()
    }

    fun getGlossTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLOSS_THEME, false)

    fun setGlossTheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOSS_THEME, enabled).apply()
    }

    // ── Glow ───────────────────────────────────────────────────────────────

    fun getGlowMode(context: Context): String =
        prefs(context).getString(KEY_GLOW_MODE, "MEDIUM") ?: "MEDIUM"

    fun setGlowMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_GLOW_MODE, mode).apply()
    }

    fun getNovaGlow(context: Context): String =
        prefs(context).getString(KEY_NOVA_GLOW, "MEDIUM") ?: "MEDIUM"

    fun setNovaGlow(context: Context, intensity: String) {
        prefs(context).edit().putString(KEY_NOVA_GLOW, intensity).apply()
    }

    // ── Shape, type, chrome ────────────────────────────────────────────────

    fun getAppTheme(context: Context): String =
        prefs(context).getString(KEY_APP_THEME, "HOLOGRAPHIC") ?: "HOLOGRAPHIC"

    fun setAppTheme(context: Context, preset: String) {
        prefs(context).edit().putString(KEY_APP_THEME, preset).apply()
    }

    fun getImmersiveMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IMMERSIVE_MODE, false)

    fun setImmersiveMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_IMMERSIVE_MODE, enabled).apply()
    }

    /** Default must be a real RobotFontStyle value -- valueOf() on anything else throws at boot. */
    fun getRobotFontStyle(context: Context): String =
        prefs(context).getString(KEY_ROBOT_FONT, "MONTSERRAT_GEOMETRIC") ?: "MONTSERRAT_GEOMETRIC"

    fun setRobotFontStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_ROBOT_FONT, style).apply()
    }

    fun getHomeButtonShape(context: Context): String =
        prefs(context).getString(KEY_HOME_BUTTON_SHAPE, "CIRCLE") ?: "CIRCLE"

    fun setHomeButtonShape(context: Context, shape: String) {
        prefs(context).edit().putString(KEY_HOME_BUTTON_SHAPE, shape).apply()
    }
    fun getHomeButtonScale(context: Context): Float =
        prefs(context).getFloat(KEY_HOME_BUTTON_SCALE, 1.0f)

    fun setHomeButtonScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_HOME_BUTTON_SCALE, scale).apply()
    }

    fun getRobotNameFontSize(context: Context): Float =
        prefs(context).getFloat(KEY_ROBOT_NAME_FONT_SIZE, 24f)

    fun setRobotNameFontSize(context: Context, size: Float) {
        prefs(context).edit().putFloat(KEY_ROBOT_NAME_FONT_SIZE, size).apply()
    }

    /** Returns null when the user has never picked one, so the caller keeps its own default. */
    fun getRobotNameFontColor(context: Context): Long? =
        prefs(context).let { if (it.contains(KEY_ROBOT_NAME_FONT_COLOR)) it.getLong(KEY_ROBOT_NAME_FONT_COLOR, -1L) else null }

    fun setRobotNameFontColor(context: Context, colorValue: Long) {
        prefs(context).edit().putLong(KEY_ROBOT_NAME_FONT_COLOR, colorValue).apply()
    }

    fun getUseLightScheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_USE_LIGHT_SCHEME, false)

    fun setUseLightScheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_USE_LIGHT_SCHEME, enabled).apply()
    }

    // ── Per-robot accent override ──────────────────────────────────────────
    // A robot ships with its mentor's accent_color. If the user recolours it,
    // that choice is stored against THAT robot only -- so switching robots
    // still switches branding, and "reset" can restore the mentor's look.

    fun getRobotAccentOverride(context: Context, eaId: String?): Long? {
        if (eaId.isNullOrBlank()) return null
        val store = prefs(context)
        val key = KEY_ROBOT_ACCENT_PREFIX + eaId
        return if (store.contains(key)) store.getLong(key, 0L) else null
    }

    fun setRobotAccentOverride(context: Context, eaId: String?, colorValue: Long) {
        if (eaId.isNullOrBlank()) return
        prefs(context).edit().putLong(KEY_ROBOT_ACCENT_PREFIX + eaId, colorValue).apply()
    }

    fun clearRobotAccentOverride(context: Context, eaId: String?) {
        if (eaId.isNullOrBlank()) return
        prefs(context).edit().remove(KEY_ROBOT_ACCENT_PREFIX + eaId).apply()
    }

    // ── Home layout ────────────────────────────────────────────────────────
    // Stored as plain enum names and comma-joined name lists rather than JSON.
    // Unknown names are dropped by the caller, so a preset renamed in a later
    // build degrades to the default instead of throwing at boot.

    fun getHomeLayout(context: Context): String? =
        prefs(context).getString(KEY_HOME_LAYOUT, null)

    fun setHomeLayout(context: Context, layout: String) {
        prefs(context).edit().putString(KEY_HOME_LAYOUT, layout).apply()
    }

    fun getArtMode(context: Context, layout: String): String? =
        prefs(context).getString(KEY_ART_MODE_PREFIX + layout, null)

    fun setArtMode(context: Context, layout: String, mode: String) {
        prefs(context).edit().putString(KEY_ART_MODE_PREFIX + layout, mode).apply()
    }

    fun getWidgetOrder(context: Context, layout: String): List<String> =
        prefs(context).getString(KEY_WIDGET_ORDER_PREFIX + layout, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun setWidgetOrder(context: Context, layout: String, order: List<String>) {
        prefs(context).edit().putString(KEY_WIDGET_ORDER_PREFIX + layout, order.joinToString(",")).apply()
    }

    fun getHiddenWidgets(context: Context, layout: String): Set<String> =
        prefs(context).getString(KEY_WIDGET_HIDDEN_PREFIX + layout, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    fun setHiddenWidgets(context: Context, layout: String, hidden: Set<String>) {
        prefs(context).edit().putString(KEY_WIDGET_HIDDEN_PREFIX + layout, hidden.joinToString(",")).apply()
    }

    /** Drops every per-layout arrangement, leaving the accent and glow alone. */
    fun resetArrangement(context: Context) {
        val store = prefs(context)
        val edit = store.edit()
        store.all.keys
            .filter {
                it.startsWith(KEY_ART_MODE_PREFIX) ||
                    it.startsWith(KEY_WIDGET_ORDER_PREFIX) ||
                    it.startsWith(KEY_WIDGET_HIDDEN_PREFIX)
            }
            .forEach { edit.remove(it) }
        edit.apply()
    }
}
