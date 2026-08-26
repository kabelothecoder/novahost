package com.novaedge.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.speech.tts.TextToSpeech
import com.novaedge.app.ui.components.TradingViewChartContainer
import com.novaedge.app.ui.components.MarketFeedContainer
import java.util.Locale
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.novaedge.app.R
import com.novaedge.app.navigation.Routes
import com.novaedge.app.sdk.MetaAPIManager
import com.novaedge.app.ui.components.*
import com.novaedge.app.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import io.github.jan.supabase.functions.functions
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign

/**
 * @description Strips legacy prefixes (ALGO_CORE ::) and underscores from raw robot names,
 * then formats the result in clean Title Case for display.
 * Example: "ALGO_CORE :: apex_scalper_v2" → "Apex Scalper V2"
 */
fun sanitizeRobotName(raw: String): String {
    return raw
        .replace(Regex("(?i)ALGO_CORE\\s*::\\s*"), "")
        .replace("_", " ")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, mainViewModel: com.novaedge.app.ui.viewmodels.MainViewModel) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var strategyMode by remember { mutableStateOf("Aggressive") }
    var showActivationError by remember { mutableStateOf(false) }
    var showRobotPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val isPremium by mainViewModel.isPremium.collectAsState()
    
    val adminDisplayName by mainViewModel.adminDisplayName.collectAsState()
    val backgroundMediaUrl by mainViewModel.backgroundMediaUrl.collectAsState()
    val mediaType by mainViewModel.mediaType.collectAsState()
    val robotName by mainViewModel.robotName.collectAsState()
    val userLicenses by mainViewModel.userLicenses.collectAsState()
    val checkoutUrl by mainViewModel.checkoutUrl.collectAsState()
    // Voice Command State
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
    }

    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val prefs = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotEmpty()) {
                    mainViewModel.checkSubscriptionStatus(email)
                    mainViewModel.fetchUserLicenses(email)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val themeState = LocalNovaEdgeTheme.current
    val themeUpdater = LocalNovaEdgeThemeUpdater.current
    val isCircleAvatar = themeState.useRoundedShape

    var showRobotDialog by remember { mutableStateOf(false) }

    val toggleRun: () -> Unit = {
        if (!isConnecting) {
            scope.launch {
                if (!isRunning) {
                    isConnecting = true
                    var success = false
                    val validBots = com.novaedge.app.sdk.TerminalPrefs.getSavedBotProfiles(context)
                    if (validBots.isNotEmpty()) {
                        success = true
                    } else {
                        val token = com.novaedge.app.sdk.TerminalPrefs.getConfig(context)?.token
                        if (token != null) {
                            success = true
                        } else {
                            try {
                                com.novaedge.app.sdk.SupabaseSetup.client.functions.invoke("validate-license")
                                success = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                success = false
                            }
                        }
                    }

                    if (success) {
                        try {
                            // Won't start the engine unless a trading account is
                            // genuinely linked -- a robot that reports RUNNING
                            // with nothing attached is worse than one that
                            // refuses to start.
                            if (!MetaAPIManager.synchronize(context)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Connect your trading account before starting the robot.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                isConnecting = false
                                return@launch
                            }
                            val intent = Intent(context, com.novaedge.app.service.NovaEdgePulseService::class.java)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            
                            if (ttsReady && !isSpeaking) {
                                isSpeaking = true
                                tts.language = Locale.US
                                tts.speak("Welcome Swoosh. Nova Edge systems activated.", TextToSpeech.QUEUE_FLUSH, null, null)
                                scope.launch {
                                    delay(3500)
                                    isSpeaking = false
                                }
                            }
                            isRunning = true
                            com.novaedge.app.sdk.MetaAPIManager.botStatus.value = com.novaedge.app.sdk.BotStatus.RUNNING
                        } catch (e: Exception) {
                            android.util.Log.e("Nova Edge", "Startup failed", e)
                            android.widget.Toast.makeText(context, "Network Error: Could not connect to trade server.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        showActivationError = true
                    }
                    isConnecting = false
                } else {
                    try {
                        com.novaedge.app.sdk.MetaAPIManager.botStatus.value = com.novaedge.app.sdk.BotStatus.STOPPED
                        MetaAPIManager.disconnect()
                        context.stopService(Intent(context, com.novaedge.app.service.NovaEdgePulseService::class.java))
                        isRunning = false
                    } catch (e: Exception) {
                        android.util.Log.e("Nova Edge", "Disconnect failed", e)
                        android.widget.Toast.makeText(context, "Error stopping trade server.", android.widget.Toast.LENGTH_LONG).show()
                        isRunning = false
                    }
                }
            }
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {

        Scaffold(
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .then(if (!isPremium) Modifier.blur(15.dp) else Modifier),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current

            val brokerConnected by MetaAPIManager.isConnected.collectAsState()

            Spacer(Modifier.height(configuration.screenHeightDp.dp * 0.35f))

            val btnShape = when (themeState.homeButtonShape) {
                com.novaedge.app.ui.theme.HomeButtonShape.CIRCLE -> CircleShape
                com.novaedge.app.ui.theme.HomeButtonShape.OVAL -> RoundedCornerShape(percent = 50)
                com.novaedge.app.ui.theme.HomeButtonShape.SQUARE -> RoundedCornerShape(8.dp)
            }

            RobotHero(
                adminName = adminDisplayName,
                robotName = robotName
            )

            Spacer(Modifier.height(32.dp))

            // 3-node ignition control strip
            IgnitionPod(
                isRunning = isRunning,
                isConnecting = isConnecting,
                onToggleRun = toggleRun,
                onPairsClick = { navController.navigate(Routes.PAIRS) },
                onRobotPickerClick = { showRobotPicker = true }
            )

            // Signature gap
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Powered by Nova Edge",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )



            // Identity block — robot name sanitized + bound to LocalRobotFont
            Spacer(Modifier.height(8.dp))
            val robotFontStyle = LocalRobotFont.current
            val vmRobotNameRaw by mainViewModel.robotName.collectAsState()
            val displayName = sanitizeRobotName(vmRobotNameRaw)
            Text(
                text = displayName,
                style = robotFontStyle,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
            if (themeState.isTradeCalculatorEnabled) {
                ForexRiskCalculator()
            }
            Spacer(Modifier.height(32.dp))
            } // End of Column
            } // End of Box

            if (isRunning && isPremium) {
                DraggableFloatingAvatar(
                    robotAvatarUrl = themeState.robotAvatarUrl,
                    onClick = { showRobotDialog = true }
                )
            }

            if (showRobotDialog) {
                RobotActiveDialog(
                    robotName = robotName,
                    adminName = adminDisplayName,
                    onDismiss = { showRobotDialog = false },
                    onStop = toggleRun
                )
            }

            if (!isPremium) {
                val subscriptionRoute by mainViewModel.subscriptionRoute.collectAsState()
                val checkoutUrl by mainViewModel.checkoutUrl.collectAsState()
                val errorMessage by mainViewModel.errorMessage.collectAsState()

                PaywallOverlay(
                    primaryColor = themeState.primaryColor,
                    subscriptionRoute = subscriptionRoute,
                    checkoutUrl = checkoutUrl,
                    errorMessage = errorMessage,
                    onCheckStatus = { email -> mainViewModel.checkSubscriptionStatus(email) },
                    onUnlocked = {
                        mainViewModel.unlockApp()
                        navController.navigate(Routes.HOME) { popUpTo(0) }
                    },
                    onReset = { mainViewModel.resetSubscriptionState() }
                )
            }
        }

        // Robot Picker BottomSheet
        if (showRobotPicker) {
            ModalBottomSheet(
                onDismissRequest = { showRobotPicker = false },
                containerColor = Color(0xFF1E1E1E),
                dragHandle = null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CONNECTED ROBOTS",
                                color = themeState.primaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            IconButton(onClick = { showRobotPicker = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        // Subscription Countdown Display
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = themeState.primaryColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                val isPremiumUser by mainViewModel.isPremium.collectAsState()
                                Text(
                                    text = if (isPremiumUser) "LIFETIME LICENSE" else "30 DAYS REMAINING",
                                    color = themeState.primaryColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(themeState.primaryColor.copy(alpha = 0.15f))
                                    .border(1.dp, themeState.primaryColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (checkoutUrl != null) {
                                            val customTabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder()
                                                .setShowTitle(true)
                                                .build()
                                            customTabsIntent.launchUrl(context, android.net.Uri.parse(checkoutUrl!!))
                                        } else {
                                            showRobotPicker = false
                                            navController.navigate(Routes.ONBOARDING)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "RENEW",
                                    color = themeState.primaryColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        EA_InventoryList(
                            licenses = userLicenses,
                            onAddKeyClick = {
                                showRobotPicker = false
                                navController.navigate(Routes.ONBOARDING)
                            },
                            onRobotSelected = { selectedLicense ->
                                val symbolsList = selectedLicense.symbols ?: emptyList()
                                val prefs = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putString("active_bot_id", selectedLicense.id)
                                    // The robot's id -- what incoming signals are
                                    // tagged with. Without this the device cannot
                                    // tell its own robot's calls from another
                                    // mentor's.
                                    putString("active_ea_id", selectedLicense.ea_id ?: "")
                                    putString("display_name", selectedLicense.display_name ?: "TRADING BOT")
                                    putString("avatar_url", selectedLicense.avatar_url)
                                    putString("accent_color", selectedLicense.accent_color)
                                    putString("allowed_symbols", symbolsList.joinToString(","))
                                }.apply()
                                if (ttsReady && !isSpeaking) {
                                    isSpeaking = true
                                    tts.language = Locale.US
                                    val script = selectedLicense.tts_script ?: "Neural link established for ${selectedLicense.display_name ?: "Trading Bot"}."
                                    tts.speak(script, TextToSpeech.QUEUE_FLUSH, null, null)
                                    scope.launch { delay(3500); isSpeaking = false }
                                }
                                themeUpdater(
                                    themeState.copy(
                                        robotName = selectedLicense.display_name ?: "TRADING BOT",
                                        robotAvatarUrl = selectedLicense.avatar_url,
                                        allowedSymbols = symbolsList
                                    )
                                )
                                showRobotPicker = false
                            }
                        )
                    }
                }
            }
        }

        if (showActivationError) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { showActivationError = false },
                contentAlignment = Alignment.Center
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = "Error", tint = Crimson, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Activation Failed", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Unable to validate license. Please contact support.", color = Color(0xFFEDEDED), fontSize = 14.sp)
                        Spacer(Modifier.height(24.dp))
                        GradientButton("DISMISS", { showActivationError = false }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

        // Removed Delete dialog
        
    }
}

@Composable
fun RobotHero(
    adminName: String,
    robotName: String
) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally, 
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Welcome, ${if (adminName.isNotEmpty()) adminName else "Trader"}",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        
        Spacer(Modifier.height(8.dp))
        
        Surface(
            color = primaryColor.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Rounded.SmartToy, 
                    contentDescription = null, 
                    tint = primaryColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Active Robot: $robotName",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ControlPod(isRunning: Boolean, onToggleRun: () -> Unit, onPairsClick: () -> Unit) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor
    val secondaryColor = themeState.secondaryColor
    val borderBrush = if(themeState.isGlossTheme) Brush.linearGradient(listOf(primaryColor, secondaryColor)) else Brush.linearGradient(listOf(primaryColor, primaryColor))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        OvalControlButton(
            icon = Icons.Rounded.AutoAwesome,
            label = "Quotes",
            borderBrush = borderBrush,
            onClick = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onPairsClick()
            },
            modifier = Modifier.weight(1f)
        )

        OvalControlButton(
            icon = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            label = if (isRunning) "STOP" else "START",
            borderBrush = borderBrush,
            onClick = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onToggleRun()
            },
            modifier = Modifier.weight(1f),
            isPulse = isRunning,
            pulseColor = if (isRunning) Crimson else Color.Transparent
        )
    }
}

@Composable
fun OvalControlButton(
    icon: ImageVector,
    label: String,
    borderBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPulse: Boolean = false,
    pulseColor: Color = Color.Transparent
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseSize by transition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseSize"
    )

    Box(
        modifier = modifier
            .height(72.dp)
            .scale(if (isPulse) pulseSize else 1f)
            .shadow(if (isPulse) 16.dp else 4.dp, CircleShape, spotColor = if (isPulse) pulseColor else Color.Black)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, borderBrush, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * @description Symmetric 3-node ignition control strip for the HomeScreen.
 * Architecture:
 *   LEFT  (small circle, 60dp) — Quotes panel trigger, SwapHoriz velocity icon.
 *   CENTER (large circle, 96dp) — START/STOP ignition engine. Infinite pulse scale
 *          animation fires while the bot is running (1.0f → 1.08f @ 900ms cycle).
 *   RIGHT  (small circle, 60dp) — Robot Swapper Matrix trigger, GridView icon.
 *
 * The center node's glow shadow also scales from 8dp → 24dp in sync with the pulse,
 * creating a breathing halo effect on the active accent color.
 */
@Composable
fun IgnitionPod(
    isRunning: Boolean,
    isConnecting: Boolean = false,
    onToggleRun: () -> Unit,
    onPairsClick: () -> Unit,
    onRobotPickerClick: () -> Unit
) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor
    val secondaryColor = themeState.secondaryColor
    val borderBrush = if (themeState.isGlossTheme)
        Brush.linearGradient(listOf(primaryColor, secondaryColor))
    else
        Brush.linearGradient(listOf(primaryColor, primaryColor))

    val btnShape = when (themeState.homeButtonShape) {
        com.novaedge.app.ui.theme.HomeButtonShape.CIRCLE -> CircleShape
        com.novaedge.app.ui.theme.HomeButtonShape.OVAL -> RoundedCornerShape(percent = 50)
        com.novaedge.app.ui.theme.HomeButtonShape.SQUARE -> RoundedCornerShape(8.dp)
    }

    // Ignition pulse animation — only active when bot is running
    val ignitionTransition = rememberInfiniteTransition(label = "ignition")
    val ignitionScale by ignitionTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ignitionScale"
    )
    val ignitionGlow by ignitionTransition.animateFloat(
        initialValue = 8f,
        targetValue = if (isRunning) 24f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ignitionGlow"
    )

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .scale(themeState.homeButtonScale),
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ─── LEFT: Quotes ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(60.dp)
                .shadow(6.dp, btnShape, spotColor = primaryColor.copy(alpha = 0.3f))
                .clip(btnShape)
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, borderBrush, btnShape)
                .clickable {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onPairsClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.QueryStats,
                    contentDescription = "Quotes",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text("QUOTES", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.width(20.dp))

        // ─── CENTER: Ignition Engine ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(ignitionScale)
                .shadow(
                    elevation = ignitionGlow.dp,
                    shape = btnShape,
                    spotColor = if (isRunning) primaryColor else primaryColor.copy(alpha = 0.4f),
                    ambientColor = if (isRunning) primaryColor.copy(alpha = 0.5f) else primaryColor.copy(alpha = 0.2f)
                )
                .clip(btnShape)
                .background(
                    if (isRunning)
                        Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.8f)))
                    else
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.7f)))
                )
                .border(
                    width = if (isRunning) 2.dp else 1.5.dp,
                    brush = borderBrush,
                    shape = btnShape
                )
                .clickable(enabled = !isConnecting) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggleRun()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = primaryColor,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = if (isRunning) "STOP" else "START",
                        tint = if (isRunning) primaryColor else Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (isRunning) "STOP" else "START",
                        color = if (isRunning) primaryColor else Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        Spacer(Modifier.width(20.dp))

        // ─── RIGHT: Robot Picker Matrix ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(60.dp)
                .shadow(6.dp, btnShape, spotColor = primaryColor.copy(alpha = 0.3f))
                .clip(btnShape)
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, borderBrush, btnShape)
                .clickable {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onRobotPickerClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = "Asset Hub",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("ASSET HUB", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun EA_InventoryList(
    licenses: List<com.novaedge.app.sdk.LicenseRecord> = emptyList(),
    onAddKeyClick: () -> Unit = {},
    onRobotSelected: (com.novaedge.app.sdk.LicenseRecord) -> Unit = {}
) {
    val themeState = LocalNovaEdgeTheme.current
    val themeUpdater = LocalNovaEdgeThemeUpdater.current
    val primaryColor = themeState.primaryColor
    val secondaryColor = themeState.secondaryColor
    val borderBrush = if(themeState.isGlossTheme) Brush.linearGradient(listOf(primaryColor, secondaryColor)) else Brush.linearGradient(listOf(primaryColor, primaryColor))

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CONNECTED ROBOTS:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        
        Spacer(Modifier.height(16.dp))

        if (licenses.isEmpty()) {
            Text("No active licenses found. Tap [+] to add your Nova Edge key.", color = Color(0xFFEDEDED), fontSize = 12.sp)
        } else {
            licenses.forEach { license ->
                val isActive = themeState.robotName == (license.display_name ?: "TRADING BOT")
                
                val cardModifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onRobotSelected(license) }.then(
                    if (isActive) Modifier.border(1.dp, primaryColor, RoundedCornerShape(16.dp)) else Modifier
                )
                
                val content: @Composable ColumnScope.() -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (license.avatar_url != null) {
                            AsyncImage(
                                model = license.avatar_url,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.new_avatar),
                                error = painterResource(id = R.drawable.new_avatar)
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = if (isActive) primaryColor else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                (license.display_name ?: "ROBOT").uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                license.status?.uppercase() ?: "ACTIVE",
                                color = if (isActive) primaryColor.copy(alpha = 0.7f) else Color(0xFFEDEDED),
                                fontSize = 10.sp
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (themeState.isGlossTheme) primaryColor else Color.Green)
                            )
                        }
                    }
                }

                GlassDepthCard(modifier = cardModifier, content = content)
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Add Keys Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(CircleShape)
                .background(Color.Transparent) // Transparent center
                .border(2.dp, borderBrush, CircleShape) // Thicker themed border
                .clickable { onAddKeyClick() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, borderBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add", tint = primaryColor, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("ADD KEYS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Have a valid License Key", color = Color(0xFFEDEDED), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        // Powered by Nova Edge
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(0.6f)) {
            Text("Powered by ", color = Color.White, fontSize = 12.sp)
            Text("Nova Edge", color = primaryColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}


@Composable
fun DraggableFloatingAvatar(robotAvatarUrl: String?, onClick: () -> Unit) {
    val themeState = LocalNovaEdgeTheme.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Safe zone padding
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(72.dp)
                .shadow(16.dp, CircleShape, spotColor = themeState.primaryColor)
                .clip(CircleShape)
                .background(Charcoal)
                .border(2.dp, themeState.primaryColor, CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (robotAvatarUrl != null) {
                AsyncImage(
                    model = robotAvatarUrl,
                    contentDescription = "Robot Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Default Robot",
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Pulsing inner ring to show it's active
            val transition = rememberInfiniteTransition(label = "avatar_pulse")
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.2f, targetValue = 0.6f,
                animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "avatar_pulse_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(4.dp, themeState.primaryColor.copy(alpha = pulseAlpha), CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotActiveDialog(
    robotName: String,
    adminName: String,
    onDismiss: () -> Unit, 
    onStop: () -> Unit
) {
    val themeState = LocalNovaEdgeTheme.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ACTIVE ROBOT", 
                    color = themeState.primaryColor, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Details
            Surface(
                color = Color.Black.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Robot:", color = Color.Gray, fontSize = 14.sp)
                        Text(robotName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Account:", color = Color.Gray, fontSize = 14.sp)
                        Text(adminName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", color = Color.Gray, fontSize = 14.sp)
                        Text("Running", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Powered by ", color = Color(0xFFEDEDED), fontSize = 10.sp)
                Text("Nova Edge", color = themeState.primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    onStop()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeState.primaryColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("STOP ROBOT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ForexRiskCalculator() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
    val themeState = LocalNovaEdgeTheme.current
    
    var balance by remember { mutableStateOf(prefs.getString("calc_balance", "") ?: "") }
    var selectedRiskIndex by remember { mutableStateOf(prefs.getInt("calc_risk_index", 0)) }

    val riskOptions = listOf(1.0 to "Conservative (1%)", 2.0 to "Moderate (2%)", 5.0 to "Aggressive (5%)")
    val balanceVal = balance.toDoubleOrNull() ?: 0.0
    val riskVal = riskOptions[selectedRiskIndex].first

    val pipsBuffer = 20.0
    val standardPipValue = 10.0
    var suggestedLotSize = (balanceVal * (riskVal / 100.0)) / (pipsBuffer * standardPipValue)
    if (suggestedLotSize > 0.0) {
        suggestedLotSize = maxOf(0.01, suggestedLotSize)
    }

    LaunchedEffect(balanceVal, riskVal, suggestedLotSize) {
        prefs.edit().apply {
            putString("calc_balance", balance)
            putInt("calc_risk_index", selectedRiskIndex)
            putFloat("smart_lot_size", suggestedLotSize.toFloat())
            putFloat("smart_risk_pct", riskVal.toFloat())
        }.apply()
    }

    // Glassmorphic panel with dynamic stroke
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(themeState.secondaryBackgroundColor ?: Color.Black.copy(alpha = 0.4f))
            .border(1.dp, themeState.primaryColor, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("TRADE CALCULATOR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(24.dp))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ACCOUNT BALANCE (\$)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .background(themeState.primaryColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, themeState.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(vertical = 12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text("RISK APPETITE", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                riskOptions.forEachIndexed { index, pair ->
                    val isSelected = selectedRiskIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) themeState.primaryColor else Color.Transparent)
                            .clickable { selectedRiskIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pair.second,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Output Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(themeState.primaryColor.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("SMART LOT SIZE", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.2f", suggestedLotSize),
                    color = themeState.primaryColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
