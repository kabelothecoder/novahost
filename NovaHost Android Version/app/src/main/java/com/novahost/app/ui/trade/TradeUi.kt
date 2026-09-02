package com.novahost.app.ui.trade

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.sdk.BotStatus
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.service.TradeFeed
import com.novahost.app.ui.home.PulsingDot
import com.novahost.app.ui.home.onArtFloor
import com.novahost.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The robot's status panel and the trade takeover, in one place.
 *
 * Both surfaces that show "what is my robot doing" render from here -- the Home
 * sheet and the floating bubble. They were separate implementations of the same
 * card, which is how one of them ended up printing the raw `SYSTEM_ADMIN`
 * placeholder as an account name while the other filtered it out.
 */

private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// ─── Shared bits ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = HomeTextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
        modifier = modifier
    )
}

/** Colour for an outcome. Semantic only -- never the mentor's accent. */
private fun TradeFeed.TradeEvent.tone(): Color = when (phase) {
    TradeFeed.Phase.FILLED -> HomeLive
    TradeFeed.Phase.REJECTED -> HomeSell
    TradeFeed.Phase.SKIPPED -> HomeAccentAmber
    else -> HomeAccentBlue
}

private fun TradeFeed.TradeEvent.outcomeLabel(): String = when (phase) {
    TradeFeed.Phase.FILLED -> "FILLED"
    TradeFeed.Phase.REJECTED -> "REJECTED"
    TradeFeed.Phase.SKIPPED -> "SKIPPED"
    TradeFeed.Phase.SENDING -> "SENDING"
    TradeFeed.Phase.SIZING -> "SIZING"
    TradeFeed.Phase.RECEIVED -> "RECEIVED"
}

// ─── The panel ───────────────────────────────────────────────────────────────

/**
 * @param onStop null hides the stop control -- the bubble shows status without
 *        offering to stop the robot from over another app, where a mis-tap is
 *        expensive and unrecoverable.
 */
@Composable
fun ActiveRobotPanel(
    robotName: String,
    accountLabel: String?,
    status: BotStatus,
    accent: Color = HomeAccentBlue,
    onStop: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val history by TradeFeed.history.collectAsState()
    val logs by MetaAPIManager.logs.collectAsState()
    var showRawLog by remember { mutableStateOf(false) }

    val safeAccent = accent.onArtFloor()
    val running = status == BotStatus.RUNNING

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(HomeCanvas)
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 22.dp)
    ) {
        // ---- Header ----------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("ACTIVE ROBOT", Modifier.weight(1f))
            if (onDismiss != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(HomeSurface)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = HomeTextSecondary, fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Identity --------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HomeSurfaceSunken)
                .border(1.dp, HomeBorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                robotName.ifBlank { "Your robot" },
                color = HomeTextBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(color = if (running) HomeLive else HomeTextDim, animate = running)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (running) "Running" else status.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    color = if (running) HomeLive else HomeTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // The account is only shown when it is a real one. The old sheet
                // printed the SYSTEM_ADMIN placeholder verbatim, which read to
                // users as though their trades were going somewhere else.
                val account = accountLabel
                    ?.takeUnless { it.isBlank() || it == "SYSTEM_ADMIN" }
                if (account != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        account,
                        color = HomeTextValue,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Trade feed ------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("ENTRY LOG", Modifier.weight(1f))
            Text(
                if (showRawLog) "Hide terminal" else "Terminal",
                color = safeAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showRawLog = !showRawLog }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (history.isEmpty()) {
            Text(
                if (running) "Waiting for your mentor's next call."
                else "Start the robot to begin receiving trades.",
                color = HomeTextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                history.forEach { event -> TradeRow(event) }
            }
        }

        // ---- Raw terminal ----------------------------------------------------
        AnimatedVisibility(
            visible = showRawLog,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HomeSurfaceWell)
                    .border(1.dp, HomeBorderFaint, RoundedCornerShape(12.dp))
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                logs.takeLast(80).forEach {
                    Text(
                        it,
                        color = HomeTextValue,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        if (onStop != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    // Pill, per the design system. Every primary action is one.
                    .clip(CircleShape)
                    .background(if (running) HomeSell else safeAccent)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (running) "STOP ROBOT" else "START ROBOT",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun TradeRow(event: TradeFeed.TradeEvent) {
    val tone = event.tone()
    val buy = event.side.equals("BUY", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background((if (buy) HomeLive else HomeSell).copy(alpha = 0.16f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    event.side.uppercase(),
                    color = if (buy) HomeLive else HomeSell,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }

            Spacer(Modifier.width(9.dp))
            Text(
                event.pair,
                color = HomeTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            event.volume?.let {
                Spacer(Modifier.width(7.dp))
                Text(
                    "$it lots",
                    color = HomeTextDim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                event.outcomeLabel(),
                color = tone,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }

        // The reason only earns a line when there is something to act on. A fill
        // needs no explanation; a refusal always does.
        if (event.isFailure && !event.message.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                event.message,
                color = HomeTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(5.dp))
        Text(
            clock.format(Date(event.at)),
            color = HomeTextFaint,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─── The takeover ────────────────────────────────────────────────────────────

/**
 * Full-screen state of a trade as it lands.
 *
 * Shown wherever the robot is working -- in the app, and over other apps via the
 * bubble's overlay window. It stays up through the outcome rather than vanishing
 * when the broker answers, because a takeover that disappeared at that moment
 * would show the user a spinner and never the result.
 *
 * Auto-dismisses so it can never strand somebody in another app behind a screen
 * they cannot get past; a fill clears quickly, a failure lingers because there
 * is something to read.
 */
@Composable
fun TradeTakeover(
    accent: Color = HomeAccentBlue,
    onDismiss: () -> Unit
) {
    val event by TradeFeed.current.collectAsState()
    val current = event ?: return

    val tone = when (current.phase) {
        TradeFeed.Phase.FILLED -> HomeLive
        TradeFeed.Phase.REJECTED -> HomeSell
        TradeFeed.Phase.SKIPPED -> HomeAccentAmber
        else -> accent.onArtFloor()
    }

    LaunchedEffect(current.id, current.phase) {
        if (current.isSettled) {
            kotlinx.coroutines.delay(if (current.isFailure) 6_000L else 3_200L)
            onDismiss()
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )
    val settledScale by animateFloatAsState(
        targetValue = if (current.isSettled) 1f else 0.94f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "settle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF20A0A10))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Expanding ring, only while the broker has not answered.
                if (!current.isSettled) {
                    Box(
                        modifier = Modifier
                            .size((96 + ring * 84).dp)
                            .alpha((1f - ring) * 0.5f)
                            .clip(CircleShape)
                            .border(2.dp, tone, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(settledScale)
                        .clip(CircleShape)
                        .background(tone.copy(alpha = 0.14f))
                        .border(2.dp, tone, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (current.phase) {
                            TradeFeed.Phase.FILLED -> "✓"
                            TradeFeed.Phase.REJECTED -> "✕"
                            TradeFeed.Phase.SKIPPED -> "–"
                            else -> current.side.take(1).uppercase()
                        },
                        color = tone,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                current.outcomeLabel(),
                color = tone,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "${current.side.uppercase()} ${current.pair}",
                color = HomeTextBright,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            current.volume?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$it lots" + (current.orderType
                        ?.takeIf { t -> !t.equals("MARKET", true) }
                        ?.let { t -> "  ·  ${t.lowercase()}" } ?: ""),
                    color = HomeTextValue,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!current.message.isNullOrBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    current.message,
                    color = HomeTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "Tap to dismiss",
                color = HomeTextFaint,
                fontSize = 11.sp
            )
        }
    }
}
