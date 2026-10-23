package com.novahost.app.ui.scanner

import android.content.Context

/**
 * Where the user's own trading rules live.
 *
 * The guardrail panel has always said "your rules" and offered an EDIT MY RULES
 * button, and until now both were untrue: `GuardrailConfig()` was constructed
 * with hardcoded defaults on every recomposition, the button was a bordered
 * `Row` with no click handler, and there was no screen behind it. The app was
 * describing limits the user had never set and could not change.
 *
 * That is a worse failure than having no guardrails at all. A rule the user
 * believes they set is a rule they will trade against.
 *
 * Device-local on purpose. These are personal limits rather than an entitlement,
 * nothing server-side reads them, and syncing them would mean a second device
 * silently changing the rules on the first. The per-symbol caps in
 * [com.novahost.app.sdk.SymbolPlanStore] are the ones that DO sync, because
 * those are enforced by the executor and the handset is not trusted with them.
 */
object GuardrailStore {

    // Shares the appearance store rather than opening a fourth SharedPreferences
    // file. These are screen-level preferences, not credentials.
    private const val PREFS = "nova_appearance"

    private const val KEY_MIN_RR = "guardrail_min_rr"
    // Renamed with the units. The old `guardrail_max_stop_pips` value is left
    // unread rather than migrated: it was a pip count, and there is no honest
    // conversion from one to a candle multiple without knowing which instrument
    // it was set against. Anyone who customised it starts again from a default
    // that works on every symbol, which the old one did not.
    private const val KEY_MAX_STOP = "guardrail_max_stop_atr"
    private const val KEY_MAX_LOSSES = "guardrail_max_losses"
    private const val KEY_EVENT_CLEARANCE = "guardrail_event_minutes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The user's rules, or the defaults where they have not set one.
     *
     * Read per field rather than as a blob so adding a rule later does not
     * invalidate what someone already configured.
     */
    fun load(context: Context): GuardrailConfig = with(prefs(context)) {
        GuardrailConfig(
            minBlendedRR = getFloat(KEY_MIN_RR, GuardrailConfig.Defaults.minBlendedRR.toFloat()).toDouble(),
            maxStopAtr = getFloat(KEY_MAX_STOP, GuardrailConfig.Defaults.maxStopAtr.toFloat()).toDouble(),
            maxConsecutiveLosses = getInt(KEY_MAX_LOSSES, GuardrailConfig.Defaults.maxConsecutiveLosses),
            minEventClearanceMinutes = getInt(KEY_EVENT_CLEARANCE, GuardrailConfig.Defaults.minEventClearanceMinutes)
        ).sanitised()
    }

    /** Sanitises before writing, so a bad value cannot be stored and re-read. */
    fun save(context: Context, config: GuardrailConfig) {
        val clean = config.sanitised()
        prefs(context).edit().apply {
            putFloat(KEY_MIN_RR, clean.minBlendedRR.toFloat())
            putFloat(KEY_MAX_STOP, clean.maxStopAtr.toFloat())
            putInt(KEY_MAX_LOSSES, clean.maxConsecutiveLosses)
            putInt(KEY_EVENT_CLEARANCE, clean.minEventClearanceMinutes)
        }.apply()
    }

    /** True when the user has changed anything from the shipped defaults. */
    fun isCustomised(context: Context): Boolean = load(context) != GuardrailConfig.Defaults
}
