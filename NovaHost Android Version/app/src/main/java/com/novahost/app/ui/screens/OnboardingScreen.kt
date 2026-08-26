package com.novahost.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.novahost.app.R
import com.novahost.app.navigation.Routes
import com.novahost.app.ui.components.LightStatusBarEffect
import com.novahost.app.ui.components.NovaCtaButton
import com.novahost.app.ui.components.NovaDot
import com.novahost.app.ui.components.NovaMonoText
import com.novahost.app.ui.components.NovaProgressRail
import com.novahost.app.ui.components.SplashMark
import com.novahost.app.ui.components.StaggerIn
import com.novahost.app.ui.theme.NovaAccentSelected
import com.novahost.app.ui.theme.NovaArtBrush
import com.novahost.app.ui.theme.NovaBorderInput
import com.novahost.app.ui.theme.NovaBorderSoft
import com.novahost.app.ui.theme.NovaBorderStrong
import com.novahost.app.ui.theme.NovaCandleIdle
import com.novahost.app.ui.theme.NovaDangerSoft
import com.novahost.app.ui.theme.NovaDangerText
import com.novahost.app.ui.theme.NovaDangerTint
import com.novahost.app.ui.theme.NovaFlowLine
import com.novahost.app.ui.theme.NovaMotion
import com.novahost.app.ui.theme.NovaPhoneSleep
import com.novahost.app.ui.theme.NovaPlaceholderFill
import com.novahost.app.ui.theme.NovaShadow
import com.novahost.app.ui.theme.NovaShadowRaised
import com.novahost.app.ui.theme.NovaShapes
import com.novahost.app.ui.theme.NovaSkeleton
import com.novahost.app.ui.theme.NovaSuccessSoft
import com.novahost.app.ui.theme.NovaSuccessText
import com.novahost.app.ui.theme.NovaSuccessTint
import com.novahost.app.ui.theme.NovaSurface
import com.novahost.app.ui.theme.NovaSurfaceField
import com.novahost.app.ui.theme.NovaTextDisabled
import com.novahost.app.ui.theme.NovaTextMuted
import com.novahost.app.ui.theme.NovaTextPrimary
import com.novahost.app.ui.theme.NovaTextSecondary
import com.novahost.app.ui.theme.NovaType
import com.novahost.app.ui.theme.SoftLightBlue
import com.novahost.app.ui.theme.SoftLightPurple
import kotlinx.coroutines.delay

/**
 * ONBOARDING -- 7 steps, one structure.
 *
 * Every step is the same two-part frame: an art pane on the F1F5FA -> FFFFFF
 * gradient, and a white sheet that rises 26dp over it carrying STEP NN, a title,
 * a body and exactly one primary action. Steps 05 and 06 hold their CTA grey
 * until they validate; step 07 holds it until the licence has bound.
 *
 * Copy is verbatim from the design. Treat it as content, not placeholder.
 */
private const val STEP_COUNT = 7

private val STEP_TITLES = listOf(
    "Welcome to NovaHost.",
    "AI-Powered Vision.",
    "Connect Any Broker.",
    "Silent Precision.",
    "What should it call you?",
    "How do you trade?",
    "Securing Your Vault."
)

private data class TradingStyle(val id: String, val label: String, val note: String)

private val TRADING_STYLES = listOf(
    TradingStyle("Scalp", "Scalp", "Minutes in, minutes out"),
    TradingStyle("Day", "Day", "Opened and closed same session"),
    TradingStyle("Swing", "Swing", "Held for days at a time")
)

private const val NAME_MAX = 18

@Composable
fun OnboardingScreen(
    navController: NavHostController,
    onFinished: (() -> Unit)? = null
) {
    LightStatusBarEffect()

    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == 6) {
            locked = false
            delay(NovaMotion.LOCK_MS.toLong())
            locked = true
        }
    }

    val validity = nameValidity(name)
    val nameOk = validity == NameState.OK
    val showNameError = touched && (validity == NameState.CHARS || validity == NameState.SHORT)

    fun advance() {
        if (step < STEP_COUNT - 1) step++
    }

    fun finish() {
        persistOnboarding(context, name.trim(), style)
        if (onFinished != null) {
            onFinished()
        } else {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NovaSurface)
    ) {
        NovaProgressRail((step + 1).toFloat() / STEP_COUNT)

        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> StepFrame(
                    stepIndex = 0,
                    body = "Institutional-grade trading technology, built for your pocket. Let's get your engine started.",
                    art = { WelcomeArt() },
                    action = { NovaCtaButton("Start Setup", onClick = { advance() }) }
                )

                1 -> StepFrame(
                    stepIndex = 1,
                    body = "Your mentor hosts a bot with AI-powered vision. Send it a screenshot of any chart and it reads the setup back to you — entry, Stop Loss and Take Profit.",
                    art = { VisionArt() },
                    action = { NovaCtaButton("Next", onClick = { advance() }) }
                )

                2 -> StepFrame(
                    stepIndex = 2,
                    body = "NovaHost doesn't lock you in. Bring the MT4 or MT5 account you already trade with, and keep it.",
                    art = { BrokerArt() },
                    action = { NovaCtaButton("Next", onClick = { advance() }) }
                )

                3 -> StepFrame(
                    stepIndex = 3,
                    body = "Close the app, let the screen go dark, put the phone in your pocket. Your bot keeps reading the chart without it.",
                    art = { SilentPrecisionArt() },
                    action = { NovaCtaButton("Next", onClick = { advance() }) }
                )

                4 -> StepFrame(
                    stepIndex = 4,
                    body = "Your display name is how the bot addresses you in every notification it sends.",
                    artHeight = 196.dp,
                    sheetFillsRest = true,
                    art = { NotificationPreviewArt(name = name.trim(), valid = nameOk) },
                    content = {
                        DisplayNameField(
                            value = name,
                            onValueChange = { name = it.take(NAME_MAX); touched = true },
                            error = showNameError,
                            valid = nameOk,
                            helper = when {
                                showNameError && validity == NameState.CHARS ->
                                    "Letters and numbers only — no symbols."
                                showNameError -> "Use at least 2 characters."
                                else -> "Shown in notifications, never to anyone else."
                            }
                        )
                    },
                    action = { NovaCtaButton("Continue", enabled = nameOk, onClick = { advance() }) }
                )

                5 -> StepFrame(
                    stepIndex = 5,
                    body = "We'll pre-configure your chart scanner based on your style.",
                    artHeight = 172.dp,
                    sheetFillsRest = true,
                    art = { CandleArt() },
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TRADING_STYLES.forEach { option ->
                                StyleOptionRow(
                                    option = option,
                                    selected = style == option.id,
                                    onSelect = { style = option.id }
                                )
                            }
                        }
                    },
                    action = { NovaCtaButton("Continue", enabled = style != null, onClick = { advance() }) }
                )

                else -> StepFrame(
                    stepIndex = 6,
                    title = if (locked) "Vault secured." else STEP_TITLES[6],
                    body = if (locked)
                        "This NovaHost license is now locked to this device. It will not run anywhere else."
                    else
                        "For enterprise-grade security, this NovaHost license is locking exclusively to this physical device.",
                    art = { VaultArt(locked = locked) },
                    action = { NovaCtaButton("Enter NovaHost", enabled = locked, onClick = { finish() }) }
                )
            }
        }
    }
}

// ───────────────────────────── frame ─────────────────────────────

/**
 * The step frame. [artHeight] pins the art pane when a step needs room for
 * controls (05, 06); otherwise the art takes everything the sheet does not.
 */
@Composable
private fun StepFrame(
    stepIndex: Int,
    body: String,
    art: @Composable () -> Unit,
    action: @Composable () -> Unit,
    title: String = STEP_TITLES[stepIndex],
    artHeight: Dp? = null,
    sheetFillsRest: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = (if (artHeight != null) Modifier.height(artHeight) else Modifier.weight(1f))
                .fillMaxWidth()
                .background(NovaArtBrush),
            contentAlignment = Alignment.Center
        ) {
            ArtEnter(key = stepIndex) { art() }
        }

        Column(
            modifier = Modifier
                .then(if (sheetFillsRest) Modifier.weight(1f) else Modifier)
                .fillMaxWidth()
                .offset(y = (-26).dp)
                .clip(NovaShapes.Sheet)
                .background(NovaSurface)
                .padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 14.dp)
        ) {
            StaggerIn(NovaMotion.CopyDelays[0], key = stepIndex) {
                NovaMonoText("STEP " + (stepIndex + 1).toString().padStart(2, '0'))
            }
            Spacer(Modifier.height(12.dp))
            StaggerIn(NovaMotion.CopyDelays[1], key = title) {
                Text(title, style = NovaType.StepTitle, color = NovaTextPrimary)
            }
            Spacer(Modifier.height(12.dp))
            StaggerIn(NovaMotion.CopyDelays[2], key = body) {
                Text(body, style = NovaType.Body, color = NovaTextSecondary)
            }
            if (content != null) {
                Spacer(Modifier.height(22.dp))
                content()
            }
            if (sheetFillsRest) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(26.dp))
            Spacer(Modifier.height(18.dp))
            StaggerIn(NovaMotion.CopyDelays[3], key = stepIndex) { action() }
        }
    }
}

/** Art enter: scale .94 -> 1, alpha 0 -> 1, 620ms emphasised. */
@Composable
private fun ArtEnter(key: Any?, content: @Composable () -> Unit) {
    var play by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { play = true }
    val p by animateFloatAsState(
        targetValue = if (play) 1f else 0f,
        animationSpec = tween(NovaMotion.ART_MS, easing = NovaMotion.Emphasized),
        label = "artEnter"
    )
    Box(
        Modifier.graphicsLayer {
            alpha = p
            val s = 0.94f + 0.06f * p
            scaleX = s
            scaleY = s
        }
    ) { content() }
}

// ───────────────────────────── 01 art ─────────────────────────────

@Composable
private fun WelcomeArt() {
    val pulse = rememberInfiniteTransition(label = "welcomeGlow")
    val glow by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(2100), RepeatMode.Reverse),
        label = "glow"
    )
    val float by pulse.animateFloat(
        initialValue = 0f,
        targetValue = -9f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "float"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(280.dp)
                .graphicsLayer {
                    scaleX = glow
                    scaleY = glow
                    alpha = 0.45f + (glow - 1f) * 2.5f
                }
                .background(
                    Brush.radialGradient(
                        listOf(
                            SoftLightPurple.copy(alpha = 0.34f),
                            SoftLightBlue.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        SplashMark(size = 248.dp, modifier = Modifier.graphicsLayer { translationY = float })
    }
}

// ───────────────────────────── 02 art ─────────────────────────────

@Composable
private fun VisionArt() {
    val sweep = rememberInfiniteTransition(label = "scan")
    val y by sweep.animateFloat(
        initialValue = -78f,
        targetValue = 294f,
        animationSpec = infiniteRepeatable(tween(2800)),
        label = "scanY"
    )
    val bracket by sweep.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "bracket"
    )
    Column(Modifier.width(236.dp)) {
        Box(
            Modifier
                .size(width = 236.dp, height = 294.dp)
                .shadow(24.dp, NovaShapes.ArtCard, ambientColor = NovaShadowRaised, spotColor = NovaShadowRaised)
                .clip(NovaShapes.ArtCard)
                .background(NovaSurface)
        ) {
            Image(
                painter = painterResource(R.drawable.mt5_chart),
                contentDescription = "MT5 chart",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Scan line: a band with a lit leading edge, sweeping top to bottom.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .graphicsLayer { translationY = y * density }
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, SoftLightBlue.copy(alpha = 0.16f))
                        )
                    )
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(SoftLightBlue)
                )
            }
            CornerBracket(Alignment.TopStart, bracket)
            CornerBracket(Alignment.TopEnd, bracket)
            CornerBracket(Alignment.BottomStart, bracket)
            CornerBracket(Alignment.BottomEnd, bracket)
        }
        // The card overlaps the chart's lower edge rather than sitting under it.
        SignalCard(Modifier.offset(y = (-46).dp))
    }
}

@Composable
private fun CornerBracket(alignment: Alignment, alpha: Float) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(alignment)
                .padding(12.dp)
                .size(26.dp)
                .graphicsLayer { this.alpha = alpha }
                .border(2.5.dp, SoftLightBlue, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun SignalCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(16.dp, NovaShapes.ArtCardSmall, ambientColor = NovaShadowRaised, spotColor = NovaShadowRaised)
            .clip(NovaShapes.ArtCardSmall)
            .background(NovaSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("EURUSD", style = NovaType.ArtHeading, color = NovaTextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NovaDot(7.dp, NovaSuccessSoft)
                Spacer(Modifier.width(6.dp))
                Text("Buy setup", style = NovaType.Tag, color = NovaSuccessText)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PriceCell("Entry", "1.0842")
            PriceCell("Stop", "1.0818")
            PriceCell("Target", "1.0897")
        }
    }
}

@Composable
private fun PriceCell(label: String, value: String) {
    Column {
        Text(label, style = NovaType.Caption, color = NovaTextMuted)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = NovaType.Tag.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = NovaTextPrimary
        )
    }
}

// ───────────────────────────── 03 art ─────────────────────────────

@Composable
private fun BrokerArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrokerTile(R.drawable.broker_exness, 55.dp, "Exness")
            BrokerTile(R.drawable.broker_trade245, 71.dp, "Trade245")
            BrokerTile(R.drawable.broker_xm, 62.dp, "XM")
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrokerTile(R.drawable.broker_razor, 115.dp, "Razor Markets")
            BrokerTile(R.drawable.broker_justmarkets, 82.dp, "JustMarkets", bordered = true)
        }
        FlowArrow()
        SplashMark(size = 124.dp)
    }
}

/**
 * One broker tile.
 *
 * Each [width] is the logo's own aspect ratio at the shared 54dp height -- 55dp
 * for the square Exness mark, 115dp for the wide Razor lockup -- so the artwork
 * is never cropped or letterboxed and every tile optically matches its
 * neighbours despite being a different size.
 */
@Composable
private fun BrokerTile(res: Int, width: Dp, label: String, bordered: Boolean = false) {
    Box(
        Modifier
            .size(width = width, height = 54.dp)
            .shadow(8.dp, RoundedCornerShape(15.dp), ambientColor = NovaShadow, spotColor = NovaShadow)
            .clip(RoundedCornerShape(15.dp))
            .background(NovaSurface)
            .then(
                // JustMarkets is white to its edges, so without a hairline it
                // dissolves into the tile it is sitting on.
                if (bordered) Modifier.border(1.dp, NovaBorderSoft, RoundedCornerShape(15.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Dashed connector: brokers above, the host below. */
@Composable
private fun FlowArrow() {
    Canvas(Modifier.size(width = 30.dp, height = 52.dp)) {
        val cx = size.width / 2f
        drawLine(
            color = NovaFlowLine,
            start = Offset(cx, 2f),
            end = Offset(cx, size.height * 0.77f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
        )
        val tipY = size.height * 0.83f
        drawLine(
            NovaFlowLine,
            Offset(cx - 7.dp.toPx(), tipY - 8.dp.toPx()),
            Offset(cx, tipY),
            1.5.dp.toPx(),
            StrokeCap.Round
        )
        drawLine(
            NovaFlowLine,
            Offset(cx + 7.dp.toPx(), tipY - 8.dp.toPx()),
            Offset(cx, tipY),
            1.5.dp.toPx(),
            StrokeCap.Round
        )
    }
}

// ───────────────────────────── 04 art ─────────────────────────────

@Composable
private fun SilentPrecisionArt() {
    val loop = rememberInfiniteTransition(label = "silent")
    val dim by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100), RepeatMode.Reverse),
        label = "dim"
    )
    val tick by loop.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "tick"
    )
    Box(Modifier.size(width = 300.dp, height = 314.dp)) {
        // Chart, still running.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp)
                .size(width = 224.dp, height = 262.dp)
                .shadow(22.dp, NovaShapes.ArtCard, ambientColor = NovaShadowRaised, spotColor = NovaShadowRaised)
                .clip(NovaShapes.ArtCard)
                .background(NovaSurface)
        ) {
            Image(
                painter = painterResource(R.drawable.mt5_chart),
                contentDescription = "Chart still running",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 74.dp)
                    .height(56.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TickBar(34.dp, SoftLightBlue, tick)
                TickBar(50.dp, SoftLightPurple, 1.55f - tick)
                TickBar(26.dp, SoftLightBlue, tick)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .shadow(6.dp, NovaShapes.Pill, ambientColor = NovaShadow, spotColor = NovaShadow)
                    .clip(NovaShapes.Pill)
                    .background(NovaSurface.copy(alpha = 0.94f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaDot(7.dp, NovaSuccessSoft, alpha = 0.4f + 0.6f * tick)
                Spacer(Modifier.width(7.dp))
                Text("Still trading", style = NovaType.BodySmall, color = NovaSuccessText)
            }
        }

        // Phone, asleep.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(y = (-16).dp)
                .rotate(5f)
                .size(width = 112.dp, height = 206.dp)
                .shadow(22.dp, RoundedCornerShape(26.dp), ambientColor = NovaShadowRaised, spotColor = NovaShadowRaised)
                .clip(RoundedCornerShape(26.dp))
                .background(NovaSurface)
                .border(3.dp, NovaTextPrimary, RoundedCornerShape(26.dp))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 11.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.54f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(NovaSkeleton))
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(NovaPlaceholderFill))
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(NovaPlaceholderFill))
                Box(Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(NovaSkeleton))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dim }
                    .background(NovaPhoneSleep)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(width = 44.dp, height = 3.dp)
                    .clip(NovaShapes.Pill)
                    .background(NovaTextMuted.copy(alpha = 0.55f))
            )
        }

        // Caption chip.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .shadow(10.dp, NovaShapes.Pill, ambientColor = NovaShadow, spotColor = NovaShadow)
                .clip(NovaShapes.Pill)
                .background(NovaSurface)
                .padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovaDot(11.dp, NovaTextMuted)
            Spacer(Modifier.width(9.dp))
            Text("Phone asleep", style = NovaType.Ghost, color = NovaTextSecondary)
        }
    }
}

@Composable
private fun TickBar(height: Dp, color: Color, scale: Float) {
    Box(
        Modifier
            .width(6.dp)
            .height(height)
            .graphicsLayer {
                scaleY = scale.coerceIn(0.4f, 1f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

// ───────────────────────────── 05 art + field ─────────────────────────────

@Composable
private fun NotificationPreviewArt(name: String, valid: Boolean) {
    Row(
        modifier = Modifier
            .width(268.dp)
            .padding(top = 30.dp)
            .shadow(16.dp, NovaShapes.ArtCardSmall, ambientColor = NovaShadow, spotColor = NovaShadow)
            .clip(NovaShapes.ArtCardSmall)
            .background(NovaSurface)
            .border(1.dp, NovaBorderSoft, NovaShapes.ArtCardSmall)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        SplashMark(size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NovaHost",
                    style = NovaType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    color = NovaTextPrimary
                )
                Spacer(Modifier.width(7.dp))
                NovaMonoText("now")
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (valid && name.isNotBlank())
                    "$name, your Day setup on EURUSD just filled."
                else
                    "Your setup on EURUSD just filled.",
                style = NovaType.BodySmall,
                color = NovaTextSecondary
            )
        }
    }
}

private enum class NameState { EMPTY, CHARS, SHORT, OK }

private val NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9 .'-]*$")

private fun nameValidity(raw: String): NameState {
    val v = raw.trim()
    if (v.isEmpty()) return NameState.EMPTY
    if (!NAME_PATTERN.matches(v)) return NameState.CHARS
    if (v.length < 2) return NameState.SHORT
    return NameState.OK
}

@Composable
private fun DisplayNameField(
    value: String,
    onValueChange: (String) -> Unit,
    error: Boolean,
    valid: Boolean,
    helper: String
) {
    // One shake per transition into the error state, not a loop: the field
    // reports a mistake once and then stays still to be corrected.
    val shake by animateFloatAsState(
        targetValue = if (error) 1f else 0f,
        animationSpec = tween(380, easing = NovaMotion.Emphasized),
        label = "shake"
    )
    val density = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val amp = with(density) { 4.dp.toPx() }
                    translationX = amp * kotlin.math.sin(shake * 12f) * (1f - shake)
                }
                .height(56.dp)
                .clip(NovaShapes.OptionRow)
                .background(if (error) NovaDangerTint else NovaSurfaceField)
                .border(
                    width = 1.5.dp,
                    color = when {
                        error -> NovaDangerSoft
                        valid -> SoftLightBlue
                        else -> NovaBorderInput
                    },
                    shape = NovaShapes.OptionRow
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Display name", style = NovaType.FieldValue, color = NovaTextDisabled)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = NovaType.FieldValue.copy(color = NovaTextPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (error) {
                Spacer(Modifier.width(10.dp))
                StateGlyph(NovaDangerSoft, "!")
            } else if (valid) {
                Spacer(Modifier.width(10.dp))
                StateGlyph(NovaSuccessSoft, "✓")
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                helper,
                style = NovaType.BodySmall,
                color = if (error) NovaDangerText else NovaTextMuted,
                modifier = Modifier.weight(1f, fill = false)
            )
            NovaMonoText("${value.length}/$NAME_MAX", color = NovaTextDisabled)
        }
    }
}

@Composable
private fun StateGlyph(fill: Color, glyph: String) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = NovaType.Caption.copy(fontWeight = FontWeight.SemiBold),
            color = NovaSurface,
            textAlign = TextAlign.Center
        )
    }
}

// ───────────────────────────── 06 art + options ─────────────────────────────

@Composable
private fun CandleArt() {
    val heights = listOf(0.36f, 0.58f, 0.44f, 0.76f, 0.52f, 1f, 0.64f)
    val colors = listOf(
        NovaCandleIdle, NovaCandleIdle, NovaCandleIdle,
        SoftLightBlue, NovaCandleIdle, SoftLightPurple, NovaCandleIdle
    )
    Row(
        modifier = Modifier.height(56.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        heights.forEachIndexed { i, h ->
            var play by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(120L + i * 50L); play = true }
            val p by animateFloatAsState(
                targetValue = if (play) 1f else 0.15f,
                animationSpec = tween(520, easing = NovaMotion.Emphasized),
                label = "candle$i"
            )
            Box(
                Modifier
                    .width(7.dp)
                    .fillMaxHeight(h)
                    .graphicsLayer {
                        scaleY = p
                        alpha = p
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors[i])
            )
        }
    }
}

@Composable
private fun StyleOptionRow(option: TradingStyle, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(NovaShapes.OptionRow)
            .background(if (selected) NovaAccentSelected else NovaSurface)
            .border(1.5.dp, if (selected) SoftLightBlue else NovaBorderSoft, NovaShapes.OptionRow)
            .clickable { onSelect() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(option.label, style = NovaType.OptionLabel, color = NovaTextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(option.note, style = NovaType.BodySmall, color = NovaTextSecondary)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) SoftLightBlue else NovaSurface)
                .border(1.5.dp, if (selected) SoftLightBlue else NovaBorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) NovaDot(8.dp, NovaSurface)
        }
    }
}

// ───────────────────────────── 07 art ─────────────────────────────

@Composable
private fun VaultArt(locked: Boolean) {
    val loop = rememberInfiniteTransition(label = "vault")
    val spin by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000)),
        label = "spin"
    )
    val float by loop.animateFloat(
        initialValue = 0f,
        targetValue = -9f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "vaultFloat"
    )
    val pulse by loop.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "vaultPulse"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
            val ringColor = if (locked) NovaSuccessSoft.copy(alpha = 0.35f) else SoftLightBlue.copy(alpha = 0.55f)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = if (locked) 0f else spin }
            ) {
                val stroke = 1.5.dp.toPx()
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2f - stroke,
                    style = Stroke(
                        width = stroke,
                        pathEffect = if (locked) null
                        else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 8.dp.toPx()))
                    )
                )
                drawCircle(
                    color = if (locked) NovaSuccessSoft.copy(alpha = 0.2f) else SoftLightPurple.copy(alpha = 0.28f),
                    radius = size.minDimension / 2f - 22.dp.toPx(),
                    style = Stroke(width = stroke)
                )
            }
            SplashMark(
                size = 150.dp,
                modifier = Modifier.graphicsLayer { translationY = if (locked) 0f else float }
            )
            if (locked) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 26.dp, bottom = 30.dp)
                        .size(46.dp)
                        .shadow(10.dp, CircleShape, ambientColor = NovaShadowRaised, spotColor = NovaShadowRaised)
                        .clip(CircleShape)
                        .background(NovaSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(width = 26.dp, height = 22.dp)) {
                        val w = size.width
                        val h = size.height
                        drawLine(
                            NovaSuccessSoft,
                            Offset(w * 0.15f, h * 0.52f),
                            Offset(w * 0.38f, h * 0.77f),
                            3.4.dp.toPx(),
                            StrokeCap.Round
                        )
                        drawLine(
                            NovaSuccessSoft,
                            Offset(w * 0.38f, h * 0.77f),
                            Offset(w * 0.85f, h * 0.21f),
                            3.4.dp.toPx(),
                            StrokeCap.Round
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        if (locked) {
            Row(
                modifier = Modifier
                    .clip(NovaShapes.Pill)
                    .background(NovaSuccessTint)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaDot(7.dp, NovaSuccessSoft)
                Spacer(Modifier.width(8.dp))
                NovaMonoText("DEVICE BOUND", color = NovaSuccessText)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NovaDot(7.dp, SoftLightBlue, alpha = pulse)
                NovaDot(7.dp, SoftLightBlue, alpha = 1.35f - pulse)
                NovaDot(7.dp, SoftLightBlue, alpha = pulse)
            }
        }
    }
}

// ───────────────────────────── persistence ─────────────────────────────

/**
 * What the flow collected, into the prefs file the rest of the app reads.
 *
 * The name goes to `trader_name`, NOT `display_name`. `display_name` is already
 * taken: licence activation writes the ROBOT's name there, and MainViewModel,
 * NotificationHelper and NovaHostPulseService all read it back as the robot's
 * identity. Writing the human's name into that key would rename the user's robot
 * to the user. `trader_name` is the key the activation flow already uses for the
 * person, so this stays consistent with it.
 */
private fun persistOnboarding(context: Context, name: String, style: String?) {
    context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE).edit().apply {
        putString("trader_name", name.ifBlank { "Trader" })
        if (style != null) putString("trading_style", style)
        putBoolean("onboarding_complete", true)
        apply()
    }
}
