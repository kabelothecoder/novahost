package com.novaedge.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novaedge.app.R
import com.novaedge.app.navigation.Routes
import com.novaedge.app.ui.components.GlassCard
import com.novaedge.app.ui.components.GradientButton
import com.novaedge.app.ui.components.MeshGradientBackground
import com.novaedge.app.ui.theme.*

private val DEMO_EAS = listOf("GoldRush Pro", "NightScalper v3", "TrendFollower AI", "NEO-5 Grid")

/**
 * Screen 3 — Handshake (License Entry)
 * Premium: Mesh bg, styled field, GradientButton, glass EA cards with logo
 */
@Composable
fun LicenseScreen(navController: NavController) {
    var licenseKey by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MeshGradientBackground()

        Column(
            modifier                = Modifier.fillMaxWidth(0.9f),
            verticalArrangement     = Arrangement.spacedBy(16.dp),
            horizontalAlignment     = Alignment.CenterHorizontally
        ) {
            Text("Activate License", style = MaterialTheme.typography.headlineMedium, color = OnSurface)
            Text("Enter your Nova Edge activation key", style = MaterialTheme.typography.bodyMedium, color = ActiveGrey)

            OutlinedTextField(
                value         = licenseKey,
                onValueChange = { licenseKey = it },
                label         = { Text("License Key", color = ActiveGrey) },
                modifier      = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                singleLine    = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor  = SafetyOrange,
                    unfocusedBorderColor = OutlineVariant.copy(alpha = 0.5f),
                    cursorColor         = SafetyOrange,
                    focusedTextColor    = OnSurface,
                    unfocusedTextColor  = OnSurface,
                    focusedContainerColor   = SurfaceContainer.copy(alpha = 0.5f),
                    unfocusedContainerColor = SurfaceContainerLow.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            GradientButton(
                text    = if (isVerifying) "Verifying…" else "Activate",
                onClick = {
                    isVerifying = true
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = licenseKey.isNotBlank() && !isVerifying
            )

            Spacer(Modifier.height(8.dp))
            Text("Previously Activated EAs", style = MaterialTheme.typography.titleLarge, color = OnSurface)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DEMO_EAS) { ea ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // EA Logo — using app logo as placeholder
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                SafetyOrange.copy(alpha = 0.15f),
                                                SafetyOrange.copy(alpha = 0.05f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = ea,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(ea, color = OnSurface, fontWeight = FontWeight.Medium)
                                Text("Licensed", color = ActiveGrey, fontSize = 11.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SafetyOrange)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 4 — The Vault (Payment)
 * Premium: Mesh bg, glass tier cards with gradient accents
 */
@Composable
fun VaultScreen(navController: NavController) {
    val tiers = listOf(
        Triple("Starter", "R299/mo", listOf("1 EA Slot", "Live Only", "Basic Support")),
        Triple("Pro", "R799/mo", listOf("5 EA Slots", "Live + Demo", "Priority Support", "Analytics")),
        Triple("Elite", "R1499/mo", listOf("Unlimited EAs", "All Modes", "24/7 Support", "Custom Algos", "VPS Included"))
    )

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MeshGradientBackground()

        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("The Vault", style = MaterialTheme.typography.headlineLarge, color = OnSurface)
            Text("Choose your Nova Edge tier", style = MaterialTheme.typography.bodyMedium, color = ActiveGrey)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiers.forEachIndexed { i, (name, price, features) ->
                    GlassCard(
                        modifier    = Modifier.weight(1f),
                        borderColor = if (i == 1) SafetyOrange.copy(alpha = 0.4f) else CardBorderLight
                    ) {
                        if (i == 1) Text("● POPULAR", color = SafetyOrange, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text(name, style = MaterialTheme.typography.titleLarge, color = OnSurface)
                        Text(price, color = Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        features.forEach { f -> Text("· $f", color = ActiveGrey, fontSize = 11.sp) }
                        Spacer(Modifier.height(12.dp))
                        GradientButton(
                            text    = "Select",
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.VAULT) { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            gradientColors = if (i == 1) listOf(SafetyOrange, PrimaryContainer) else listOf(SurfaceContainerHighest, SurfaceContainerHigh),
                            textColor = if (i == 1) OnSurface else ActiveGrey
                        )
                    }
                }
            }
        }
    }
}
