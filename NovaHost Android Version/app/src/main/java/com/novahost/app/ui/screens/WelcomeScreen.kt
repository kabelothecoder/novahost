package com.novahost.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novahost.app.R
import com.novahost.app.navigation.Routes
import com.novahost.app.ui.components.GradientButton
import com.novahost.app.ui.components.MeshGradientBackground
import com.novahost.app.ui.theme.*

/**
 * Screen 1 — Ignition (Welcome)
 * Premium: Animated gradient ring around logo, mesh background, GradientButton
 */
@Composable
fun WelcomeScreen(navController: NavController) {

    // PulseTransition: infinite breathe scale
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val logoScale by pulseAnim.animateFloat(
        initialValue    = 1f,
        targetValue     = 1.06f,
        animationSpec   = infiniteRepeatable(
            animation   = tween(1600, easing = FastOutSlowInEasing),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    val ringAlpha by pulseAnim.animateFloat(
        initialValue = 0.2f,
        targetValue  = 0.5f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, SoftLightBlue.copy(alpha = 0.2f)))),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Logo with animated gradient ring
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(logoScale)
                )

                Image(
                    painter            = painterResource(id = R.drawable.novahost_mark),
                    contentDescription = "NovaHost Logo",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(140.dp)
                )
            }

            // Brand label
            Text(
                text       = "NovaHost",
                style      = MaterialTheme.typography.displayLarge,
                color      = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text  = "The Future of Mobile VPS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(12.dp))

            // CTA — Gradient button
            GradientButton(
                text    = "Enter NovaHost",
                onClick = { navController.navigate(Routes.ONBOARDING) },
                modifier = Modifier.width(240.dp),
                gradientColors = listOf(SoftLightBlue, SoftLightBlue),
                textColor = Color.White
            )
        }
    }
}
