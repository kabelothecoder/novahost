package com.novahost.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.ui.components.NovaButton
import com.novahost.app.ui.components.NovaButtonVariant
import com.novahost.app.ui.theme.Crimson
import kotlinx.coroutines.delay

/**
 * Where a broker-link attempt has got to.
 *
 * The screen used to report outcomes through a snackbar, which is the wrong
 * instrument twice over: it dismisses itself, and a link legitimately takes up
 * to two minutes, so the user is rarely still watching when it fires. An
 * outcome this consequential stays on screen until they act on it.
 */
sealed interface LinkState {
    data object Idle : LinkState

    data class Working(
        val phase: MetaAPIManager.LinkPhase,
        val startedAt: Long
    ) : LinkState

    data class Linked(
        val server: String,
        val platform: String,
        val account: String
    ) : LinkState

    data class Failed(val reason: String) : LinkState
}

@Composable
fun LinkStatusPanel(
    state: LinkState,
    accent: Color,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    when (state) {
        is LinkState.Idle -> Unit
        is LinkState.Working -> LinkWorkingPanel(state, accent)
        is LinkState.Linked -> LinkSuccessPanel(state, onContinue)
        is LinkState.Failed -> LinkFailurePanel(state, onRetry)
    }
}

@Composable
private fun LinkWorkingPanel(state: LinkState.Working, accent: Color) {
    // A live second count. An indeterminate bar on its own cannot distinguish
    // "working" from "hung", and this wait is long enough that the user will
    // start to wonder which one they are looking at.
    var elapsed by remember(state.startedAt) { mutableStateOf(0L) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            elapsed = (System.currentTimeMillis() - state.startedAt) / 1000
            delay(1000)
        }
    }

    val headline = when (state.phase) {
        MetaAPIManager.LinkPhase.REGISTERING -> "Connecting to your broker"
        MetaAPIManager.LinkPhase.VERIFYING -> "Confirming the connection"
    }

    val detail = when (state.phase) {
        MetaAPIManager.LinkPhase.REGISTERING ->
            "Your broker is authenticating the login. This can take up to two minutes — " +
                "keep the app open."
        MetaAPIManager.LinkPhase.VERIFYING ->
            "The network dropped before we heard back. Checking whether the account " +
                "linked anyway — usually it did."
    }

    Surface(
        color = accent.copy(alpha = 0.07f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = accent,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    headline,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatElapsed(elapsed),
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(12.dp))

            IndeterminateBar(accent = accent)

            Spacer(Modifier.height(12.dp))

            Text(
                detail,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/** A looping sweep. Indeterminate on purpose — the server reports no percentage. */
@Composable
private fun IndeterminateBar(accent: Color) {
    val transition = rememberInfiniteTransition(label = "linkSweep")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val trackWidth = maxWidth
            val bandWidth = trackWidth * 0.38f
            Box(
                modifier = Modifier
                    .offset(x = (trackWidth + bandWidth) * progress - bandWidth)
                    .width(bandWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, accent, Color.Transparent)
                        )
                    )
            )
        }
    }
}

@Composable
private fun LinkSuccessPanel(state: LinkState.Linked, onContinue: () -> Unit) {
    val green = Color(0xFF3DD68C)

    Surface(
        color = green.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, green.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = green,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Trading account linked",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            LinkDetailRow("Server", state.server)
            LinkDetailRow("Login", state.account)
            LinkDetailRow("Platform", state.platform)

            Spacer(Modifier.height(14.dp))

            Text(
                "You can start the robot now.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.5.sp
            )

            Spacer(Modifier.height(14.dp))

            NovaButton(
                text = "DONE",
                onClick = onContinue,
                variant = NovaButtonVariant.Primary,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LinkFailurePanel(state: LinkState.Failed, onRetry: () -> Unit) {
    Surface(
        color = Crimson.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Crimson.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Crimson,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Could not link this account",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                state.reason,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            NovaButton(
                text = "TRY AGAIN",
                onClick = onRetry,
                variant = NovaButtonVariant.Secondary,
                icon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LinkDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 11.5.sp)
        Text(
            value,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatElapsed(seconds: Long): String =
    if (seconds < 60) seconds.toString() + "s"
    else (seconds / 60).toString() + "m " + (seconds % 60).toString() + "s"
