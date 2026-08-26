package com.novaedge.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaedge.app.sdk.EconomicEvent
import com.novaedge.app.sdk.ForexRepository
import com.novaedge.app.sdk.MarketSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * @description ViewModel responsible for providing market data and managing user watchlists.
 */
class MarketsViewModel : ViewModel() {

    private val repository = ForexRepository

    val livePrices: StateFlow<Map<String, Double>> = repository.livePrices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val bullishMomentum: StateFlow<Map<String, Float>> = repository.bullishMomentum
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val economicCalendar: StateFlow<List<EconomicEvent>> = repository.economicCalendar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val marketSessions: StateFlow<List<MarketSession>> = repository.marketSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _watchlist = MutableStateFlow<List<String>>(listOf("OANDA:XAU_USD", "OANDA:EUR_USD", "OANDA:GBP_USD", "BINANCE:BTCUSDT"))
    val watchlist: StateFlow<List<String>> = _watchlist.asStateFlow()

    init {
        // Initialize the repository connections and fetching
        repository.initialize()
    }

    /**
     * @description Adds a new symbol to the user's watchlist in memory.
     */
    fun addToWatchlist(symbol: String) {
        val currentList = _watchlist.value.toMutableList()
        if (!currentList.contains(symbol)) {
            currentList.add(symbol)
            _watchlist.value = currentList
        }
    }

    /**
     * @description Removes a symbol from the user's watchlist in memory.
     */
    fun removeFromWatchlist(symbol: String) {
        val currentList = _watchlist.value.toMutableList()
        if (currentList.contains(symbol)) {
            currentList.remove(symbol)
            _watchlist.value = currentList
        }
    }
    
    /**
     * @description Refreshes the market sessions statuses manually.
     */
    fun refreshMarketSessions() {
        repository.updateMarketSessions()
    }
}
