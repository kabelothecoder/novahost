package com.novahost.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.novahost.app.navigation.Routes
import com.novahost.app.ui.theme.LocalNovaHostTheme

@Composable
fun TopNavMenuOverlay(navController: NavController, currentRoute: String?) {
    // Only show on authenticated main screens
    val showNav = currentRoute in listOf(
        Routes.HOME, Routes.TERMINAL, Routes.SCANNER, Routes.SETTINGS, Routes.PAIRS
    )

    if (!showNav) return

    val themeState = LocalNovaHostTheme.current
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Overlay Panel
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { isMenuOpen = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 110.dp, start = 32.dp, end = 32.dp)
                ) {
                    Text(
                        text = "NovaHost NAVIGATION",
                        color = themeState.primaryColor,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    NavMenuItem("Home Dashboard", Icons.Rounded.Home, currentRoute == Routes.HOME) {
                        isMenuOpen = false
                        if (currentRoute != Routes.HOME) navController.navigate(Routes.HOME)
                    }
                    NavMenuItem("MetaTrader Connection", Icons.Rounded.SyncAlt, currentRoute == Routes.TERMINAL) {
                        isMenuOpen = false
                        if (currentRoute != Routes.TERMINAL) navController.navigate(Routes.TERMINAL)
                    }
                    NavMenuItem("Chart Scanner", Icons.Rounded.QueryStats, currentRoute == Routes.SCANNER) {
                        isMenuOpen = false
                        if (currentRoute != Routes.SCANNER) navController.navigate(Routes.SCANNER)
                    }
                    // Trading Symbols is deliberately not listed.
                    //
                    // It has exactly one way in: the Quotes button on the home
                    // ignition row. Two entry points to a screen that decides
                    // what the robot is allowed to trade meant two places to
                    // look for it and no clue which was canonical -- and the
                    // home button is the one sitting next to START, where the
                    // question "what will this trade?" actually gets asked.
                    NavMenuItem("Global Settings", Icons.Rounded.Settings, currentRoute == Routes.SETTINGS) {
                        isMenuOpen = false
                        if (currentRoute != Routes.SETTINGS) navController.navigate(Routes.SETTINGS)
                    }
                }
            }
        }

        // Circular Floating Button
        Box(
            modifier = Modifier
                .padding(top = 48.dp, start = 24.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.DarkGray.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
                .clickable { isMenuOpen = !isMenuOpen },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMenuOpen) Icons.Rounded.Close else Icons.Rounded.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun NavMenuItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val themeState = LocalNovaHostTheme.current
    val color = if (isSelected) themeState.primaryColor else Color.White
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clickable { onClick() }
    ) {
        Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = color, fontSize = 18.sp)
    }
}
