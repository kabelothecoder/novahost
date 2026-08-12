package com.novaedge.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.novaedge.app.ui.components.*
import com.novaedge.app.ui.theme.*
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun PairManagementScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("metahost_prefs", android.content.Context.MODE_PRIVATE)
    val rawSymbols = prefs.getString("allowed_symbols", "") ?: ""
    val serverSymbols = if (rawSymbols.isEmpty()) emptyList() else rawSymbols.split(",")

    val availablePairs = serverSymbols
    var allowedPairs by remember { mutableStateOf(serverSymbols) }

    Box(modifier = Modifier.fillMaxSize().background(Charcoal)) {
        MeshGradientBackground()
        
        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Header with Back Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = Cyan)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("TRADING SYMBOLS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp)
                }
                
                Spacer(Modifier.height(16.dp))

                val smartLotSize = prefs.getFloat("smart_lot_size", 0f)
                if (smartLotSize > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Cyan.copy(alpha = 0.1f))
                            .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Applying automated smart lot of ${String.format(java.util.Locale.US, "%.2f", smartLotSize)} per trade based on risk configuration.",
                            color = Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                
                // Content Area
                Box(modifier = Modifier.weight(1f)) {
                    // 3-Col Bento Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(availablePairs) { pair ->
                            val isAllowed = allowedPairs.contains(pair)
                            val bgColor = if (isAllowed) DeepPurple else Color(0xFF0055FF).copy(alpha = 0.1f)
                            val borderColor = if (isAllowed) Cyan else Color(0x40FFFFFF)
                            
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .border(if (isAllowed) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val newPairs = if (isAllowed) allowedPairs - pair else allowedPairs + pair
                                        allowedPairs = newPairs
                                        prefs.edit().putString("allowed_symbols", newPairs.joinToString(",")).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(pair, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isAllowed) Cyan.copy(alpha=0.2f) else Color.White.copy(alpha=0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (isAllowed) "ALLOWED" else "ALL PAIRS",
                                            color = if (isAllowed) Cyan else ActiveGrey,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
