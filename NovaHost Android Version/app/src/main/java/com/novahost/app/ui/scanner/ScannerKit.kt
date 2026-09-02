package com.novahost.app.ui.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.South
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novahost.app.R
import com.novahost.app.ui.home.multiplier
import com.novahost.app.ui.home.onArtFloor
import com.novahost.app.ui.theme.HomeBorderFaint
import com.novahost.app.ui.theme.HomeBorderSubtle
import com.novahost.app.ui.theme.HomeTextDim
import com.novahost.app.ui.theme.HomeTextFaint
import com.novahost.app.ui.theme.HomeTextPrimary
import com.novahost.app.ui.theme.HomeTextValue
import com.novahost.app.ui.theme.NovaGlow
import com.novahost.app.ui.theme.ScanBorderStrong
import com.novahost.app.ui.theme.ScanBuy
import com.novahost.app.ui.theme.ScanBuyInk
import com.novahost.app.ui.theme.ScanBuyMeta
import com.novahost.app.ui.theme.ScanBuyQuiet
import com.novahost.app.ui.theme.ScanCanvas
import com.novahost.app.ui.theme.ScanDisabledFill
import com.novahost.app.ui.theme.ScanSell
import com.novahost.app.ui.theme.ScanSellText
import com.novahost.app.ui.theme.ScanSellMeta
import com.novahost.app.ui.theme.ScanSellQuiet
import com.novahost.app.ui.theme.ScanSurface
import com.novahost.app.ui.theme.ScanSurfaceRaised
import com.novahost.app.ui.theme.ScanSurfaceSunken
import com.novahost.app.ui.theme.ScanTextBright
import com.novahost.app.ui.theme.ScanTextSoft
import com.novahost.app.ui.theme.ScanTextTrace
import com.novahost.app.ui.theme.ScanTp2
import com.novahost.app.ui.theme.ScanTp2Ink
import com.novahost.app.ui.theme.ScanTp3
import com.novahost.app.ui.theme.ScanTp3Ink
import com.novahost.app.ui.theme.ScanTrack
import com.novahost.app.ui.theme.ScanWarn
import com.novahost.app.ui.theme.ScanWell
import com.novahost.app.ui.theme.ScanWarnDim
import com.novahost.app.ui.theme.ScanWarnText

/**
 * The scanner's drawing vocabulary.
 *
 * Sibling to `ui/home/HomeKit.kt` and for the same reason: the five states in
 * the design share a score ring, a ladder, a check row and a rule row, and each
 * of those is fiddly enough that a second hand-rolled copy would drift from the
 * first within one change.
 *
 * Two things in the design are deliberately not reproduced here:
 *
 * - **The mock status bar** (9:41, signal, battery). Those are artboard
 *   furniture standing in for chrome Android already draws. Painting our own
 *   would put two clocks on the screen.
 * - **The bottom tab bar** (Home / Robots / Scanner / Settings). This app has
 *   no bottom navigation -- it navigates through `TopNavMenuOverlay`, a
 *   floating menu button drawn over every authenticated route. Building the
 *   design's tab bar would mean inventing a navigation model the rest of the
 *   app does not have.
 */

// -- Metrics ----------------------------------------------------------------

/**
 * Top inset that clears the floating nav button.
 *
 * `TopNavMenuOverlay` puts a 48dp disc at (start 24dp, top 48dp) over this
 * route, drawn from MainActivity above the nav graph, so it occupies the first
 * 96dp of the window no matter what this screen does. Home solves the same
 * collision by insetting its top row horizontally (`HomeTopChromeInset`), but
 * home's top row is a pill and a disc. The scanner's is a back arrow, a title
 * and a trailing action spanning the full width, and squashing that by 88dp
 * would cost the header its composition -- so this one drops below the button
 * instead of stepping around it.
 */
val ScannerTopInset = 56.dp

/** The design's side gutter, on every one of the five states. */
val ScannerGutter = 20.dp

// -- Ground -----------------------------------------------------------------

/**
 * The mentor's art, blurred back until it is texture rather than subject.
 *
 * The design blurs the avatar 34px and drops it to half opacity behind a
 * four-stop scrim, which is the same contrast-floor move the home layouts make
 * -- the art has to survive whatever image a mentor uploads without taking the
 * score numeral down with it.
 *
 * [artFraction] is how far down the screen the art is allowed to reach before
 * the ground takes over completely. The later states in the design drop the art
 * entirely and keep only the accent bloom, because a price ladder read against
 * a photograph is a price ladder nobody reads.
 */
@Composable
fun ScannerBackground(
    artUrl: String?,
    accent: Color,
    glow: NovaGlow,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    bloomStrength: Float = 0.16f
) {
    // `Modifier.blur` does nothing below API 31 -- it does not throw, it simply
    // draws the image sharp. minSdk here is 26, so on a real slice of devices
    // the mentor's photograph would sit crisp behind the score numeral, which is
    // the exact contrast failure the blur exists to prevent. Where the blur
    // cannot run, the scrim is taken heavier to buy back the same separation.
    val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    Box(modifier = modifier.fillMaxSize().background(ScanCanvas)) {
        if (showArt) {
            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                val art = if (canBlur) Modifier.fillMaxSize().blur(34.dp) else Modifier.fillMaxSize()
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = art,
                        placeholder = painterResource(id = R.drawable.new_avatar),
                        error = painterResource(id = R.drawable.new_avatar)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.new_avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = art
                    )
                }
                val lift = if (canBlur) 0f else 0.16f
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to ScanCanvas.copy(alpha = 0.40f + lift),
                            0.32f to ScanCanvas.copy(alpha = (0.80f + lift).coerceAtMost(1f)),
                            0.60f to ScanCanvas.copy(alpha = (0.94f + lift).coerceAtMost(1f)),
                            1f to ScanCanvas
                        )
                    )
                )
            }
        }

        // The accent bloom under the top chrome. Present in all five states,
        // art or no art -- it is what ties the screen to the active robot.
        if (glow != NovaGlow.OFF) {
            val strength = (bloomStrength * glow.multiplier).coerceAtMost(0.34f)
            Box(
                modifier = Modifier.fillMaxWidth().height(340.dp).background(
                    Brush.verticalGradient(
                        0f to accent.copy(alpha = strength),
                        1f to Color.Transparent
                    )
                )
            )
        }
    }
}

// -- Chrome -----------------------------------------------------------------

/**
 * The header every state carries: back, an identity block, one trailing action.
 *
 * [subtitle] and [inlineDetail] are alternatives, not companions -- the input
 * state stacks a caption under the title, while the result states run the price
 * and timeframe along the baseline beside it.
 */
@Composable
fun ScannerHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    inlineDetail: (@Composable RowScope.() -> Unit)? = null,
    divider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (divider) Modifier.drawBehind {
                    drawLine(
                        color = Color(0xFF1A1A20),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                } else Modifier
            )
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = HomeTextDim,
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onBack)
        )
        if (inlineDetail != null) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    color = ScanTextBright,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                inlineDetail()
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ScanTextBright,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = HomeTextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
        if (trailing != null) trailing()
    }
}

/** The all-caps section heading, and whatever the design hangs off its right edge. */
@Composable
fun ScanSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HomeTextFaint,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** A bordered surface. The scanner's single most repeated shape. */
@Composable
fun ScanCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    background: Color = ScanSurface,
    borderColor: Color = HomeBorderFaint,
    contentPadding: Dp = 13.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content
    )
}

/** A label-over-value well, inset inside a card rather than sitting on the ground. */
@Composable
fun ScanWellTile(
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HomeTextPrimary,
    value: String? = null,
    valueContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ScanWell)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = HomeTextFaint,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp
        )
        Spacer(Modifier.height(3.dp))
        if (valueContent != null) {
            valueContent()
        } else {
            Text(
                text = value.orEmpty(),
                color = valueColor,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// -- Score ------------------------------------------------------------------

/** The ring's fill, by how much conviction the score carries. */
private fun convictionColors(conviction: Conviction): List<Color> = when (conviction) {
    // Two tones rather than one: at 80/100 the arc is most of the circle, and a
    // single flat green reads as a solid disc with a bite out of it. The ramp
    // gives the eye a start and an end so the sweep reads as a measurement.
    Conviction.STRONG -> listOf(ScanBuy, ScanTp2)
    Conviction.MODERATE -> listOf(ScanWarn)
    Conviction.WEAK -> listOf(ScanSell)
}

fun convictionColor(conviction: Conviction): Color = convictionColors(conviction).first()

/**
 * The confluence meter.
 *
 * Starts at six o'clock and sweeps clockwise, matching the design's
 * `conic-gradient(from 0.5turn, ...)`. Starting at the bottom rather than the
 * top matters: a meter that starts at twelve and ends near twelve at a high
 * score has its most important reading squeezed against its own origin.
 */
@Composable
fun ScoreRing(
    score: Int,
    conviction: Conviction,
    modifier: Modifier = Modifier,
    diameter: Dp = 186.dp,
    thickness: Dp = 17.dp,
    glow: NovaGlow = NovaGlow.MEDIUM,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = convictionColors(conviction)
    val fraction = (score.coerceIn(0, 100)) / 100f

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        if (glow != NovaGlow.OFF) {
            val bloom = (0.16f * glow.multiplier).coerceAtMost(0.30f)
            Box(
                modifier = Modifier.fillMaxSize().drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to colors.first().copy(alpha = bloom),
                            1f to Color.Transparent
                        ),
                        radius = size.minDimension * 0.78f
                    )
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val strokePx = thickness.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Butt)

            drawArc(
                color = ScanTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // 90 degrees is six o'clock in Compose's frame, where zero is three.
            val start = 90f
            val total = 360f * fraction
            if (total > 0f) {
                val segment = total / colors.size
                colors.forEachIndexed { index, color ->
                    drawArc(
                        color = color,
                        startAngle = start + segment * index,
                        // A hairline of overlap between neighbouring segments.
                        // Without it antialiasing leaves a track-coloured seam
                        // that reads as a gap in the score.
                        sweepAngle = segment + if (index < colors.lastIndex) 0.6f else 0f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }
            }
        })

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/** The filled BUY / SELL pill. */
@Composable
fun DirectionPill(direction: Direction, modifier: Modifier = Modifier) {
    val fill = if (direction == Direction.BUY) ScanBuy else ScanSell
    val ink = if (direction == Direction.BUY) ScanBuyInk else Color(0xFF3A0A11)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (direction == Direction.BUY) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = direction.label,
            color = ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.6.sp
        )
    }
}

/** The outlined conviction chip that sits beside the direction pill. */
@Composable
fun ConvictionChip(conviction: Conviction, modifier: Modifier = Modifier) {
    val tint = convictionColor(conviction)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, tint.copy(alpha = 0.40f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = conviction.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
    }
}

/**
 * One line of the score breakdown.
 *
 * A failed check is amber rather than red on purpose. Red is what the guardrail
 * screen uses for a blocker, and a check that merely scored zero has not
 * blocked anything -- it has only declined to add twenty points.
 */
@Composable
fun ConfluenceRow(check: ConfluenceCheck, modifier: Modifier = Modifier) {
    val tint = if (check.passed) ScanBuy else ScanWarn
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (check.passed) ScanSurface else ScanWarn.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (check.passed) HomeBorderFaint else ScanWarn.copy(alpha = 0.32f),
                shape
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Icon(
            if (check.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.title,
                color = HomeTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = check.detail,
                color = if (check.passed) HomeTextDim else ScanWarnText,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Text(
            text = "+" + check.points,
            color = if (check.passed) ScanBuy else ScanWarnDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// -- Ladder -----------------------------------------------------------------

private val LadderRungHeight = 20.dp
private val LadderEntryHeight = 22.dp
private val LadderGap = 46.dp
private val LadderEntryGap = 44.dp

/** One horizontal line of the ladder, before it knows where it sits. */
private data class LadderRung(
    val label: String,
    val meta: String,
    val price: String,
    val tint: Color,
    val metaTint: Color,
    val priceTint: Color,
    val isEntry: Boolean = false,
    val isStop: Boolean = false,
    val dashed: Boolean = true
) {
    val height: Dp get() = if (isEntry) LadderEntryHeight else LadderRungHeight
}

/**
 * The vertical run between two rungs.
 *
 * Every gap is the same except the one separating entry from stop, which the
 * design pulls in slightly. Deriving it from the pair rather than from the index
 * is what lets the same list render a BUY (stop last) and a SELL (stop first).
 */
private fun gapBetween(above: LadderRung, below: LadderRung): Dp =
    if ((above.isEntry && below.isStop) || (above.isStop && below.isEntry)) LadderEntryGap
    else LadderGap

/**
 * The price ladder: three targets, the entry, the stop, drawn to scale in order.
 *
 * Built from price order rather than from a fixed BUY arrangement, so a SELL
 * plan puts its targets below the entry and its stop above without a second
 * code path. The design only draws the BUY case; hardcoding that would have
 * rendered every SELL upside down.
 */
@Composable
fun PriceLadder(plan: TradePlan, modifier: Modifier = Modifier) {
    val instrument = plan.instrument
    val isBuy = plan.direction == Direction.BUY

    // Furthest target first. `legs` is ordered TP1..TP3 by R multiple, and the
    // furthest target is the one that sits furthest from the entry line.
    val targets = plan.legs.reversed().map { leg ->
        LadderRung(
            label = leg.name,
            meta = "1:" + trimmed(leg.rMultiple) + " · " + (leg.allocation * 100).toInt() + "%",
            price = instrument.formatPrice(leg.price),
            tint = ScanBuy,
            metaTint = ScanBuyMeta,
            priceTint = ScanBuyQuiet,
            dashed = true
        )
    }

    val entry = LadderRung(
        label = "ENTRY",
        meta = "market · " + String.format("%.2f", plan.totalLots) + " lots",
        price = instrument.formatPrice(plan.entry),
        tint = Color.White,
        metaTint = HomeTextDim,
        priceTint = Color.White,
        isEntry = true,
        dashed = false
    )

    val stop = LadderRung(
        label = "SL",
        meta = trimmed(plan.stopPips) + " pips · -" + plan.money(plan.actualRisk),
        price = instrument.formatPrice(plan.stop),
        tint = ScanSell,
        metaTint = ScanSellMeta,
        priceTint = ScanSellQuiet,
        isStop = true,
        dashed = false
    )

    // Highest price at the top, always. A buy stacks its targets above the
    // entry and puts the stop underneath; a sell is the same ladder inverted.
    val rungs = if (isBuy) targets + entry + stop else listOf(stop, entry) + targets.reversed()
    val entryIndex = rungs.indexOfFirst { it.isEntry }

    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ScanSurfaceSunken)
            .border(1.dp, HomeBorderFaint, shape)
            .drawBehind {
                // Profit above the entry for a buy, below it for a sell. The
                // split is measured off the rung stack rather than hardcoded so
                // it lands on the entry line in both directions.
                var offset = 0f
                for (index in 0 until entryIndex) {
                    offset += rungs[index].height.toPx()
                    offset += gapBetween(rungs[index], rungs[index + 1]).toPx()
                }
                val split = offset + LadderEntryHeight.toPx() / 2f

                val upperTint = if (isBuy) ScanBuy else ScanSell
                val lowerTint = if (isBuy) ScanSell else ScanBuy

                drawRect(
                    brush = Brush.verticalGradient(
                        0f to upperTint.copy(alpha = 0.20f),
                        1f to upperTint.copy(alpha = 0.03f),
                        startY = 0f,
                        endY = split
                    ),
                    size = Size(size.width, split)
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to lowerTint.copy(alpha = 0.05f),
                        1f to lowerTint.copy(alpha = 0.20f),
                        startY = split,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, split),
                    size = Size(size.width, size.height - split)
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rungs.forEachIndexed { index, rung ->
                LadderLine(rung)
                if (index < rungs.lastIndex) {
                    Spacer(Modifier.height(gapBetween(rung, rungs[index + 1])))
                }
            }
        }
    }
}

@Composable
private fun LadderLine(rung: LadderRung) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rung.height)
            .then(if (rung.isEntry) Modifier.background(Color.White.copy(alpha = 0.06f)) else Modifier)
            .drawBehind {
                val strokeWidth = if (rung.dashed) 1.dp.toPx() else 1.5.dp.toPx()
                drawLine(
                    color = rung.tint.copy(alpha = if (rung.dashed) 0.46f else 0.70f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidth,
                    pathEffect = if (rung.dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    } else null
                )
            }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rung.label,
            color = rung.tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.width(7.dp))
        Text(text = rung.meta, color = rung.metaTint, fontSize = 9.sp)
        Spacer(Modifier.weight(1f))
        Text(
            text = rung.price,
            color = rung.priceTint,
            fontSize = 11.sp,
            fontWeight = if (rung.isEntry) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** The three ladder tones, in rung order. Shared by the allocation bar and the table dots. */
val LadderTints = listOf(ScanBuy, ScanTp2, ScanTp3)
private val LadderInks = listOf(ScanBuyInk, ScanTp2Ink, ScanTp3Ink)

/** The single stacked bar showing how the position divides. */
@Composable
fun AllocationBar(plan: TradePlan, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape)
            .background(ScanSurface)
    ) {
        plan.legs.forEachIndexed { index, leg ->
            Box(
                modifier = Modifier
                    .weight(leg.allocation.toFloat().coerceAtLeast(0.01f))
                    .fillMaxSize()
                    .background(LadderTints[index]),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (leg.allocation * 100).toInt().toString() + "%",
                    color = LadderInks[index],
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// -- Rules ------------------------------------------------------------------

/** The icon and tone a severity draws with, everywhere it is drawn. */
fun severityTint(severity: RuleSeverity): Color = when (severity) {
    RuleSeverity.BLOCK -> ScanSell
    RuleSeverity.WARN -> ScanWarn
    RuleSeverity.PASS -> ScanBuy
}

private fun severityIcon(severity: RuleSeverity): ImageVector = when (severity) {
    RuleSeverity.BLOCK -> Icons.Rounded.Block
    RuleSeverity.WARN -> Icons.Rounded.Warning
    RuleSeverity.PASS -> Icons.Rounded.CheckCircle
}

/**
 * One guardrail, in either of the two forms the design draws it.
 *
 * [showBadge] is the difference between the passing panel, where the outcome is
 * a right-aligned figure, and the blocked panel, where it is a BLOCK / WARN /
 * PASS tag. Same rule, same data, two readings -- one is a receipt, the other
 * is a verdict.
 */
@Composable
fun GuardrailRow(
    outcome: GuardrailOutcome,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    showDetail: Boolean = true
) {
    val tint = severityTint(outcome.severity)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = if (showBadge) 9.dp else 7.dp),
        verticalAlignment = if (showBadge) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            severityIcon(outcome.severity),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (showBadge) 16.dp else 15.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = outcome.title,
                color = if (showBadge) HomeTextPrimary else HomeTextValue,
                fontSize = 11.sp,
                fontWeight = if (showBadge) FontWeight.SemiBold else FontWeight.Normal
            )
            if (showBadge && showDetail) {
                Text(
                    text = outcome.detail,
                    color = when (outcome.severity) {
                        RuleSeverity.BLOCK -> ScanSellText
                        RuleSeverity.WARN -> ScanWarnText
                        RuleSeverity.PASS -> ScanBuyMeta
                    },
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        if (showBadge) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(tint.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = outcome.severity.name,
                    color = tint,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Text(
                text = outcome.reading,
                color = tint,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// -- Context ----------------------------------------------------------------

/** One cell of the timeframe alignment grid. */
@Composable
fun TimeframeCard(read: TimeframeRead, modifier: Modifier = Modifier) {
    val aligned = read.bias != Bias.NEUTRAL
    val tint = when (read.bias) {
        Bias.BULLISH -> ScanBuy
        Bias.BEARISH -> ScanSell
        Bias.NEUTRAL -> HomeTextDim
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (aligned) tint.copy(alpha = 0.05f) else ScanSurface)
            .border(1.dp, if (aligned) tint.copy(alpha = 0.24f) else HomeBorderFaint, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = read.timeframe,
                color = HomeTextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Icon(
                when (read.bias) {
                    Bias.BULLISH -> Icons.Rounded.North
                    Bias.BEARISH -> Icons.Rounded.South
                    Bias.NEUTRAL -> Icons.Rounded.East
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = read.bias.label,
            color = HomeTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = read.note,
            color = HomeTextDim,
            fontSize = 9.sp,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// -- Controls ---------------------------------------------------------------

/** A pill in the symbol rail. Selected fills with the robot's accent. */
@Composable
fun SymbolChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) accent else ScanSurfaceRaised)
            .then(if (selected) Modifier else Modifier.border(1.dp, HomeBorderSubtle, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            // Ink on the accent, not white: a mentor's accent can be pale enough
            // that white on it is unreadable, and this pill is how the user knows
            // which instrument they are about to size a position in.
            color = if (selected) accent.readableInk() else ScanTextSoft,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    }
}

/**
 * Black or white, whichever survives on this fill.
 *
 * The design hardcodes near-black labels because its accent is a mid blue. The
 * app lets a mentor pick any accent, so the label has to follow the fill --
 * `#08080A` on a deep violet accent is the same invisible button the contrast
 * floor exists to prevent, just on the other side of the ramp.
 */
fun Color.readableInk(): Color =
    if (luminance() > 0.5f) Color(0xFF08080A) else Color.White

/** The Scalp / Day / Swing rail, and any other three-way choice shaped like it. */
@Composable
fun <T> SegmentedRail(
    options: List<T>,
    selected: T,
    accent: Color,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
    caption: (T) -> String
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ScanWell)
            .border(1.dp, HomeBorderSubtle, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val inner = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(inner)
                    .then(
                        if (isSelected) Modifier
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.46f), inner)
                        else Modifier
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) ScanTextBright else HomeTextDim,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = caption(option),
                    color = if (isSelected) accent.onArtFloor() else ScanTextTrace,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * [SegmentedRail] wrapped onto two rows.
 *
 * Four options do not fit one rail on a handset -- at 360dp each cell is about
 * 80dp and "Price Action" wraps mid-word -- so the strategy picker is a 2x2
 * grid. Same well, same selected treatment, same type: it should read as the
 * same control as the mode rail above it, only taller.
 *
 * A short odd list is padded with an invisible spacer rather than being allowed
 * to stretch, so three options give two full-width-half cells and a gap, not one
 * cell twice the size of its neighbour.
 */
@Composable
fun <T> SegmentedGrid(
    options: List<T>,
    selected: T,
    accent: Color,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    label: (T) -> String,
    caption: (T) -> String
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ScanWell)
            .border(1.dp, HomeBorderSubtle, shape)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    val isSelected = option == selected
                    val inner = RoundedCornerShape(10.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(inner)
                            .then(
                                if (isSelected) Modifier
                                    .background(accent.copy(alpha = 0.14f))
                                    .border(1.dp, accent.copy(alpha = 0.46f), inner)
                                else Modifier
                            )
                            .clickable { onSelect(option) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label(option),
                            color = if (isSelected) ScanTextBright else HomeTextDim,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = caption(option),
                            color = if (isSelected) accent.onArtFloor() else ScanTextTrace,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The screen's one committing action.
 *
 * [enabled] false does not merely dim it -- it swaps the fill for the sunken
 * disabled tone and drops the click entirely, so a blocked plan has no pressable
 * execute button anywhere on screen.
 */
@Composable
fun ScanPrimaryCta(
    label: String,
    icon: ImageVector,
    fill: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glow: NovaGlow = NovaGlow.MEDIUM,
    height: Dp = 56.dp
) {
    val ink = if (enabled) fill.readableInk() else HomeTextFaint
    val background = if (enabled) fill else ScanDisabledFill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(background)
            .then(
                if (enabled) Modifier
                else Modifier.border(1.dp, ScanBorderStrong, CircleShape)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.3.sp
        )
    }
}

/** The outlined "next section" affordance the design puts under states 02 and 03. */
@Composable
fun ScanAdvanceCta(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, ScanBorderStrong, CircleShape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = HomeTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = HomeTextPrimary, modifier = Modifier.size(18.dp))
    }
}

/** An icon-and-copy strip. Used for the split note, the privacy line and the event advice. */
@Composable
fun ScanNote(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    borderColor: Color? = null,
    textColor: Color = HomeTextDim
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(
                horizontal = if (borderColor != null) 13.dp else 2.dp,
                vertical = if (borderColor != null) 11.dp else 0.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text = text, color = textColor, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

/** The centred footnote under a CTA. The faintest text the scanner draws. */
@Composable
fun ScanFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ScanTextTrace,
        fontSize = 9.sp,
        letterSpacing = 0.4.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/** A dashed-outline container. The chart dropzone, and the empty robot slot. */
@Composable
fun DashedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = ScanBorderStrong,
    background: Color = ScanSurfaceRaised,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f)
                    )
                )
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
