package com.novahost.app.service

import android.content.Context
import android.util.LruCache
import com.novahost.app.sdk.BotStatus
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.sdk.NotificationHelper
import com.novahost.app.sdk.NovaHostBackend
import com.novahost.app.sdk.SymbolPlanStore
import com.novahost.app.sdk.TradeSignal
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Receives mentor signals and places the trades.
 *
 * ## Why this is not in the overlay any more
 *
 * This pipeline used to live in a `LaunchedEffect` inside `PulseApp` -- the
 * Composable set as the content of the floating bubble's `ComposeView`. That
 * view is only handed to the WindowManager when `botStatus` flips to RUNNING,
 * and **a ComposeView that is never attached to a window never composes**. So
 * the effect never ran, the channel was never subscribed, and no signal was ever
 * received.
 *
 * That made the single most important function of the product a side effect of a
 * decorative bubble being on screen. Execution now belongs to the service, and
 * the bubble observes [activeSignal] and is free to be absent.
 *
 * ## Why realtime is no longer the delivery mechanism
 *
 * It still could not be trusted after that fix, and the reason is structural
 * rather than a bug: **a realtime broadcast is fire-and-forget.** No
 * acknowledgement, no redelivery, no cursor. A handset that dozed, backgrounded,
 * switched from wifi to data, or simply held a socket that lapsed for two
 * seconds lost the signal permanently -- and neither end recorded that anything
 * had gone missing. Sixteen live broadcasts produced zero executions and zero
 * explanations.
 *
 * No amount of care inside this file fixes that, because the message never
 * arrives to be careful with.
 *
 * So the roles are swapped. `claim-signals` is the delivery mechanism: the app
 * asks what it has not yet taken, and the server hands each signal to exactly
 * one device, exactly once. The realtime broadcast is kept purely as a **nudge**
 * -- it carries no payload this code trusts, it just means "ask now" and buys
 * back the latency that polling alone would cost.
 *
 * The consequences worth stating:
 *
 *  - A signal survives the app being killed, the socket dying, or the phone
 *    sleeping through it, up to the server's freshness window.
 *  - Nothing is executed twice, even with the nudge and a poll racing, and even
 *    with two handsets on one licence -- the claim is atomic server-side.
 *  - A signal too old to be worth trading is retired by the server and reported,
 *    not silently entered into. Waking up to positions opened on a call from an
 *    hour ago would be worse than the miss.
 */
object SignalListener {

    /** The signal currently being acted on, for the bubble to animate against. */
    data class ActiveSignal(val pair: String, val startedAt: Long)

    private val _activeSignal = MutableStateFlow<ActiveSignal?>(null)
    val activeSignal: StateFlow<ActiveSignal?> = _activeSignal.asStateFlow()

    /** True once the realtime nudge channel is subscribed. Diagnostics only. */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private var nudgeJob: Job? = null
    private var pollJob: Job? = null

    /**
     * How often the app asks for work when nothing has nudged it.
     *
     * This is the interval that decides how late a trade can be if realtime is
     * down entirely -- so it is the number that has to be survivable on its own,
     * not one that assumes the socket works. Twenty seconds is three requests a
     * minute on a device that already holds a wake lock and a foreground
     * notification: immaterial to battery, and a delay a manual mentor call can
     * absorb.
     */
    private const val POLL_INTERVAL_MS = 20_000L

    /**
     * How long to wait after a failed poll.
     *
     * Longer than the normal interval because the common cause is no network,
     * and hammering a dead radio is how a background service becomes a battery
     * complaint.
     */
    private const val POLL_BACKOFF_MS = 60_000L

    /**
     * Signals already acted on by this process.
     *
     * Belt and braces. The server's claim is the real guarantee and this cannot
     * see across a restart, but it costs nothing and closes the window where a
     * nudge and a poll overlap inside one process.
     */
    private val processedSignals = LruCache<String, Boolean>(200)

    /**
     * Serialises the two callers.
     *
     * A nudge and a timer poll can land together, and both call [drain]. Without
     * this they would each claim a disjoint half of the outstanding signals and
     * execute them concurrently -- not a correctness bug given the server-side
     * claim, but it interleaves the terminal feed and the notifications into
     * something no user could follow.
     */
    private val draining = Mutex()

    /**
     * Starts both paths for as long as [scope] lives.
     *
     * Idempotent: calling it again while already running is a no-op, so a
     * restarted service does not end up with two pollers racing each other.
     */
    fun start(context: Context, scope: CoroutineScope) {
        if (pollJob?.isActive == true) return

        // ---- The guarantee: ask, on a timer, forever --------------------------
        pollJob = scope.launch {
            MetaAPIManager.addLog(">> Listening for signals")
            while (isActive) {
                val ok = drain(context)
                delay(if (ok) POLL_INTERVAL_MS else POLL_BACKOFF_MS)
            }
        }

        // ---- The accelerator: ask immediately when told to --------------------
        //
        // Deliberately a separate job. If the socket never connects, or throws,
        // or the realtime service is degraded, the poll above is untouched and
        // the robot still trades -- which is the entire point of the split.
        nudgeJob = scope.launch {
            try {
                val channel = NovaHostBackend.client.realtime.channel("signals")
                val nudges = channel.broadcastFlow<TradeSignal>(event = "new-signal")

                channel.subscribe()
                _listening.value = true

                // The payload is ignored on purpose. It is not authenticated,
                // it is not filtered to this licence, and it has been observed
                // to disagree with the row it was built from. It means one
                // thing: something was just broadcast, go and ask properly.
                nudges.collect { drain(context) }
            } catch (e: Exception) {
                // Not fatal and not alarming. Polling covers it; saying "signal
                // feed disconnected" here would tell a user their robot has
                // stopped working when it has not.
                android.util.Log.w("NovaHost", "PULSE: realtime nudge unavailable: ${e.message}")
            } finally {
                _listening.value = false
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        nudgeJob?.cancel()
        pollJob = null
        nudgeJob = null
        _listening.value = false
        _activeSignal.value = null
    }

    /**
     * Asks for outstanding signals and acts on every one.
     *
     * Returns false when the poll itself failed, which the caller uses to back
     * off. A poll that succeeds and returns nothing is a normal, healthy result
     * and returns true.
     */
    private suspend fun drain(context: Context): Boolean = draining.withLock {
        // The RUNNING gate is checked BEFORE claiming, and that ordering is
        // load-bearing. Claiming is destructive -- a claimed signal is never
        // offered again, to this device or any other on the licence -- so a
        // stopped robot that polled would quietly consume the signals a running
        // handset on the same key was waiting for.
        if (MetaAPIManager.botStatus.value != BotStatus.RUNNING) return@withLock true

        val result = MetaAPIManager.claimSignals(context)

        val claim = result.getOrElse { err ->
            android.util.Log.w("NovaHost", "PULSE: claim failed - ${err.message}")
            return@withLock false
        }

        // The mentor may have changed what the robot is allowed to trade since
        // this device activated. Applied here because the poll is the only thing
        // that talks to the server regularly -- before this, the allowance was
        // written once at activation and never refreshed, so an edited robot
        // never reached a handset already in the field.
        if (SymbolPlanStore.updateAllowance(context, claim.allowed_symbols)) {
            MetaAPIManager.addLog(">> Robot symbols updated by your mentor")
            // Push the reconciled plan back so the server's per-symbol config
            // matches the new allowance rather than the one it replaced.
            SymbolPlanStore.sync(context)
        }

        // The robot was not listening when these arrived and they are now too old
        // to act on. Named rather than swallowed: from the user's side an
        // unreported miss is indistinguishable from a mentor who sent nothing,
        // which is exactly the confusion that made a dead pipeline look healthy.
        if (claim.stale > 0) {
            val mins = claim.window_seconds / 60
            MetaAPIManager.addLog(
                ">> Missed ${claim.stale} signal(s) while offline — older than ${mins}m, not traded"
            )
        }

        for (signal in claim.signals) {
            execute(context, signal)
        }

        true
    }

    /**
     * Records a signal this device chose not to trade.
     *
     * Every one of the gates below is correct, and every one of them used to
     * return into `Log.d` and nowhere else. From the user's side a dropped
     * signal and a quiet market are the same thing: no notification, no line in
     * the terminal feed, nothing in `signal_logs` -- which is a server-side
     * table only the executor writes, and the executor is precisely what a
     * dropped signal never reaches.
     *
     * The feed is where a user already looks to see what the robot is doing, so
     * that is where a refusal belongs.
     */
    private fun drop(signal: TradeSignal, reason: String) {
        android.util.Log.d("NovaHost", "PULSE: ${signal.pair} dropped -- $reason")
        MetaAPIManager.addLog(">> ${signal.pair} skipped: $reason")

        // Recorded, not just logged. A trade the user's own rules refused is
        // something they chose, and it should be visible as a decision in the
        // feed rather than buried in a terminal line they will never scroll to.
        TradeFeed.record(
            TradeFeed.TradeEvent(
                id = signal.id ?: signal.signal_id ?: reason,
                pair = signal.pair,
                side = signal.action,
                orderType = signal.order_type,
                phase = TradeFeed.Phase.SKIPPED,
                message = reason.replaceFirstChar { it.uppercase() }
            )
        )
    }

    private suspend fun execute(context: Context, signal: TradeSignal) {
        // 1. DEDUPLICATION (local). The server's claim already guarantees this;
        //    the cache only closes the in-process nudge/poll overlap.
        val signalId = signal.id ?: return
        if (processedSignals.get(signalId) != null) return
        processedSignals.put(signalId, true)

        val prefs = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)

        // 2. ROBOT OWNERSHIP CHECK (multi-tenancy).
        //
        // `claim-signals` already filters to the robot this licence is bound to,
        // so this should never fire. Kept because it is the check that stops a
        // user executing trades from a mentor they never paid, and a check like
        // that belongs on both sides of the wire.
        val activeEaId = prefs.getString("active_ea_id", "") ?: ""
        val signalEaId = signal.ea_id ?: ""
        if (activeEaId.isNotEmpty() && signalEaId.isNotEmpty() && activeEaId != signalEaId) {
            val runningName = prefs.getString("display_name", null)
            drop(
                signal,
                "it is for another robot" + (runningName?.let { " (this licence runs $it)" } ?: "")
            )
            return
        }

        // 3. SIZE THE TRADE
        //
        // The user's plan, not the robot's allowance. `allowed_symbols` is what
        // the mentor permits and is enforced again server-side; the plan is
        // which of those the subscriber actually ticked, and at what size.
        val plan = SymbolPlanStore.load(context)
        val hasPlan = plan.symbols.isNotEmpty()

        val userBalance = MetaAPIManager.balance.value
        val adminBalance = signal.adminBalance ?: userBalance
        val signalLot = signal.lot ?: 0.01

        val volume = if (hasPlan) {
            // Null means the user did not tick this symbol -- their decision
            // outranks the signal.
            SymbolPlanStore.resolveLot(
                plan = plan,
                symbol = signal.pair,
                signalLot = signalLot,
                userBalance = userBalance,
                adminBalance = adminBalance
            )
        } else {
            // No plan on this device: the original mentor-relative copy.
            val scaled = if (adminBalance > 0.0) {
                (userBalance / adminBalance) * signalLot
            } else 0.01
            kotlin.math.round(maxOf(0.01, scaled) * 100.0) / 100.0
        }

        if (volume == null) {
            drop(signal, "it is not switched on in your trading symbols")
            return
        }

        _activeSignal.value = ActiveSignal(signal.pair, System.currentTimeMillis())

        // Everything the UI needs to render this trade, carried on the event
        // rather than flattened into a log line. The takeover reads this.
        TradeFeed.begin(
            TradeFeed.TradeEvent(
                id = signalId,
                pair = signal.pair,
                side = signal.action,
                volume = volume,
                sl = signal.sl,
                tp = signal.tp,
                orderType = signal.order_type,
                openPrice = signal.open_price,
                phase = TradeFeed.Phase.SENDING
            )
        )
        MetaAPIManager.addLog(">> Signal: ${signal.action} ${signal.pair} @ $volume lots")

        // 4. EXECUTE
        // Broker credentials and the MetaCopier account id stay server-side --
        // the licence is the only thing this device sends.
        val tradeResult = MetaAPIManager.executeTrade(
            context = context,
            pair = signal.pair,
            side = signal.action,
            volume = volume,
            sl = signal.sl,
            tp = signal.tp,
            signalId = signal.signal_id ?: signal.id,
            // A mentor can now call a level rather than only the market. Passing
            // these straight through is what makes "buy the retest at 1.0850"
            // arrive as a BuyLimit instead of filling wherever price happens to
            // be standing when the handset picks the signal up.
            orderType = signal.order_type,
            openPrice = signal.open_price,
            pendingExpirySeconds = signal.pending_expiry_seconds
        )

        // 5. NOTIFY -- only when the trade actually went through. Announcing an
        // execution that failed is worse than silence: the user believes they
        // are in a position they do not hold.
        tradeResult
            .onSuccess { msg ->
                MetaAPIManager.addLog(">> Order accepted: ${signal.pair}")
                TradeFeed.settle {
                    it.copy(
                        phase = TradeFeed.Phase.FILLED,
                        code = "EXECUTED",
                        message = TradeFeed.describe("EXECUTED", msg)
                    )
                }
                NotificationHelper.showTradeNotification(
                    context = context,
                    pair = signal.pair,
                    action = signal.action,
                    price = "Current Market"
                )
            }
            .onFailure { err ->
                android.util.Log.e("NovaHost", "PULSE_ERROR: execution failed - ${err.message}")
                MetaAPIManager.addLog(">> Order rejected: ${err.message}")

                // The executor's own code where we have one. It is what turns
                // "the order could not be placed" into something the user can
                // act on -- switch the symbol back on, close a position, top up.
                val rejection = err as? MetaAPIManager.TradeRejected
                val code = rejection?.code
                val reason = TradeFeed.describe(code, rejection?.message ?: err.message)

                // A signal that arrived while the broker was still finishing its
                // connection is not a failure the user needs woken for -- the
                // feed line is enough, and the next signal places normally. It
                // reads as SKIPPED ("never sent"), not REJECTED ("broker said
                // no"). Every other rejection still raises a notification.
                val stillConnecting = code == "ACCOUNT_CONNECTING"

                TradeFeed.settle {
                    it.copy(
                        phase = if (stillConnecting) TradeFeed.Phase.SKIPPED else TradeFeed.Phase.REJECTED,
                        code = code,
                        message = reason
                    )
                }
                if (!stillConnecting) {
                    NotificationHelper.showTradeFailedNotification(
                        context = context,
                        pair = signal.pair,
                        reason = reason
                    )
                }
            }

        // Long enough for the bubble to finish its animation, short enough that a
        // second signal in the same batch is not held up behind it.
        delay(2_000)
        _activeSignal.value = null
    }
}
