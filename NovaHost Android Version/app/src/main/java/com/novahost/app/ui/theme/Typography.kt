package com.novahost.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Space Grotesk via system / embedded fonts fallback
// For production: add font files to res/font/ and reference them here
val SpaceGroteskFamily = FontFamily.Default  // Replace with actual font when available
val InterFamily        = FontFamily.SansSerif

val NovaHostTypography = Typography(
    displayLarge = TextStyle(
        fontFamily   = SpaceGroteskFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 56.sp,
        letterSpacing = (-0.02).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 16.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        color      = ActiveGrey,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        color      = ActiveGrey,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily    = InterFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        letterSpacing = (0.5).sp
    )
)

// ═══════════════════════════════════════════════════════════════════════════
// SPLASH + ONBOARDING TYPE
// ═══════════════════════════════════════════════════════════════════════════
// The designs use three families with no overlap:
//
//   Space Grotesk   headings and the wordmark. Nothing else.
//   Inter           body, buttons, field text, chips.
//   JetBrains Mono  uppercase machine labels -- STEP 01, CHECKING CONNECTION,
//                   DEVICE BOUND. Always tracked out .13-.16em.
//
// These are declared as named styles rather than folded into NovaHostTypography
// because that Typography is what every already-built screen renders through,
// and two of its roles (bodyMedium, bodySmall) carry a hardcoded light-grey
// colour for the dark surfaces. Rewriting it to the designs' metrics would have
// re-typeset Home, Markets and Settings -- and turned their body copy near-black
// on a near-black ground. Splash and Onboarding name what they mean instead.
//
// To ship the designs' actual faces, drop these six files into res/font/ and
// swap the three family declarations below for the commented FontFamily(...)
// beneath each. Nothing else changes.
//
//   space_grotesk_medium.ttf     space_grotesk_semibold.ttf
//   inter_regular.ttf            inter_medium.ttf   inter_semibold.ttf
//   jetbrains_mono_medium.ttf
//
// (Import com.novahost.app.R when you do -- Font is already imported here.)

val MonoFamily: FontFamily = FontFamily.Monospace
// val MonoFamily = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))
//
// val SpaceGroteskFamily = FontFamily(
//     Font(R.font.space_grotesk_medium,   FontWeight.Medium),
//     Font(R.font.space_grotesk_semibold, FontWeight.SemiBold)
// )
// val InterFamily = FontFamily(
//     Font(R.font.inter_regular,  FontWeight.Normal),
//     Font(R.font.inter_medium,   FontWeight.Medium),
//     Font(R.font.inter_semibold, FontWeight.SemiBold)
// )

/**
 * The mono machine label. Not a badge: no container, no press target -- a pill
 * would imply something you can act on, and none of these are.
 *
 * 10sp / .16em on step markers, 11sp / .14em on the splash status line.
 * Compose takes tracking in sp, so .16em at 10sp is 1.6sp.
 */
val NovaMonoLabel = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 1.6.sp
)

val NovaMonoStatus = NovaMonoLabel.copy(fontSize = 11.sp, letterSpacing = 1.54.sp)

/** Every text style Splash and Onboarding draw, at the designs' exact metrics. */
object NovaType {
    /** Step titles: 29sp, -0.6px tracking, 1.15 line height. */
    val StepTitle = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.6).sp
    )

    /** The splash wordmark. */
    val Wordmark = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        letterSpacing = (-0.5).sp
    )

    /** Headings inside an art pane: EURUSD on the signal card. */
    val ArtHeading = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.3).sp
    )

    /** Step body copy, and the splash error explanation. */
    val Body = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    )

    /** Notification preview, option note, helper line. */
    val BodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    )

    /** Option row label, splash error headline. */
    val OptionLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )

    /** What the user typed, in the display-name field. */
    val FieldValue = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )

    /** CTA label. */
    val Cta = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )

    /** Ghost trigger label, caption chips. */
    val Ghost = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )

    /** "Buy setup", price cell values -- small and set tight. */
    val Tag = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )

    /** Price cell captions: Entry / Stop / Target. */
    val Caption = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
}
