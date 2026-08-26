package com.novaedge.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.novaedge.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * MeshGradientBackground
 *
 * Animated multi-point radial gradient that slowly drifts to create
 * an atmospheric, living background. Replaces flat Obsidian overlays.
 *
 * @param isHot  When true (bot running), uses orange/crimson tones.
 *               When false, uses cool cyan/blue tones.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier.fillMaxSize(),
    isHot: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshBg")

    // Slow drift angle — one full rotation every 20s
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshAngle"
    )

    // Secondary slower orbit
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 180f,
        targetValue  = 540f,
        animationSpec = infiniteRepeatable(
            animation  = tween(28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshAngle2"
    )

    // Color palette based on state
    val color1 = if (isHot) GradientEmber else GradientDeep
    val color2 = if (isHot) GlowOrange else GlowCyan
    val color3 = if (isHot) GlowCrimson else Color(0x1A2D91FF) // blue tint
    val baseColor = GradientMidnight

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Base dark fill
        drawRect(color = Obsidian)

        // Radial gradient 1 — drifts in top-right area
        val rad1 = Math.toRadians(angle.toDouble())
        val cx1 = w * 0.7f + w * 0.12f * cos(rad1).toFloat()
        val cy1 = h * 0.25f + h * 0.08f * sin(rad1).toFloat()

        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(color2, Color.Transparent),
                center = Offset(cx1, cy1),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(cx1, cy1)
        )

        // Radial gradient 2 — drifts in bottom-left area
        val rad2 = Math.toRadians(angle2.toDouble())
        val cx2 = w * 0.3f + w * 0.1f * cos(rad2).toFloat()
        val cy2 = h * 0.7f + h * 0.06f * sin(rad2).toFloat()

        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(color3, Color.Transparent),
                center = Offset(cx2, cy2),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(cx2, cy2)
        )

        // Subtle top gradient overlay for depth
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    color1.copy(alpha = 0.6f),
                    Color.Transparent,
                    baseColor.copy(alpha = 0.4f)
                )
            )
        )
    }
}
