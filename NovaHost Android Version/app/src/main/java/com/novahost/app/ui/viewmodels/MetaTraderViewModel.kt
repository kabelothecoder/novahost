package com.novahost.app.ui.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel

private val Context.dataStore by preferencesDataStore(name = "metatrader_prefs")

class MetaTraderViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val ACCOUNT_ID_KEY   = stringPreferencesKey("account_id")
    private val SERVER_KEY       = stringPreferencesKey("server")
    private val ACCOUNT_TYPE_KEY = stringPreferencesKey("account_type")
    private val PLATFORM_KEY     = stringPreferencesKey("platform")

    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _server = MutableStateFlow("")
    val server: StateFlow<String> = _server

    private val _accountType = MutableStateFlow("Standard — No Bonus")
    val accountType: StateFlow<String> = _accountType

    /**
     * MT4 or MT5, remembered across visits.
     *
     * The tab used to reset to MT5 on every recomposition of the screen, so an
     * MT4 user who fixed their password on a second attempt silently resubmitted
     * it as MT5. The broker answers that with a credential rejection, which sent
     * them back to re-checking a password that was right all along.
     */
    private val _platform = MutableStateFlow("MT5")
    val platform: StateFlow<String> = _platform

    /** Automatically derives the symbol suffix based on account type.
     *  Micro accounts require a .m suffix on every symbol (e.g. XAUUSD.m).
     *  Standard/Specialty accounts trade the raw symbol.
     */
    val symbolSuffix: StateFlow<String> get() = MutableStateFlow(
        if (_accountType.value.startsWith("Micro")) ".m" else ""
    )

    init {
        loadCredentials()
    }

    private fun loadCredentials() {
        viewModelScope.launch {
            _accountId.value   = context.dataStore.data.map { it[ACCOUNT_ID_KEY]   ?: "" }.first()
            _password.value    = "" // Never load raw password from disk
            _server.value      = context.dataStore.data.map { it[SERVER_KEY]        ?: "" }.first()
            _accountType.value = context.dataStore.data.map { it[ACCOUNT_TYPE_KEY] ?: "Standard — No Bonus" }.first()
            _platform.value    = context.dataStore.data.map { it[PLATFORM_KEY]     ?: "MT5" }.first()
        }
    }

    fun updatePlatform(value: String) {
        val normalized = if (value.equals("MT4", ignoreCase = true)) "MT4" else "MT5"
        _platform.value = normalized
        viewModelScope.launch { context.dataStore.edit { it[PLATFORM_KEY] = normalized } }
    }

    fun updateAccountId(id: String) {
        _accountId.value = id
        viewModelScope.launch { context.dataStore.edit { it[ACCOUNT_ID_KEY] = id } }
    }

    fun updatePassword(pass: String) {
        _password.value = pass
        // DO NOT persist the raw password to DataStore
    }

    fun updateServer(srv: String) {
        _server.value = srv
        viewModelScope.launch { context.dataStore.edit { it[SERVER_KEY] = srv } }
    }

    fun updateAccountType(type: String) {
        _accountType.value = type
        viewModelScope.launch { context.dataStore.edit { it[ACCOUNT_TYPE_KEY] = type } }
    }

    /** Derives the symbol suffix from the current account type for trade execution. */
    fun getSymbolSuffix(): String = if (_accountType.value.startsWith("Micro")) ".m" else ""
}

