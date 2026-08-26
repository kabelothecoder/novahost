package com.novaedge.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.  Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaedge.app.R
import com.novaedge.app.ui.theme.*

// ============================================================
// GlassCard — Premium glass container with inner glow
// ============================================================
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    blurAlpha: Float = 0.3f,
    borderColor: Color = Color(0x80FFFFFF),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .neonGlow(LocalNovaEdgeTheme.current.holographicGlowMode, LocalNovaEdgeTheme.current.primaryColor, 4.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SurfaceContainerHigh.copy(alpha = blurAlpha),
                        SurfaceContainer.copy(alpha = blurAlpha * 0.85f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(borderColor, CardBorderDark)
                ),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

// ============================================================
// GradientButton — Premium CTA with gradient fill & glow
// ============================================================
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPulsing: Boolean = false,
    gradientColors: List<Color> = emptyList(),
    glowColor: Color = Color.Unspecified,
    textColor: Color = OnSurface
) {
    val themeState = LocalNovaEdgeTheme.current
    val actualGlowColor = if (glowColor != Color.Unspecified) glowColor else themeState.primaryColor
    val actualGradientColors = if (gradientColors.isNotEmpty()) gradientColors else {
        if (themeState.isGlossTheme) listOf(themeState.primaryColor, themeState.secondaryColor)
        else listOf(themeState.primaryColor, themeState.primaryColor)
    }
    val pulseAnim = rememberInfiniteTransition(label = "btnPulse")
    val pulseGlowAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlowAlpha"
    )
    val activeGlowColor = if (isPulsing) actualGlowColor.copy(alpha = actualGlowColor.alpha * pulseGlowAlpha) else actualGlowColor
    val activeElevation = if (isPulsing) 18.dp else 12.dp
    val shape = CircleShape
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) activeElevation else 4.dp,
                shape = shape,
                ambientColor = if (enabled) activeGlowColor else Color.Transparent,
                spotColor = if (enabled) activeGlowColor else Color.Transparent
            )
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(actualGradientColors)
                else Brush.horizontalGradient(
                    listOf(
                        SurfaceContainerHighest,
                        SurfaceContainerHigh
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (enabled) Color(0x80FFFFFF) else Color.Transparent,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = text,
            fontWeight = FontWeight.Bold,
            fontSize   = 16.sp,
            color      = if (enabled) textColor else ActiveGrey,
            letterSpacing = 0.5.sp
        )
    }
}

// ============================================================
// CircularAvatar — "Master Machine" Double-Border
// ============================================================
@Composable
fun CircularAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor
    val secondaryColor = themeState.secondaryColor
    
    val pulseAnim = rememberInfiniteTransition(label = "breathePulse")
    val breatheScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )
    val breatheAlpha by pulseAnim.animateFloat(
        initialValue = 0.4f,
        targetValue  = 0.9f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(breatheScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer Orange Glow Border (2px)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    ambientColor = primaryColor.copy(alpha = breatheAlpha),
                    spotColor = primaryColor.copy(alpha = breatheAlpha)
                )
                .border(2.dp, if (themeState.isGlossTheme) Brush.linearGradient(listOf(primaryColor, secondaryColor)) else Brush.linearGradient(listOf(primaryColor, primaryColor)), CircleShape)
        )

        // Inner White Border (1px)
        Box(
            modifier = Modifier
                .size(size - 6.dp)
                .clip(CircleShape)
                .background(Surface)
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (themeState.robotAvatarUrl != null) {
                AsyncImage(
                    model = themeState.robotAvatarUrl,
                    contentDescription = "Dynamic Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Master Machine Avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

// ============================================================
// MetricGlassCard — Icon + label + value with accent stripe
// ============================================================
@Composable
fun MetricGlassCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = SafetyOrange
) {
    GlassCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Accent stripe + icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            color = Color(0xFFEDEDED),
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = OnSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

// ============================================================
// GlassAlertComponent — Validation alert
// ============================================================
@Composable
fun GlassAlertComponent(
    visible: Boolean,
    message: String = "Please enter a valid trading account number from your broker.",
    onDismiss: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn() + slideInVertically { -40 },
        exit    = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SafetyOrange.copy(alpha = 0.12f))
                .border(1.dp, SafetyOrange.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "⚠",
                    color      = SafetyOrange,
                    fontSize   = 18.sp,
                    modifier   = Modifier.padding(end = 10.dp)
                )
                Text(
                    text       = message,
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = SafetyOrange.copy(alpha = 0.9f),
                    modifier   = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("✕", color = SafetyOrange)
                }
            }
        }
    }
}

// ============================================================
// NovaEdgeSwitch — Live / Demo pill toggle with sliding indicator
// ============================================================
@Composable
fun NovaEdgeSwitch(
    isLive: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = if (isLive) SafetyOrange else Cyan

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(SurfaceContainerHigh)
            .border(1.dp, activeColor.copy(alpha = 0.25f), RoundedCornerShape(50.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(true to "LIVE", false to "DEMO").forEach { (value, label) ->
            val selected = isLive == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (selected) Brush.horizontalGradient(
                            listOf(
                                activeColor.copy(alpha = 0.3f),
                                activeColor.copy(alpha = 0.15f)
                            )
                        )
                        else Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.Transparent)
                        )
                    )
                    .clickable { onToggle(value) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color      = if (selected) activeColor else ActiveGrey,
                    fontSize   = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ============================================================
// BottomGlassDock — 5-tab floating nav with outlined/filled icon states
// ============================================================
data class NavItem(
    val route: String,
    val iconActive: ImageVector,
    val iconInactive: ImageVector,
    val label: String
)

@Composable
fun BottomGlassDock(
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        NavItem("home",     Icons.Rounded.Home,         Icons.Rounded.Home,         "Home"),
        NavItem("terminal", Icons.Rounded.Terminal,     Icons.Rounded.Terminal,     "MetaTrader"),
        NavItem("scanner",  Icons.Rounded.DocumentScanner,  Icons.Rounded.DocumentScanner,  "Chart Scanner"),
        NavItem("markets",  Icons.Rounded.ShowChart,    Icons.Rounded.ShowChart,    "Markets"),
        NavItem("settings", Icons.Rounded.Settings,     Icons.Rounded.Settings,     "Settings")
    )

    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor
    val secondaryColor = themeState.secondaryColor
    val accentBrush = if (themeState.isGlossTheme)
        Brush.horizontalGradient(listOf(primaryColor, secondaryColor))
    else
        Brush.horizontalGradient(listOf(primaryColor, primaryColor))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 20.dp)
            .shadow(24.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .border(1.dp, accentBrush, RoundedCornerShape(32.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val selected = selectedRoute == item.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigate(item.route) }
                    .background(if (selected) primaryColor.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(vertical = 8.dp)
            ) {
                if (selected) {
                    // Active: glowing filled icon with shadow
                    Icon(
                        imageVector = item.iconActive,
                        contentDescription = item.label,
                        tint = primaryColor,
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = primaryColor, spotColor = primaryColor)
                    )
                } else {
                    // Inactive: outlined / muted
                    Icon(
                        imageVector = item.iconInactive,
                        contentDescription = item.label,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.label,
                    fontSize = 9.sp,
                    color = if (selected) primaryColor else Color.White.copy(alpha = 0.35f),
                    letterSpacing = 0.3.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

// ============================================================
// StatusBadge — Top-right connection indicator
// ============================================================
@Composable
fun StatusBadge(isConnected: Boolean, modifier: Modifier = Modifier) {
    val color = if (isConnected) Color(0xFF00E676) else Crimson
    val text = if (isConnected) "Connected" else "Not Connected"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh.copy(alpha = 0.5f))
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .shadow(8.dp, CircleShape, ambientColor = color, spotColor = color)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = OnSurface,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================
// GlassTextField — Consistent 1px border styled input
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    isPassword: Boolean = false,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFFEDEDED), fontSize = 12.sp) },
        readOnly = readOnly,
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle Password Visibility",
                        tint = Color(0xFFEDEDED),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = Color(0x40FFFFFF),
            focusedContainerColor = SurfaceContainerHigh.copy(alpha = 0.3f),
            unfocusedContainerColor = SurfaceContainer.copy(alpha = 0.2f),
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface
        ),
        singleLine = true
    )
}

// ============================================================
// ShimmerButton — Connect Button with Shimmer
// ============================================================
@Composable
fun ShimmerButton(
    text: String,
    onClick: () -> Unit,
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan
) {
    val transition = rememberInfiniteTransition(label = "btnShimmer")
    val shimmerTranslate by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val pulseScale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val shimmerBrush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, Color(0x40FFFFFF), Color.Transparent),
        startX = shimmerTranslate,
        endX = shimmerTranslate + 400f
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(if (isConnecting) pulseScale else 1f)
            .shadow(16.dp, RoundedCornerShape(8.dp), ambientColor = accentColor, spotColor = accentColor)
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = if (isConnecting) 0.6f else 0.8f))
            .background(if (isConnecting) shimmerBrush else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
            .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(8.dp))
            .clickable(enabled = !isConnecting, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isConnecting) {
            Text("CONNECTING...", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        } else {
            Text(text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
        }
    }
}
// ============================================================
// SoftGlassLoadingOverlay — Full screen blurred loader
// ============================================================
@Composable
fun SoftGlassLoadingOverlay(
    visible: Boolean,
    text: String = "Establishing Neural Link...",
    accentColor: Color = Cyan
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .blur(16.dp)
                .clickable(enabled = false) {}, // Intercept clicks
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassCard(
                    modifier = Modifier.padding(32.dp),
                    blurAlpha = 0.4f
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = text.uppercase(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
// ============================================================
// NeonStealthCard — Institutional sharp edge design
// ============================================================
@Composable
fun NeonStealthCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Cyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color.Black.copy(alpha = 0.9f))
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            .drawBehind {
                // Subtle inner glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(borderColor.copy(alpha = 0.1f), Color.Transparent),
                        center = center,
                        radius = size.maxDimension / 2
                    )
                )
            }
            .padding(16.dp),
        content = content
    )
}

// ============================================================
// GlassDepthCard — Heavy blur with layered depth
// ============================================================
@Composable
fun GlassDepthCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 32.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .blur(40.dp) // Simulated heavy blur
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            content = content
        )
    }
}

// ============================================================
// PulseRing — Expanding glass rings for heartbeat effects
// ============================================================
@Composable
fun PulseRing(
    modifier: Modifier = Modifier,
    color: Color = SafetyOrange,
    ringCount: Int = 3
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        repeat(ringCount) { index ->
            val delay = index * 800
            val scale by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, delayMillis = delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "scale_$index"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, delayMillis = delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .border(1.5.dp, color.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

// ============================================================
// GlassGauge — Semi-circular progress indicator
// ============================================================
@Composable
fun GlassGauge(
    value: Float, // 0f to 1f
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    val strokeWidth = 12.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        (size.width - diameter) / 2,
                        (size.height - diameter) / 2
                    )
                    val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                    
                    // Background track
                    drawArc(
                        color = Color.White.copy(alpha = 0.1f),
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    
                    // Progress track
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to accentColor.copy(alpha = 0.2f),
                            0.5f to accentColor,
                            1f to accentColor.copy(alpha = 0.2f)
                        ),
                        startAngle = 150f,
                        sweepAngle = 240f * value,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEDEDED),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ============================================================
// CyberCutCard — Asymmetric Cyber-Cut with Neon Glow
// ============================================================
@Composable
fun CyberCutCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(topStart = 0.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 24.dp)
    
    Box(
        modifier = modifier
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.1f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.05f), shape)
            .drawBehind {
                val cornerRadius = 24.dp.toPx()
                // Top Right Neon Glow
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(size.width - cornerRadius * 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(cornerRadius * 2f, cornerRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

// ============================================================
// HolographicCard — Holographic Floating Frameless Layout
// ============================================================
@Composable
fun HolographicCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val bracketHeight = size.height * 0.4f
                val bracketWidth = 12.dp.toPx()
                val strokeW = 2.dp.toPx()
                
                // Left Bracket
                val leftPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(bracketWidth, (size.height - bracketHeight) / 2)
                    lineTo(0f, (size.height - bracketHeight) / 2)
                    lineTo(0f, (size.height + bracketHeight) / 2)
                    lineTo(bracketWidth, (size.height + bracketHeight) / 2)
                }
                
                drawPath(
                    path = leftPath,
                    color = accentColor.copy(alpha = 0.6f),
                    style = Stroke(width = strokeW)
                )

                // Right Bracket
                val rightPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width - bracketWidth, (size.height - bracketHeight) / 2)
                    lineTo(size.width, (size.height - bracketHeight) / 2)
                    lineTo(size.width, (size.height + bracketHeight) / 2)
                    lineTo(size.width - bracketWidth, (size.height + bracketHeight) / 2)
                }
                
                drawPath(
                    path = rightPath,
                    color = accentColor.copy(alpha = 0.6f),
                    style = Stroke(width = strokeW)
                )
                
                // Text Aura Layering
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                        center = center,
                        radius = size.maxDimension * 0.6f
                    )
                )
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}
