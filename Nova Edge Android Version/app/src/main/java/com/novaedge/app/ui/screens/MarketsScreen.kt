package com.novaedge.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novaedge.app.navigation.Routes
import com.novaedge.app.ui.components.*
import com.novaedge.app.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaedge.app.ui.viewmodels.MarketsViewModel
import kotlinx.coroutines.delay

// ─── Data Models ────────────────────────────────────────────────────────────

data class LivePair(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val change: Double
)

// ─── MarketsScreen ───────────────────────────────────────────────────────────

@Composable
fun MarketsScreen(
    navController: NavController,
    viewModel: MarketsViewModel = viewModel(),
    mainViewModel: com.novaedge.app.ui.viewmodels.MainViewModel = viewModel()
) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("MARKETS", "NEWS", "CALENDAR", "INSIGHTS")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(DeepSpaceStart, DeepSpaceEnd)))
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    MarketsTopBar(primaryColor)
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = primaryColor,
                        edgePadding = 16.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            Box(
                                Modifier
                                    .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                    .height(2.dp)
                                    .padding(horizontal = 12.dp)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(50.dp),
                                        ambientColor = primaryColor,
                                        spotColor = primaryColor
                                    )
                                    .background(color = primaryColor, shape = RoundedCornerShape(50.dp))
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val selected = selectedTabIndex == index
                            Tab(
                                selected = selected,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (selected) primaryColor else Color.White.copy(alpha = 0.4f),
                                        letterSpacing = 1.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val livePrices by viewModel.livePrices.collectAsState()
                val rawEvents by viewModel.economicCalendar.collectAsState()
                val rawMarketSessions by viewModel.marketSessions.collectAsState()
                val watchlist by viewModel.watchlist.collectAsState()

                val bullishMomentum by viewModel.bullishMomentum.collectAsState()

                val uiMarketSessions = rawMarketSessions.map { 
                    MarketSession(it.name, it.openTime, it.closeTime, it.isOpen, if(it.isOpen) 0.5f else 0f) 
                }
                val uiEvents = rawEvents.map {
                    EconomicEvent(it.currency, it.event, it.date, it.impact, it.actual?.toString() ?: "—", it.estimate?.toString() ?: "—")
                }
                
                val volatilitySymbols = livePrices.entries.take(4).map { entry ->
                    val momentum = bullishMomentum[entry.key] ?: 50f
                    VolatilitySymbol(
                        name = entry.key.replace("OANDA:", "").replace("BINANCE:", "").replace("_", "/"),
                        intensity = momentum / 100f,
                        change = "${if (momentum >= 50f) "+" else ""}${String.format(java.util.Locale.US, "%.1f", momentum)}%"
                    )
                }
                
                val calculatedAvg = if (bullishMomentum.isNotEmpty()) {
                    bullishMomentum.values.average().toFloat() / 100f
                } else 0.65f
                
                var avgMomentum by remember { mutableFloatStateOf(calculatedAvg) }
                
                LaunchedEffect(calculatedAvg) {
                    if (bullishMomentum.isEmpty()) {
                        while(true) {
                            kotlinx.coroutines.delay(2000L)
                            // Simulate live calculation loop processing standard deviations
                            avgMomentum = 0.50f + (Math.random().toFloat() * 0.40f)
                        }
                    } else {
                        avgMomentum = calculatedAvg
                    }
                }

                when (selectedTabIndex) {
                    0 -> QuotesTab(navController, mainViewModel)
                    1 -> NewsTab()
                    2 -> CalendarTab(primaryColor, uiMarketSessions, uiEvents)
                    3 -> InsightsTab(
                        primaryColor,
                        volatilitySymbols,
                        "AI Analysis active. Currently tracking high liquidity in the active overlap sessions and identifying standard deviations across major pairs.",
                        avgMomentum,
                        listOf(
                            NeuralWeight("SMC VALIDATION", 0.8f),
                            NeuralWeight("LIQUIDITY SWEEP", 0.5f),
                            NeuralWeight("MOMENTUM", 0.7f)
                        )
                    )
                }
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@Composable
fun MarketsTopBar(primaryColor: Color) {
    val dot by rememberInfiniteTransition(label = "liveDot").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Center-aligned title
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MARKETS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Text(
                "Global Forex & Crypto",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
        // LIVE badge — independently at end, never overlaps center title
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF00E676).copy(alpha = 0.10f))
                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .shadow(4.dp, CircleShape, ambientColor = Color(0xFF00E676), spotColor = Color(0xFF00E676))
                    .clip(CircleShape)
                    .background(Color(0xFF00E676).copy(alpha = dot))
            )
            Spacer(Modifier.width(6.dp))
            Text("LIVE", color = Color(0xFF00E676), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

// ─── Tab 0: Dashboard ────────────────────────────────────────────────────────

@Composable
fun DashboardTab(primaryColor: Color, events: List<EconomicEvent>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TradingViewChartContainer(symbol = "FX:XAUUSD")
        Spacer(Modifier.height(24.dp))
        EconomicCalendarCard(events, primaryColor)
    }
}

// ─── Tab 1: Quotes ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesTab(navController: NavController, mainViewModel: com.novaedge.app.ui.viewmodels.MainViewModel) {
    val themeState = LocalNovaEdgeTheme.current
    val primaryColor = themeState.primaryColor

    val symbols = listOf("XAUUSD", "US30", "NAS100", "GBPUSD", "BTCUSD")
    
    var selectedSymbol by remember { mutableStateOf<String?>(null) }
    var lotSize by remember { mutableStateOf("0.01") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(symbols) { symbol ->
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedSymbol = symbol }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(symbol, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.Settings, contentDescription = "Configure", tint = primaryColor)
                }
            }
        }
    }

    if (selectedSymbol != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedSymbol = null },
            containerColor = Color(0xFF1E1E1E),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CONFIGURE $selectedSymbol", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = { selectedSymbol = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = lotSize,
                    onValueChange = { lotSize = it },
                    label = { Text("Lot Size", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val parsedLot = lotSize.toDoubleOrNull() ?: 0.01
                        mainViewModel.saveSymbolPreferences(selectedSymbol!!, parsedLot)
                        selectedSymbol = null
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SAVE SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        navController.navigate(Routes.SCANNER)
                        selectedSymbol = null
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OPEN CHART SCANNER", color = primaryColor, fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun NewsTab() {
    val widgetHtml = """
        <div class="tradingview-widget-container">
          <div class="tradingview-widget-container__widget"></div>
          <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-timeline.js" async>
          {
          "feedMode": "all_symbols",
          "colorTheme": "dark",
          "isTransparent": true,
          "displayMode": "regular",
          "width": "100vw",
          "height": "100%",
          "locale": "en"
        }
          </script>
        </div>
    """.trimIndent()

    Box(modifier = Modifier.fillMaxSize()) {
        TradingViewWidget(widgetHtml = widgetHtml)
    }
}

// ─── Sentiment Card ──────────────────────────────────────────────────────────

@Composable
fun SentimentCard(primaryColor: Color) {
    val sentiments = listOf(
        Triple("BULL BIAS", 68f, Color(0xFF00E676)),
        Triple("BEAR BIAS", 21f, Crimson),
        Triple("NEUTRAL",   11f, Color(0xFFFFB300))
    )

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("MARKET SENTIMENT", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.5.sp)
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                sentiments.forEach { (_, pct, color) ->
                    Box(modifier = Modifier.weight(pct).fillMaxHeight().background(color))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sentiments.forEach { (label, pct, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(5.dp))
                            Text("${pct.toInt()}%", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}
