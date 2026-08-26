package com.novahost.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape and elevation tokens for the Premium Light system.
 *
 * These existed only as inline literals before -- `RoundedCornerShape(24.dp)`
 * was repeated across GlassComponents, PaywallOverlay and several screens, so
 * "the card radius" was never actually one value anyone could change.
 */
object NovaShapes {
    /** Every action trigger. The design system allows no square-cornered buttons. */
    val Pill: Shape = CircleShape

    /** Cards, sheets, bottom sheets. */
    val Card: Shape = RoundedCornerShape(24.dp)

    /** Inputs, chips, small controls. */
    val Control: Shape = RoundedCornerShape(12.dp)

    /** Inset wells: log panels, sunken telemetry strips. */
    val Sunken: Shape = RoundedCornerShape(16.dp)

    // ── Splash + Onboarding ────────────────────────────────────────────────
    // Separate tokens rather than retuned versions of Card/Control above: the
    // dashboard radii are load-bearing for every existing screen, and the
    // designs happen to sit 2-4dp away from them. Two names beat one silently
    // changed value.

    /** The sheet that carries step copy. Top corners only -- it rises 26dp
     *  over the art pane and its bottom edge is never visible. */
    val Sheet: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

    /** Chart cards inside an art pane. */
    val ArtCard: Shape = RoundedCornerShape(22.dp)

    /** Floating info cards: the signal card, the notification preview. */
    val ArtCardSmall: Shape = RoundedCornerShape(18.dp)

    /** Display-name field and the trading-style option rows. */
    val OptionRow: Shape = RoundedCornerShape(16.dp)
}

/** The one gradient in the system: 135 degrees, accent-secondary to accent-primary. */
val NovaCtaBrush: Brush = Brush.linearGradient(listOf(SoftLightPurple, SoftLightBlue))

/** Progress rail fill, left to right. */
val NovaRailBrush: Brush = Brush.horizontalGradient(listOf(SoftLightPurple, SoftLightBlue))

/** The art pane behind every onboarding step. */
val NovaArtBrush: Brush = Brush.verticalGradient(listOf(NovaArtTop, NovaSurface))

/**
 * Soft elevation, the light-mode replacement for the design system's
 * "Liquid Cyber-Glass". Glass needs a dark ground to read; on a light canvas
 * depth has to come from shadow instead.
 */
object NovaElevation {
    /** Resting card: y+8, blur 24, black 6%. */
    fun Modifier.novaCard(shape: Shape = NovaShapes.Card): Modifier =
        this.shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = NovaShadow,
            spotColor = NovaShadow
        )

    /** Raised surface -- sheets, dialogs, the active robot card. */
    fun Modifier.novaRaised(shape: Shape = NovaShapes.Card): Modifier =
        this.shadow(
            elevation = 16.dp,
            shape = shape,
            ambientColor = NovaShadowRaised,
            spotColor = NovaShadowRaised
        )

    /**
     * Primary action at rest: the shadow is tinted with the accent so the
     * button reads as lit rather than merely raised.
     *
     * Pass the ACTIVE ROBOT's accent here, not the system blue -- this is the
     * one place per-robot theming is meant to show.
     */
    fun Modifier.novaAccentGlow(accent: Color, shape: Shape = NovaShapes.Pill): Modifier =
        this.shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = accent.copy(alpha = 0.30f),
            spotColor = accent.copy(alpha = 0.30f)
        )
}

/**
 * Press physics carried over from the design system unchanged: a press scales
 * to 0.97 on spring easing. Only the material differs on light.
 */
object NovaMotion {
    const val PRESS_SCALE = 0.97f
    val PressedElevation: Dp = 2.dp

    // ── Splash + Onboarding ────────────────────────────────────────────────
    /** M3 emphasised decelerate. Every move in both designs uses this curve. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** A pill CTA presses shallower than a card -- it has no shadow to lose. */
    const val CTA_PRESS_SCALE = 0.985f

    const val ENTER_MS = 320    // splash mark scale-in, 0.92 -> 1
    const val ART_MS = 620      // onboarding art enter, scale .94 -> 1
    const val SHEET_MS = 520    // sheet rise, 26dp
    const val COPY_MS = 460     // copy stagger
    const val RAIL_MS = 460     // progress rail width
    const val HANDOFF_MS = 220  // wordmark fade at system -> Compose handoff
    const val EXIT_MS = 240     // splash exit crossfade
    const val LOCK_MS = 1800    // vault bind

    /** Copy stagger offsets, in order: label, title, body, action. */
    val CopyDelays = listOf(60, 110, 170, 230)
}

/**
 * Parses a robot's `accent_color` (e.g. "#C9A227") into a Color.
 *
 * Returns [SoftLightBlue] for anything unusable rather than throwing -- the
 * value comes from a mentor typing into the portal, so it will eventually be
 * blank, malformed, or a colour name. A bad accent must never crash the app or
 * leave the screen unthemed.
 */
fun parseRobotAccent(hex: String?): Color {
    val raw = hex?.trim()?.removePrefix("#") ?: return SoftLightBlue
    if (raw.length != 6 && raw.length != 8) return SoftLightBlue
    return try {
        val value = raw.toLong(16)
        // Six digits carry no alpha, so make it opaque.
        val argb = if (raw.length == 6) value or 0xFF000000L else value
        // Use the ARGB-Int constructor. toInt() intentionally wraps to the
        // signed bit pattern the constructor expects -- the ULong overload
        // takes Compose's packed representation, not plain ARGB.
        Color(argb.toInt())
    } catch (e: NumberFormatException) {
        SoftLightBlue
    }
}

/**
 * The accent for the robot this device is licensed to, as written by licence
 * activation and the robot picker. Falls back to the system blue.
 */
fun robotAccent(context: android.content.Context): Color =
    parseRobotAccent(
        context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
            .getString("accent_color", null)
    )
