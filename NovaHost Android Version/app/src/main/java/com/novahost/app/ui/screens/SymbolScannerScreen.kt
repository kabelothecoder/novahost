package com.novaedge.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novaedge.app.navigation.Routes
import com.novaedge.app.ui.components.*
import com.novaedge.app.ui.theme.*
import kotlin.random.Random
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.novaedge.app.sdk.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.util.UUID
import kotlinx.serialization.Serializable
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import io.github.jan.supabase.storage.*
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.call.*
import androidx.compose.runtime.getValue

@Composable
fun SymbolScannerScreen(navController: NavController, mainViewModel: com.novaedge.app.ui.viewmodels.MainViewModel) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val isPremium by mainViewModel.isPremium.collectAsState()
    
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("SCAN")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepSpaceStart, DeepSpaceEnd)
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Back button anchored to the start
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        
                        // Centered screen title
                        Text(
                            text = "CHART SCANNER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 2.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        // History icon anchored to the end, independent of title
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "Scan History Empty", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Scan History",
                                tint = primaryColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .shadow(4.dp, CircleShape, ambientColor = primaryColor, spotColor = primaryColor)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .then(if (!isPremium) Modifier.blur(15.dp) else Modifier)
            ) {
                when (selectedTabIndex) {
                    0 -> ScanSection(primaryColor, navController)
                }
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
    }
}

enum class ScanState { IDLE, ANALYZING, COMPLETED }

private fun hexToColor(hex: String): Color {
    return try {
        val clean = hex.trimStart('#')
        Color(android.graphics.Color.parseColor("#$clean"))
    } catch (e: Exception) {
        Color(0xFFF24E4E) // SafetyOrange default
    }
}

@Composable
fun ScanSection(primaryColor: Color, navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val savedBots = remember { com.novaedge.app.sdk.TerminalPrefs.getSavedBotProfiles(context) }
    var selectedBot by remember { mutableStateOf(savedBots.firstOrNull()) }
    var expandedBot by remember { mutableStateOf(false) }

    val activeAccent = primaryColor // Fallback to primaryColor

    var scanState by remember { mutableStateOf(ScanState.IDLE) }
    
    var selectedTradingMode by remember { mutableStateOf("Scalp") }
    val tradingModes = listOf("Scalp", "Day", "Swing")
    
    var selectedOrderType by remember { mutableStateOf("Market") }
    val orderTypes = listOf("Market", "Buy Stop", "Sell Stop", "Trail")
    
    var lotSize by remember { mutableFloatStateOf(0.01f) }
    var tradeCount by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(24.dp))

        // 1. Top-Level Configuration Card & Dynamic Tinting Engine
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("Active Robot Profile", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                @OptIn(ExperimentalMaterial3Api::class)
                ExposedDropdownMenuBox(
                    expanded = expandedBot,
                    onExpandedChange = { expandedBot = !expandedBot }
                ) {
                    OutlinedTextField(
                        value = selectedBot?.displayName ?: "No Profile",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        leadingIcon = {
                            if (selectedBot?.avatarUrl != null) {
                                AsyncImage(
                                    model = selectedBot?.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Rounded.SmartToy, contentDescription = null, tint = activeAccent, modifier = Modifier.size(32.dp))
                            }
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBot) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = expandedBot,
                        onDismissRequest = { expandedBot = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        savedBots.forEach { bot ->
                            DropdownMenuItem(
                                text = { Text(bot.displayName, color = Color.White) },
                                onClick = {
                                    selectedBot = bot
                                    expandedBot = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        // Dashed-Border Upload Dropzone
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .drawBehind {
                    val stroke = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                    )
                    drawRoundRect(
                        color = activeAccent.copy(alpha = 0.5f),
                        style = stroke,
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )
                }
                .clickable { /* TODO: Initiate image picker */ }
                .background(activeAccent.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = activeAccent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Tap to upload chart screenshot", color = activeAccent.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Trading Mode
        Text("Trading Mode", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        ) {
            tradingModes.forEach { mode ->
                val isSelected = selectedTradingMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) activeAccent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedTradingMode = mode }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(mode, color = if (isSelected) activeAccent else Color.White.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Order Type
        Text("Order Type", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
        ) {
            orderTypes.forEach { type ->
                val isSelected = selectedOrderType == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) activeAccent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { selectedOrderType = type }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(type, color = if (isSelected) activeAccent else Color.White.copy(alpha=0.6f), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines=1)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        // Steppers
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Lot Size Stepper
            Column(modifier = Modifier.weight(1f)) {
                Text("Lot Size", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (lotSize > 0.01f) lotSize -= 0.01f }) { Icon(Icons.Rounded.Remove, contentDescription = "-", tint = activeAccent) }
                    Text(String.format("%.2f", lotSize), color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { lotSize += 0.01f }) { Icon(Icons.Rounded.Add, contentDescription = "+", tint = activeAccent) }
                }
            }
            
            // Trade Count Stepper
            Column(modifier = Modifier.weight(1f)) {
                Text("Trades", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (tradeCount > 1) tradeCount-- }) { Icon(Icons.Rounded.Remove, contentDescription = "-", tint = activeAccent) }
                    Text(tradeCount.toString(), color = Color.White, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { tradeCount++ }) { Icon(Icons.Rounded.Add, contentDescription = "+", tint = activeAccent) }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Analyze Button
        if (scanState == ScanState.IDLE) {
            GradientButton(
                text = "ANALYZE CHART",
                onClick = { scanState = ScanState.ANALYZING },
                modifier = Modifier.fillMaxWidth(),
                isPulsing = true
            )
        } else if (scanState == ScanState.ANALYZING) {
            AnimatedAnalysisChecklist(
                activeAccent = activeAccent,
                onComplete = { scanState = ScanState.COMPLETED }
            )
        } else {
            DiagnosticOutputBox(activeAccent = activeAccent)
            Spacer(Modifier.height(24.dp))
            GradientButton(
                text = "RESET SCANNER",
                onClick = { scanState = ScanState.IDLE },
                modifier = Modifier.fillMaxWidth(),
                isPulsing = false
            )
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AnimatedAnalysisChecklist(activeAccent: Color, onComplete: () -> Unit) {
    val steps = listOf(
        "ANALYZING CHART...",
        "DETECTING CANDLES...",
        "READING STRUCTURE...",
        "FINDING LEVELS...",
        "BUILDING SIGNAL..."
    )
    var currentStep by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        for (i in steps.indices) {
            currentStep = i
            kotlinx.coroutines.delay(800)
        }
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        steps.forEachIndexed { index, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index < currentStep) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = activeAccent, modifier = Modifier.size(20.dp))
                } else if (index == currentStep) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = activeAccent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = text,
                    color = if (index <= currentStep) Color.White else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun DiagnosticOutputBox(activeAccent: Color) {
    val widgetHtml = """
        <div class="tradingview-widget-container">
          <div class="tradingview-widget-container__widget"></div>
          <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-technical-analysis.js" async>
          {
          "interval": "15m",
          "width": "100%",
          "isTransparent": true,
          "height": "100%",
          "symbol": "OANDA:XAUUSD",
          "showIntervalTabs": true,
          "locale": "en",
          "colorTheme": "dark"
        }
          </script>
        </div>
    """.trimIndent()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(450.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, activeAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        com.novaedge.app.ui.components.TradingViewWidget(widgetHtml = widgetHtml)
    }
}


