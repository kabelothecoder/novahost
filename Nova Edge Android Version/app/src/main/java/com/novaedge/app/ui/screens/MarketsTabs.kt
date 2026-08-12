package com.novaedge.app.ui.screens

// @description Shared composable sections for the Markets tab:
// CalendarTab (Market Sessions + Economic Calendar)
// InsightsTab (Volatility Heatmap + AI Analysis)

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaedge.app.ui.components.GlassGauge
import com.novaedge.app.ui.theme.*

// ─── Data models (shared) ─────────────────────────────────────────────────────
data class MarketSession(val name: String, val open: String, val close: String, val isOpen: Boolean, val progress: Float)
data class EconomicEvent(val currency: String, val title: String, val time: String, val impact: String, val actual: String = "", val forecast: String = "")
data class VolatilitySymbol(val name: String, val intensity: Float, val change: String)
data class NeuralWeight(val name: String, val value: Float)

// ─── CALENDAR TAB (Sessions + Calendar) ───────────────────────────────────────

@Composable
fun CalendarTab(
    primaryColor: Color,
    sessions: List<MarketSession>,
    events: List<EconomicEvent>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(32.dp))
        Text("MARKET SESSIONS", modifier = Modifier.padding(horizontal = 24.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
        Text("Local Time Liquidity Windows", modifier = Modifier.padding(horizontal = 24.dp), color = ActiveGrey, fontSize = 11.sp)

        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(200.dp).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(sessions) { session ->
                SessionCard(session, primaryColor)
            }
        }

        Spacer(Modifier.height(32.dp))

        Text("ECONOMIC CALENDAR", modifier = Modifier.padding(horizontal = 24.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))

        EconomicCalendarCard(events, primaryColor)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SessionCard(session: MarketSession, primaryColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (session.isOpen) primaryColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(session.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (session.isOpen) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                            .shadow(4.dp, CircleShape, spotColor = primaryColor)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("${session.open} - ${session.close}", color = ActiveGrey, fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(session.progress)
                        .fillMaxHeight()
                        .background(if (session.isOpen) primaryColor else Color.Gray)
                )
            }
        }
    }
}

@Composable
fun EconomicCalendarCard(events: List<EconomicEvent>, primaryColor: Color) {
    val widgetHtml = """
        <div class="tradingview-widget-container">
          <div class="tradingview-widget-container__widget"></div>
          <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-events.js" async>
          {
          "colorTheme": "dark",
          "isTransparent": true,
          "width": "100vw",
          "height": "100%",
          "locale": "en",
          "importanceFilter": "-1,0,1"
        }
          </script>
        </div>
    """.trimIndent()
    Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
        com.novaedge.app.ui.components.TradingViewWidget(widgetHtml = widgetHtml)
    }
}

// ─── INSIGHTS TAB (Pulse + Neural) ────────────────────────────────────────────

@Composable
fun InsightsTab(
    primaryColor: Color,
    volatilitySymbols: List<VolatilitySymbol>,
    aiInsights: String,
    confidence: Float,
    weights: List<NeuralWeight>
) {
    val widgetHtml = """
        <div class="tradingview-widget-container">
          <div class="tradingview-widget-container__widget"></div>
          <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-technical-analysis.js" async>
          {
          "interval": "15m",
          "width": "100vw",
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
    Box(modifier = Modifier.fillMaxSize()) {
        com.novaedge.app.ui.components.TradingViewWidget(widgetHtml = widgetHtml)
    }
}

@Composable
fun VolatilityCard(symbol: VolatilitySymbol, primaryColor: Color) {
    val isPositive = symbol.change.startsWith("+")
    val color = if (isPositive) Color(0xFF00E676) else Crimson
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(symbol.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Momentum: ${symbol.change}",
                    color = color,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(symbol.intensity)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)))
                )
            }
        }
    }
}

@Composable
fun NeuralWeightRow(weight: NeuralWeight, accentColor: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(weight.name.replace("_", " "), color = ActiveGrey, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${(weight.value * 100).toInt()}%", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Box(modifier = Modifier.fillMaxWidth(weight.value).fillMaxHeight().background(accentColor))
        }
    }
}
