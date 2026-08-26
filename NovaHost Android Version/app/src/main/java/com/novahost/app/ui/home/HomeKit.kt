package com.novahost.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novahost.app.R
import com.novahost.app.ui.theme.HomeAccentBlue
import com.novahost.app.ui.theme.HomeBorderSubtle
import com.novahost.app.ui.theme.HomeLive
import com.novahost.app.ui.theme.HomeSurfaceSunken
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.HomeTextValue
import com.novahost.app.ui.theme.NovaGlow

// ── Contrast floor ─────────────────────────────────────────────────────────
// Mentor accent, user override, art mode and per-element glow multiply into
// combinations that go illegible. These two helpers are the floor the design
// calls non-optional: no accent chosen in the portal or in Settings can push
// text or a control edge below a readable level over art.

/**
 * Lifts [this] until it reads against a dark scrim.
 *
 * A mentor is free to pick a near-black accent; over their own art that accent
 * becomes invisible rather than subtle. Blending toward white preserves the hue
 * they chose while guaranteeing the label can be read.
 */
fun Color.onArtFloor(minLuminance: Float = 0.42f): Color {
    var result = this
    var guard = 0
    while (result.luminance() < minLuminance && guard < 12) {
        result = lerpColor(result, Color.White, 0.18f)
        guard++
    }
    return result
}

/** Straight-line blend. Compose's own lerp is fine but pulls in a graphics import per call site. */
private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha
)

/**
 * The glow multiplier, matching the design's OFF / SOFT / MEDIUM / INTENSE rail.
 *
 * Every glow value in the layouts is a base figure times this, so one setting
 * moves all five interfaces together and OFF genuinely means no bloom anywhere.
 */
val NovaGlow.multiplier: Float
    get() = when (this) {
        NovaGlow.OFF -> 0f
        NovaGlow.SOFT -> 0.5f
        NovaGlow.MEDIUM -> 1f
        NovaGlow.INTENSE -> 1.6f
    }

/**
 * Left inset for a layout's top row.
 *
 * `TopNavMenuOverlay` floats a 48dp menu button at (start 24dp, top 48dp) over
 * every authenticated screen, Home included, and it is drawn from MainActivity
 * above the nav graph -- nothing a layout does can move it. So the layouts'
 * own top row starts clear of it rather than underneath it. If that overlay is
 * ever retired, this goes back to the standard 22dp gutter.
 */
val HomeTopChromeInset = 88.dp

/** The standard side gutter for home content. */
val HomeGutter = 22.dp

// ── Art ────────────────────────────────────────────────────────────────────

/**
 * The mentor's art, drawn behind a layout at the intensity [artMode] allows.
 *
 * [HomeArtMode.AVATAR] draws nothing here -- that mode keeps the art inside the
 * hero's own circular crop, so the background stays the layout's flat ground.
 * Every mode that does reach the background gets a scrim; that is what makes
 * the contrast floor hold no matter which image a mentor uploads.
 */
@Composable
fun MentorArtBackground(
    artUrl: String?,
    artMode: HomeArtMode,
    ground: Color,
    accent: Color,
    glow: NovaGlow,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(ground)) {
        when (artMode) {
            HomeArtMode.AVATAR -> Unit

            HomeArtMode.FRAMED -> {
                // Art occupies the top 56%; content stacks on the ground below it.
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.56f)) {
                    ArtImage(artUrl)
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to ground.copy(alpha = 0.55f),
                                0.4f to ground.copy(alpha = 0.10f),
                                1f to ground.copy(alpha = 0.90f)
                            )
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.52f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.22f to ground
                            )
                        )
                )
            }

            HomeArtMode.FULL -> {
                ArtImage(artUrl)
                // Four stops, not two: the middle of the image has to stay
                // visible or the art mode is pointless, while the top strip and
                // the control zone both need enough ground to read against.
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to ground.copy(alpha = 0.72f),
                            0.26f to ground.copy(alpha = 0.18f),
                            0.58f to ground.copy(alpha = 0.55f),
                            0.82f to ground.copy(alpha = 0.95f),
                            1f to ground
                        )
                    )
                )
                // Accent bloom under the control zone, so the ignition reads as
                // lit from below rather than pasted on.
                if (glow != NovaGlow.OFF) {
                    val bloom = (0.20f * glow.multiplier).coerceAtMost(0.40f)
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.62f to Color.Transparent,
                                0.88f to accent.copy(alpha = bloom),
                                1f to Color.Transparent
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtImage(artUrl: String?) {
    if (artUrl != null) {
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = painterResource(id = R.drawable.new_avatar),
            error = painterResource(id = R.drawable.new_avatar)
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.new_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── Chrome ─────────────────────────────────────────────────────────────────

/** The connection pill. A pulsing dot only when the broker is genuinely linked. */
@Composable
fun HomeStatusPill(
    text: String,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PulsingDot(color = if (connected) HomeLive else HomeTextFaint, animate = connected)
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun PulsingDot(color: Color, animate: Boolean = true, size: Dp = 5.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = if (animate) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = if (animate) alpha else 1f))
    )
}

/** The settings affordance every layout carries, as a glass disc over art. */
@Composable
fun HomeSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Settings,
            contentDescription = "Settings",
            tint = Color.White,
            modifier = Modifier.size(19.dp)
        )
    }
}

// ── Controls ───────────────────────────────────────────────────────────────

/**
 * The one obvious glowing control. Every layout has exactly one.
 *
 * [wide] draws the pill form used by Full-Bleed Art, Glass Stack and Signal
 * Feed; Focus Engine uses [IgnitionCircle] instead.
 */
@Composable
fun IgnitionCta(
    isRunning: Boolean,
    isConnecting: Boolean,
    accent: Color,
    glow: NovaGlow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    height: Dp = 66.dp
) {
    val haptics = LocalHapticFeedback.current
    val edge = accent.onArtFloor()
    val transition = rememberInfiniteTransition(label = "cta")
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ctaBreath"
    )

    Box(
        modifier = modifier
            .height(height)
            .scale(breath)
            .shadow(
                elevation = (34.dp * glow.multiplier).coerceAtMost(40.dp),
                shape = shape,
                spotColor = edge,
                ambientColor = edge.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(edge.copy(alpha = 0.34f), edge.copy(alpha = 0.16f))
                )
            )
            .border(1.5.dp, edge, shape)
            .clickable(enabled = !isConnecting) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (isRunning) "STOP" else "START",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/** Focus Engine's 172dp ignition. The whole screen exists to frame this. */
@Composable
fun IgnitionCircle(
    isRunning: Boolean,
    isConnecting: Boolean,
    accent: Color,
    glow: NovaGlow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val edge = accent.onArtFloor()
    val transition = rememberInfiniteTransition(label = "ignition")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ignitionPulse"
    )

    Box(
        modifier = modifier
            .size(172.dp)
            .scale(if (isRunning) pulse else 1f)
            .shadow(
                elevation = (56.dp * glow.multiplier).coerceAtMost(60.dp),
                shape = CircleShape,
                spotColor = edge,
                ambientColor = edge.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(edge.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.82f)))
            )
            .border(2.dp, edge, CircleShape)
            .clickable(enabled = !isConnecting) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isConnecting) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(58.dp)
                )
                Text(
                    text = if (isRunning) "STOP" else "START",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            }
        }
    }
}

/** A quiet satellite control. Never competes with the ignition. */
@Composable
fun SatelliteButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    size: Dp = 66.dp
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * The ignition row shared by three layouts: one glowing CTA flanked by Quotes
 * and Asset Hub. Pinned as [HomeWidget.IGNITION_POD].
 */
@Composable
fun IgnitionRow(
    isRunning: Boolean,
    isConnecting: Boolean,
    accent: Color,
    glow: NovaGlow,
    onToggleRun: () -> Unit,
    onQuotes: () -> Unit,
    onAssetHub: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    controlHeight: Dp = 66.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IgnitionCta(
            isRunning = isRunning,
            isConnecting = isConnecting,
            accent = accent,
            glow = glow,
            onClick = onToggleRun,
            modifier = Modifier.weight(1f),
            shape = shape,
            height = controlHeight
        )
        SatelliteButton(Icons.Rounded.QueryStats, "Quotes", onQuotes, shape = shape, size = controlHeight)
        SatelliteButton(Icons.Rounded.Layers, "Asset Hub", onAssetHub, shape = shape, size = controlHeight)
    }
}

// ── Readouts ───────────────────────────────────────────────────────────────

/** A label-over-value tile. Values are monospace so a changing digit does not reflow the row. */
@Composable
fun HomeStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HomeTextValue,
    glass: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (glass) 20.dp else 11.dp))
            .background(if (glass) Color.White.copy(alpha = 0.07f) else HomeSurfaceSunken)
            .border(
                1.dp,
                if (glass) Color.White.copy(alpha = 0.14f) else HomeBorderSubtle,
                RoundedCornerShape(if (glass) 20.dp else 11.dp)
            )
            .padding(horizontal = if (glass) 16.dp else 11.dp, vertical = if (glass) 15.dp else 10.dp)
    ) {
        Column {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.44f),
                fontSize = if (glass) 9.sp else 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(if (glass) 6.dp else 4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = if (glass) 19.sp else 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** Section heading with the hairline the design runs to the right of every label. */
@Composable
fun HomeSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HomeAccentBlue,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** The "POWERED BY NOVAHOST" footer. Deliberately the faintest thing on screen. */
@Composable
fun PoweredByFooter(modifier: Modifier = Modifier) {
    Text(
        text = "POWERED BY NOVAHOST",
        color = Color.White.copy(alpha = 0.34f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/** A frosted panel. Glass Stack's whole vocabulary, reused by widgets on art. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content
    )
}

/** A flat card on a flat ground. Signal Feed and the settings screens use this instead of glass. */
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(13.dp),
    borderColor: Color = HomeBorderSubtle,
    background: Color = HomeSurfaceSunken,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        content = content
    )
}
