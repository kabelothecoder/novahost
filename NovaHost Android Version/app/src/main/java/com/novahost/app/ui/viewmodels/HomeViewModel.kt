package com.novahost.app.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novahost.app.sdk.BotStatus
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.ui.home.HomeArtMode
import com.novahost.app.ui.home.HomeCustomization
import com.novahost.app.ui.home.HomeLayout
import com.novahost.app.ui.home.HomeWidget
import com.novahost.app.ui.home.LayoutArrangement
import com.novahost.app.ui.home.defaultArrangementFor
import com.novahost.app.ui.home.loadHomeCustomization
import com.novahost.app.ui.home.saveActiveLayout
import com.novahost.app.ui.home.saveArrangement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Everything the Home screen is, minus how it looks.
 *
 * The run state, the connect-in-progress flag and the TTS engine used to be
 * `remember`ed inside the HomeScreen composable. That was survivable while
 * there was one home screen; with five interchangeable layouts it is not.
 * Switching layout recomposes Home with a different subtree, and a
 * composition-scoped `remember` takes the running bot's UI state with it -- the
 * service keeps trading while the button flips back to START. State that
 * outlives the layout has to live outside the layout.
 *
 * The TTS instance is here for the same reason plus one more: it holds a native
 * engine connection that must be shut down exactly once, and [onCleared] is the
 * only place that can promise that across a layout switch.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // ── Run state ──────────────────────────────────────────────────────────

    private val _isRunning = MutableStateFlow(MetaAPIManager.botStatus.value == BotStatus.RUNNING)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    /**
     * Why the robot would not start, phrased for the user and paired with the
     * screen that fixes it.
     *
     * This used to be a bare boolean behind a dialog reading "Unable to validate
     * license. Please contact support." -- which was wrong twice over. It was
     * raised for a missing *broker link* as often as a missing licence, and it
     * named support as the only remedy for something the user can fix in two
     * taps. Ignition has two distinct preconditions, so it has two distinct
     * failures.
     */
    data class ActivationError(
        val title: String,
        val body: String,
        val actionLabel: String,
        val actionRoute: String
    )

    private val _activationError = MutableStateFlow<ActivationError?>(null)
    val activationError: StateFlow<ActivationError?> = _activationError.asStateFlow()

    /** Surfaced to the user as a toast; consumed once so a rotation does not repeat it. */
    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    // ── Customization ──────────────────────────────────────────────────────

    private val _customization = MutableStateFlow(loadHomeCustomization(application))
    val customization: StateFlow<HomeCustomization> = _customization.asStateFlow()

    // ── Voice ──────────────────────────────────────────────────────────────

    private var ttsReady = false
    private var isSpeaking = false
    private val tts: TextToSpeech = TextToSpeech(application) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }

    override fun onCleared() {
        tts.stop()
        tts.shutdown()
        super.onCleared()
    }

    // ── Ignition ───────────────────────────────────────────────────────────

    /**
     * Starts or stops the pulse service.
     *
     * Won't start the engine unless a trading account is genuinely linked -- a
     * robot that reports RUNNING with nothing attached is worse than one that
     * refuses to start.
     */
    fun toggleRun() {
        if (_isConnecting.value) return
        val context = getApplication<Application>()

        viewModelScope.launch {
            if (!_isRunning.value) {
                _isConnecting.value = true

                // Precondition 1: a licence key is actually stored on this
                // handset. Read locally rather than round-tripped -- activation
                // already bound the key server-side, and gating ignition on a
                // second network call means a flat signal reads as "unlicensed".
                val licenceKey = context
                    .getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("license_key", null)

                if (licenceKey.isNullOrBlank()) {
                    _activationError.value = ActivationError(
                        title = "No licence on this device",
                        body = "Activate a robot licence key before starting. If you have one, " +
                            "add it now; if you do not, your mentor issues it.",
                        actionLabel = "ADD LICENCE KEY",
                        actionRoute = com.novahost.app.navigation.Routes.ACTIVATE
                    )
                    _isConnecting.value = false
                    return@launch
                }

                try {
                    // Precondition 2: that licence has a trading account bound to
                    // it. synchronize() is the authority on this and logs its own
                    // reason to the terminal feed.
                    if (!MetaAPIManager.synchronize(context)) {
                        _activationError.value = ActivationError(
                            title = "No trading account linked",
                            body = "Your licence is valid, but no MetaTrader account is connected " +
                                "to it yet. Link your account and the robot will start.",
                            actionLabel = "CONNECT METATRADER",
                            actionRoute = com.novahost.app.navigation.Routes.TERMINAL
                        )
                        _isConnecting.value = false
                        return@launch
                    }

                    val intent = Intent(context, com.novahost.app.service.NovaHostPulseService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }

                    speak("Welcome. NovaHost systems activated.")
                    _isRunning.value = true
                    MetaAPIManager.botStatus.value = BotStatus.RUNNING
                } catch (e: Exception) {
                    android.util.Log.e("NovaHost", "Startup failed", e)
                    _transientMessage.value = "Network Error: Could not connect to trade server."
                }
                _isConnecting.value = false
            } else {
                try {
                    MetaAPIManager.botStatus.value = BotStatus.STOPPED
                    MetaAPIManager.disconnect()
                    context.stopService(Intent(context, com.novahost.app.service.NovaHostPulseService::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("NovaHost", "Disconnect failed", e)
                    _transientMessage.value = "Error stopping trade server."
                }
                // The button returns to START either way. Leaving it on STOP
                // after a failed teardown strands the user with no way back.
                _isRunning.value = false
            }
        }
    }

    fun dismissActivationError() {
        _activationError.value = null
    }

    fun consumeTransientMessage() {
        _transientMessage.value = null
    }

    /**
     * Puts one sentence in front of the user.
     *
     * Routed through the ViewModel rather than raised from a composable so a
     * recomposition -- a layout switch, a theme change -- cannot re-show a
     * message the user already dismissed. Home consumes these exactly once.
     */
    fun raiseTransientMessage(message: String) {
        if (message.isNotBlank()) _transientMessage.value = message
    }

    /** Speaks a mentor's script, ignoring the request if the engine is busy or unavailable. */
    fun speak(script: String) {
        if (!ttsReady || isSpeaking || script.isBlank()) return
        isSpeaking = true
        tts.language = Locale.US
        tts.speak(script, TextToSpeech.QUEUE_FLUSH, null, null)
        viewModelScope.launch {
            delay(3500)
            isSpeaking = false
        }
    }

    // ── Layout, art mode, widget arrangement ───────────────────────────────

    fun selectLayout(layout: HomeLayout) {
        saveActiveLayout(getApplication(), layout)
        _customization.value = _customization.value.copy(layout = layout)
    }

    fun setArtMode(layout: HomeLayout, mode: HomeArtMode) {
        updateArrangement(layout) { it.copy(artMode = mode) }
    }

    fun toggleWidgetVisible(layout: HomeLayout, widget: HomeWidget) {
        if (widget.pinned) return
        updateArrangement(layout) { current ->
            val hidden = if (widget in current.hidden) current.hidden - widget else current.hidden + widget
            current.copy(hidden = hidden)
        }
    }

    /**
     * Moves [widget] to [targetIndex] within the layout's reorderable list.
     *
     * Index is clamped rather than validated: the drag handler reports a
     * position derived from a pointer offset, and a finger dragged past the end
     * of the list should pin to the end, not throw.
     */
    fun moveWidget(layout: HomeLayout, widget: HomeWidget, targetIndex: Int) {
        updateArrangement(layout) { current ->
            val order = current.order.toMutableList()
            val from = order.indexOf(widget)
            if (from < 0) return@updateArrangement current
            order.removeAt(from)
            order.add(targetIndex.coerceIn(0, order.size), widget)
            current.copy(order = order)
        }
    }

    /** Restores every layout's default art mode, order and visibility. Accent and glow are untouched. */
    fun resetArrangements() {
        val context = getApplication<Application>()
        com.novahost.app.sdk.NovaPrefs.resetArrangement(context)
        _customization.value = _customization.value.copy(
            arrangements = HomeLayout.entries.associateWith { defaultArrangementFor(it) }
        )
    }

    private fun updateArrangement(layout: HomeLayout, transform: (LayoutArrangement) -> LayoutArrangement) {
        val current = _customization.value
        val updated = transform(current.arrangementFor(layout))
        saveArrangement(getApplication(), layout, updated)
        _customization.value = current.copy(
            arrangements = current.arrangements + (layout to updated)
        )
    }
}
