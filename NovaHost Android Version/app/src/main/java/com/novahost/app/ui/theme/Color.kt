package com.novahost.app.ui.theme

import androidx.compose.ui.graphics.Color

// === Obsidian Forge v2 Palette ===
val Obsidian         = Color(0xFF0A0A0A)
val SafetyOrange     = Color(0xFFFF3D00)
val Cyan             = Color(0xFF00E5FF)
val ActiveGrey       = Color(0xFFADAAAA)
val Crimson          = Color(0xFFDC143C)
val AmberWarning     = Color(0xFFFFBF00)

val NeonOrange       = Color(0xFFFF3D00)
val CyberCyan        = Color(0xFF00F0FF)
val NeonPurple       = Color(0xFFD700FF)
val ElectricGreen    = Color(0xFF39FF14)
val VolcanicOrange   = Color(0xFFFF5722)
val HotPink          = Color(0xFFFF007F)
val ElectricBlue     = Color(0xFF0022FF)

// ═══════════════════════════════════════════════════════════════════════════
// PREMIUM LIGHT — the shipping palette
// ═══════════════════════════════════════════════════════════════════════════
// Mirrors the semantic structure of the NovaHost Design System, translated
// from its graphite/cobalt dark ramp to light. Everything above this block is
// legacy and is being migrated out screen by screen.
//
// Naming rule: name by ROLE (NovaCanvas, NovaTextMuted), never by appearance
// (Obsidian, Cyan). A colour named for how it looks becomes a lie the first
// time the theme changes -- which is exactly how this file ended up holding
// three generations of palette at once.

// ── Surfaces ───────────────────────────────────────────────────────────────
val NovaCanvas        = Color(0xFFF4F7F9)  // app background
val NovaSurface       = Color(0xFFFFFFFF)  // cards, sheets, inputs
val NovaSurfaceSunken  = Color(0xFFEDF1F5) // log panels, inset wells, disabled
val NovaBorder        = Color(0xFFE2E8F0)  // hairlines, card borders
val NovaBorderStrong  = Color(0xFFCBD5E1)  // emphasised dividers

// ── Text ───────────────────────────────────────────────────────────────────
val NovaTextPrimary   = Color(0xFF1A1D20)  // headings, values
val NovaTextSecondary = Color(0xFF5A6472)  // body, descriptions
val NovaTextMuted     = Color(0xFF8A94A6)  // captions, placeholders, disabled
val NovaTextOnAccent  = Color(0xFFFFFFFF)  // label on a filled accent button

// ── Accent ─────────────────────────────────────────────────────────────────
val SoftLightBlue     = Color(0xFF5C9CE6)  // accent-primary (also the fallback
                                           // when a robot defines no accent)
val SoftLightPurple   = Color(0xFFA288E3)  // accent-secondary, used sparingly
val NovaAccentDim     = Color(0x1F5C9CE6)  // 12% -- tinted rows, selected chips

// ── Status ─────────────────────────────────────────────────────────────────
// Darkened from the design system's dark-mode values: #22C55E green and
// #EF4444 red both fail WCAG AA on white. These pass on #FFFFFF and #F4F7F9.
val NovaSuccess       = Color(0xFF16A34A)  // profit, connected, executed
val NovaDanger        = Color(0xFFDC2626)  // loss, disconnected, failed
val NovaWarning       = Color(0xFFD97706)  // pending, degraded
val NovaLive          = Color(0xFF5C9CE6)  // robot running

val NovaSuccessDim    = Color(0x1F16A34A)
val NovaDangerDim     = Color(0x1FDC2626)
val NovaWarningDim    = Color(0x1FD97706)

// ── Symbol categories ──────────────────────────────────────────────────────
// The design system's four fixed tones, flattened from gradients to solid
// fills for light surfaces.
val CatForex          = Color(0xFF0B68FF)
val CatCrypto         = Color(0xFF8B00E0)
val CatMetals         = Color(0xFFE08600)
val CatIndices        = Color(0xFFDC2626)

// ── Elevation shadow tints ─────────────────────────────────────────────────
// "Liquid Cyber-Glass" needs a dark ground to read. On light, interactivity is
// signalled by soft elevation instead -- the press physics survive, only the
// material changes.
val NovaShadow        = Color(0x0F000000)  // 6%  -- resting cards
val NovaShadowRaised  = Color(0x14000000)  // 8%  -- sheets, dialogs
val NovaShadowPressed = Color(0x1A000000)  // 10% -- press state

@Deprecated(
    "Legacy name kept only so existing screens compile. Use NovaCanvas.",
    ReplaceWith("NovaCanvas", "com.novahost.app.ui.theme.NovaCanvas")
)
val PremiumLightBg   = Color(0xFFF8F9FA)

// Surface hierarchy
val Surface          = Color(0xFF131313)
val SurfaceDim       = Color(0xFF0E0E0E)
val SurfaceContainerLow     = Color(0xFF1C1B1B)
val SurfaceContainer        = Color(0xFF201F1F)
val SurfaceContainerHigh    = Color(0xFF2A2A2A)
val SurfaceContainerHighest = Color(0xFF353534)
val SurfaceBright    = Color(0xFF3A3939)

// On-colors
val OnPrimary        = Color(0xFF621100)
val OnSecondary      = Color(0xFF00363D)
val OnSurface        = Color(0xFFE5E2E1)
val OnSurfaceVariant = Color(0xFFE8BDB3)
val OutlineVariant   = Color(0xFF5E3F38)

// Primary / Secondary token maps
val PrimaryContainer = Color(0xFFFF562C)
val SecondaryContainer = Color(0xFF00E3FD)
val TertiaryContainer  = Color(0xFF2D91FF)

// === NEW — Mesh Gradient Colors ===
val GradientDeep       = Color(0xFF050510)   // near-black with blue tint
val GradientMidnight   = Color(0xFF0D0D1A)   // deep navy
val GradientEmber      = Color(0xFF1A0800)   // warm ember undertone
val GradientCyanDark   = Color(0xFF001519)   // deep teal
val DeepPurple         = Color(0xFF1A0B2E)   // background mesh layer 0
val Charcoal           = Color(0xFF0D0D0D)   // background mesh layer 0
val DeepSpaceStart     = Color(0xFF050A10)
val DeepSpaceEnd       = Color(0xFF001220)

// === NEW — Glow Variants ===
val GlowOrange         = Color(0x80FF3D00)   // 50% alpha orange glow
val GlowCyan           = Color(0x6600E5FF)   // 40% alpha cyan glow
val GlowAmber          = Color(0x80FFBF00)   // 50% alpha amber glow
val GlowCrimson        = Color(0x80DC143C)   // 50% alpha crimson glow

// === NEW — Card Glass Overlay ===
val CardGlassTop       = Color(0x1AFFFFFF)   // 10% white — top shine
val CardGlassBottom    = Color(0x00FFFFFF)   // 0% — fades to transparent
val CardBorderLight    = Color(0x33FFFFFF)   // 20% white border highlight
val CardBorderDark     = Color(0x0DFFFFFF)   // 5% — bottom border

// ═══════════════════════════════════════════════════════════════════════════
// SPLASH + ONBOARDING — values taken verbatim from the two designs
// ═══════════════════════════════════════════════════════════════════════════
// Added rather than merged into the block above: the shipping palette already
// carries a success/danger pair tuned for WCAG AA on the dashboard surfaces,
// and the designs use a softer pair that only reads correctly against the
// onboarding art panes. Overwriting the originals would have re-toned every
// P/L figure in the app to make two screens match, so both pairs live here and
// each screen names the one it means.

// ── Surfaces ───────────────────────────────────────────────────────────────
val NovaArtTop          = Color(0xFFF1F5FA) // top of every art-pane gradient
val NovaSurfaceField    = Color(0xFFFBFCFD) // input fill at rest
val NovaBorderInput     = Color(0xFFE1E6EC) // input border at rest
val NovaBorderSoft      = Color(0xFFE6E9ED) // card hairline inside art
val NovaTrack           = Color(0xFFEBEEF2) // progress rail track
val NovaDisabledFill    = Color(0xFFEDF0F4) // CTA fill when it cannot be pressed
val NovaPlaceholderFill = Color(0xFFF1F5FA) // wireframe blocks inside art
val NovaSkeleton        = Color(0xFFE4E9EF) // skeleton lines inside art

// ── Text ───────────────────────────────────────────────────────────────────
val NovaTextDisabled    = Color(0xFFA2ABB8) // placeholder, disabled CTA label

// ── Accent ─────────────────────────────────────────────────────────────────
val NovaAccentSelected  = Color(0x145C9CE6) // 8%  — selected option row fill

/**
 * The accent, darkened for text and icons that sit on white.
 *
 * SoftLightBlue is a fill colour: at 3.0:1 on #FFFFFF it fails WCAG AA for
 * anything below 18sp, so a link or a button label tinted with it is unreadable
 * for the people who most need to read it. This is the same hue taken to 4.6:1.
 * Use it wherever the accent has to be *read* rather than merely seen.
 */
val NovaAccentDeep      = Color(0xFF3E7CC4)

// ── Status, onboarding tone ────────────────────────────────────────────────
val NovaSuccessSoft     = Color(0xFF2FA36B) // dots, check badge, bound state
val NovaSuccessText     = Color(0xFF238055) // label on a light success tint
val NovaSuccessTint     = Color(0x1A2FA36B)
val NovaDangerSoft      = Color(0xFFD6455D) // inline field error border
val NovaDangerText      = Color(0xFFC13A52) // helper copy under a failed field
val NovaDangerTint      = Color(0x0DD6455D)

// ── Art-only tones ─────────────────────────────────────────────────────────
val NovaCandleIdle      = Color(0xFFC9D3DE) // unhighlighted candles
val NovaFlowLine        = Color(0xFFB9C6D6) // dashed broker → host connector
val NovaPhoneSleep      = Color(0xFF0B0D10) // screen-off overlay

// ── Home Command Center (dark only) ────────────────────────────────────────
// The five home layouts are dark regardless of the app's light/dark setting:
// they sit on mentor art, and a light ground under a photograph has no reading
// that works. These are separate tokens rather than reuses of Obsidian/Surface
// because the layouts each need a slightly different black to keep their art
// gradient from banding.
val HomeCanvas        = Color(0xFF0E0E11) // settings screens, list backgrounds
val HomeCanvasArt     = Color(0xFF08080C) // Full-Bleed Art ground
val HomeCanvasFocus   = Color(0xFF05050A) // Focus Engine ground
val HomeCanvasGlass   = Color(0xFF0A0A10) // Glass Stack ground
val HomeCanvasFeed    = Color(0xFF08080B) // Signal Feed ground

val HomeSurface       = Color(0xFF16161A) // raised rows on the canvas
val HomeSurfaceRaised = Color(0xFF1A1A1E) // picker cards, phone bezel
val HomeSurfaceSunken = Color(0xFF101015) // stat tiles, feed rows
val HomeSurfaceWell   = Color(0xFF0C0C11) // terminal log well

val HomeBorder        = Color(0xFF26262E) // hairlines on canvas
val HomeBorderStrong  = Color(0xFF2C2C33) // chips, bezel, control outlines
val HomeBorderSubtle  = Color(0xFF22222A) // feed row hairline
val HomeBorderFaint   = Color(0xFF1E1E26) // log well hairline

val HomeTextBright    = Color(0xFFF4F4F6) // headings
val HomeTextPrimary   = Color(0xFFE8E8EA) // body on canvas
val HomeTextValue     = Color(0xFFC8C8D0) // monospace readouts
val HomeTextSecondary = Color(0xFF9A9AA4) // descriptions
val HomeTextMuted     = Color(0xFF8A8A94) // captions, section labels
val HomeTextDim       = Color(0xFF6E6E78) // metadata
val HomeTextFaint     = Color(0xFF5A5A64) // field labels inside a row

/** The five mentor accents the picker offers. Blue is the fallback when a robot defines none. */
val HomeAccentBlue    = Color(0xFF5C9CE6)
val HomeAccentViolet  = Color(0xFFA288E3)
val HomeAccentJade    = Color(0xFF3FD6A8)
val HomeAccentAmber   = Color(0xFFE8A33D)
val HomeAccentCrimson = Color(0xFFE0544E)

/** Live/OK state on a dark ground. Distinct from NovaSuccess, which is tuned for light. */
val HomeLive          = Color(0xFF3FD6A8)
val HomeSell          = Color(0xFFE0544E)

// ═══════════════════════════════════════════════════════════════════════════
// PERMISSIONS — values taken verbatim from NovaHost Permissions v3
// ═══════════════════════════════════════════════════════════════════════════
// The permissions flow draws two things nothing else in the app draws: a
// toggle rendered to look like the row in Android's own settings list, and a
// miniature phone showing the outcome of granting. Both need tones that sit
// between the ones above, so they are named here rather than approximated with
// the closest existing token -- the whole point of the mock row is that it
// reads as the real Android row, and a track two shades off breaks that.

/** Toggle track when off, and the un-reached segment of the step rail. */
val NovaToggleTrack   = Color(0xFFDCE3EB)

/** Copy inside the dark reassurance bubble. Not NovaTextOnAccent: the bubble
 *  is ink, not accent, and pure white on ink is harsher than the design. */
val NovaTextOnInk     = Color(0xFFE9EDF2)

// ── Hero phone art ─────────────────────────────────────────────────────────
val NovaArtScreen     = Color(0xFFF7F9FB) // miniature screen, overlay step
val NovaArtScreenAlt  = Color(0xFFEEF2F6) // miniature screen, notification step
val NovaArtSkeleton   = Color(0xFFD8E0E9) // the longer wireframe bar
val NovaArtSkeletonLo = Color(0xFFE2E8EF) // the shorter, fainter one
val NovaArtChip       = Color(0xFFE6EBF1) // tab stubs along the bottom

// ═══════════════════════════════════════════════════════════════════════════
// CHART SCANNER — values taken verbatim from NovaHost - Chart Scanner
// ═══════════════════════════════════════════════════════════════════════════
// The scanner reuses the Home* text and border tokens above wherever the design
// lands on the same value, so only what is genuinely new to this screen is
// named here. Two things are:
//
// 1. A buy/sell pair of its own. HomeLive (#3FD6A8) and HomeSell (#E0544E) are
//    status tones -- "the robot is running", "the connection dropped". The
//    scanner draws direction on money: a BUY pill, a green half of a price
//    ladder, a red stop line. The design tuned a separate pair for that job and
//    substituting the status pair flattens the ladder's two halves into nearly
//    the same hue. Same reasoning as the onboarding block above.
//
// 2. Ladder and allocation tones. TP1/TP2/TP3 have to read as three distinct
//    rungs at 6dp square, which no single accent with three alphas achieves.
//
// The surfaces are deliberately translucent rather than pre-composited flats:
// screens 01 and 02 sit on the mentor's art, and a solid card there would punch
// a hole in it. Compose cannot blur a backdrop cheaply, so the design's
// `backdrop-filter` reads here as fill alpha alone -- the art shows through,
// it just is not blurred.

// ── Ground ─────────────────────────────────────────────────────────────────
/** The scanner's own black. Deeper than HomeCanvas so the score ring's bloom has somewhere to fall off to. */
val ScanCanvas         = Color(0xFF0A0B11)

// ── Surfaces ───────────────────────────────────────────────────────────────
val ScanSurface        = Color(0x99101118) // rgba(16,17,24,.6)  -- breakdown rows, event cards
val ScanSurfaceRaised  = Color(0x9913141C) // rgba(19,20,28,.6)  -- quote card, chips, pills
val ScanSurfaceSunken  = Color(0x9E0C0D13) // rgba(12,13,19,.62) -- table header/footer, ladder
val ScanSurfaceGlass   = Color(0x9E131318) // rgba(19,19,24,.62) -- robot cards, dropzone
val ScanWell           = Color(0x8C0E0F16) // rgba(14,15,22,.55) -- inset stat wells inside a card
val ScanDisabledFill   = Color(0x9917171E) // rgba(23,23,30,.6)  -- the blocked CTA

// ── Borders ────────────────────────────────────────────────────────────────
// #1E1E26 and #22222A are HomeBorderFaint/HomeBorderSubtle; only the third is new.
val ScanBorderStrong   = Color(0xFF2C2C36) // control outlines, dashed dropzone, disabled CTA

// ── Track ──────────────────────────────────────────────────────────────────
/** The unfilled arc of a score ring. Lighter than any border: it is a bar, not a hairline. */
val ScanTrack          = Color(0xFF1C1C24)

// ── Direction ──────────────────────────────────────────────────────────────
val ScanBuy            = Color(0xFF3DD68C)
val ScanBuyInk         = Color(0xFF062A18) // label on a filled BUY pill
val ScanBuyQuiet       = Color(0xFFCDE8DC) // a TP price on the green half of the ladder
val ScanBuyMeta        = Color(0xFF6E7A72) // "1:3 · 25%" beside a TP label

val ScanSell           = Color(0xFFFF5C6C)
val ScanSellQuiet      = Color(0xFFF5C9CE) // the SL price on the red half of the ladder
val ScanSellMeta       = Color(0xFF7A6068) // "16.0 pips · −$96" beside the SL label
val ScanSellText       = Color(0xFFA87A80) // body copy inside a blocked panel

// ── Caution ────────────────────────────────────────────────────────────────
val ScanWarn           = Color(0xFFF5A524)
val ScanWarnInk        = Color(0xFF2B1D02) // currency tag on a filled amber chip
val ScanWarnText       = Color(0xFFC79A48) // body copy inside an amber panel
val ScanWarnDim        = Color(0xFF7A6A48) // the "+0" on a failed check

// ── Ladder rungs ───────────────────────────────────────────────────────────
// TP1 is ScanBuy. These two carry TP2 and TP3 down a green→teal→cyan ramp so
// the allocation bar reads as three parts rather than one green block.
val ScanTp2            = Color(0xFF2ED3B7)
val ScanTp2Ink         = Color(0xFF052A24)
val ScanTp3            = Color(0xFF2AB8C9)
val ScanTp3Ink         = Color(0xFF04262B)

// ── Text ───────────────────────────────────────────────────────────────────
// HomeTextPrimary/Value/Muted/Dim/Faint cover #E8E8EA/#C8C8D0/#8A8A94/#6E6E78/
// #5A5A64 exactly. These three are the scanner's own.
val ScanTextBright     = Color(0xFFF2F2F4) // screen titles, the score numeral
val ScanTextSoft       = Color(0xFFA7A7B0) // unselected chip labels, narrative copy
/** The faintest readable step. Scan counters, the privacy line, "tap to rebalance". */
val ScanTextTrace      = Color(0xFF4A4A54)
