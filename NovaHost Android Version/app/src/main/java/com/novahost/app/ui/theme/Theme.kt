package com.novahost.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

enum class AppTheme {
    HOLOGRAPHIC, FULLSCREEN_HERO, NEON_FRAME, HOLOGRAPHIC_GLOW
}

enum class HolographicGlowMode {
    /**
     * No bloom at all.
     *
     * Added for the Settings redesign, whose glow ladder starts at Off and
     * treats it as a supported choice -- the battery option, and the option for
     * anyone who finds bloom uncomfortable -- exactly as [NovaGlow.OFF] already
     * did. Without it, the "Off" row could not turn off the glow that
     * `Modifier.neonGlow` draws.
     */
    OFF, SOFT, MEDIUM, INTENSE, PULSE
}

enum class HomeButtonShape {
    CIRCLE, OVAL, SQUARE
}

/**
 * @description Defines the available typeface profiles for the robot name label.
 * Each value corresponds to a premium editorial typography aesthetic:
 *
 * OLD_MONEY            → Luxury Serif — evokes The Seasons / Old Money gravitas.
 * BODONI_DISPLAY       → High-contrast bold display — evokes Bodoni FLF / Playfair.
 * MONTSERRAT_GEOMETRIC → Clean geometric sans — evokes Montserrat / Gothic branding.
 * SYMPHONY_CREATIVE    → Unique creative character — evokes Symphony / Perandory script.
 */
enum class RobotFontStyle {
    OLD_MONEY, BODONI_DISPLAY, MONTSERRAT_GEOMETRIC, SYMPHONY_CREATIVE
}

// === Dynamic Theme State ===
data class NovaHostThemeState(
    // The active robot's accent. Also the fallback when a robot defines no
    // accent_color -- it used to default to Crimson and then get special-cased
    // back to blue at the point of use, so the "default" was never the value
    // anyone actually saw.
    val primaryColor: Color = SoftLightBlue,

    // Glow intensity for the Neon Glow preset. Operator-controlled from
    // Settings -> Appearance. OFF is a supported state, not a broken one: the
    // crisp edge survives, so it serves as both the accessibility option and
    // the battery option.
    val glow: NovaGlow = NovaGlow.Default,
    val appTheme: AppTheme = AppTheme.HOLOGRAPHIC,
    val useRoundedShape: Boolean = true,
    val isGlossTheme: Boolean = false,
    val secondaryColor: Color = Cyan,
    val backgroundAssetIndex: Int? = null,
    val holographicGlowMode: HolographicGlowMode = HolographicGlowMode.MEDIUM,
    val secondaryBackgroundColor: Color? = null,

    /**
     * Light is an explicit opt-in, not the default.
     *
     * `premiumLightColorScheme` used to be hardwired as the only scheme while
     * every screen painted dark surfaces over it. Anything the screens did not
     * paint themselves -- ripples, disabled text, unstyled dividers, the
     * keyboard's own surface -- resolved against a light palette on a dark
     * screen. Dark is what the app actually is; light is a setting.
     */
    val useLightScheme: Boolean = false,

    /** @description When true, card borders and inventory rows are suppressed — only the full-bleed background fills the screen. */
    val immersiveMode: Boolean = false,
    /** @description Controls which editorial typeface is used for the robot name label on HomeScreen. */
    val robotFontStyle: RobotFontStyle = RobotFontStyle.MONTSERRAT_GEOMETRIC,
    val homeButtonScale: Float = 1.0f,
    val robotNameFontSize: Float = 24f,
    val robotNameFontColor: Color = Color.White,
    val homeButtonShape: HomeButtonShape = HomeButtonShape.CIRCLE,
)

val LocalNovaHostTheme = compositionLocalOf { NovaHostThemeState() }
val LocalNovaHostThemeUpdater = compositionLocalOf<(NovaHostThemeState) -> Unit> { {} }

/**
 * @description CompositionLocal that resolves the active RobotFontStyle enum into a
 * concrete TextStyle. Consumers read `LocalRobotFont.current` to get the text style
 * for the robot name label without coupling to the raw FontFamily values.
 */
val LocalRobotFont = compositionLocalOf {
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 24.sp,
        letterSpacing = 4.sp
    )
}

/**
 * @description Resolves a RobotFontStyle enum value into a concrete Compose TextStyle.
 * Typography calibrated to match premium editorial aesthetics.
 */
fun robotFontStyleToTextStyle(style: RobotFontStyle): TextStyle = when (style) {

    RobotFontStyle.OLD_MONEY -> TextStyle(
        // Luxury Serif — evokes The Seasons / Old Money — ultra-light wide tracking
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Thin,
        fontSize = 28.sp,
        letterSpacing = 8.sp
    )

    RobotFontStyle.BODONI_DISPLAY -> TextStyle(
        // High-contrast bold display — evokes Bodoni FLF / Playfair Display
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        letterSpacing = 2.sp
    )

    RobotFontStyle.MONTSERRAT_GEOMETRIC -> TextStyle(
        // Clean geometric branding — evokes Montserrat / Gothic sans-serif
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = 3.sp
    )

    RobotFontStyle.SYMPHONY_CREATIVE -> TextStyle(
        // Unique creative character — evokes Symphony / Perandory script
        fontFamily = FontFamily.Cursive,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        letterSpacing = 1.sp
    )
}

fun premiumLightColorScheme(primary: Color = SoftLightBlue) = androidx.compose.material3.lightColorScheme(
    primary            = primary,
    primaryContainer   = SoftLightBlue,
    onPrimary          = Color.White,
    secondary          = SoftLightPurple,
    secondaryContainer = SoftLightPurple,
    onSecondary        = Color.White,
    tertiary           = TertiaryContainer,
    background         = PremiumLightBg,
    surface            = Color.White,
    surfaceVariant     = Color(0xFFF0F3F7),
    onBackground       = Color(0xFF1A1D20),
    onSurface          = Color(0xFF1A1D20),
    onSurfaceVariant   = Color(0xFF8A94A6),
    outline            = Color(0xFFE8EDF3),
    error              = Color(0xFFE57373)
)

fun obsidianColorScheme(primary: Color = Crimson) = darkColorScheme(
    primary            = primary,
    primaryContainer   = PrimaryContainer,
    onPrimary          = OnPrimary,
    secondary          = Cyan,
    secondaryContainer = SecondaryContainer,
    onSecondary        = OnSecondary,
    tertiary           = TertiaryContainer,
    background         = Obsidian,
    surface            = Surface,
    surfaceVariant     = SurfaceContainerHighest,
    onBackground       = OnSurface,
    onSurface          = OnSurface,
    onSurfaceVariant   = ActiveGrey,
    outline            = OutlineVariant,
    error              = Color(0xFFFFB4AB)
)

@Composable
fun NovaHostTheme(
    themeState: NovaHostThemeState = NovaHostThemeState(),
    robotBranding: RobotBranding = RobotBranding(),
    content: @Composable () -> Unit
) {
    // primaryColor already defaults to SoftLightBlue, so the old Crimson
    // special-case is gone. A robot's accent_color flows straight through.
    // Dark is the real scheme -- see NovaHostThemeState.useLightScheme.
    val colorScheme = if (themeState.useLightScheme) {
        premiumLightColorScheme(themeState.primaryColor)
    } else {
        obsidianColorScheme(themeState.primaryColor)
    }
    val baseStyle = robotFontStyleToTextStyle(themeState.robotFontStyle)
    val robotTextStyle = baseStyle.copy(
        fontSize = themeState.robotNameFontSize.sp,
        color = themeState.robotNameFontColor
    )

    CompositionLocalProvider(
        LocalNovaHostTheme provides themeState,
        LocalRobotBranding provides robotBranding,
        LocalRobotFont provides robotTextStyle
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = NovaHostTypography,
            content     = content
        )
    }
}

val NovaHostThemeState.cardShape: Shape
    get() = if (useRoundedShape) RoundedCornerShape(12.dp) else RectangleShape
