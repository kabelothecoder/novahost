package com.novahost.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novahost.app.ui.theme.HolographicGlowMode

/**
 * @description Applies a holographic neon glow halo around a composable using the canvas
 * shadow layer API. Each tier produces a meaningfully distinct visual depth:
 *
 * SOFT    → Subtle 4dp elevation shadow. Barely-there ambient glow at 20% alpha.
 *            Ideal for resting / unfocused card states.
 *
 * MEDIUM  → 16dp elevation shadow with full-intensity spot color.
 *            Standard active state — clearly visible neon ring.
 *
 * INTENSE → Fixed canvas drawShadowLayer at 32px radius with 85% alpha.
 *            Creates a deep bloom halo — significantly heavier than MEDIUM.
 *            Non-animated; always at full intensity for maximum visual weight.
 *
 * PULSE   → Animated canvas drawShadowLayer with radius breathing between 12px → 32px
 *            at a 1400ms cycle. Creates a live heartbeat glow effect.
 *            Used on the active avatar ring and running ignition button.
 */
fun Modifier.neonGlow(intensity: HolographicGlowMode, color: Color, cornerRadius: Dp = 12.dp): Modifier = composed {
    when (intensity) {

        // Tier 0: the crisp edge and nothing else. Callers keep their border
        // and background; they just get no shadow under them.
        HolographicGlowMode.OFF -> this

        HolographicGlowMode.SOFT -> {
            // Tier 1: Subtle ambient presence — low elevation, low alpha
            this.shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = color.copy(alpha = 0.4f),
                ambientColor = color.copy(alpha = 0.2f)
            )
        }

        HolographicGlowMode.MEDIUM -> {
            // Tier 2: Standard active neon ring — clearly visible, not overwhelming
            this.shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = color,
                ambientColor = color.copy(alpha = 0.5f)
            )
        }

        HolographicGlowMode.INTENSE -> {
            // Tier 3: Fixed deep bloom — maximum fixed-radius canvas shadow (32px)
            this.drawBehind {
                val transparentColor = color.copy(alpha = 0f).toArgb()
                val shadowColor = color.copy(alpha = 0.85f).toArgb()
                this.drawIntoCanvas {
                    val paint = Paint().apply {
                        style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                        strokeWidth = 4f
                    }
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = transparentColor
                    frameworkPaint.setShadowLayer(32f, 0f, 0f, shadowColor)
                    it.drawRoundRect(
                        0f, 0f,
                        this.size.width, this.size.height,
                        cornerRadius.toPx(), cornerRadius.toPx(),
                        paint
                    )
                }
            }
        }

        HolographicGlowMode.PULSE -> {
            // Tier 4: Animated breathing glow — radius oscillates 12px → 32px @ 1400ms
            val infiniteTransition = rememberInfiniteTransition(label = "NeonBreathing")
            val glowRadius by infiniteTransition.animateFloat(
                initialValue = 12f,
                targetValue = 32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "GlowRadius"
            )
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "GlowAlpha"
            )

            this.drawBehind {
                val transparentColor = color.copy(alpha = 0f).toArgb()
                val shadowColor = color.copy(alpha = glowAlpha).toArgb()
                this.drawIntoCanvas {
                    val paint = Paint().apply {
                        style = androidx.compose.ui.graphics.PaintingStyle.Stroke
                        strokeWidth = 4f
                    }
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.color = transparentColor
                    frameworkPaint.setShadowLayer(glowRadius, 0f, 0f, shadowColor)
                    it.drawRoundRect(
                        0f, 0f,
                        this.size.width, this.size.height,
                        cornerRadius.toPx(), cornerRadius.toPx(),
                        paint
                    )
                }
            }
        }
    }
}
