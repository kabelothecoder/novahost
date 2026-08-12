package com.novaedge.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novaedge.app.MainActivity
import com.novaedge.app.ui.components.GlassCard
import com.novaedge.app.ui.components.GradientButton
import com.novaedge.app.ui.components.GlobalVideoBackground
import com.novaedge.app.ui.theme.NovaEdgeTheme
import kotlinx.coroutines.launch

class OverlayPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Settings.canDrawOverlays(this)) {
            proceedToMain()
            return
        }

        setContent {
            NovaEdgeTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    GlobalVideoBackground()
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                            )
                        )
                    )

                    PermissionGuideScreen(
                        onRequestPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        },
                        onContinue = { proceedToMain() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            proceedToMain()
        }
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PermissionGuideScreen(onRequestPermission: () -> Unit, onContinue: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        
        Text(
            text = "Nova Edge PULSE",
            color = com.novaedge.app.ui.theme.Cyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(24.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> GuideStep(
                    icon = Icons.Rounded.BubbleChart,
                    title = "Unlock the Pulse",
                    description = "A floating Command Bubble that stays on your screen, allowing you to monitor active trades and switch strategies without staying inside the app."
                )
                1 -> GuideStep(
                    icon = Icons.Rounded.Security,
                    title = "Grant Access",
                    description = "To enable this HUD, Android requires you to allow Nova Edge to 'Draw over other apps' (Appear on top)."
                )
                2 -> GuideStep(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Stay Connected",
                    description = "Once granted, the Pulse Bubble will automatically appear when an active bot is running."
                )
            }
        }

        // Pager indicators
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { iteration ->
                val color = if (pagerState.currentPage == iteration) com.novaedge.app.ui.theme.Cyan else Color.White.copy(alpha = 0.2f)
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(if (pagerState.currentPage == iteration) 10.dp else 8.dp)
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pagerState.currentPage < 2) {
                    GradientButton(
                        text = "NEXT",
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        enabled = true
                    )
                } else {
                    GradientButton(
                        text = "GRANT PERMISSION",
                        onClick = onRequestPermission,
                        enabled = true
                    )
                }
            }
        }
    }
}

@Composable
fun GuideStep(icon: ImageVector, title: String, description: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .shadow(24.dp, CircleShape, spotColor = com.novaedge.app.ui.theme.Cyan)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .border(2.dp, com.novaedge.app.ui.theme.Cyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = com.novaedge.app.ui.theme.Cyan,
                modifier = Modifier.size(64.dp)
            )
        }
        
        Spacer(Modifier.height(48.dp))
        
        Text(
            text = title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = description,
            color = com.novaedge.app.ui.theme.ActiveGrey,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
