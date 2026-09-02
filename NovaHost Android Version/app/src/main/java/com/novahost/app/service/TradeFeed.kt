package com.novahost.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the robot is doing with the user's money, in a form the UI can render.
 *
 * This exists because [SignalListener.ActiveSignal] carried only a pair and a
 * timestamp. That is enough to pulse a dot; it is not enough to tell somebody
 * their order was refused because the symbol is switched off, or that it filled
 * at 0.02 lots. Everything the user might reasonably ask after a trade lands --
 * side, size, levels, and above all *why* it did not go through -- now travels
 * with the event instead of being flattened into a log string.
 *
 * Two flows, deliberately separate:
 *
 *  - [current] is the one in flight. It drives the full-screen takeover, and it
 *    goes null once the trade has settled and been shown.
 *  - [history] is the feed. It survives the animation so the user can scroll
 *    back through the session.
 *
 * In memory only. A restart clears it, which is honest -- the authority on what
 * was actually traded is the broker account, never this list. Persisting it
 * would invite the user to trust a local cache over their own terminal.
 */
object TradeFeed {

    /** How far a trade has got. Ordered by progression, not severity. */
    enum class Phase {
        /** Claimed from the server, not yet sized. */
        RECEIVED,

        /** Being sized against the user's plan and balance. */
        SIZING,

        /** Handed to the executor; the broker has not answered yet. */
        SENDING,

        /** The broker accepted it. */
        FILLED,

        /** The broker or the executor refused it. */
        REJECTED,

        /** Never sent -- the user's own rules or allowance stopped it. */
        SKIPPED
    }

    data class TradeEvent(
        val id: String,
        val pair: String,
        val side: String,
        val volume: Double? = null,
        val sl: Double? = null,
        val tp: Double? = null,
        val orderType: String? = null,
        val openPrice: Double? = null,
        val phase: Phase = Phase.RECEIVED,
        /** Machine code from the executor: EXECUTED, POSITION_CAP, NO_ACCOUNT_LINKED... */
        val code: String? = null,
        /** Already human-readable. What the UI shows verbatim. */
        val message: String? = null,
        val at: Long = System.currentTimeMillis()
    ) {
        val isSettled: Boolean
            get() = phase == Phase.FILLED || phase == Phase.REJECTED || phase == Phase.SKIPPED

        val isFailure: Boolean
            get() = phase == Phase.REJECTED || phase == Phase.SKIPPED
    }

    private val _current = MutableStateFlow<TradeEvent?>(null)
    val current: StateFlow<TradeEvent?> = _current.asStateFlow()

    private val _history = MutableStateFlow<List<TradeEvent>>(emptyList())
    val history: StateFlow<List<TradeEvent>> = _history.asStateFlow()

    /** Enough to scroll a session without letting a busy day grow without bound. */
    private const val MAX_HISTORY = 60

    fun begin(event: TradeEvent) {
        _current.value = event
    }

    /** Advances the in-flight trade without disturbing history. */
    fun update(transform: (TradeEvent) -> TradeEvent) {
        _current.value = _current.value?.let(transform)
    }

    /**
     * Records the final state and keeps it on screen.
     *
     * The event stays in [current] so the takeover can show the outcome -- a
     * takeover that vanished the instant the broker answered would show the user
     * a spinner and nothing else. [dismiss] is what clears it.
     */
    fun settle(transform: (TradeEvent) -> TradeEvent) {
        val settled = _current.value?.let(transform) ?: return
        _current.value = settled
        _history.value = (listOf(settled) + _history.value).take(MAX_HISTORY)
    }

    /**
     * Logs an outcome for a trade that never became current -- a signal stopped
     * before sizing, which has no in-flight phase to advance.
     */
    fun record(event: TradeEvent) {
        _history.value = (listOf(event) + _history.value).take(MAX_HISTORY)
    }

    fun dismiss() {
        _current.value = null
    }

    fun clear() {
        _current.value = null
        _history.value = emptyList()
    }

    /**
     * Turns an executor code into something a trader can act on.
     *
     * The raw message is preferred where the server already wrote a good one --
     * it knows the user's actual symbol names and limits and this does not. This
     * is the fallback for codes that arrive bare, and the place to add wording
     * for a new code rather than scattering strings through the UI.
     */
    fun describe(code: String?, raw: String?): String {
        val fromServer = raw?.trim()?.takeIf { it.isNotEmpty() }
        return when (code) {
            "EXECUTED" -> fromServer ?: "Order accepted by your broker."
            "NO_ACCOUNT_LINKED" ->
                "No broker account is linked to this licence yet. Link one in Broker Setup."
            "INSUFFICIENT_MARGIN" ->
                fromServer ?: "Not enough free margin in your account for this position size."
            "POSITION_CAP" ->
                fromServer ?: "You already hold the maximum number of trades you allowed for this symbol."
            "SYMBOL_NOT_LICENSED" ->
                fromServer ?: "Your robot is not permitted to trade this symbol."
            "SYMBOL_DISABLED" ->
                fromServer ?: "Your broker has this symbol closed for trading right now."
            "ACCOUNT_CONNECTING" ->
                fromServer ?: "Your broker was still connecting when this signal arrived. Newer signals will go through once it is up."
            "ACCOUNT_DISCONNECTED", "ACCOUNT_IS_NOT_CONNTECTED", "ACCOUNT_NOT_CONNECTED" ->
                fromServer ?: "Your broker isn't connected right now. Reconnect it in Broker Setup, then try again."
            "ACCOUNT_WRONG_CREDENTIALS" ->
                fromServer ?: "Your broker rejected the saved login. Reconnect with your trading (master) password."
            "ACCOUNT_READONLY" ->
                fromServer ?: "This account can't place orders -- it's linked with an investor (read-only) password, or the broker disabled trading on it."
            "MARKET_CLOSED" ->
                fromServer ?: "The market for this symbol is closed right now."
            "BROKER_REJECTION", "BROKER_REJECTED" ->
                fromServer ?: "Your broker refused the order without giving a reason. Check the symbol's trading hours and your free margin."
            "BAD_VOLUME" ->
                fromServer ?: "The lot size falls outside what your broker accepts for this symbol."
            "INVALID_STOPS" ->
                fromServer ?: "Your broker refused the stop loss or take profit level."
            "ENTRY_OUTSIDE_BRACKET" ->
                fromServer ?: "The entry price sits on the wrong side of the current market."
            "MISSING_ENTRY_PRICE" ->
                fromServer ?: "A pending order arrived without a price to wait at."
            "LICENCE_EXPIRED" -> "Your licence has expired."
            "LICENCE_INACTIVE" -> "Your licence is not active."
            "LICENCE_UNKNOWN" -> "This licence was not recognised."
            "SERVER_MISCONFIGURED", "FATAL" ->
                fromServer ?: "Something went wrong on our side. This one is on us, not your account."
            "DRY_RUN" -> "Test mode -- nothing was sent to your broker."
            else -> fromServer ?: "The order could not be placed."
        }
    }
}
