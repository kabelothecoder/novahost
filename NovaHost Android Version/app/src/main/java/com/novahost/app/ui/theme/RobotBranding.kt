package com.novahost.app.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Who the licensed robot is, as opposed to how the app looks.
 *
 * These fields used to sit inside [NovaHostThemeState] next to the accent
 * colour and the corner radius. That coupling had a concrete cost: the home
 * layout preset, art mode and widget order are visual settings the user owns,
 * but every robot switch rebuilds the theme state wholesale, so anything living
 * there would have been reset each time a different licence was selected. The
 * user's arrangement is not the mentor's to overwrite.
 *
 * The accent stays on the theme side deliberately -- a robot's `accent_color`
 * *is* meant to re-theme the app, and that is the one place per-robot branding
 * shows through.
 */
data class RobotBranding(
    /**
     * Empty until a licence key supplies the real robot. A hardcoded name here
     * ships someone else's robot to every user who has not activated yet -- the
     * same defect that put "QUANTUM_BREAKER" on live devices.
     */
    val name: String = "",
    val avatarUrl: String? = null,
    val backgroundImageUrl: String? = null,
    /** The mentor or desk that publishes this robot, shown above the name. */
    val mentorName: String = "",
    /** Blank when the mentor set no tagline; layouts drop the line rather than pad it. */
    val tagline: String = "",
    val allowedSymbols: List<String> = emptyList(),
    val productCode: String = "NovaHost",
    /**
     * Null until a promo asset is uploaded to THIS project's storage bucket.
     *
     * This used to default to a metahost_promo.mp4 on the legacy NovaHost backend
     * project kivpdtisymhymmndndun, which no longer resolves at DNS at all --
     * so every robot carried a dead URL and every launch opened an ExoPlayer
     * that could only fail. Re-upload the asset to epulmnfbxjmaimefhofp and set
     * it here (or per-robot from the portal) to bring the video back.
     */
    val promoVideoUrl: String? = null
)

val LocalRobotBranding = compositionLocalOf { RobotBranding() }
val LocalRobotBrandingUpdater = compositionLocalOf<(RobotBranding) -> Unit> { {} }
