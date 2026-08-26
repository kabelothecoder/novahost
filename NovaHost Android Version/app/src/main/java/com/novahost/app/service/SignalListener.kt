package com.novahost.app.service

import android.content.Context
import android.util.LruCache
import com.novahost.app.sdk.BotStatus
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.sdk.NotificationHelper
import com.novahost.app.sdk.SupabaseSetup
import com.novahost.app.sdk.SymbolPlanStore
import com.novahost.app.sdk.TradeSignal
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * received. `signal_logs` stayed empty because `metacopier-execute` was never
 * called at all.
 *
 * That made the single most important function of the product a side effect of a
 * decorative bubble being on screen. Any user who declined the overlay
 * permission -- or whose OEM buries it -- had an app that silently never traded
 * and gave no indication anything was wrong.
 *
 * Execution now belongs to the service. The bubble observes [activeSignal] and
 * is free to be absent.
 */
object SignalListener {

    /** The signal currently being acted on, for the bubble to animate against. */
    data class ActiveSignal(val pair: String, val startedAt: Long)

    private val _activeSignal = MutableStateFlow<ActiveSignal?>(null)
    val activeSignal: StateFlow<ActiveSignal?> = _activeSignal.asStateFlow()

    /** True once the realtime channel is subscribed. Surfaced for diagnostics. */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private var job: Job? = null

    /** Signals already acted on, so a redelivery cannot open a second position. */
    private val processedSignals = LruCache<String, Boolean>(100)

    /**
     * Subscribes to the mentor signal channel for as long as [scope] lives.
     *
     * Idempotent: calling it again while already listening is a no-op, so a
     * restarted service does not end up with two subscriptions racing to execute
     * the same signal.
     */
    fun start(context: Context, scope: CoroutineScope) {
        if (job?.isActive == true) return

        job = scope.launch {
            try {
                // Broadcast, not Postgres changes. Realtime applies RLS per
                // subscriber and the `signals` SELECT policy requires
                // `licenses.owner_id = auth.uid()`; a licence-key install has no
                // auth session, so postgresChangeFlow emitted nothing, ever, for
                // anyone -- silently, because the channel still subscribed fine.
                //
                // `broadcast-signal` already emits every signal on this channel
                // after inserting it, so this listens to what the portal was
                // always sending. The broadcast payload also carries
                // `adminBalance`, which is not a column on `signals`, so
                // mentor-relative sizing works instead of collapsing to 1:1.
                val channel = SupabaseSetup.client.realtime.channel("signals")
                val signalFlow = channel.broadcastFlow<TradeSignal>(event = "new-signal")

                channel.subscribe()
                _listening.value = true
                MetaAPIManager.addLog(">> Listening for signals")

                signalFlow.collect { signal -> handle(context, signal) }

            } catch (e: Exception) {
                android.util.Log.e("NovaHost", "PULSE_ERROR: Realtime connection dropped", e)
                MetaAPIManager.addLog(">> Signal feed disconnected")
            } finally {
                _listening.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _listening.value = false
        _activeSignal.value = null
    }

    private suspend fun handle(context: Context, signal: TradeSignal) {
        // 1. DEDUPLICATION
        val signalId = signal.id ?: return
        if (processedSignals.get(signalId) != null) return
        processedSignals.put(signalId, true)

        // 2. IS THE ROBOT EVEN RUNNING?
        //
        // Previously implicit: the bubble only existed while RUNNING, so a
        // stopped robot could not receive anything. Now that the socket outlives
        // the bubble, the gate has to be stated. A stopped robot does not trade.
        if (MetaAPIManager.botStatus.value != BotStatus.RUNNING) {
            android.util.Log.d("NovaHost", "PULSE: robot is stopped -- signal ${signal.pair} dropped")
            return
        }

        val prefs = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)

        // 3. ROBOT OWNERSHIP CHECK (multi-tenancy).
        // This device is licensed to exactly one robot. Signals are tagged with
        // the robot that issued them, so anything from a different robot must be
        // dropped -- otherwise a user executes trades from a mentor they never
        // paid.
        val activeEaId = prefs.getString("active_ea_id", "") ?: ""
        val signalEaId = signal.ea_id ?: ""
        if (activeEaId.isNotEmpty() && signalEaId.isNotEmpty() && activeEaId != signalEaId) {
            android.util.Log.d("NovaHost", "PULSE: ignoring signal for robot $signalEaId (this device runs $activeEaId)")
            return
        }

        // 4. SIZE THE TRADE
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
            android.util.Log.d(
                "NovaHost",
                "PULSE: ${signal.pair} is not in this device's selection -- signal dropped"
            )
            return
        }

        _activeSignal.value = ActiveSignal(signal.pair, System.currentTimeMillis())
        MetaAPIManager.addLog(">> Signal: ${signal.action} ${signal.pair} @ $volume lots")

        // 5. EXECUTE
        // Broker credentials and the MetaCopier account id stay server-side --
        // the licence is the only thing this device sends.
        val tradeResult = MetaAPIManager.executeTrade(
            context = context,
            pair = signal.pair,
            side = signal.action,
            volume = volume,
            sl = signal.sl,
            tp = signal.tp,
            signalId = signal.signal_id ?: signal.id
        )

        // 6. NOTIFY -- only when the trade actually went through. Announcing an
        // execution that failed is worse than silence: the user believes they
        // are in a position they do not hold.
        tradeResult
            .onSuccess {
                MetaAPIManager.addLog(">> Order accepted: ${signal.pair}")
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
                NotificationHelper.showTradeFailedNotification(
                    context = context,
                    pair = signal.pair,
                    reason = err.message ?: "Trade could not be placed."
                )
            }

        kotlinx.coroutines.delay(10_000)
        _activeSignal.value = null
    }
}
