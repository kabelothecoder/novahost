package com.novahost.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novahost.app.ui.components.*
import com.novahost.app.ui.theme.*
import androidx.compose.ui.draw.alpha
import com.novahost.app.BuildConfig

@Composable
fun HelpSupportScreen(navController: NavController) {
    val scrollState = rememberScrollState()
    val themeState = LocalNovaHostTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize().background(Charcoal)) {
        MeshGradientBackground()

        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Spacer(Modifier.height(52.dp))
            
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back", tint = themeState.primaryColor)
                }
                Text("Core Architecture Profile", style = MaterialTheme.typography.headlineMedium, color = OnSurface)
            }
            Text("NovaHost Infrastructure & Licensing", style = MaterialTheme.typography.bodyMedium, color = ActiveGrey)

            Spacer(Modifier.height(8.dp))

            // Version info & core
            GlassCard(blurAlpha = 0.3f) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Charcoal) // Dark bg for avatar
                    ) {
                        CircularAvatar(size = 60.dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("NovaHost Runtime Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = themeState.primaryColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Architecture: Cloud-Native Neural Link", color = ActiveGrey, fontSize = 12.sp)
                    }
                }
            }

            // Contact Channels
            Text("OFFICIAL SUPPORT CHANNELS", color = ActiveGrey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                // Email button
                GlassCard(blurAlpha = 0.3f, modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = "Email", tint = themeState.primaryColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("SysAdmin Contact", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                // Telegram button
                GlassCard(blurAlpha = 0.3f, modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Telegram", tint = themeState.primaryColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Live Operations", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Installation Guide
            Text("INFRASTRUCTURE DEPLOYMENT", color = ActiveGrey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val btnColors = if (themeState.isGlossTheme) listOf(themeState.primaryColor, themeState.secondaryColor) else listOf(themeState.primaryColor, themeState.primaryColor)
            GradientButton(
                text = "SYSTEM DEPLOYMENT DOCS",
                onClick = { 
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://novahost.app/docs"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                gradientColors = btnColors,
                glowColor = themeState.primaryColor
            )
            Button(
                onClick = { 
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://novahost.app/portal"))
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = themeState.primaryColor)
                    Spacer(Modifier.width(8.dp))
                    Text("ACCESS CLOUD PORTAL", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Frequently Asked Questions
            Text("SYSTEM CONFIGURATION FAQ", color = ActiveGrey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            
            FAQItem(
                question = "How do I activate my trading robot?",
                answer = "Go to the Settings screen, tap 'Add License', and enter the license key provided to you. Make sure you are connected to the internet."
            )
            FAQItem(
                question = "How do I connect my broker account?",
                answer = "Go to the Terminal screen, enter your MT4 or MT5 login details, select your broker's server, and tap 'Connect'."
            )
            FAQItem(
                question = "Which currency pairs can I trade?",
                answer = "The pairs you can trade are automatically unlocked based on your specific license."
            )

            Spacer(Modifier.height(32.dp))

            // Powered by NovaHost
            Row(modifier = Modifier.fillMaxWidth().alpha(0.6f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Powered by ", color = Color.White, fontSize = 12.sp)
                Text("NovaHost", color = themeState.primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun FAQItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    
    GlassCard(blurAlpha = 0.2f, modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(question, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ActiveGrey
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(answer, color = ActiveGrey, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
