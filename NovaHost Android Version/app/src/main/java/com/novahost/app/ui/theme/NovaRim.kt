package com.novahost.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glow intensity for the Neon Glow preset.
 *
 * Off is a fully supported state, not a degraded one: the crisp edge survives
 * so structure and focus stay readable with zero bloom. It is the
 * accessibility option for anyone who finds bloom uncomfortable, and the
 * battery option for a screen that stays open all day.
 */
enum class NovaGlow(
    val blur: Dp,
    /** Alpha of the outer bloom. */
    val bloomAlpha: Float,
    /** Alpha of the inner haze that makes a surface look lit from within. */
    val innerAlpha: Float
) {
    OFF(0.dp, 0f, 0f),
    SOFT(9.dp, 0.14f, 0.05f),
    MEDIUM(18.dp, 0.28f, 0.10f),
    INTENSE(27.dp, 0.42f, 0.15f);

    companion object {
        val Default = MEDIUM

        fun fromOrdinalOrDefault(value: Int): NovaGlow =
            entries.getOrElse(value) { Default }
    }
}

/** Alpha of the 1px edge. Constant across intensities so shapes never dissolve. */
private const val EDGE_ALPHA = 0.55f

/**
 * The Neon Glow rim — one recipe used by every interactive surface.
 *
 * Three layers, matching the design system spec:
 *   1. a crisp 1px edge, present even at [NovaGlow.OFF]
 *   2. an outer bloom that spills onto the canvas
 *   3. an inner haze so the surface reads as lit rather than outlined
 *
 * Pass the ACTIVE ROBOT's accent. Nothing here defines its own colour, so a
 * single accent change re-themes every rim in the app.
 *
 * Order matters: the shadow must be applied before the border, or the bloom
 * draws over the edge and the outline looks soft.
 */
fun Modifier.novaRim(
    accent: Color,
    shape: Shape,
    glow: NovaGlow = NovaGlow.Default,
    /** Widen the bloom. Used by the robot portrait so it lights the screen. */
    hero: Boolean = false
): Modifier {
    val bloom = if (hero) glow.blur * 1.6f else glow.blur
    val bloomAlpha = if (hero) (glow.bloomAlpha * 1.1f).coerceAtMost(1f) else glow.bloomAlpha
    val edgeWidth = if (hero) 1.5.dp else 1.dp

    val withBloom =
        if (glow == NovaGlow.OFF) this
        else this.shadow(
            elevation = bloom,
            shape = shape,
            ambientColor = accent.copy(alpha = bloomAlpha),
            spotColor = accent.copy(alpha = bloomAlpha),
            clip = false
        )

    val withHaze =
        if (glow == NovaGlow.OFF) withBloom
        else withBloom.background(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = if (hero) glow.innerAlpha * 1.2f else glow.innerAlpha),
                    Color.Transparent
                )
            ),
            shape = shape
        )

    return withHaze.border(edgeWidth, accent.copy(alpha = EDGE_ALPHA), shape)
}

/**
 * Pressed rim: the bloom collapses inward and the edge brightens, so the light
 * looks squeezed out of the surface rather than switched off.
 */
fun Modifier.novaRimPressed(
    accent: Color,
    shape: Shape,
    glow: NovaGlow = NovaGlow.Default
): Modifier {
    val withBloom =
        if (glow == NovaGlow.OFF) this
        else this.shadow(
            elevation = glow.blur * 0.4f,
            shape = shape,
            ambientColor = accent.copy(alpha = glow.bloomAlpha * 0.6f),
            spotColor = accent.copy(alpha = glow.bloomAlpha * 0.6f),
            clip = false
        )

    val withHaze =
        if (glow == NovaGlow.OFF) withBloom
        else withBloom.background(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = glow.innerAlpha * 2f), Color.Transparent)
            ),
            shape = shape
        )

    return withHaze.border(1.dp, accent.copy(alpha = 0.75f), shape)
}

// ═══════════════════════════════════════════════════════════════════════════
// NEON GLOW SURFACES
// ═══════════════════════════════════════════════════════════════════════════
// Deeper than graphite -- bloom needs near-black to read. Named by role so a
// second preset can redefine them without the names becoming lies.

val NeonCanvas        = Color(0xFF07070B)
val NeonCanvasRaised  = Color(0xFF0D0D13)
val NeonSurface       = Color(0xFF121219)
val NeonSurfaceSunken = Color(0xFF0A0A0F)
val NeonHairline      = Color(0xFF1E1E29)

val NeonText          = Color(0xFFF2F5F8)
val NeonTextSecondary = Color(0xA8F2F5F8)
val NeonTextMuted     = Color(0x66F2F5F8)

/**
 * Status colours are FIXED. They never take the robot's accent.
 *
 * A robot with a red accent must not make a winning trade look like a loss,
 * and a green-accented robot must not make a loss look like a win. This is the
 * one place in the preset where per-robot theming is deliberately refused.
 */
val NeonSuccess = Color(0xFF22E06B)
val NeonDanger  = Color(0xFFFF4D4D)
val NeonWarning = Color(0xFFFFB020)
val NeonIdle    = Color(0xFF2A2A36)
