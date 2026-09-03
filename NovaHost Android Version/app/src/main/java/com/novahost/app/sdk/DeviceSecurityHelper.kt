package com.novahost.app.sdk

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * The handset identity a licence is bound to.
 *
 * ANDROID_ID stays the primary source, and deliberately so: it is what every
 * existing binding in `subscriptions.device_id` was written from. Deriving the
 * id differently -- hashing it with something, salting it, combining it with a
 * generated value -- would change the answer for handsets that are already
 * bound, and a device whose id no longer matches its own licence is a customer
 * locked out of an app they paid for. Whatever else changes here, a device that
 * returns a usable ANDROID_ID must keep returning exactly that.
 *
 * What changed is the failure case. This used to hand back the literal string
 * "UNKNOWN_DEVICE" whenever ANDROID_ID came back null, which meant every such
 * handset shared one identity: the first to arrive bound the licence, and every
 * other one was told its purchase was "active on another device". The server
 * now rejects that string outright, and this no longer produces it.
 */
object DeviceSecurityHelper {

    private const val PREFS = "metahost_prefs"
    private const val KEY_FALLBACK_ID = "device_fallback_id"

    /**
     * ANDROID_ID values that are not identities.
     *
     * `9774d56d682e549c` is the well-known one: a bug across a wide range of
     * older devices returned it from a large number of handsets, so it
     * identifies a manufacturing defect rather than a phone. All-zero and
     * all-same-character values come from emulators and some custom ROMs.
     */
    private val KNOWN_BAD_IDS = setOf(
        "9774d56d682e549c",
        "0000000000000000",
        "unknown",
        "android_id",
    )

    /**
     * A stable id for this install on this handset.
     *
     * Never blank, and never "UNKNOWN_DEVICE".
     */
    fun getDeviceId(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        if (isUsable(androidId)) return androidId!!

        return fallbackId(context)
    }

    private fun isUsable(id: String?): Boolean {
        val value = id?.trim()?.lowercase() ?: return false
        if (value.isEmpty()) return false
        if (value in KNOWN_BAD_IDS) return false
        // A single repeated character is not an identity either.
        if (value.toSet().size <= 1) return false
        return true
    }

    /**
     * A random id generated once and kept in app storage.
     *
     * Weaker than ANDROID_ID -- clearing app data or reinstalling produces a new
     * one, and the licence then has to be moved -- but it is an identity, which
     * is the thing the shared "UNKNOWN_DEVICE" string was not. It is only ever
     * reached by handsets that would otherwise have collided with each other.
     */
    private fun fallbackId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_FALLBACK_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }

        // Prefixed so a support conversation can tell at a glance that this
        // handset had no usable hardware id, which changes what advice is right.
        val generated = "gen-" + UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_FALLBACK_ID, generated).apply()
        return generated
    }
}
