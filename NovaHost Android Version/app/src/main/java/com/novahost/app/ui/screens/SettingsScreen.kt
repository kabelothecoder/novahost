package com.novaedge.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novaedge.app.ui.components.*
import com.novaedge.app.ui.theme.*
import kotlinx.coroutines.launch
import android.content.Intent

private val SOLID_COLORS = listOf(
    Color(0xFFFFD700), // Electric Gold
    Color(0xFF00FA9A), // Mint Emerald
    Color(0xFFDC143C), // Crimson Red
    Color(0xFF8A2BE2), // Deep Cyber Violet
    Color(0xFFE5E5E5), // Platinum Grey
    Color(0xFFFF3D00), // Safety Orange
    Color(0xFF00E5FF), // Cyber Cyan
    Color(0xFF7B2FBE), // Royal Purple
    Color(0xFF00E676), // Neon Green
    Color(0xFFF5005A), // Hot Pink
    Color(0xFF2979FF), // Electric Blue
    Color(0xFFD700FF), // Neon Magenta
    Color(0xFF39FF14), // Plasma Green
    Color(0xFFFF9500), // Tech Gold
    Color(0xFF00F2FE)  // Ice Blue
)

// === Dual-Tone Gradient Accent Pairs ===
private val GRADIENT_ACCENTS = listOf(
    "Cyber Cyan → Purple" to Pair(Color(0xFF00E5FF), Color(0xFFD700FF)),
    "Deep Ruby → Onyx"   to Pair(Color(0xFFDC143C), Color(0xFF1A1A1A)),
    "Solar Gold → Crimson" to Pair(Color(0xFFFFBF00), Color(0xFFDC143C)),
    "Matrix Green → Void" to Pair(Color(0xFF39FF14), Color(0xFF030303)),
    "Ice Blue → Sapphire" to Pair(Color(0xFF00F2FE), Color(0xFF1E3A8A)),
    "Plasma → Magenta"    to Pair(Color(0xFF2979FF), Color(0xFFD700FF)),
    "Ember → Rose"        to Pair(Color(0xFFFF5722), Color(0xFFF5005A)),
    "Ghost → Steel"       to Pair(Color(0xFFE8E8E8), Color(0xFF444444))
)

private val GLOSS_THEMES = listOf(
    Pair(Color(0xFF00C6FF), Color(0xFF0072FF)), // Deep Blue
    Pair(Color(0xFFF5AF19), Color(0xFFF12711)), // Sunset
    Pair(Color(0xFF11998E), Color(0xFF38EF7D)), // Emerald
    Pair(Color(0xFFDA22FF), Color(0xFF9733EE)), // Purple Glow
    Pair(Color(0xFFFF0099), Color(0xFF493240)), // Synthwave
    Pair(Color(0xFF00F2FE), Color(0xFF4FACFE)), // Ice
    Pair(Color(0xFFFF9A44), Color(0xFFFC6076)), // Peach
    Pair(Color(0xFF8E2DE2), Color(0xFF4A00E0))  // Cosmic
)

@Composable
fun SettingsScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf("settings") }
    
    val globalThemeState = LocalNovaEdgeTheme.current
    val updateGlobalTheme = LocalNovaEdgeThemeUpdater.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Local Preview State
    var previewTheme by remember { mutableStateOf(globalThemeState) }
    
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(globalThemeState.secondaryBackgroundColor ?: Charcoal)) {
        MeshGradientBackground()

        Scaffold(
            containerColor = Color.Transparent,
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
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "SETTINGS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Text(
                "Customize your Nova Edge experience",
                style = MaterialTheme.typography.bodyMedium,
                color = ActiveGrey,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Accent Color Strip — solid neons
            SettingsGroup(title = "ACCENT COLOR") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SOLID_COLORS) { color ->
                        val isSelected = previewTheme.primaryColor == color
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(if (isSelected) 16.dp else 6.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = color, spotColor = color)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                                .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    val newTheme = previewTheme.copy(primaryColor = color, isGlossTheme = false)
                                    previewTheme = newTheme
                                    updateGlobalTheme(newTheme)
                                    com.novaedge.app.sdk.TerminalPrefs.setThemeColorValue(context, color.value.toLong())
                                }
                        )
                    }
                }
            }

            // Dual-Tone Gradient Accent Pairs
            SettingsGroup(title = "LIQUID GRADIENT ACCENTS") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(GRADIENT_ACCENTS) { (label, pair) ->
                        val (c1, c2) = pair
                        val isSelected = previewTheme.isGlossTheme &&
                            previewTheme.primaryColor == c1 && previewTheme.secondaryColor == c2
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(if (isSelected) 16.dp else 4.dp, androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Brush.linearGradient(listOf(c1, c2)))
                                .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    val newTheme = previewTheme.copy(primaryColor = c1, secondaryColor = c2, isGlossTheme = true)
                                    previewTheme = newTheme
                                    updateGlobalTheme(newTheme)
                                    com.novaedge.app.sdk.TerminalPrefs.setThemeColorValue(context, c1.value.toLong())
                                }
                        )
                    }
                }
            }

            // Glow Mode Selector Strip
            SettingsGroup(title = "HOLOGRAPHIC GLOW MODE") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf(
                        "Soft Glow" to com.novaedge.app.ui.theme.HolographicGlowMode.SOFT,
                        "Medium Glow" to com.novaedge.app.ui.theme.HolographicGlowMode.MEDIUM,
                        "Intense Glow" to com.novaedge.app.ui.theme.HolographicGlowMode.INTENSE,
                        "Pulse Flow" to com.novaedge.app.ui.theme.HolographicGlowMode.PULSE
                    )
                    items(modes) { (label, mode) ->
                        val isSelected = previewTheme.holographicGlowMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) previewTheme.primaryColor.copy(alpha=0.2f) else Color.Transparent)
                                .border(1.dp, if (isSelected) previewTheme.primaryColor else CardBorderDark, RoundedCornerShape(8.dp))
                                .clickable {
                                    val newTheme = previewTheme.copy(holographicGlowMode = mode)
                                    previewTheme = newTheme
                                    updateGlobalTheme(newTheme)
                                    com.novaedge.app.sdk.TerminalPrefs.setGlowMode(context, mode.name)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) previewTheme.primaryColor else ActiveGrey,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Secondary Background Color — extended 8-swatch grid
            SettingsGroup(title = "SECONDARY BACKGROUND") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val bgColors = listOf(
                        "Obsidian"       to Color(0xFF0A0A0A),
                        "Deep Space"     to Color(0xFF0F172A),
                        "Midnight Blue"  to Color(0xFF0B1B3D),
                        "Gunmetal"       to Color(0xFF1C1C1E),
                        "Cosmic Purple"  to Color(0xFF1A0B2E),
                        "Void Teal"      to Color(0xFF001519),
                        "Cyber Dark"     to Color(0xFF050A10),
                        "Molten Onyx"    to Color(0xFF1A0800)
                    )
                    items(bgColors) { (label, color) ->
                        val isSelected = previewTheme.secondaryBackgroundColor == color
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val newTheme = previewTheme.copy(secondaryBackgroundColor = color)
                                previewTheme = newTheme
                                updateGlobalTheme(newTheme)
                                com.novaedge.app.sdk.TerminalPrefs.setSecondaryBgColor(context, color.value.toLong())
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .shadow(if (isSelected) 16.dp else 4.dp, androidx.compose.foundation.shape.CircleShape)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(color)
                                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color(0x1AFFFFFF), androidx.compose.foundation.shape.CircleShape)
                            )
                            if (isSelected) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(previewTheme.primaryColor)
                                )
                            }
                        }
                    }
                }
            }

            // Pure Background Immersive Mode toggle
            SettingsGroup(title = "DISPLAY") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Pure Background Immersive Mode",
                            color = Color(0xFFEDEDED),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Hides all cards — only background art visible",
                            color = ActiveGrey,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = previewTheme.immersiveMode,
                        onCheckedChange = { enabled ->
                            val newTheme = previewTheme.copy(immersiveMode = enabled)
                            previewTheme = newTheme
                            updateGlobalTheme(newTheme)
                            com.novaedge.app.sdk.TerminalPrefs.setImmersiveMode(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = previewTheme.primaryColor,
                            checkedTrackColor = previewTheme.primaryColor.copy(alpha = 0.3f)
                        )
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Show Trade Calculator",
                            color = Color(0xFFEDEDED),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Display the execution monitoring card on home",
                            color = ActiveGrey,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = previewTheme.isTradeCalculatorEnabled,
                        onCheckedChange = { enabled ->
                            val newTheme = previewTheme.copy(isTradeCalculatorEnabled = enabled)
                            previewTheme = newTheme
                            updateGlobalTheme(newTheme)
                            com.novaedge.app.sdk.TerminalPrefs.setTradeCalculatorEnabled(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = previewTheme.primaryColor,
                            checkedTrackColor = previewTheme.primaryColor.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Robot Font Style selector — editorial typography registry
            SettingsGroup(title = "ROBOT FONT STYLE") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val fontOptions = listOf(
                        "Old Money\nThe Seasons"    to com.novaedge.app.ui.theme.RobotFontStyle.OLD_MONEY,
                        "Bodoni FLF\nPlayfair"       to com.novaedge.app.ui.theme.RobotFontStyle.BODONI_DISPLAY,
                        "Montserrat\nGeometric"      to com.novaedge.app.ui.theme.RobotFontStyle.MONTSERRAT_GEOMETRIC,
                        "Symphony\nPerandory"        to com.novaedge.app.ui.theme.RobotFontStyle.SYMPHONY_CREATIVE
                    )
                    items(fontOptions) { (label, fontStyle) ->
                        val isSelected = previewTheme.robotFontStyle == fontStyle
                        val chipTextStyle = com.novaedge.app.ui.theme.robotFontStyleToTextStyle(fontStyle)
                        val lines = label.split("\n")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) previewTheme.primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (isSelected) previewTheme.primaryColor else CardBorderDark, RoundedCornerShape(8.dp))
                                .clickable {
                                    val newTheme = previewTheme.copy(robotFontStyle = fontStyle)
                                    previewTheme = newTheme
                                    updateGlobalTheme(newTheme)
                                    com.novaedge.app.sdk.TerminalPrefs.setRobotFontStyle(context, fontStyle.name)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Primary label in the chip's own font — live preview
                                Text(
                                    text = lines[0],
                                    style = chipTextStyle.copy(
                                        fontSize = 13.sp,
                                        color = if (isSelected) previewTheme.primaryColor else Color(0xFFEDEDED)
                                    )
                                )
                                if (lines.size > 1) {
                                    Text(
                                        text = lines[1],
                                        fontSize = 9.sp,
                                        color = if (isSelected) previewTheme.primaryColor.copy(alpha = 0.6f) else ActiveGrey,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Home Button Shape Selector
            SettingsGroup(title = "HOME BUTTON SHAPE") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val shapeOptions = listOf(
                        "Circle" to com.novaedge.app.ui.theme.HomeButtonShape.CIRCLE,
                        "Oval"   to com.novaedge.app.ui.theme.HomeButtonShape.OVAL,
                        "Square" to com.novaedge.app.ui.theme.HomeButtonShape.SQUARE
                    )
                    items(shapeOptions) { (label, shape) ->
                        val isSelected = previewTheme.homeButtonShape == shape
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) previewTheme.primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (isSelected) previewTheme.primaryColor else CardBorderDark, RoundedCornerShape(8.dp))
                                .clickable {
                                    val newTheme = previewTheme.copy(homeButtonShape = shape)
                                    previewTheme = newTheme
                                    updateGlobalTheme(newTheme)
                                    com.novaedge.app.sdk.TerminalPrefs.setHomeButtonShape(context, shape.name)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) previewTheme.primaryColor else ActiveGrey,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Typography and Scaling Selectors
            SettingsGroup(title = "TYPOGRAPHY & SCALING") {
                val appPrefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
                Text("Home Button Scale: ${String.format("%.1fx", previewTheme.homeButtonScale)}", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = previewTheme.homeButtonScale,
                    onValueChange = { 
                        val newTheme = previewTheme.copy(homeButtonScale = it)
                        previewTheme = newTheme
                        updateGlobalTheme(newTheme)
                        appPrefs.edit().putFloat("home_button_scale", it).apply()
                    },
                    valueRange = 0.8f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = previewTheme.primaryColor, activeTrackColor = previewTheme.primaryColor)
                )

                Spacer(Modifier.height(8.dp))
                Text("Robot Name Font Size: ${previewTheme.robotNameFontSize.toInt()}sp", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = previewTheme.robotNameFontSize,
                    onValueChange = { 
                        val newTheme = previewTheme.copy(robotNameFontSize = it)
                        previewTheme = newTheme
                        updateGlobalTheme(newTheme)
                        appPrefs.edit().putFloat("robot_name_font_size", it).apply()
                    },
                    valueRange = 16f..48f,
                    colors = SliderDefaults.colors(thumbColor = previewTheme.primaryColor, activeTrackColor = previewTheme.primaryColor)
                )

                Spacer(Modifier.height(8.dp))
                Text("Robot Name Font Color", color = Color.White, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top=8.dp)) {
                    val colors = listOf(Color.White, Color.Black, Crimson, Cyan, Color(0xFFFFD700), SafetyOrange)
                    items(colors) { color ->
                        val isSelected = previewTheme.robotNameFontColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (isSelected) previewTheme.primaryColor else Color.Transparent, CircleShape)
                                .clickable {
                                    val newTheme = previewTheme.copy(robotNameFontColor = color)
                                    previewTheme = newTheme
                                    updateGlobalTheme(newTheme)
                                    appPrefs.edit().putLong("robot_name_font_color", color.value.toLong()).apply()
                                }
                        )
                    }
                }
            }

            // Remove Broker section
            var showRemoveDialog by remember { mutableStateOf(false) }
            SettingsGroup(title = "DANGER ZONE") {
                androidx.compose.material3.Button(
                    onClick = { showRemoveDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Crimson.copy(alpha=0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Crimson)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", tint = Crimson)
                    Spacer(Modifier.width(12.dp))
                    Text("REMOVE BROKER & WIPE DATA", color = Crimson, fontWeight = FontWeight.Bold)
                }
            }

            if (showRemoveDialog) {
                val scope = rememberCoroutineScope()
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showRemoveDialog = false },
                    title = { Text("REMOVE BROKER?", color = Color.White) },
                    text = { Text("This will clear all credentials and stop the neural link.", color = ActiveGrey) },
                    confirmButton = {
                        androidx.compose.material3.Button(
                            onClick = {
                                scope.launch {
                                    context.stopService(Intent(context, com.novaedge.app.service.NovaEdgePulseService::class.java))
                                    com.novaedge.app.sdk.MetaAPIManager.disconnect()
                                    com.novaedge.app.sdk.TerminalPrefs.clear(context)
                                    showRemoveDialog = false
                                    navController.navigate(com.novaedge.app.navigation.Routes.TERMINAL) {
                                        popUpTo(com.novaedge.app.navigation.Routes.HOME) { inclusive = true }
                                    }
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Crimson)
                        ) {
                            Text("REMOVE", color = Color.White)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showRemoveDialog = false }) {
                            Text("CANCEL", color = ActiveGrey)
                        }
                    },
                    containerColor = Charcoal
                )
            }

            Spacer(Modifier.height(16.dp))

            // Save Button
            val saveBtnColors = if (previewTheme.isGlossTheme) listOf(previewTheme.primaryColor, previewTheme.secondaryColor) else listOf(previewTheme.primaryColor, previewTheme.primaryColor)
            GradientButton(
                text = "SAVE THEME",
                onClick = { updateGlobalTheme(previewTheme) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                gradientColors = saveBtnColors,
                glowColor = previewTheme.primaryColor
            )

            // Help & Support Link
            GlassCard(blurAlpha = 0.3f) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(com.novaedge.app.navigation.Routes.HELP_SUPPORT) }.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HelpOutline, contentDescription = "Help", tint = Cyan)
                        Spacer(Modifier.width(12.dp))
                        Text("Help & Support", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ActiveGrey)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
}



@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = ActiveGrey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        GlassCard(blurAlpha = 0.3f) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}
