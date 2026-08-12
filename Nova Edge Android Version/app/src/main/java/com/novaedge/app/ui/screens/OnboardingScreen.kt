package com.novaedge.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novaedge.app.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Design System Tokens — Soft UI Light Mode
// ─────────────────────────────────────────────────────────────────────────────

private val SoftBgStart       = Color(0xFFF4F7F9)
private val SoftBgEnd         = Color(0xFFE8EEF2)
private val CardWhite         = Color(0xFFFFFFFF)
private val HeaderCharcoal    = Color(0xFF1A1D20)
private val SubtitleGrey      = Color(0xFF8A94A6)
private val PillBlue          = Color(0xFF5B9CF6)
private val PillBlueDeep      = Color(0xFF3B7DE8)
private val ChipBackground    = Color(0xFFF0F4FF)
private val ChipBorder        = Color(0xFFD8E4FF)
private val InsetFieldBg      = Color(0xFFF0F3F7)
private val SpinnerBlue       = Color(0xFF5B9CF6)
private val SpinnerTrack      = Color(0xFFDDE6F5)
private val LockTeal          = Color(0xFF4AC9B0)
private val LockTealLight     = Color(0xFFE0F7F3)
private val CardShadowColor   = Color(0x0F000000)
private val ActiveDot         = Color(0xFF5B9CF6)
private val InactiveDot       = Color(0xFFCDD5E0)

private val CardShape         = RoundedCornerShape(28.dp)
private val PillShape         = RoundedCornerShape(50.dp)
private val ChipShape         = RoundedCornerShape(16.dp)
private val FieldShape        = RoundedCornerShape(14.dp)

// ─────────────────────────────────────────────────────────────────────────────
// Root Composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(navController: NavController, mainViewModel: com.novaedge.app.ui.viewmodels.MainViewModel) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    // State lifted from Step 2 (display name) and Step 5 (license key)
    var displayName by remember { mutableStateOf("") }
    var licenseKey  by remember { mutableStateOf("") }

    val bgBrush = Brush.verticalGradient(listOf(SoftBgStart, SoftBgEnd))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        // ── Pager ─────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)   // leave room for bottom nav
        ) { page ->
            when (page) {
                0 -> Step1Value()
                1 -> Step2Identity(displayName) { displayName = it }
                2 -> Step3Risk()
                3 -> Step4Setup(pagerState, scope)
                4 -> Step5Activation(licenseKey, { licenseKey = it }, displayName, navController)
            }
        }

        // ── Bottom Navigation ──────────────────────────────────────────────
        OnboardingBottomNav(
            modifier = Modifier.align(Alignment.BottomCenter),
            pagerState = pagerState,
            totalPages = 5,
            onNext = {
                scope.launch {
                    if (pagerState.currentPage < 4) {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    } else {
                        // Action handled by ACTIVATE TERMINAL button on Step 5
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingBottomNav(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    totalPages: Int,
    onNext: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 28.dp)
    ) {
        // Pagination Dots — centered
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalPages) { index ->
                val isActive = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isActive) 22.dp else 7.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "dotWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (isActive) ActiveDot else InactiveDot,
                    animationSpec = tween(280),
                    label = "dotColor"
                )
                Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(width)
                        .clip(PillShape)
                        .background(color)
                )
            }
        }

        // Pill Next Button — right-aligned (hidden on Step 4 which auto-advances)
        if (pagerState.currentPage != 3) {
            val isLast = pagerState.currentPage == 4
            Button(
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(50.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PillBlue,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),
                contentPadding = PaddingValues(horizontal = if (isLast) 20.dp else 24.dp, vertical = 0.dp)
            ) {
                if (isLast) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Activate",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.3.sp
                    )
                } else {
                    Text(
                        text = "Next",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Value Proposition (overlapping tilted cards)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Step1Value() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Headline
        Text(
            text = "The ultimate\nmobile trading VPS.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = HeaderCharcoal,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Text(
            text = "Run 24/7 expert advisors, right from your pocket.",
            fontSize = 15.sp,
            color = SubtitleGrey,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 44.dp)
        )

        // Overlapping tilted cards stack
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            // Back card — rotated −10°
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(160.dp)
                    .graphicsLayer {
                        rotationZ = -10f
                        translationY = 10f
                        translationX = -10f
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ValueChip(icon = Icons.Rounded.Speed, label = "Low Latency Execution")
                    Spacer(Modifier.height(10.dp))
                    ValueChip(icon = Icons.Rounded.Cloud, label = "Hosted in the Cloud")
                }
            }

            // Front card — slight positive tilt
            SoftCard(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(160.dp)
                    .graphicsLayer {
                        rotationZ = 4f
                        translationY = -8f
                        translationX = 12f
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    ValueChip(icon = Icons.Rounded.AccessTime, label = "99.9% Uptime SLA")
                    Spacer(Modifier.height(10.dp))
                    ValueChip(icon = Icons.Rounded.Shield, label = "VPS-Grade Security")
                }
            }
        }
    }
}

@Composable
private fun ValueChip(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ChipBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PillBlue,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HeaderCharcoal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Identity (Display Name)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Step2Identity(
    displayName: String,
    onDisplayNameChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top icon badge
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(ChipBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = PillBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Secure Your\nConnection.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = HeaderCharcoal,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Set a display name for your trading session.",
            fontSize = 15.sp,
            color = SubtitleGrey,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(36.dp))

        // Clean white card with inset field
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "DISPLAY NAME",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SubtitleGrey,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                SoftInsetField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    placeholder = "e.g. ALPHA TRADER",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Risk Protection (floating chips)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Step3Risk() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF7FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = PillBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Smart Risk\nProtection.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = HeaderCharcoal,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Guardrails built into every session to protect your capital.",
            fontSize = 15.sp,
            color = SubtitleGrey,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        // Floating risk chip rows
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RiskChipRow(
                icon = Icons.Rounded.MoneyOff,
                title = "Daily Loss Limit",
                description = "Halts trading if drawdown exceeds threshold"
            )
            RiskChipRow(
                icon = Icons.Rounded.SignalCellularAlt,
                title = "Spread Filter",
                description = "Skips entries during abnormal spread widening"
            )
            RiskChipRow(
                icon = Icons.Rounded.AccountBalance,
                title = "Margin Safety Check",
                description = "Validates free margin before every order"
            )
        }
    }
}

@Composable
private fun RiskChipRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChipBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PillBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeaderCharcoal
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = SubtitleGrey,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4 — Setup / Loading (auto-advance after 2.5s)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Step4Setup(pagerState: PagerState, scope: kotlinx.coroutines.CoroutineScope) {

    // Auto-advance after 2.5 seconds
    LaunchedEffect(Unit) {
        delay(2500L)
        scope.launch {
            pagerState.animateScrollToPage(4)
        }
    }

    // Animated spinner rotation
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing)
        ),
        label = "spinAngle"
    )

    // Pulsing progress dots
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse),
        label = "dot2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse),
        label = "dot3"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Spinner card
        SoftCard(modifier = Modifier.size(160.dp)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Track ring
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawArc(
                        color = SpinnerTrack,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx())
                    )
                }
                // Spinning arc
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, SpinnerBlue, SpinnerBlue)
                        ),
                        startAngle = spinAngle,
                        sweepAngle = 240f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx())
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Configuring\nWorkspace…",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = HeaderCharcoal,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Setting up your secure environment.",
            fontSize = 15.sp,
            color = SubtitleGrey,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        // Pulsing step indicator
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(dotAlpha1, dotAlpha2, dotAlpha3).forEach { alpha ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SpinnerBlue.copy(alpha = alpha))
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "This will only take a moment.",
            fontSize = 12.sp,
            color = SubtitleGrey.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 5 — Activation (License Key)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Step5Activation(
    licenseKey: String,
    onLicenseKeyChange: (String) -> Unit,
    displayName: String,
    navController: NavController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isActivating by remember { mutableStateOf(false) }
    var activationError by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lock icon badge
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(LockTealLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = LockTeal,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Connection\nSecured.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = HeaderCharcoal,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Enter your license key to activate your terminal.",
            fontSize = 15.sp,
            color = SubtitleGrey,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        // License Key input card
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "LICENSE KEY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SubtitleGrey,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                SoftInsetField(
                    value = licenseKey,
                    onValueChange = onLicenseKeyChange,
                    placeholder = "XXXX-XXXX-XXXX-XXXX",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    isPassword = true
                )

                Spacer(Modifier.height(16.dp))

                // ACTIVATE TERMINAL button — full width pill
                Button(
                    onClick = {
                        if (licenseKey.isBlank()) return@Button
                        isActivating = true
                        activationError = null
                        scope.launch {
                            // Real server-side validation. This previously just
                            // delayed 1.5s and navigated on, so ANY typed string
                            // unlocked the app -- the paid licence gate was
                            // decorative. The key must be checked against the
                            // backend and bound to this device.
                            val androidId = android.provider.Settings.Secure.getString(
                                context.contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID
                            ) ?: ""

                            val result: com.novaedge.app.sdk.LicenseActivationResponse? = try {
                                com.novaedge.app.sdk.SupabaseSetup.client.functions
                                    .invoke("validate-license") {
                                        setBody(
                                            com.novaedge.app.sdk.LicenseActivationRequest(
                                                license_key = licenseKey.trim().uppercase(),
                                                android_id = androidId
                                            )
                                        )
                                    }
                                    .body<com.novaedge.app.sdk.LicenseActivationResponse>()
                            } catch (e: Exception) {
                                android.util.Log.e("Nova Edge", "License activation failed", e)
                                null
                            }

                            if (result == null || !result.success) {
                                // Fail closed: no navigation, key not stored.
                                activationError = result?.error
                                    ?: "Could not verify this licence key. Check your connection and try again."
                                isActivating = false
                                return@launch
                            }

                            val symbols = (result.allowed_symbols?.takeIf { it.isNotEmpty() }
                                ?: result.symbols ?: emptyList())

                            val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("license_key", licenseKey.trim().uppercase())
                                // Robot identity, delivered by the licence key.
                                putString("active_ea_id", result.ea_id ?: "")
                                putString("display_name", result.display_name ?: result.product_name ?: "TRADING BOT")
                                putString("avatar_url", result.avatar_url)
                                putString("accent_color", result.accent_color)
                                putString("background_video_url", result.background_video_url)
                                putString("tts_script", result.tts_script)
                                putString("allowed_symbols", symbols.joinToString(","))
                                putString("trader_name", displayName.ifBlank { "Trader" })
                            }.apply()

                            isActivating = false
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                            }
                        }
                    },
                    enabled = !isActivating && licenseKey.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PillBlue,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isActivating) "ACTIVATING..." else "ACTIVATE TERMINAL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp
                    )
                }

                // Rejected key -- tell the user why instead of failing silently.
                activationError?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = Color(0xFFD64545),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Your license key was provided at purchase.\nContact support if you need help.",
            fontSize = 12.sp,
            color = SubtitleGrey.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Primitives
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @description Reusable Soft UI white floating card with 28dp radius and
 * a diffused 24dp shadow (rgba(0,0,0,0.06) equivalent via ambientColor).
 */
@Composable
private fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = CardShape,
                ambientColor = CardShadowColor,
                spotColor = CardShadowColor
            )
            .clip(CardShape)
            .background(CardWhite)
    ) {
        content()
    }
}

/**
 * @description Borderless inset-style text field — simulates CSS inset shadow
 * with a soft grey background and no visible border. Matches the Soft UI spec.
 */
@Composable
private fun SoftInsetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = HeaderCharcoal,
            letterSpacing = if (isPassword) 2.sp else 0.sp
        ),
        keyboardOptions = keyboardOptions,
        visualTransformation = if (isPassword && value.isNotEmpty())
            PasswordVisualTransformation('•') else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FieldShape)
                    .background(InsetFieldBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = SubtitleGrey,
                        letterSpacing = if (isPassword) 1.sp else 0.sp
                    )
                }
                innerTextField()
            }
        }
    )
}
