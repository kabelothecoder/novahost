package com.novahost.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.ui.theme.*

/**
 * Neon Glow component set.
 *
 * Every component reads the active robot's accent from [LocalNovaHostTheme] and
 * the shared [novaRim] recipe. Nothing defines its own glow, so one intensity
 * value moves the whole set.
 */

// ═══════════════════════════════════════════════════════════════════════════
// BUTTON
// ═══════════════════════════════════════════════════════════════════════════

enum class NovaButtonVariant { Primary, Secondary, Ghost, Danger }

/**
 * Pill-shaped action trigger.
 *
 * Press collapses the bloom inward and scales to 0.97 — the light reads as
 * squeezed out rather than switched off.
 *
 * Disabled drops the rim entirely. A dimmed glow still looks available, and in
 * a preset where glow means "actionable" that is the wrong signal.
 */
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NovaButtonVariant = NovaButtonVariant.Secondary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val theme = LocalNovaHostTheme.current
    val accent = when (variant) {
        NovaButtonVariant.Danger -> NeonDanger
        else -> theme.primaryColor
    }
    val glow = theme.glow

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading

    val fill = when {
        !active -> Color(0xFF0E0E15)
        variant == NovaButtonVariant.Primary -> accent.copy(alpha = 0.11f)
        variant == NovaButtonVariant.Danger -> NeonDanger.copy(alpha = 0.10f)
        variant == NovaButtonVariant.Ghost -> Color.Transparent
        else -> Color.White.copy(alpha = 0.02f)
    }

    val shaped = modifier
        .defaultMinSize(minHeight = 46.dp)
        .scale(if (pressed && active) NovaMotion.PRESS_SCALE else 1f)
        .then(
            when {
                !active -> Modifier.border(1.dp, NeonHairline, NovaShapes.Pill)
                variant == NovaButtonVariant.Ghost ->
                    Modifier.border(1.dp, NeonText.copy(alpha = 0.13f), NovaShapes.Pill)
                pressed -> Modifier.novaRimPressed(accent, NovaShapes.Pill, glow)
                else -> Modifier.novaRim(accent, NovaShapes.Pill, glow)
            }
        )
        .background(fill, NovaShapes.Pill)
        .clip(NovaShapes.Pill)
        .clickableNoRipple(interaction, enabled = active, onClick = onClick)
        .padding(horizontal = 26.dp, vertical = 12.dp)

    Row(shaped, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !active -> NeonText.copy(alpha = 0.2f)
                    variant == NovaButtonVariant.Ghost -> NeonText.copy(alpha = 0.5f)
                    else -> accent
                },
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 0.dp)
            )
        }
        Text(
            text = if (loading) "…" else text,
            color = when {
                !active -> NeonText.copy(alpha = 0.28f)
                variant == NovaButtonVariant.Ghost -> NeonTextSecondary
                else -> NeonText
            },
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CARD
// ═══════════════════════════════════════════════════════════════════════════

enum class NovaCardVariant {
    /** Matte. The resting state for everything that is not live. */
    Default,

    /** Rimmed. Reserved for the ONE live item on screen. */
    Active,

    /** Inset well: log panels, telemetry strips. Never glows. */
    Sunken
}

/**
 * The rim marks the single live item. A screen with three glowing cards has no
 * focal point — the failure mode across most of the competitor set.
 */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    variant: NovaCardVariant = NovaCardVariant.Default,
    shape: Shape = NovaShapes.Card,
    content: @Composable () -> Unit
) {
    val theme = LocalNovaHostTheme.current

    val styled = when (variant) {
        NovaCardVariant.Active -> modifier
            .novaRim(theme.primaryColor, shape, theme.glow)
            .background(Color.White.copy(alpha = 0.02f), shape)

        NovaCardVariant.Sunken -> modifier
            .background(NeonSurfaceSunken, shape)
            .border(1.dp, NeonHairline, shape)

        NovaCardVariant.Default -> modifier.background(NeonSurface, shape)
    }

    Box(styled.clip(shape)) { content() }
}

// ═══════════════════════════════════════════════════════════════════════════
// STATUS DOT
// ═══════════════════════════════════════════════════════════════════════════

enum class NovaStatus { Live, Idle, Degraded, Disconnected }

/**
 * The only element that animates on its own.
 *
 * Colours are FIXED and never take the robot's accent — live is green and
 * disconnected is red on every robot, in every preset.
 */
@Composable
fun NovaStatusDot(
    status: NovaStatus,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 9.dp
) {
    val color = when (status) {
        NovaStatus.Live -> NeonSuccess
        NovaStatus.Degraded -> NeonWarning
        NovaStatus.Disconnected -> NeonDanger
        NovaStatus.Idle -> NeonIdle
    }

    // Live breathes at 2.6s. Everything else is static.
    val alpha = if (status == NovaStatus.Live) {
        val transition = rememberInfiniteTransition(label = "novaDotBreathe")
        val animated by transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300),
                repeatMode = RepeatMode.Reverse
            ),
            label = "novaDotAlpha"
        )
        animated
    } else 1f

    Box(
        modifier
            .size(size)
            .alpha(alpha)
            .then(
                if (status == NovaStatus.Idle) Modifier
                else Modifier.novaRim(color, CircleShape, NovaGlow.SOFT)
            )
            .background(color, CircleShape)
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// TEXT FIELD
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Matte at rest — an input is not an action. The rim appears on focus, so the
 * accent doubles as the focus indicator.
 *
 * [errorText] should name what actually happened ("Licence key not found"),
 * never a generic "Invalid input" and never a cause that was not verified.
 */
@Composable
fun NovaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default
) {
    val theme = LocalNovaHostTheme.current
    val isError = errorText != null

    Column(modifier) {
        Text(
            text = label.uppercase(),
            color = NeonTextMuted,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            placeholder = placeholder?.let {
                { Text(it, color = NeonText.copy(alpha = 0.30f), fontSize = 12.5.sp) }
            },
            textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.02f),
                unfocusedContainerColor = Color(0xFF0E0E15),
                disabledContainerColor = NeonSurfaceSunken,
                errorContainerColor = Color(0xFF0E0E15),
                focusedBorderColor = theme.primaryColor.copy(alpha = 0.55f),
                unfocusedBorderColor = Color(0xFF22222E),
                disabledBorderColor = NeonHairline,
                errorBorderColor = NeonDanger.copy(alpha = 0.60f),
                focusedTextColor = NeonText,
                unfocusedTextColor = NeonText,
                disabledTextColor = NeonText.copy(alpha = 0.25f),
                cursorColor = theme.primaryColor
            ),
            modifier = Modifier.defaultMinSize(minHeight = 46.dp)
        )

        val message = errorText ?: helperText
        if (message != null) {
            Text(
                text = message,
                color = if (isError) NeonDanger.copy(alpha = 0.9f) else NeonText.copy(alpha = 0.34f),
                fontSize = 10.5.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }
    }
}

/** Click without the Material ripple, which fights the glow. */
private fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = onClick
)
