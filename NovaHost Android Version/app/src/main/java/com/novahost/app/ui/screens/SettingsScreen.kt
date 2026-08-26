package com.novahost.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novahost.app.ui.home.HomeTopChromeInset
import com.novahost.app.ui.home.loadHomeCustomization
import com.novahost.app.ui.theme.*
import kotlinx.coroutines.launch

/*
 * Settings, rebuilt from the "NovaHost Settings v2" design.
 *
 * Three things the old screen did that this one does not:
 *
 *  1. It nested a bordered card (GlassCard) inside another bordered card for
 *     every group. One glass panel per group now, hairline dividers between
 *     rows.
 *  2. It put every choice on the hub, most of them in horizontal scrollers that
 *     clipped the swatches and named none of them. Choices moved to their own
 *     panes, as named rows with the hex beside them.
 *  3. It ended in a SAVE THEME button that saved nothing new -- every control
 *     already wrote through on tap. The button is gone and the footer says so.
 *
 * The panes are internal state, not nav routes: the whole thing is one screen
 * with one back affordance, which is what lets the live preview stay pinned
 * across a pane change. Layout, Widgets and Help are the exception -- those are
 * real screens already and the hub rows push to them.
 */

// ── Design tokens ──────────────────────────────────────────────────────────
// Lifted from the design's inline styles so a value appears once here rather
// than at each of the thirty-odd places that used it in the HTML.

private val PanelFill = Color(0x0EFFFFFF)      // rgba(255,255,255,.055)
private val PanelBorder = Color(0x17FFFFFF)    // rgba(255,255,255,.09)
private val RowDivider = Color(0x12FFFFFF)     // rgba(255,255,255,.07)
private val ChipIdle = Color(0x0DFFFFFF)       // rgba(255,255,255,.05)
private val ChipSelected = Color(0x1AFFFFFF)   // rgba(255,255,255,.1)
private val SwatchRing = Color(0x33FFFFFF)     // inset 0 0 0 1px rgba(255,255,255,.2)
private val ToggleTrackOff = Color(0x21FFFFFF) // rgba(255,255,255,.13)

private val HeadingText = Color(0xFFF4F6FA)
private val RowTitleText = Color(0xFFF2F4F7)
private val SectionText = Color(0xFFA7B0C0)
private val BodyText = Color(0xFFC6CDD8)
private val ListText = Color(0xFFDDE2EA)
private val ValueText = Color(0xFF98A2B3)
private val SubtitleText = Color(0xFF8B95A6)
private val MonoText = Color(0xFF7C8697)
private val ChevronTint = Color(0xFF6E7788)
private val FooterText = Color(0xFF5D6675)

private val DangerText = Color(0xFFFF6B84)
private val DangerHeading = Color(0xFFFF8FA3)
private val DangerLabel = Color(0xFFFFD3DB)
private val DangerFill = Color(0x12FF4D6D)
private val DangerFillArmed = Color(0x47FF4D6D)
private val DangerBorder = Color(0x3DFF6B84)
private val DangerBorderStrong = Color(0x80FF6B84)

private val PanelShape = RoundedCornerShape(22.dp)
private const val PUSH_MS = 300
private const val FADE_MS = 320

/** The base tone the wallpaper falls back to when no background has been picked. */
private val DefaultBackdrop = Color(0xFF0A0C10)

// ── Catalogues ─────────────────────────────────────────────────────────────
// Every swatch is a named row in the redesign, so the names that used to live
// in end-of-line comments are data now.

private data class NamedColor(val name: String, val color: Color)

private val ACCENTS = listOf(
    NamedColor("Electric Gold", Color(0xFFFFD700)),
    NamedColor("Mint Emerald", Color(0xFF00FA9A)),
    NamedColor("Crimson Red", Color(0xFFDC143C)),
    NamedColor("Cyber Violet", Color(0xFF8A2BE2)),
    NamedColor("Platinum Grey", Color(0xFFE5E5E5)),
    NamedColor("Safety Orange", Color(0xFFFF3D00)),
    NamedColor("Cyber Cyan", Color(0xFF00E5FF)),
    NamedColor("Royal Purple", Color(0xFF7B2FBE)),
    NamedColor("Neon Green", Color(0xFF00E676)),
    NamedColor("Hot Pink", Color(0xFFF5005A)),
    NamedColor("Electric Blue", Color(0xFF2979FF)),
    NamedColor("Neon Magenta", Color(0xFFD700FF)),
    NamedColor("Plasma Green", Color(0xFF39FF14)),
    NamedColor("Tech Gold", Color(0xFFFF9500)),
    NamedColor("Ice Blue", Color(0xFF00F2FE))
)

private data class NamedGradient(
    val name: String,
    val note: String,
    val start: Color,
    val end: Color
) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
}

private val GRADIENTS = listOf(
    NamedGradient("Nebula", "Cyan into magenta", Color(0xFF00E5FF), Color(0xFFD700FF)),
    NamedGradient("Deep Ruby", "Crimson into onyx", Color(0xFFDC143C), Color(0xFF1A1A1A)),
    NamedGradient("Solar", "Gold into crimson", Color(0xFFFFBF00), Color(0xFFDC143C)),
    NamedGradient("Matrix", "Green into void", Color(0xFF39FF14), Color(0xFF030303)),
    NamedGradient("Tide", "Ice blue into sapphire", Color(0xFF00F2FE), Color(0xFF1E3A8A)),
    NamedGradient("Plasma", "Blue into magenta", Color(0xFF2979FF), Color(0xFFD700FF)),
    NamedGradient("Ember", "Orange into rose", Color(0xFFFF5722), Color(0xFFF5005A)),
    NamedGradient("Ghost", "Bone into steel", Color(0xFFE8E8E8), Color(0xFF444444))
)

private val BACKDROPS = listOf(
    NamedColor("Obsidian", Color(0xFF0A0A0A)),
    NamedColor("Deep Space", Color(0xFF0F172A)),
    NamedColor("Midnight Blue", Color(0xFF0B1B3D)),
    NamedColor("Gunmetal", Color(0xFF1C1C1E)),
    NamedColor("Cosmic Purple", Color(0xFF1A0B2E)),
    NamedColor("Void Teal", Color(0xFF001519)),
    NamedColor("Cyber Dark", Color(0xFF050A10)),
    NamedColor("Molten Onyx", Color(0xFF1A0800))
)

/**
 * The glow ladder.
 *
 * [NovaGlow] is the one the design describes -- it is the enum that has an OFF,
 * and OFF is a real setting there ("Flat surfaces, best for battery"), not a
 * degraded one. [HolographicGlowMode] is carried alongside because
 * `Modifier.neonGlow` still reads it; picking a step writes both so the two
 * cannot drift.
 */
private data class GlowStep(
    val name: String,
    val note: String,
    val glow: NovaGlow,
    val holographic: HolographicGlowMode
)

private val GLOW_STEPS = listOf(
    GlowStep("Off", "Flat surfaces, best for battery", NovaGlow.OFF, HolographicGlowMode.OFF),
    GlowStep("Soft", "A faint halo on lit elements", NovaGlow.SOFT, HolographicGlowMode.SOFT),
    GlowStep("Medium", "The NovaHost default", NovaGlow.MEDIUM, HolographicGlowMode.MEDIUM),
    GlowStep("Intense", "Heavy bloom, arcade feel", NovaGlow.INTENSE, HolographicGlowMode.INTENSE)
)

private data class FontChoice(val name: String, val style: RobotFontStyle) {
    /** "Old Money — The Seasons" reads as "Old Money" on the hub row. */
    val shortName: String get() = name.substringBefore(" — ")
}

private val FONTS = listOf(
    FontChoice("Old Money — The Seasons", RobotFontStyle.OLD_MONEY),
    FontChoice("Bodoni FLF — Playfair", RobotFontStyle.BODONI_DISPLAY),
    FontChoice("Montserrat — Geometric", RobotFontStyle.MONTSERRAT_GEOMETRIC),
    FontChoice("Symphony — Perandory", RobotFontStyle.SYMPHONY_CREATIVE)
)

private val NAME_COLORS = listOf(
    NamedColor("White", Color.White),
    NamedColor("Bone", Color(0xFFE4E0D6)),
    NamedColor("Crimson", Crimson),
    NamedColor("Cyan", Cyan),
    NamedColor("Amber", Color(0xFFF0B95C)),
    NamedColor("Ink", Color(0xFF0A0C10))
)

private data class ShapeChoice(
    val name: String,
    val shape: HomeButtonShape,
    val markHeight: Dp,
    val markRadius: Dp
)

private val SHAPES = listOf(
    ShapeChoice("Circle", HomeButtonShape.CIRCLE, 40.dp, 20.dp),
    ShapeChoice("Oval", HomeButtonShape.OVAL, 28.dp, 14.dp),
    ShapeChoice("Square", HomeButtonShape.SQUARE, 40.dp, 12.dp)
)

/**
 * The panes the hub pushes to.
 *
 * [showsPreview] is the design's PREVIEW_ON map: the panes that change how home
 * looks get the pinned mock, the ones that do not (danger) get the space back.
 */
private enum class SettingsPane(val title: String, val showsPreview: Boolean = false) {
    HUB("Settings"),
    ACCENT("Accent colour", showsPreview = true),
    GRADIENT("Gradient accent", showsPreview = true),
    BACKDROP("Background", showsPreview = true),
    GLOW("Glow", showsPreview = true),
    TYPE("Bot name & type", showsPreview = true),
    BUTTON("Home button", showsPreview = true),
    DANGER("Remove broker")
}

// ── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    navController: NavController,
    homeViewModel: com.novahost.app.ui.viewmodels.HomeViewModel? = null
) {
    val theme = LocalNovaHostTheme.current
    val updateTheme = LocalNovaHostThemeUpdater.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var pane by remember { mutableStateOf(SettingsPane.HUB) }
    var armed by remember { mutableStateOf(false) }

    // The hub's Layout and Widgets rows report the live arrangement. Read it
    // from the shared view model when there is one so a change made on the
    // Interface screen is already reflected on the way back; fall back to a
    // direct prefs read for the preview/standalone case.
    val customization = homeViewModel?.customization?.collectAsState()?.value
        ?: remember { loadHomeCustomization(context) }

    val backdrop = theme.secondaryBackgroundColor ?: DefaultBackdrop
    val accent = theme.primaryColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawNovaWallpaper(accent, backdrop) }
    ) {
        // The scrim the design paints over the wallpaper: dark at the top so the
        // title holds, dark again at the bottom so the footer does.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB8080910),
                        0.34f to Color(0x6B080910),
                        1f to Color(0xDB080910)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Absolute, not inset-relative, and deliberately so: the menu
                // disc this screen has to live beside is placed at a flat
                // (start 24dp, top 48dp) from MainActivity, ignoring insets.
                // Measuring from the same origin is what keeps the two aligned
                // -- with statusBarsPadding here the disc's lower edge cut
                // across the first line of content on this device.
                .padding(top = 48.dp)
                .navigationBarsPadding()
        ) {
            SettingsHeader(
                title = pane.title,
                onBack = {
                    if (pane == SettingsPane.HUB) {
                        navController.popBackStack()
                    } else {
                        pane = SettingsPane.HUB
                        armed = false
                    }
                }
            )

            // Pinned rather than scroll-away. The design calls it a sticky
            // preview; the point is that it is on screen while you are tapping
            // the thing it previews, which is what makes the Save button
            // unnecessary.
            if (pane.showsPreview) {
                LivePreview(
                    theme = theme,
                    backdrop = backdrop,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
                )
            }

            AnimatedContent(
                targetState = pane,
                transitionSpec = {
                    val enter = if (targetState == SettingsPane.HUB) {
                        fadeIn(tween(FADE_MS))
                    } else {
                        fadeIn(tween(PUSH_MS, easing = NovaMotion.Emphasized)) +
                            slideInHorizontally(
                                tween(PUSH_MS, easing = NovaMotion.Emphasized)
                            ) { full -> full / 20 }
                    }
                    enter togetherWith fadeOut(tween(160)) using SizeTransform(clip = false)
                },
                label = "settingsPane"
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 40.dp)
                ) {
                    when (current) {
                        SettingsPane.HUB -> HubPane(
                            theme = theme,
                            backdrop = backdrop,
                            customization = customization,
                            onOpen = { pane = it },
                            onLayout = { navController.navigate(com.novahost.app.navigation.Routes.INTERFACE) },
                            onWidgets = { navController.navigate(com.novahost.app.navigation.Routes.ARRANGE_WIDGETS) },
                            onHelp = { navController.navigate(com.novahost.app.navigation.Routes.HELP_SUPPORT) },
                            onImmersive = { on ->
                                updateTheme(theme.copy(immersiveMode = on))
                                com.novahost.app.sdk.NovaPrefs.setImmersiveMode(context, on)
                            },
                            onLight = { on ->
                                updateTheme(theme.copy(useLightScheme = on))
                                com.novahost.app.sdk.NovaPrefs.setUseLightScheme(context, on)
                            }
                        )

                        SettingsPane.ACCENT -> AccentPane(theme) { picked ->
                            updateTheme(theme.copy(primaryColor = picked.color, isGlossTheme = false))
                            com.novahost.app.sdk.NovaPrefs.setAccentColor(context, picked.color.value.toLong())
                            com.novahost.app.sdk.NovaPrefs.setGlossTheme(context, false)
                        }

                        SettingsPane.GRADIENT -> GradientPane(theme) { picked ->
                            updateTheme(
                                theme.copy(
                                    primaryColor = picked.start,
                                    secondaryColor = picked.end,
                                    isGlossTheme = true
                                )
                            )
                            com.novahost.app.sdk.NovaPrefs.setAccentColor(context, picked.start.value.toLong())
                            com.novahost.app.sdk.NovaPrefs.setSecondaryAccent(context, picked.end.value.toLong())
                            com.novahost.app.sdk.NovaPrefs.setGlossTheme(context, true)
                        }

                        SettingsPane.BACKDROP -> BackdropPane(theme, backdrop) { picked ->
                            updateTheme(theme.copy(secondaryBackgroundColor = picked.color))
                            com.novahost.app.sdk.NovaPrefs.setSecondaryBgColor(context, picked.color.value.toLong())
                        }

                        SettingsPane.GLOW -> GlowPane(theme) { step ->
                            updateTheme(
                                theme.copy(glow = step.glow, holographicGlowMode = step.holographic)
                            )
                            com.novahost.app.sdk.NovaPrefs.setNovaGlow(context, step.glow.name)
                            com.novahost.app.sdk.NovaPrefs.setGlowMode(context, step.holographic.name)
                        }

                        SettingsPane.TYPE -> TypePane(
                            theme = theme,
                            onFont = { choice ->
                                updateTheme(theme.copy(robotFontStyle = choice.style))
                                com.novahost.app.sdk.NovaPrefs.setRobotFontStyle(context, choice.style.name)
                            },
                            onSize = { size ->
                                updateTheme(theme.copy(robotNameFontSize = size))
                                com.novahost.app.sdk.NovaPrefs.setRobotNameFontSize(context, size)
                            },
                            onColor = { picked ->
                                updateTheme(theme.copy(robotNameFontColor = picked.color))
                                com.novahost.app.sdk.NovaPrefs.setRobotNameFontColor(context, picked.color.value.toLong())
                            }
                        )

                        SettingsPane.BUTTON -> ButtonPane(
                            theme = theme,
                            onShape = { choice ->
                                updateTheme(theme.copy(homeButtonShape = choice.shape))
                                com.novahost.app.sdk.NovaPrefs.setHomeButtonShape(context, choice.shape.name)
                            },
                            onScale = { scale ->
                                updateTheme(theme.copy(homeButtonScale = scale))
                                com.novahost.app.sdk.NovaPrefs.setHomeButtonScale(context, scale)
                            }
                        )

                        SettingsPane.DANGER -> {
                            val scope = rememberCoroutineScope()
                            DangerPane(
                                armed = armed,
                                onTap = {
                                    if (!armed) {
                                        armed = true
                                    } else {
                                        scope.launch {
                                            context.stopService(
                                                Intent(context, com.novahost.app.service.NovaHostPulseService::class.java)
                                            )
                                            com.novahost.app.sdk.MetaAPIManager.disconnect()
                                            com.novahost.app.sdk.TerminalPrefs.clear(context)
                                            armed = false
                                            navController.navigate(com.novahost.app.navigation.Routes.TERMINAL) {
                                                popUpTo(com.novahost.app.navigation.Routes.HOME) { inclusive = true }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Back disc and pane title.
 *
 * The design centres the title under a lone back button at the top left. This
 * app's top left is not free: `TopNavMenuOverlay` floats a 48dp menu disc at
 * (start 24dp, top 48dp) over every authenticated screen, drawn from
 * MainActivity above the nav graph, so nothing here can move it -- the first
 * build of this screen put the back disc straight underneath it. The back disc
 * therefore starts clear of that button ([HomeTopChromeInset], the inset the
 * home layouts already use for the same reason) and the title leads from it
 * rather than centring, which is what a two-button header wants anyway and
 * what keeps "Bot name & type" from being squeezed to an ellipsis.
 */
@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HomeTopChromeInset, end = 20.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                tint = Color(0xFFE9EAEC),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontFamily = SpaceGroteskFamily,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            color = HeadingText,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Panes ──────────────────────────────────────────────────────────────────

@Composable
private fun HubPane(
    theme: NovaHostThemeState,
    backdrop: Color,
    customization: com.novahost.app.ui.home.HomeCustomization,
    onOpen: (SettingsPane) -> Unit,
    onLayout: () -> Unit,
    onWidgets: () -> Unit,
    onHelp: () -> Unit,
    onImmersive: (Boolean) -> Unit,
    onLight: (Boolean) -> Unit
) {
    val accent = theme.primaryColor
    val glowStep = GLOW_STEPS.firstOrNull { it.glow == theme.glow } ?: GLOW_STEPS[2]
    val font = FONTS.firstOrNull { it.style == theme.robotFontStyle } ?: FONTS[2]
    val arrangement = customization.active

    Text(
        text = "Every look and layout for NovaHost, in one place.",
        fontSize = 14.sp,
        color = ValueText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 22.dp)
    )

    SectionLabel("Appearance")
    GlassPanel {
        SettingsRow(
            title = "Accent colour",
            subtitle = "Buttons, highlights and active states",
            onClick = { onOpen(SettingsPane.ACCENT) },
            trailing = { Swatch(theme.primaryColor, 20.dp) }
        )
        SettingsRow(
            title = "Gradient accent",
            subtitle = "Used on the home button and cards",
            onClick = { onOpen(SettingsPane.GRADIENT) },
            trailing = {
                Swatch(
                    brush = Brush.linearGradient(listOf(theme.primaryColor, theme.secondaryColor)),
                    size = 20.dp
                )
            }
        )
        SettingsRow(
            title = "Background",
            subtitle = "Base tone behind every screen",
            onClick = { onOpen(SettingsPane.BACKDROP) },
            trailing = { Swatch(backdrop, 20.dp) }
        )
        SettingsRow(
            title = "Glow",
            subtitle = "Halo strength around lit elements",
            value = glowStep.name,
            onClick = { onOpen(SettingsPane.GLOW) },
            divider = false,
            trailing = { Swatch(accent, 20.dp, glow = theme.glow) }
        )
    }

    Spacer(Modifier.height(26.dp))
    SectionLabel("Home screen")
    GlassPanel {
        SettingsRow(
            title = "Layout",
            subtitle = "Five arrangements, with or without art mode",
            value = customization.layout.displayName,
            onClick = onLayout
        )
        SettingsRow(
            title = "Widgets",
            subtitle = "Reorder or hide cards on the active layout",
            value = "${arrangement.shownCount} of ${arrangement.order.size}",
            onClick = onWidgets
        )
        SettingsRow(
            title = "Home button",
            subtitle = "Shape and size of the floating control",
            value = "${theme.homeButtonShape.label} · ${formatScale(theme.homeButtonScale)}×",
            onClick = { onOpen(SettingsPane.BUTTON) }
        )
        SettingsRow(
            title = "Bot name & type",
            subtitle = "Typeface, size and colour of the name",
            value = font.shortName,
            onClick = { onOpen(SettingsPane.TYPE) },
            divider = false,
            trailing = { Swatch(theme.robotNameFontColor, 20.dp) }
        )
    }

    Spacer(Modifier.height(26.dp))
    SectionLabel("Display")
    GlassPanel {
        ToggleRow(
            title = "Immersive background",
            subtitle = "Hide all cards, keep only the art",
            checked = theme.immersiveMode,
            accent = accent,
            onCheckedChange = onImmersive
        )
        ToggleRow(
            title = "Light theme",
            subtitle = "Menus only — home layouts stay dark",
            checked = theme.useLightScheme,
            accent = accent,
            divider = false,
            onCheckedChange = onLight
        )
    }

    Spacer(Modifier.height(26.dp))
    GlassPanel {
        SettingsRow(title = "Help & support", onClick = onHelp)
        SettingsRow(
            title = "Remove broker & wipe data",
            titleColor = DangerText,
            chevronColor = Color(0xFF8E4453),
            onClick = { onOpen(SettingsPane.DANGER) },
            divider = false
        )
    }

    Spacer(Modifier.height(18.dp))
    Text(
        // No Save button any more -- there is nothing left for it to do.
        text = "NOVAHOST · CHANGES SAVE INSTANTLY",
        fontFamily = MonoFamily,
        fontSize = 10.5.sp,
        color = FooterText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun AccentPane(theme: NovaHostThemeState, onPick: (NamedColor) -> Unit) {
    PaneBlurb("One colour drives buttons, sliders and active states across the app.")
    GlassPanel {
        ACCENTS.forEachIndexed { index, entry ->
            ChoiceRow(
                selected = !theme.isGlossTheme && theme.primaryColor == entry.color,
                accent = theme.primaryColor,
                divider = index != ACCENTS.lastIndex,
                onClick = { onPick(entry) },
                leading = { Swatch(entry.color, 28.dp) }
            ) {
                Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RowTitleText)
                Text(
                    text = entry.color.toHex(),
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = MonoText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun GradientPane(theme: NovaHostThemeState, onPick: (NamedGradient) -> Unit) {
    PaneBlurb("Gradients sit on the home button and the hero card only.")
    GlassPanel {
        GRADIENTS.forEachIndexed { index, entry ->
            ChoiceRow(
                selected = theme.isGlossTheme &&
                    theme.primaryColor == entry.start &&
                    theme.secondaryColor == entry.end,
                accent = theme.primaryColor,
                divider = index != GRADIENTS.lastIndex,
                onClick = { onPick(entry) },
                leading = { Swatch(brush = entry.brush, size = 28.dp) }
            ) {
                Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RowTitleText)
                Text(
                    text = entry.note,
                    fontSize = 12.5.sp,
                    color = SubtitleText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun BackdropPane(
    theme: NovaHostThemeState,
    backdrop: Color,
    onPick: (NamedColor) -> Unit
) {
    PaneBlurb("The base tone under the glass. Cards tint themselves to match.")
    GlassPanel {
        BACKDROPS.forEachIndexed { index, entry ->
            ChoiceRow(
                selected = backdrop == entry.color,
                accent = theme.primaryColor,
                divider = index != BACKDROPS.lastIndex,
                onClick = { onPick(entry) },
                leading = { Swatch(entry.color, 28.dp) }
            ) {
                Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RowTitleText)
                Text(
                    text = entry.color.toHex(),
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    color = MonoText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun GlowPane(theme: NovaHostThemeState, onPick: (GlowStep) -> Unit) {
    PaneBlurb("Stronger glow costs a little battery on OLED panels.")
    GlassPanel {
        GLOW_STEPS.forEachIndexed { index, step ->
            ChoiceRow(
                selected = theme.glow == step.glow,
                accent = theme.primaryColor,
                divider = index != GLOW_STEPS.lastIndex,
                onClick = { onPick(step) },
                // Each swatch wears the intensity it sells.
                leading = { Swatch(theme.primaryColor, 28.dp, glow = step.glow) }
            ) {
                Text(step.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RowTitleText)
                Text(
                    text = step.note,
                    fontSize = 12.5.sp,
                    color = SubtitleText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun TypePane(
    theme: NovaHostThemeState,
    onFont: (FontChoice) -> Unit,
    onSize: (Float) -> Unit,
    onColor: (NamedColor) -> Unit
) {
    PaneBlurb("Set in the real face, at the real size.")
    GlassPanel {
        FONTS.forEachIndexed { index, choice ->
            ChoiceRow(
                selected = theme.robotFontStyle == choice.style,
                accent = theme.primaryColor,
                divider = index != FONTS.lastIndex,
                verticalPadding = 16.dp,
                onClick = { onFont(choice) }
            ) {
                // The specimen is the control: the row is set in its own face.
                Text(
                    text = "NovaHost",
                    style = robotFontStyleToTextStyle(choice.style).copy(
                        fontSize = 24.sp,
                        color = HeadingText
                    )
                )
                Text(
                    text = choice.name,
                    fontSize = 12.sp,
                    color = SubtitleText,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    SectionLabel("Name size")
    SliderPanel(
        caption = "Scale of the bot name on home",
        value = "${theme.robotNameFontSize.toInt()}sp",
        accent = theme.primaryColor,
        sliderValue = theme.robotNameFontSize,
        range = 24f..72f,
        steps = 23,
        minLabel = "24",
        maxLabel = "72",
        onValueChange = onSize
    )

    Spacer(Modifier.height(24.dp))
    SectionLabel("Name colour")
    GlassPanel {
        NAME_COLORS.forEachIndexed { index, entry ->
            ChoiceRow(
                selected = theme.robotNameFontColor == entry.color,
                accent = theme.primaryColor,
                divider = index != NAME_COLORS.lastIndex,
                verticalPadding = 14.dp,
                onClick = { onColor(entry) },
                leading = { Swatch(entry.color, 26.dp) }
            ) {
                Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RowTitleText)
            }
        }
    }
}

@Composable
private fun ButtonPane(
    theme: NovaHostThemeState,
    onShape: (ShapeChoice) -> Unit,
    onScale: (Float) -> Unit
) {
    PaneBlurb("The floating control that opens NovaHost from anywhere.")

    val fill = if (theme.isGlossTheme) {
        Brush.linearGradient(listOf(theme.primaryColor, theme.secondaryColor))
    } else {
        Brush.linearGradient(listOf(theme.primaryColor, theme.primaryColor))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SHAPES.forEach { choice ->
            val selected = theme.homeButtonShape == choice.shape
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) ChipSelected else ChipIdle)
                    .border(
                        1.dp,
                        if (selected) theme.primaryColor.copy(alpha = 0.6f) else PanelBorder,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onShape(choice) }
                    .padding(top = 20.dp, bottom = 14.dp, start = 10.dp, end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(choice.markHeight)
                        .clip(RoundedCornerShape(choice.markRadius))
                        .background(fill)
                )
                Text(
                    text = choice.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) HeadingText else SectionText
                )
            }
        }
    }

    Spacer(Modifier.height(26.dp))
    SectionLabel("Size")
    SliderPanel(
        caption = "Relative to the default",
        value = "${formatScale(theme.homeButtonScale)}×",
        accent = theme.primaryColor,
        sliderValue = theme.homeButtonScale,
        range = 0.6f..1.8f,
        steps = 11,
        minLabel = "0.6×",
        maxLabel = "1.8×",
        onValueChange = onScale
    )
}

@Composable
private fun DangerPane(armed: Boolean, onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(DangerFill)
            .border(1.dp, DangerBorder, PanelShape)
            .padding(20.dp)
    ) {
        Text(
            text = "This cannot be undone",
            fontFamily = SpaceGroteskFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = DangerHeading
        )
        Text(
            text = "Disconnecting removes the broker link and erases everything " +
                "NovaHost stores on this device.",
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            color = BodyText,
            modifier = Modifier.padding(top = 10.dp)
        )
    }

    Spacer(Modifier.height(22.dp))
    GlassPanel {
        // Naming what goes, so "wipe data" is not a word the user has to trust.
        listOf(
            "Broker credentials and account link",
            "Trade history and calculator presets",
            "Every theme, layout and widget setting"
        ).forEachIndexed { index, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBottomDivider(index != 2)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("—", fontSize = 14.sp, color = DangerText)
                Text(line, fontSize = 14.sp, color = ListText)
            }
        }
    }

    Spacer(Modifier.height(26.dp))
    Text(
        text = if (armed) "Tap again to erase everything" else "Disconnect and erase",
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = DangerLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (armed) DangerFillArmed else DangerFill)
            .border(1.dp, DangerBorderStrong, RoundedCornerShape(18.dp))
            .clickable(onClick = onTap)
            .padding(17.dp)
    )
    Text(
        text = "Your licence key stays valid — you can reconnect any time.",
        fontSize = 12.5.sp,
        color = SubtitleText,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    )
}

// ── The live preview ───────────────────────────────────────────────────────

/**
 * The home mock that sits above every customization pane.
 *
 * It renders the same three things a home layout leads with -- wallpaper, bot
 * name, ignition control -- from the same theme values, so what you see here is
 * the change itself and not an illustration of it.
 */
@Composable
private fun LivePreview(
    theme: NovaHostThemeState,
    backdrop: Color,
    modifier: Modifier = Modifier
) {
    val accent = theme.primaryColor
    val fill = if (theme.isGlossTheme) {
        Brush.linearGradient(listOf(theme.primaryColor, theme.secondaryColor))
    } else {
        Brush.linearGradient(listOf(accent, accent))
    }
    val markSize = (34 * theme.homeButtonScale).dp
    val markShape: Shape = when (theme.homeButtonShape) {
        HomeButtonShape.SQUARE -> RoundedCornerShape(10.dp)
        else -> CircleShape
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .drawBehind { drawNovaWallpaper(accent, backdrop) }
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NovaHost",
                style = robotFontStyleToTextStyle(theme.robotFontStyle).copy(
                    // 0.4x, the design's ratio: the card is a scale model of
                    // home, so the name has to shrink with it.
                    fontSize = (theme.robotNameFontSize * 0.4f).sp,
                    color = theme.robotNameFontColor
                )
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(markSize)
                    .novaBloom(theme.glow, accent)
                    .clip(markShape)
                    .background(fill)
            )
        }
        Text(
            text = "LIVE PREVIEW",
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            letterSpacing = 1.3.sp,
            color = Color(0x80FFFFFF),
            modifier = Modifier.padding(start = 12.dp, top = 10.dp)
        )
    }
}

// ── Building blocks ────────────────────────────────────────────────────────

/**
 * One glass surface per group.
 *
 * Deliberately not [com.novahost.app.ui.components.GlassCard]: that one bakes
 * in 16dp of padding, which stops a divider from reaching the panel edge and is
 * how the old screen ended up with a card inside a card.
 */
@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(PanelFill)
            .border(1.dp, PanelBorder, PanelShape),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = SpaceGroteskFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = SectionText,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp)
    )
}

@Composable
private fun PaneBlurb(text: String) {
    Text(
        text = text,
        fontSize = 13.5.sp,
        color = ValueText,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 16.dp)
    )
}

/** A hub row: title, optional subtitle, optional value, optional swatch, chevron. */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = RowTitleText,
    chevronColor: Color = ChevronTint,
    divider: Boolean = true,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBottomDivider(divider)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = titleColor)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = SubtitleText,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (value != null) {
            Text(value, fontSize = 13.sp, color = ValueText)
        }
        trailing?.invoke()
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = chevronColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** A hub row whose control is a switch rather than a push. */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    divider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBottomDivider(divider)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = RowTitleText)
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = SubtitleText,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        NovaToggle(checked = checked, accent = accent)
    }
}

/** A picker row: leading swatch, the label block, a tick when it is the active choice. */
@Composable
private fun ChoiceRow(
    selected: Boolean,
    accent: Color,
    divider: Boolean,
    onClick: () -> Unit,
    verticalPadding: Dp = 15.dp,
    leading: (@Composable () -> Unit)? = null,
    label: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBottomDivider(divider)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), content = label)
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * The design's pill switch: a 48×29 track with a 24dp knob.
 *
 * Hand-rolled rather than Material's Switch because M3 draws its own outline
 * and a 52×32 footprint, neither of which sits on a hairline-divided row the
 * way the design's does.
 */
@Composable
private fun NovaToggle(checked: Boolean, accent: Color) {
    val offset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 19.dp else 0.dp,
        animationSpec = tween(220, easing = NovaMotion.Emphasized),
        label = "toggleKnob"
    )
    val track by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) accent else ToggleTrackOff,
        animationSpec = tween(220),
        label = "toggleTrack"
    )
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 29.dp)
            .clip(CircleShape)
            .background(track)
            .padding(2.5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(24.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** A slider in its own panel: caption, live value in accent, rail, end labels. */
@Composable
private fun SliderPanel(
    caption: String,
    value: String,
    accent: Color,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    minLabel: String,
    maxLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(PanelFill)
            .border(1.dp, PanelBorder, PanelShape)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(caption, fontSize = 14.sp, color = BodyText)
            Text(value, fontFamily = MonoFamily, fontSize = 12.sp, color = accent)
        }
        Slider(
            value = sliderValue.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFF4F6FA),
                activeTrackColor = accent,
                inactiveTrackColor = Color(0x24FFFFFF),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(minLabel, fontFamily = MonoFamily, fontSize = 10.sp, color = ChevronTint)
            Text(maxLabel, fontFamily = MonoFamily, fontSize = 10.sp, color = ChevronTint)
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    size: Dp,
    glow: NovaGlow = NovaGlow.OFF
) {
    Box(
        modifier = Modifier
            .size(size)
            .novaBloom(glow, color)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, SwatchRing, CircleShape)
    )
}

@Composable
private fun Swatch(brush: Brush, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, SwatchRing, CircleShape)
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────

/**
 * A hairline along the bottom edge, drawn rather than composed.
 *
 * A `Divider()` between rows would sit inside the panel's padding and stop
 * short of the edges; the design's runs the full width of the glass.
 */
private fun Modifier.drawBottomDivider(show: Boolean): Modifier =
    if (!show) this else drawBehind {
        val stroke = 1.dp.toPx()
        drawRect(
            color = RowDivider,
            topLeft = Offset(0f, size.height - stroke),
            size = androidx.compose.ui.geometry.Size(size.width, stroke)
        )
    }

/**
 * The halo under a lit element, at whatever intensity the operator picked.
 *
 * Painted rather than left to `Modifier.shadow`: an elevation shadow over a
 * near-black surface is all but invisible at swatch size, which made the four
 * rows of the Glow pane -- whose whole job is to show the difference between
 * Off and Intense -- render as four identical discs. This draws outside the
 * element's bounds and so costs the row no height.
 */
private fun Modifier.novaBloom(glow: NovaGlow, color: Color): Modifier =
    if (glow == NovaGlow.OFF) this else drawBehind {
        val radius = size.minDimension / 2f + glow.blur.toPx() * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = glow.bloomAlpha),
                0.45f to color.copy(alpha = glow.bloomAlpha * 0.7f),
                1f to Color.Transparent,
                center = center,
                radius = radius
            ),
            radius = radius
        )
    }

/**
 * The wallpaper both the screen and the preview card sit on.
 *
 * Three accent-tinted pools over the chosen base tone -- the same recipe in
 * both places, which is what makes the preview card an actual preview and not
 * a separate drawing that has to be kept in step by hand.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNovaWallpaper(
    accent: Color,
    base: Color
) {
    drawRect(base)
    drawRect(
        brush = Brush.radialGradient(
            0f to accent.copy(alpha = 0.18f),
            0.58f to Color.Transparent,
            center = Offset(size.width * 0.78f, size.height * 0.08f),
            radius = size.width * 1.2f
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            0f to accent.copy(alpha = 0.12f),
            0.60f to Color.Transparent,
            center = Offset(size.width * 0.12f, size.height * 0.92f),
            radius = size.width * 1.1f
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            0f to Color(0xFF147882).copy(alpha = 0.20f),
            0.70f to Color.Transparent,
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.width * 0.9f
        )
    )
}

private fun Color.toHex(): String =
    String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and toArgb())

// Locale-fixed: the value beside a slider is a number, and "1,2×" in a
// comma-decimal locale reads as two numbers.
private fun formatScale(value: Float): String =
    String.format(java.util.Locale.US, "%.1f", value)

private val HomeButtonShape.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
