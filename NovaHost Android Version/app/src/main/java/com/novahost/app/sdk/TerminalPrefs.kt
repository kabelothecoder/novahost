package com.novahost.app.sdk

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

    /**
     * The alias androidx.security's [MasterKeys.getOrCreate] uses internally. Named
     * here so recovery can drop the key along with the file it no longer opens.
     */
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    private const val KEY_SERVER = "terminal_server"
    private const val KEY_ACCOUNT = "terminal_account"
    private const val KEY_TOKEN = "terminal_token"
    private const val KEY_VALIDATED_BOTS = "validated_bots_list"

    private fun open(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Opens the encrypted store, rebuilding it if the ciphertext can no longer be
     * authenticated.
     *
     * The failure mode this exists for: the master key lives in the Android
     * Keystore and is bound to this install on this device, but the prefs FILE is
     * an ordinary XML file that Android's auto-backup will happily carry across a
     * reinstall or a device transfer. Restore the file without the key and every
     * read throws AEADBadTagException ("Signature/MAC verification failed").
     *
     * That used to be fatal. This object is touched from MainViewModel's
     * constructor, so the throw propagated out of ViewModelProvider.get() in
     * MainActivity.onCreate() -- before setContent() -- and the activity's
     * catch-all turned a crash into a permanently black screen that survived
     * force-stops and could only be cleared by wiping app data.
     *
     * Recovery is to throw the undecryptable file away and start a fresh keyset.
     * Nothing is lost that can be lost: the contents are broker credentials and a
     * session token, all of which have to be re-entered after a restore anyway
     * because they were never portable to begin with. Backup exclusion rules stop
     * the file travelling in the first place -- this is the belt to that braces,
     * since a key can also be dropped by a device passcode change or a keystore
     * corruption that no manifest setting protects against.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            open(context)
        } catch (e: Exception) {
            android.util.Log.w(
                "TerminalPrefs",
                "Encrypted prefs unreadable (${e.javaClass.simpleName}); rebuilding the store", e
            )
            runCatching { context.deleteSharedPreferences(PREFS_NAME) }
            runCatching {
                java.security.KeyStore.getInstance("AndroidKeyStore")
                    .apply { load(null) }
                    .deleteEntry(MASTER_KEY_ALIAS)
            }
            open(context)
        }
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

    /**
     * The raw encrypted store, for [NovaPrefs] to lift the appearance values it
     * inherited out of. Returns null when the store cannot be opened.
     *
     * Appearance no longer lives here -- theme colours, glow, shape and
     * typography moved to [NovaPrefs] so there is one home for them. This is the
     * only remaining reader of those keys and exists solely to carry existing
     * installs across. Do not add new callers; do not add new appearance keys.
     */
    internal fun legacyAppearanceStore(context: Context): SharedPreferences? =
        runCatching { getPrefs(context) }.getOrNull()
}
