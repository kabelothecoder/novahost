package com.novahost.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novahost.app.R
import com.novahost.app.ui.viewmodels.GateStage
import com.novahost.app.ui.viewmodels.GateState

/**
 * The two paygates, drawn from the "NovaHost Paygates" design.
 *
 *   [PaywallOverlay]     the app lock. One-time R599, unlocked by email.
 *   [ScannerPaywallSheet] the chart scanner upsell. One-time R349, no email --
 *                        it reuses the one the app was unlocked with.
 *
 * Both are premium light on a dark app on purpose: the gate is the one surface
 * that must not look like part of the product the user has not paid for yet.
 *
 * The design's "what you're paying for" copy folded the chart scanner into the
 * R599. It is a separate R349 purchase, so the copy here says so -- a gate that
 * promises a feature the next gate charges for is the fastest way to a refund.
 */

// ── Palette ────────────────────────────────────────────────────────────────
// Straight from the design. Local rather than in the theme: these are fixed
// light values and must not follow the app's accent, which changes per robot.

private val GateGround = Color(0xFFF4F7F9)
private val GateSurface = Color(0xFFFFFFFF)
private val GateInk = Color(0xFF1A1D20)
private val GateMuted = Color(0xFF8A94A6)
private val GateFaint = Color(0xFFB2BAC6)
private val GateHairline = Color(0xFFE1E6ED)
private val GateRule = Color(0xFFEEF1F5)
private val GateAccent = Color(0xFF5C9CE6)
private val GateDanger = Color(0xFFE4645B)
private val GateChip = Color(0xFFEEF4FC)
private val GateClear = Color(0xFFE4E9EF)

/** Every price lives here so the gates, the copy and the checkout cannot drift apart. */
const val APP_PRICE_LABEL = "R599"
const val SCANNER_PRICE_LABEL = "R349"
const val REACTIVATION_PRICE_LABEL = "R150"

// ─────────────────────────────────────────────────────────────────────────────
// Paygate 1 · the app lock
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @param gate           what the view model is doing right now.
 * @param initialEmail   pre-fills the field on a return visit.
 * @param onCheckAccess  "Check my access" — verifies an existing purchase.
 * @param onBuy          "Buy app access — R599" — starts a Payfast checkout.
 * @param onGranted      access confirmed; the host should dismiss the gate.
 * @param onCheckoutOpened  the browser has been handed the URL, clear the state.
 */
@Composable
fun PaywallOverlay(
    gate: GateState,
    initialEmail: String,
    onCheckAccess: (String) -> Unit,
    onBuy: (String) -> Unit,
    onGranted: () -> Unit,
    onCheckoutOpened: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf(initialEmail) }

    // A checkout URL is a one-shot instruction, not a piece of screen state.
    LaunchedEffect(gate.stage, gate.checkout) {
        val url = gate.checkout
        if (gate.stage == GateStage.CHECKOUT_READY && url != null) {
            openCheckout(context, url)
            onCheckoutOpened()
        }
    }

    LaunchedEffect(gate.stage) {
        if (gate.stage == GateStage.GRANTED) onGranted()
    }

    val busy = gate.stage == GateStage.CHECKING || gate.stage == GateStage.VERIFYING
    val denial = gate.message.takeIf { gate.stage == GateStage.DENIED }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GateGround)
    ) {
        // The two blue bloom fields behind the card.
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-60).dp, y = (-70).dp)
                .background(
                    Brush.radialGradient(
                        listOf(GateAccent.copy(alpha = 0.20f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(230.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(GateAccent.copy(alpha = 0.13f), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            GateBrandRow()
            Spacer(Modifier.height(16.dp))

            // ── The card ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(24.dp), spotColor = GateInk.copy(alpha = 0.16f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(GateSurface)
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GateMark(size = 76.dp, corner = 26.dp)

                Spacer(Modifier.height(14.dp))
                Text(
                    "Unlock NovaHost",
                    color = GateInk,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.7).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter your email to check your app access, or buy it if you haven't yet.",
                    color = GateMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                GateEmailField(
                    value = email,
                    onValue = { email = it },
                    enabled = !busy,
                    hasError = denial != null
                )

                AnimatedVisibility(visible = denial != null, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(GateDanger),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            denial ?: "",
                            color = GateDanger,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                GatePrimaryButton(
                    label = when (gate.stage) {
                        GateStage.CHECKING -> "Checking..."
                        GateStage.VERIFYING -> "Confirming payment..."
                        else -> "Check my access"
                    },
                    busy = busy,
                    enabled = !busy,
                    onClick = { onCheckAccess(email) }
                )

                Spacer(Modifier.height(10.dp))

                // A paid email on the wrong handset is a R150 move, not a second
                // R599 purchase, and the checkout the server hands back prices
                // it that way. The button says what will actually be charged.
                val isDeviceMove = gate.reason == "device_mismatch"

                GateSecondaryButton(
                    label = if (isDeviceMove) {
                        "Move to this device — $REACTIVATION_PRICE_LABEL"
                    } else {
                        "Buy app access — $APP_PRICE_LABEL"
                    },
                    enabled = !busy,
                    onClick = { onBuy(email) }
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GateGround)
                        .padding(horizontal = 15.dp, vertical = 13.dp)
                ) {
                    Text(
                        "What you're paying for",
                        color = GateInk,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A one-time payment for access to the NovaHost app itself — " +
                            "hosting, your robots and the execution tools. The AI chart " +
                            "scanner is a separate one-time unlock. Not mentorship, " +
                            "signals or account management.",
                        color = GateMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(GateRule))
                Spacer(Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@novahost.co")
                            putExtra(Intent.EXTRA_SUBJECT, "NovaHost Support Request")
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Email Support")) }
                    }
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = GateMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildAnnotatedString {
                            append("Need help? ")
                            withStyle(SpanStyle(color = GateAccent, fontWeight = FontWeight.Medium)) {
                                append("support@novahost.co")
                            }
                        },
                        color = GateMuted,
                        fontSize = 12.5.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                GateTrustBadge("TLS 1.3 ENCRYPTED")
                Spacer(Modifier.width(18.dp))
                GateTrustBadge("DEVICE-BOUND")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paygate 2 · the chart scanner sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The contextual scanner upsell.
 *
 * No email field by design: the scanner is bought against the email the app was
 * already unlocked with, so asking again would be asking a question the app can
 * already answer.
 *
 * @param deviceLabel  short, human-readable handset id for the binding pill.
 * @param onUnlock     starts the R349 Payfast checkout.
 * @param onRestore    re-checks the server for a purchase already made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerPaywallSheet(
    gate: GateState,
    deviceLabel: String,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    onRestore: () -> Unit,
    onGranted: () -> Unit,
    onCheckoutOpened: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(gate.stage, gate.checkout) {
        val url = gate.checkout
        if (gate.stage == GateStage.CHECKOUT_READY && url != null) {
            openCheckout(context, url)
            onCheckoutOpened()
        }
    }

    LaunchedEffect(gate.stage) {
        if (gate.stage == GateStage.GRANTED) onGranted()
    }

    val busy = gate.stage == GateStage.CHECKING || gate.stage == GateStage.VERIFYING

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GateSurface,
        contentColor = GateInk,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 2.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(GateHairline)
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GateGround)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = GateMuted, modifier = Modifier.size(16.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(6.dp))
                GateMark(size = 74.dp, corner = 24.dp)

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GateChip)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(GateAccent))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "PREMIUM FEATURE",
                        color = GateAccent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Unlock AI Vision",
                    color = GateInk,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.8).sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Instantly detect patterns, precise entries, and risk parameters with advanced AI vision.",
                    color = GateMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(GateGround)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    ScannerPerk("Unlimited scans")
                    ScannerPerk("Automatic SL / TP calculation")
                    ScannerPerk("Direct execution into your terminal")
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                SCANNER_PRICE_LABEL,
                                color = GateInk,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1.4).sp
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "one-time",
                                color = GateMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("Lifetime device licence", color = GateMuted, fontSize = 12.5.sp)
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GateGround)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(GateInk))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            deviceLabel,
                            color = GateMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                if (gate.stage == GateStage.DENIED && gate.message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        gate.message,
                        color = GateDanger,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                GatePrimaryButton(
                    label = if (busy) "Opening checkout..." else "Unlock for $SCANNER_PRICE_LABEL",
                    busy = busy,
                    enabled = !busy,
                    height = 58.dp,
                    pulse = !busy,
                    onClick = onUnlock
                )

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = GateFaint, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Licence binds securely to this device hardware.",
                        color = GateMuted,
                        fontSize = 12.5.sp
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    "Restore purchase on this device",
                    color = GateFaint,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = !busy, onClick = onRestore)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Shared parts ───────────────────────────────────────────────────────────

@Composable
private fun GateBrandRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(GateSurface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.novahost_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.5f)
                )
            }
            Spacer(Modifier.width(9.dp))
            Text("NovaHost", color = GateInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.72f))
                .border(1.dp, GateInk.copy(alpha = 0.06f), CircleShape)
                .padding(start = 11.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = GateInk, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(7.dp))
            Text("App locked", color = GateInk, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** The floating app mark with its blue bloom, shared by both gates. */
@Composable
private fun GateMark(size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    val bloom by rememberInfiniteTransition(label = "bloom").animateFloat(
        initialValue = 0.38f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "bloomAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size * 2.1f)
                .background(
                    Brush.radialGradient(
                        listOf(GateAccent.copy(alpha = 0.30f * bloom), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(size)
                .shadow(14.dp, RoundedCornerShape(corner), spotColor = GateAccent.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(corner))
                .background(GateSurface),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.novahost_mark),
                contentDescription = "NovaHost",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scale(1.45f)
            )
        }
    }
}

@Composable
private fun GateEmailField(
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean,
    hasError: Boolean
) {
    var focused by remember { mutableStateOf(false) }

    val ring = when {
        hasError -> GateDanger
        focused -> GateAccent
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GateGround)
            .border(if (ring == Color.Transparent) 0.dp else 1.5.dp, ring, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.MailOutline, contentDescription = null, tint = GateMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Email address", color = GateMuted, fontSize = 15.5.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                textStyle = TextStyle(color = GateInk, fontSize = 15.5.sp, letterSpacing = (-0.1).sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(GateAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }

        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(GateClear)
                    .clickable(enabled = enabled) { onValue("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = GateMuted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun GatePrimaryButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp = 58.dp,
    pulse: Boolean = false,
    onClick: () -> Unit
) {
    val halo by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.30f,
        targetValue = if (pulse) 0.55f else 0.30f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .shadow(12.dp, CircleShape, spotColor = GateAccent.copy(alpha = halo))
            .clip(CircleShape)
            .background(if (enabled) GateAccent else GateAccent.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp
        )
    }
}

@Composable
private fun GateSecondaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(GateSurface)
            .border(1.dp, GateHairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GateInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GateTrustBadge(label: String) {
    Text(
        label,
        color = GateFaint,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun ScannerPerk(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(GateAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = GateAccent, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = GateInk, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** Custom Tabs where available, the plain browser when not. */
private fun openCheckout(context: android.content.Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build()
            .launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        e.printStackTrace()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
