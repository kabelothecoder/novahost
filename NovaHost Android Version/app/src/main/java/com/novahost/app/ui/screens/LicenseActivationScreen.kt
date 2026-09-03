package com.novahost.app.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.novahost.app.R
import com.novahost.app.navigation.Routes
import com.novahost.app.sdk.LicenseActivationRequest
import com.novahost.app.sdk.LicenseActivationResponse
import com.novahost.app.sdk.NovaHostBackend
import com.novahost.app.ui.components.LightStatusBarEffect
import com.novahost.app.ui.components.StaggerIn
import com.novahost.app.ui.theme.MonoFamily
import com.novahost.app.ui.theme.NovaAccentDeep
import com.novahost.app.ui.theme.NovaAccentSelected
import com.novahost.app.ui.theme.NovaBorderInput
import com.novahost.app.ui.theme.NovaCanvas
import com.novahost.app.ui.theme.NovaCtaBrush
import com.novahost.app.ui.theme.NovaDangerSoft
import com.novahost.app.ui.theme.NovaDangerText
import com.novahost.app.ui.theme.NovaDangerTint
import com.novahost.app.ui.theme.NovaDisabledFill
import com.novahost.app.ui.theme.NovaElevation.novaRaised
import com.novahost.app.ui.theme.NovaMotion
import com.novahost.app.ui.theme.NovaShapes
import com.novahost.app.ui.theme.NovaSuccessSoft
import com.novahost.app.ui.theme.NovaSuccessText
import com.novahost.app.ui.theme.NovaSuccessTint
import com.novahost.app.ui.theme.NovaSurface
import com.novahost.app.ui.theme.NovaSurfaceField
import com.novahost.app.ui.theme.NovaTextDisabled
import com.novahost.app.ui.theme.NovaTextMuted
import com.novahost.app.ui.theme.NovaTextOnAccent
import com.novahost.app.ui.theme.NovaTextPrimary
import com.novahost.app.ui.theme.NovaTrack
import com.novahost.app.ui.theme.NovaType
import com.novahost.app.ui.theme.SoftLightBlue
import com.novahost.app.ui.theme.SoftLightPurple
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// ─────────────────────────────────────────────────────────────────────────────
// Licence key shape
// ─────────────────────────────────────────────────────────────────────────────
//
// Four groups of four, e.g. NH26-A3F9-B2K1-Z0Q7. This is not a design choice --
// it is the shape `generate-license` actually emits: a two-letter plan code and
// a two-digit year, then three random base-36 segments. The design canvas drew
// three groups of five, which no key the portal has ever issued would fit, so
// the mask follows the generator rather than the mockup.
//
// State always holds the bare 16 characters. Dashes live only in the visual
// transformation, so nothing downstream has to strip them and the server sees
// exactly the string it stored.

private const val GROUP_SIZE = 4
private const val GROUP_COUNT = 4
private const val KEY_LENGTH = GROUP_SIZE * GROUP_COUNT

/** Drops anything that cannot appear in a key, and caps the length. */
private fun sanitiseKey(input: String): String =
    input.uppercase()
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(KEY_LENGTH)

private fun groupKey(raw: String): String = raw.chunked(GROUP_SIZE).joinToString("-")

private val KeyOffsets = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int =
        if (offset <= 0) 0 else offset + (offset - 1) / GROUP_SIZE

    override fun transformedToOriginal(offset: Int): Int =
        offset - offset / (GROUP_SIZE + 1)
}

private val KeyMask = VisualTransformation { text ->
    TransformedText(AnnotatedString(groupKey(text.text)), KeyOffsets)
}

/** Human name for the handset the key is about to be welded to. */
private fun deviceName(): String {
    val model = Build.MODEL.orEmpty().trim()
    val make = Build.MANUFACTURER.orEmpty().trim()
    val name = when {
        model.isEmpty() -> make.ifEmpty { "This device" }
        make.isEmpty() || model.startsWith(make, ignoreCase = true) -> model
        else -> "$make $model"
    }
    return name.replaceFirstChar { it.uppercase() }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen state
// ─────────────────────────────────────────────────────────────────────────────

/** What the licensed robot turned out to be, once the server said yes. */
private data class BoundRobot(
    val displayName: String,
    val avatarUrl: String?,
    val detail: String,
    val key: String
)

private sealed interface Phase {
    /** Typing. Covers empty, partial, and complete-but-unverified. */
    data object Entry : Phase
    data object Verifying : Phase
    data class Rejected(val message: String) : Phase
    data class Bound(val robot: BoundRobot) : Phase
}

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Licence key entry and device binding -- the gate between onboarding and Home.
 *
 * Two screens live here. The first carries the field; the second is the payoff.
 * A key does not merely unlock the app, it names and dresses one specific robot,
 * so the moment it resolves is the first time the user meets the thing they
 * bought. Navigating straight to Home, which is what this screen used to do,
 * threw that moment away.
 */
@Composable
fun LicenseActivationScreen(navController: NavController) {
    LightStatusBarEffect()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    val traderName = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("trader_name", null).orEmpty()
    }

    var rawKey by rememberSaveable { mutableStateOf("") }
    var phase by remember { mutableStateOf<Phase>(Phase.Entry) }
    // Bumped on every rejection so the card shakes again on a repeat failure.
    // Without it, a second wrong key would land in silence.
    var rejectTick by remember { mutableIntStateOf(0) }

    fun verify() {
        if (rawKey.length != KEY_LENGTH || phase is Phase.Verifying) return
        focus.clearFocus()
        phase = Phase.Verifying
        scope.launch {
            // The dashes go back on before the key leaves the app. State holds
            // the bare 16 characters so the mask can group them freely, but the
            // server stores the key exactly as generate-license emitted it --
            // LI26-J0AM-4JIM-C8D3 -- and matches on it verbatim, as does every
            // later lookup that reads it back out of prefs.
            val issuedKey = groupKey(rawKey)
            val result = activateLicence(context, issuedKey)
            if (result == null || !result.success) {
                // Fail closed: nothing stored, no navigation.
                rejectTick++
                phase = Phase.Rejected(
                    result?.error
                        ?: "Could not reach the licence server. Check your connection and try again."
                )
                return@launch
            }
            persistLicence(context, issuedKey, result, traderName)
            phase = Phase.Bound(
                BoundRobot(
                    displayName = result.display_name?.takeIf { it.isNotBlank() }
                        ?: result.product_name?.takeIf { it.isNotBlank() }
                        ?: "Your robot",
                    avatarUrl = result.avatar_url?.takeIf { it.isNotBlank() },
                    detail = result.product_code?.takeIf { it.isNotBlank() }
                        ?.let { "$it · installed" }
                        ?: "Installed",
                    key = rawKey
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NovaCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Crossfade(
            targetState = phase is Phase.Bound,
            animationSpec = tween(NovaMotion.COPY_MS, easing = NovaMotion.Emphasized),
            label = "activationPhase"
        ) { bound ->
            val current = phase
            if (bound && current is Phase.Bound) {
                BoundContent(
                    robot = current.robot,
                    onContinue = {
                        // Not Home: the licence is bound but the overlay grant
                        // the bubble needs has not been asked for yet, and this
                        // is the one moment where asking has a reason attached.
                        navController.navigate(Routes.PERMISSIONS) {
                            popUpTo(Routes.ACTIVATE) { inclusive = true }
                        }
                    }
                )
            } else {
                EntryContent(
                    rawKey = rawKey,
                    phase = current,
                    rejectTick = rejectTick,
                    onKeyChange = {
                        rawKey = sanitiseKey(it)
                        // Any edit clears a rejection. Leaving the red border up
                        // while the user fixes a typo reads as "still wrong".
                        if (phase is Phase.Rejected) phase = Phase.Entry
                    },
                    onPaste = {
                        val pasted = sanitiseKey(clipboard.getText()?.text.orEmpty())
                        if (pasted.isNotEmpty()) {
                            rawKey = pasted
                            if (phase is Phase.Rejected) phase = Phase.Entry
                        }
                    },
                    onVerify = { verify() },
                    onHelp = { navController.navigate(Routes.HELP_SUPPORT) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry -- idle / verifying / rejected
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EntryContent(
    rawKey: String,
    phase: Phase,
    rejectTick: Int,
    onKeyChange: (String) -> Unit,
    onPaste: () -> Unit,
    onVerify: () -> Unit,
    onHelp: () -> Unit
) {
    val complete = rawKey.length == KEY_LENGTH
    val rejected = phase as? Phase.Rejected
    val verifying = phase is Phase.Verifying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StaggerIn(delayMs = 0) { ShieldHero() }

            Spacer(Modifier.height(26.dp))

            StaggerIn(delayMs = NovaMotion.CopyDelays[0]) {
                Text(
                    text = "Activate Your Node",
                    style = NovaType.StepTitle.copy(fontSize = 31.sp, lineHeight = 35.sp),
                    color = NovaTextPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))

            StaggerIn(delayMs = NovaMotion.CopyDelays[1]) {
                Text(
                    text = "Enter your NovaHost license key. This will permanently bind " +
                        "your enterprise cloud engine to this physical device.",
                    style = NovaType.Body,
                    color = NovaTextMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(26.dp))

            KeyCard(
                rawKey = rawKey,
                complete = complete,
                verifying = verifying,
                rejectedMessage = rejected?.message,
                rejectTick = rejectTick,
                onKeyChange = onKeyChange,
                onPaste = onPaste,
                onVerify = onVerify
            )

            // The reassurance and the rejection are the same slot. Telling
            // someone their key was refused and that keys are precious, in the
            // same breath, reads as a lecture.
            if (rejected == null) {
                Spacer(Modifier.height(16.dp))
                StaggerIn(delayMs = NovaMotion.CopyDelays[3]) { SecurityNote() }
            }

            Spacer(Modifier.height(20.dp))
        }

        ActivationCta(
            filled = complete || verifying,
            enabled = complete && !verifying,
            busy = verifying,
            label = "Verify & Lock Device",
            busyLabel = "Verifying",
            onClick = onVerify
        )

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(NovaShapes.Pill)
                .clickable { onHelp() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Where do I find my license key?",
                style = NovaType.Ghost.copy(fontSize = 14.5.sp),
                color = NovaTextMuted
            )
        }
    }
}

/**
 * The lock tile: a 100dp white plate that drifts, over a soft accent bloom.
 *
 * The only ornament on the screen, and the reason it does not read as a form.
 */
@Composable
private fun ShieldHero() {
    val loop = rememberInfiniteTransition(label = "hero")
    val lift by loop.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroLift"
    )
    val bloom by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroBloom"
    )
    val liftPx = with(LocalDensity.current) { lift.dp.toPx() }

    Box(
        modifier = Modifier.height(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer {
                    // The design's bloom is a squashed ellipse. A radial brush is
                    // always circular, so it gets flattened here instead.
                    val pulse = 1f + 0.12f * bloom
                    scaleX = pulse
                    scaleY = 0.74f * pulse
                    alpha = 0.4f + 0.32f * bloom
                }
                .background(
                    Brush.radialGradient(
                        0.0f to SoftLightBlue.copy(alpha = 0.26f),
                        0.58f to SoftLightPurple.copy(alpha = 0.08f),
                        0.74f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = liftPx }
                .size(100.dp)
                .novaRaised(RoundedCornerShape(34.dp))
                .clip(RoundedCornerShape(34.dp))
                .background(NovaSurface),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(width = 46.dp, height = 52.dp)) {
                // Traced off the design's 46x52 viewBox, scaled to whatever the
                // density gives us rather than baked at one size.
                val sx = size.width / 46f
                val sy = size.height / 52f

                val shield = Path().apply {
                    moveTo(23f * sx, 2f * sy)
                    lineTo(43f * sx, 9f * sy)
                    lineTo(43f * sx, 25.5f * sy)
                    cubicTo(
                        43f * sx, 38.5f * sy,
                        34.5f * sx, 46.8f * sy,
                        23f * sx, 50f * sy
                    )
                    cubicTo(
                        11.5f * sx, 46.8f * sy,
                        3f * sx, 38.5f * sy,
                        3f * sx, 25.5f * sy
                    )
                    lineTo(3f * sx, 9f * sy)
                    close()
                }
                drawPath(shield, NovaTextPrimary)

                val shackle = Path().apply {
                    moveTo(18.4f * sx, 24.6f * sy)
                    lineTo(18.4f * sx, 20.4f * sy)
                    cubicTo(
                        18.4f * sx, 17.8f * sy,
                        20.5f * sx, 15.7f * sy,
                        23f * sx, 15.7f * sy
                    )
                    cubicTo(
                        25.5f * sx, 15.7f * sy,
                        27.6f * sx, 17.8f * sy,
                        27.6f * sx, 20.4f * sy
                    )
                    lineTo(27.6f * sx, 24.6f * sy)
                }
                drawPath(
                    path = shackle,
                    color = SoftLightBlue,
                    style = Stroke(width = 2.4f * sx, cap = StrokeCap.Round)
                )

                drawRoundRect(
                    color = SoftLightBlue,
                    topLeft = Offset(16.4f * sx, 24.4f * sy),
                    size = Size(13.2f * sx, 11.6f * sy),
                    cornerRadius = CornerRadius(3.2f * sx)
                )
            }
        }
    }
}

@Composable
private fun KeyCard(
    rawKey: String,
    complete: Boolean,
    verifying: Boolean,
    rejectedMessage: String?,
    rejectTick: Int,
    onKeyChange: (String) -> Unit,
    onPaste: () -> Unit,
    onVerify: () -> Unit
) {
    val rejected = rejectedMessage != null

    // A rejected key is told twice: colour, and movement. Colour alone is not a
    // signal every user receives.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(rejectTick) {
        if (rejectTick > 0) {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 380
                    0f at 0
                    -4f at 84
                    4f at 175
                    -2f at 274
                    0f at 380
                }
            )
        }
    }
    val shakePx = with(LocalDensity.current) { shake.value.dp.toPx() }

    val border = when {
        rejected -> NovaDangerSoft
        complete -> SoftLightBlue
        else -> NovaBorderInput
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shakePx }
            .novaRaised(NovaShapes.Card)
            .clip(NovaShapes.Card)
            .background(NovaSurface)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "License key",
                style = NovaType.Tag,
                color = NovaTextPrimary
            )
            Text(
                text = "${rawKey.length}/$KEY_LENGTH",
                style = NovaType.BodySmall,
                color = if (complete) SoftLightBlue else NovaTextDisabled
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(NovaShapes.OptionRow)
                .background(if (rejected) NovaDangerTint else NovaSurfaceField)
                .border(1.5.dp, border, NovaShapes.OptionRow)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = rawKey,
                onValueChange = onKeyChange,
                enabled = !verifying,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    letterSpacing = 1.sp,
                    color = NovaTextPrimary
                ),
                visualTransformation = KeyMask,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onVerify() }),
                cursorBrush = SolidColor(SoftLightBlue),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 18.dp),
                decorationBox = { field ->
                    if (rawKey.isEmpty()) {
                        Text(
                            text = "XXXX-XXXX-XXXX-XXXX",
                            fontFamily = MonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp,
                            color = NovaTextDisabled
                        )
                    }
                    field()
                }
            )

            if (rejected) {
                FieldBadge(NovaDangerSoft, Icons.Rounded.PriorityHigh, "Key rejected")
            } else if (complete && !verifying) {
                FieldBadge(NovaSuccessSoft, Icons.Rounded.Check, "Key complete")
            }
        }

        // Paste earns its place only while the field is short. Once the key is
        // complete it is one more thing between the user and the button.
        if (!complete && !rejected) {
            Spacer(Modifier.height(12.dp))
            PasteButton(onPaste)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = rejectedMessage
                ?: if (complete) {
                    "Format looks right. Verifying binds it to this device for good."
                } else {
                    "Four groups of four, letters and numbers. Dashes are added for you."
                },
            style = NovaType.BodySmall,
            color = if (rejected) NovaDangerText else NovaTextMuted
        )

        Spacer(Modifier.height(16.dp))
        CardHairline()
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(NovaCanvas),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = NovaTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Binding to this device",
                    style = NovaType.Tag.copy(fontSize = 13.5.sp),
                    color = NovaTextPrimary
                )
                Text(
                    text = "${deviceName()} · Android ${Build.VERSION.RELEASE}",
                    style = NovaType.BodySmall,
                    color = NovaTextMuted
                )
            }
        }
    }
}

@Composable
private fun FieldBadge(color: Color, icon: ImageVector, label: String) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = NovaTextOnAccent,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PasteButton(onPaste: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(NovaShapes.Pill)
            .background(NovaSurface)
            .border(1.5.dp, NovaBorderInput, NovaShapes.Pill)
            .clickable { onPaste() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentPaste,
            contentDescription = null,
            tint = NovaAccentDeep,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "Paste from clipboard",
            style = NovaType.Cta.copy(fontSize = 14.5.sp),
            color = NovaAccentDeep
        )
    }
}

@Composable
private fun SecurityNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NovaAccentSelected)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = NovaAccentDeep,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = "Treat your key like a password. It activates once, on one device.",
            style = NovaType.BodySmall,
            color = NovaAccentDeep
        )
    }
}

/**
 * The 56dp pill, carrying all of its states itself rather than swapping three
 * buttons in and out -- nothing below it reflows when the state changes.
 *
 * A disabled CTA loses the gradient, because in this system the gradient is
 * what says "this is the way forward".
 */
@Composable
private fun ActivationCta(
    filled: Boolean,
    enabled: Boolean,
    busy: Boolean,
    label: String,
    busyLabel: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) NovaMotion.CTA_PRESS_SCALE else 1f,
        animationSpec = tween(90),
        label = "ctaPress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (filled) Modifier.novaRaised(NovaShapes.Pill) else Modifier)
            .clip(NovaShapes.Pill)
            .background(
                if (filled) NovaCtaBrush
                else Brush.linearGradient(listOf(NovaDisabledFill, NovaDisabledFill))
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = NovaTextOnAccent,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = if (busy) busyLabel else label,
            style = NovaType.Cta,
            color = if (filled) NovaTextOnAccent else NovaTextDisabled
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bound -- the robot the key turned out to hold
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BoundContent(robot: BoundRobot, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BoundAvatar(robot.avatarUrl)

            Spacer(Modifier.height(26.dp))

            StaggerIn(delayMs = 180) {
                Text(
                    text = "Let's start trading",
                    style = NovaType.StepTitle.copy(fontSize = 31.sp, lineHeight = 35.sp),
                    color = NovaTextPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(12.dp))

            StaggerIn(delayMs = 240) {
                Text(
                    text = "Your key unlocked the robot below. It is installed, " +
                        "bound to this device and ready to run.",
                    style = NovaType.Body,
                    color = NovaTextMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            StaggerIn(delayMs = 300) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .novaRaised(NovaShapes.Card)
                        .clip(NovaShapes.Card)
                        .background(NovaSurface)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RobotThumb(robot.avatarUrl)
                        Spacer(Modifier.width(13.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = robot.displayName,
                                style = NovaType.ArtHeading.copy(fontSize = 16.sp),
                                color = NovaTextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = robot.detail,
                                style = NovaType.BodySmall,
                                color = NovaTextMuted,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        ReadyPill()
                    }

                    CardHairline()

                    DetailRow("License", groupKey(robot.key), mono = true)

                    CardHairline()

                    DetailRow("Device", deviceName(), mono = false)
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        StaggerIn(delayMs = 360) {
            ActivationCta(
                filled = true,
                enabled = true,
                busy = false,
                label = "Let's start trading",
                busyLabel = "Let's start trading",
                onClick = onContinue
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Bound just now",
                style = NovaType.Ghost.copy(fontSize = 14.5.sp),
                color = NovaTextDisabled
            )
        }
    }
}

/** Two concentric success rings, the robot, and the check that lands on top. */
@Composable
private fun BoundAvatar(avatarUrl: String?) {
    val loop = rememberInfiniteTransition(label = "bound")
    val lift by loop.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boundLift"
    )
    val liftPx = with(LocalDensity.current) { lift.dp.toPx() }

    Box(
        modifier = Modifier.size(196.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.5.dp, NovaSuccessSoft.copy(alpha = 0.28f), CircleShape)
        )
        Box(
            Modifier
                .size(148.dp)
                .border(1.5.dp, NovaSuccessSoft.copy(alpha = 0.16f), CircleShape)
        )
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = liftPx }
                .size(132.dp)
                .novaRaised(CircleShape)
                .clip(CircleShape)
                .background(NovaSurface)
        ) {
            RobotImage(avatarUrl, Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-22).dp, y = (-26).dp)
                .size(42.dp)
                .novaRaised(CircleShape)
                .clip(CircleShape)
                .background(NovaSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Activated",
                tint = NovaSuccessSoft,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RobotThumb(avatarUrl: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NovaCanvas)
    ) {
        RobotImage(avatarUrl, Modifier.fillMaxSize())
    }
}

/**
 * The robot's face, from the portal when the licence carries one.
 *
 * The bundled drawable is a fallback, not the default: a mentor who has not
 * uploaded an avatar yet should still hand their trader a face rather than an
 * empty circle.
 */
@Composable
private fun RobotImage(avatarUrl: String?, modifier: Modifier) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Robot avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.new_avatar),
            contentDescription = "Robot avatar",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ReadyPill() {
    Row(
        modifier = Modifier
            .clip(NovaShapes.Pill)
            .background(NovaSuccessTint)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(NovaSuccessSoft)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "Ready",
            style = NovaType.BodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = NovaSuccessText
        )
    }
}

@Composable
private fun CardHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NovaTrack)
    )
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = NovaType.Tag.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp),
            color = NovaTextMuted
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = if (mono) {
                TextStyle(
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    letterSpacing = 0.5.sp
                )
            } else {
                NovaType.Tag.copy(fontSize = 13.5.sp)
            },
            color = NovaTextPrimary,
            textAlign = TextAlign.End
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Backend
// ─────────────────────────────────────────────────────────────────────────────

private const val PREFS = "metahost_prefs"

/**
 * Validates the key server-side and binds it to this handset.
 *
 * Posted through the raw HTTP client with an explicitly serialized body:
 * `functions.invoke { setBody(obj) }` relies on content negotiation this client
 * does not install, so it could throw before any request was sent -- which
 * surfaced as a misleading "check your connection" even when the network was
 * fine.
 *
 * Returns null only when the call itself failed. A key the server refuses comes
 * back as a well-formed response carrying `success = false` and a reason.
 */
private suspend fun activateLicence(
    context: Context,
    issuedKey: String
): LicenseActivationResponse? {
    val androidId = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID
    ) ?: ""

    val json = Json { ignoreUnknownKeys = true }

    return try {
        val payload = json.encodeToString(
            LicenseActivationRequest.serializer(),
            LicenseActivationRequest(license_key = issuedKey, android_id = androidId)
        )

        val response = NovaHostBackend.client.httpClient.post(
            "${com.novahost.app.BuildConfig.NOVAHOST_API_URL}/functions/v1/validate-license"
        ) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(
                HttpHeaders.Authorization,
                "Bearer ${com.novahost.app.BuildConfig.NOVAHOST_API_KEY}"
            )
            header("apikey", com.novahost.app.BuildConfig.NOVAHOST_API_KEY)
            setBody(payload)
        }

        // Read the body regardless of status: the function returns its reason
        // ("License key not found", "Device mismatch") with a 401/403, and that
        // is exactly what the user needs to see.
        val bodyText = response.body<String>()
        android.util.Log.i("NovaHost", "Activation HTTP ${response.status.value}")
        json.decodeFromString<LicenseActivationResponse>(bodyText)
    } catch (e: Exception) {
        android.util.Log.e("NovaHost", "License activation failed", e)
        null
    }
}

/** Writes the robot identity the licence just handed us. */
private fun persistLicence(
    context: Context,
    issuedKey: String,
    result: LicenseActivationResponse,
    traderName: String
) {
    val symbols = result.allowed_symbols?.takeIf { it.isNotEmpty() }
        ?: result.symbols
        ?: emptyList()

    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
        // The server's copy, not the typed one. Every device-facing function
        // upper-cases a key before looking it up, so a lower-case local copy
        // would not match the row it was validated against.
        putString("license_key", result.license_key ?: issuedKey.trim().uppercase())
        putString("active_ea_id", result.ea_id ?: "")
        putString("display_name", result.display_name ?: result.product_name ?: "TRADING BOT")
        putString("avatar_url", result.avatar_url)
        putString("accent_color", result.accent_color)
        putString("background_video_url", result.background_video_url)
        putString("tts_script", result.tts_script)
        putString("allowed_symbols", symbols.joinToString(","))
        putString("trader_name", traderName.ifBlank { "Trader" })
    }.apply()
}
