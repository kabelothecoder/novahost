package com.novahost.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.novahost.app.ui.components.LightStatusBarEffect
import com.novahost.app.ui.components.NovaCtaButton
import com.novahost.app.ui.components.NovaGhostButton
import com.novahost.app.ui.components.NovaMonoText
import com.novahost.app.ui.components.SplashMark
import com.novahost.app.ui.components.StaggerIn
import com.novahost.app.ui.theme.NovaMonoStatus
import com.novahost.app.ui.theme.NovaMotion
import com.novahost.app.ui.theme.NovaSurface
import com.novahost.app.ui.theme.NovaTextPrimary
import com.novahost.app.ui.theme.NovaTextSecondary
import com.novahost.app.ui.theme.NovaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * SPLASH
 *
 * A reachability check with a mark on it. The rules this screen holds:
 *
 *  - No spinner, ever. The mark takes the job, so nothing new appears on screen
 *    between 320ms and 4500ms.
 *  - No percentage, no determinate bar. A reachability check has no measurable
 *    progress, so there is nothing honest to fill a bar with.
 *  - No version, no tips, no tagline. The only words are the app name and,
 *    after 4.5s, what it is doing.
 *  - No path past the error. Settings or retry; the app never advances to Home
 *    without a connection.
 *  - Minimum hold 1200ms even on a 400ms check, so the wordmark always gets its
 *    beat instead of flashing.
 *
 * The timing ladder:
 *   0ms      mark scales in (0.92 -> 1.0, 320ms)
 *   1000ms   system window -> Compose handoff; wordmark fades in over 220ms
 *   1200ms   eyes arm (2400ms alpha loop) -- only if still unresolved
 *   4500ms   CHECKING CONNECTION
 *   6500ms   SLOW CONNECTION - STILL TRYING
 *   8000ms   give up, show the error block
 */
object SplashTiming {
    const val HANDOFF_MS = 1000L
    const val ARM_MS = 1200L
    const val LABEL_MS = 4500L
    const val SLOW_MS = 6500L
    const val TIMEOUT_MS = 8000L
    const val MIN_HOLD_MS = 1200L
    const val PROBE_MS = 2500L
}

private enum class SplashPhase { CHECKING, RESOLVED, FAILED }

@Composable
fun SplashScreen(onResolved: () -> Unit) {
    LightStatusBarEffect()

    val context = LocalContext.current
    val resolved by rememberUpdatedState(onResolved)
    var phase by remember { mutableStateOf(SplashPhase.CHECKING) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var attempt by remember { mutableIntStateOf(0) }

    // Returning from Wi-Fi settings re-runs the check without a second scale-in:
    // the mark is already on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && phase == SplashPhase.FAILED) attempt++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(attempt) {
        phase = SplashPhase.CHECKING
        // A retry keeps the mark at full scale and the wordmark up; only a cold
        // start begins at zero.
        val startElapsed = if (attempt == 0) 0L else SplashTiming.ARM_MS
        elapsed = startElapsed
        val start = SystemClock.elapsedRealtime() - startElapsed

        val probe = async(Dispatchers.IO) { isNetworkReachable(context) }
        val ticker = launch {
            while (isActive) {
                elapsed = SystemClock.elapsedRealtime() - start
                delay(50)
            }
        }

        val budget = SplashTiming.TIMEOUT_MS - startElapsed
        val ok = withTimeoutOrNull(budget) { probe.await() } ?: false

        // The minimum hold applies to both outcomes. It exists so the wordmark
        // gets its beat, and an error that lands at 300ms would replace a
        // wordmark that had not finished fading in yet.
        val held = SystemClock.elapsedRealtime() - start
        if (held < SplashTiming.MIN_HOLD_MS) delay(SplashTiming.MIN_HOLD_MS - held)
        ticker.cancel()

        if (ok) {
            phase = SplashPhase.RESOLVED
            delay(NovaMotion.EXIT_MS.toLong())
            resolved()
        } else {
            probe.cancel()
            phase = SplashPhase.FAILED
        }
    }

    val failed = phase == SplashPhase.FAILED
    val entering = elapsed < NovaMotion.ENTER_MS && attempt == 0
    val armed = phase == SplashPhase.CHECKING && elapsed >= SplashTiming.ARM_MS

    val markAlpha by animateFloatAsState(
        targetValue = when {
            failed -> 0.55f
            entering -> 0.4f
            else -> 1f
        },
        animationSpec = tween(NovaMotion.ENTER_MS, easing = NovaMotion.Emphasized),
        label = "markAlpha"
    )
    val markScale by animateFloatAsState(
        targetValue = when {
            entering -> 0.93f
            // Exit: 1.0 -> 0.94 under the crossfade to the destination.
            phase == SplashPhase.RESOLVED -> 0.94f
            else -> 1f
        },
        animationSpec = tween(NovaMotion.ENTER_MS, easing = NovaMotion.Emphasized),
        label = "markScale"
    )
    val wordAlpha by animateFloatAsState(
        targetValue = if (elapsed >= SplashTiming.HANDOFF_MS || failed) 1f else 0f,
        animationSpec = tween(NovaMotion.HANDOFF_MS),
        label = "wordAlpha"
    )

    // Liveness. Both eyes in phase; a fast launch never moves.
    val breathe = rememberInfiniteTransition(label = "eyes")
    val breath by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 0.38f,
        // 2400ms round trip, so 1200ms each way on Reverse.
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "breath"
    )
    val eyeAlpha = when {
        failed -> 0.2f
        armed -> breath
        else -> 0f
    }

    val statusLabel = when {
        phase != SplashPhase.CHECKING -> null
        elapsed >= SplashTiming.SLOW_MS -> "SLOW CONNECTION · STILL TRYING"
        elapsed >= SplashTiming.LABEL_MS -> "CHECKING CONNECTION"
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NovaSurface)
    ) {
        // Mark + wordmark. Optically centred: biased above true centre.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SplashMark(
                size = 132.dp,
                eyeAlpha = eyeAlpha,
                modifier = Modifier.graphicsLayer {
                    alpha = markAlpha
                    scaleX = markScale
                    scaleY = markScale
                }
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "NovaHost",
                style = NovaType.Wordmark,
                color = NovaTextPrimary,
                modifier = Modifier.graphicsLayer { alpha = wordAlpha }
            )
        }

        // First words. Low, quiet, mono.
        if (statusLabel != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 112.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                StaggerIn(delayMs = 0, key = statusLabel, rise = 4.dp) {
                    NovaMonoText(statusLabel, style = NovaMonoStatus)
                }
            }
        }

        if (failed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 40.dp, end = 40.dp, bottom = 76.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No internet connection",
                        style = NovaType.OptionLabel,
                        color = NovaTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "NovaHost needs a connection to start. Turn on Wi-Fi or mobile data, then try again.",
                        style = NovaType.BodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = NovaTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(22.dp))
                    NovaCtaButton(
                        label = "Open settings",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                    NovaGhostButton(
                        label = "Try again",
                        onClick = { attempt++ },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Two gates, both cheap. The transport says a network exists and Android has
 * validated it; the socket says something upstream actually answers. Either one
 * alone lies -- a captive portal validates and then swallows every request, and
 * "connected, no internet" Wi-Fi reports a transport that goes nowhere.
 */
private fun isNetworkReachable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    val hasTransport = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    if (!hasTransport) return false
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("1.1.1.1", 443), SplashTiming.PROBE_MS.toInt())
            true
        }
    } catch (e: Exception) {
        false
    }
}
