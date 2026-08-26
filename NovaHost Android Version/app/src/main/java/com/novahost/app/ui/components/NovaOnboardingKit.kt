package com.novahost.app.ui.components

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.novahost.app.R
import com.novahost.app.ui.theme.NovaCtaBrush
import com.novahost.app.ui.theme.NovaDisabledFill
import com.novahost.app.ui.theme.NovaMonoLabel
import com.novahost.app.ui.theme.NovaMotion
import com.novahost.app.ui.theme.NovaRailBrush
import com.novahost.app.ui.theme.NovaShapes
import com.novahost.app.ui.theme.NovaTextDisabled
import com.novahost.app.ui.theme.NovaTextMuted
import com.novahost.app.ui.theme.NovaTextOnAccent
import com.novahost.app.ui.theme.NovaTextSecondary
import com.novahost.app.ui.theme.NovaTrack
import com.novahost.app.ui.theme.NovaType
import com.novahost.app.ui.theme.SoftLightBlue

/**
 * Shared parts for Splash and Onboarding. Everything here comes straight off the
 * two designs -- no variants, no configuration beyond what a screen genuinely
 * needs.
 */

/**
 * Switches the status bar to dark content for as long as this screen is on
 * screen, and puts it back on the way out.
 *
 * Splash and Onboarding are the only white-ground screens in the app. The theme
 * sets a black status bar for the dark surfaces everywhere else, which on white
 * leaves the clock and the signal icons drawn white-on-white -- invisible. The
 * designs show them dark, so the two screens ask for that and restore whatever
 * was there before when they leave.
 */
@Composable
fun LightStatusBarEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (controller != null && previous != null) {
                controller.isAppearanceLightStatusBars = previous
            }
        }
    }
}

/** Fades and rises content once, on the emphasised curve. Used for copy stagger. */
@Composable
fun StaggerIn(
    delayMs: Int,
    key: Any? = Unit,
    durationMs: Int = NovaMotion.COPY_MS,
    rise: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    var play by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { play = true }
    val p by animateFloatAsState(
        targetValue = if (play) 1f else 0f,
        animationSpec = tween(durationMs, delayMs, NovaMotion.Emphasized),
        label = "staggerIn"
    )
    val risePx = with(LocalDensity.current) { rise.toPx() }
    Box(
        Modifier.graphicsLayer {
            alpha = p
            translationY = (1f - p) * risePx
        }
    ) { content() }
}

/** The mono machine label: STEP 01, CHECKING CONNECTION, DEVICE BOUND. */
@Composable
fun NovaMonoText(
    text: String,
    color: Color = NovaTextMuted,
    modifier: Modifier = Modifier,
    style: TextStyle = NovaMonoLabel
) = Text(text = text, style = style, color = color, modifier = modifier)

/**
 * The only container-bearing trigger in the flow. Gradient fill when live, flat
 * grey when it cannot be pressed -- a disabled CTA never keeps the gradient,
 * because in this system the gradient is what says "this is the way forward".
 *
 * [height] and [leading] exist for the permissions flow, which sets its CTA at
 * 56dp and slides a check badge in beside the label once the grant lands. They
 * are parameters rather than a second button because the press physics, the
 * disabled rule and the accent shadow are the parts worth having in one place
 * -- and a fork of this would have drifted from them within a release.
 */
@Composable
fun NovaCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    leading: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) NovaMotion.CTA_PRESS_SCALE else 1f,
        animationSpec = tween(90),
        label = "ctaPress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (enabled) Modifier.shadow(
                    elevation = 10.dp,
                    shape = NovaShapes.Pill,
                    ambientColor = SoftLightBlue.copy(alpha = 0.28f),
                    spotColor = SoftLightBlue.copy(alpha = 0.28f)
                ) else Modifier
            )
            .clip(NovaShapes.Pill)
            .background(
                if (enabled) NovaCtaBrush
                else Brush.linearGradient(listOf(NovaDisabledFill, NovaDisabledFill))
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = label,
                style = NovaType.Cta,
                color = if (enabled) NovaTextOnAccent else NovaTextDisabled
            )
        }
    }
}

/** Ghost trigger: hit area, no container. The splash "Try again". */
@Composable
fun NovaGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(NovaShapes.Pill)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = NovaType.Ghost,
            color = NovaTextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** 3dp progress rail under the status bar. Animates its width, never its colour. */
@Composable
fun NovaProgressRail(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(NovaMotion.RAIL_MS, easing = NovaMotion.Emphasized),
        label = "rail"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(NovaTrack)
    ) {
        Box(
            Modifier
                .fillMaxWidth(p)
                .height(3.dp)
                .background(NovaRailBrush)
        )
    }
}

// ───────────────────────────── the mark ─────────────────────────────

/**
 * Geometry of novahost_mark.png, measured off the file itself rather than eyeballed.
 *
 *   frame        992 x 618
 *   head         circle centred (511, 356), diameter 384
 *   eye glows    centred (434.5, 371.5) and (592, 371.5), each about 64 x 80
 *
 * The head is NOT centred in its own frame -- it sits low and slightly right of
 * middle, with a lot of empty ground above it. Dropping the raw bitmap into a
 * circular clip therefore shoves the face against the bottom edge, which is what
 * these numbers exist to correct: the mark is scaled and translated so the head
 * lands dead centre at [HEAD_FILL] of the frame, whatever size the caller asks
 * for. Re-measure these six values if the artwork is ever recropped.
 */
private const val ART_W = 992f
private const val ART_H = 618f
private const val HEAD_CX = 511f
private const val HEAD_CY = 356f
private const val HEAD_D = 384f
private const val EYE_L_CX = 434.5f
private const val EYE_R_CX = 592f
private const val EYE_CY = 371.5f

/** Glow is drawn wider than the eye itself so it reads as a halo, not a disc. */
private const val EYE_GLOW_D = 96f

/** How much of the circular frame the head fills, leaving a little air. */
private const val HEAD_FILL = 0.86f

/**
 * The mark. Not RobotAvatar: no status ring, no accent, no press target.
 *
 * [eyeAlpha] lights two glow ellipses over the render's eyes. The artwork is a
 * raster, so liveness cannot live inside it -- this is the alpha loop the design
 * calls for, sitting on top.
 */
@Composable
fun SplashMark(
    size: Dp,
    modifier: Modifier = Modifier,
    eyeAlpha: Float = 0f
) {
    BoxWithConstraints(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        val w = maxWidth

        // Everything below is a fraction of the frame, so one set of source-pixel
        // measurements drives every call site from the 36dp notification avatar
        // to the 248dp welcome art.
        val k = HEAD_FILL / HEAD_D          // frame-fractions per source pixel

        // requiredSize reports the coerced size to the parent and CENTRES the
        // oversized content inside it. That centring is already half the job, so
        // the only nudge left is how far the head sits from the middle of its own
        // frame -- offsetting by the head's absolute position on top of the
        // centring is what threw the mark most of a frame off screen.
        val offX = k * (ART_W / 2f - HEAD_CX)
        val offY = k * (ART_H / 2f - HEAD_CY)

        Image(
            painter = painterResource(R.drawable.novahost_mark),
            contentDescription = "NovaHost",
            contentScale = ContentScale.FillBounds,
            // requiredSize, not size: the mark is deliberately drawn LARGER than the
            // circle that clips it (about 2.2x wide), and Modifier.size coerces
            // itself into the parent's constraints, so it silently caps the image at
            // the frame and the offset then drags the squashed result half out of
            // view. requiredSize ignores the incoming constraints, which is the whole
            // point here.
            modifier = Modifier
                .requiredSize(width = w * (ART_W * k), height = w * (ART_H * k))
                .offset(x = w * offX, y = w * offY)
        )

        if (eyeAlpha > 0f) {
            // Once the head is centred, an eye is just its offset from the head's
            // centre, scaled. The glows are small enough to sit inside the frame,
            // so they place from the top-left with no centring to undo.
            val d = EYE_GLOW_D * k
            val eyeY = 0.5f + k * (EYE_CY - HEAD_CY) - d / 2f
            Eye(Modifier.offset(x = w * (0.5f + k * (EYE_L_CX - HEAD_CX) - d / 2f), y = w * eyeY), w * d, eyeAlpha)
            Eye(Modifier.offset(x = w * (0.5f + k * (EYE_R_CX - HEAD_CX) - d / 2f), y = w * eyeY), w * d, eyeAlpha)
        }
    }
}

@Composable
private fun Eye(modifier: Modifier, diameter: Dp, alpha: Float) {
    Box(
        modifier
            .size(diameter)
            .alpha(alpha.coerceIn(0f, 1f))
            .background(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.95f), Color.Transparent)
                ),
                shape = CircleShape
            )
    )
}

/** Small solid dot. Status, radio inner, candle ticks. */
@Composable
fun NovaDot(diameter: Dp, color: Color, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(
        modifier
            .size(diameter)
            .alpha(alpha.coerceIn(0f, 1f))
            .background(color, CircleShape)
    )
}
