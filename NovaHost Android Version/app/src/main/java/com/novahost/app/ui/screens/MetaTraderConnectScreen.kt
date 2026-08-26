package com.novaedge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.novaedge.app.ui.components.*
import com.novaedge.app.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.novaedge.app.sdk.MetaAPIManager
import com.novaedge.app.sdk.TerminalConfig
import com.novaedge.app.sdk.TerminalPrefs
import androidx.compose.ui.platform.LocalContext
import com.novaedge.app.R

import androidx.lifecycle.viewmodel.compose.viewModel
import com.novaedge.app.ui.viewmodels.MetaTraderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaTraderConnectScreen(
    navController: NavController,
    viewModel: MetaTraderViewModel = viewModel()
) {
    val accountId by viewModel.accountId.collectAsState()
    val password by viewModel.password.collectAsState()
    val server by viewModel.server.collectAsState()
    val accountType by viewModel.accountType.collectAsState()
    var expandedAccountType by remember { mutableStateOf(false) }

    // Grouped account types offered by common brokers
    data class AccountGroup(val header: String, val types: List<String>)
    val accountGroups = listOf(
        AccountGroup("Standard", listOf(
            "Standard — Bonus (300%)",
            "Standard — Bonus (100%)",
            "Standard — No Bonus",
            "Standard No Bonus"
        )),
        AccountGroup("Specialty", listOf(
            "Specialty — Zero Stop Out",
            "Specialty — VIP Account",
            "Specialty — SynX Account",
            "Specialty — Cashback"
        )),
        AccountGroup("Micro", listOf(
            "Micro — No Bonus",
            "Micro — 100% Bonus",
            "Micro — 300% Bonus"
        ))
    )
    
    var isConnecting by remember { mutableStateOf(false) }
    var connectionStatusText by remember { mutableStateOf("Deploying cloud terminal container...") }
    val isConnected by MetaAPIManager.isConnected.collectAsState()
    val context = LocalContext.current
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Dynamic Accent System
    val themeState = LocalNovaEdgeTheme.current
    val accentColor = themeState.primaryColor
    val darkAccent = accentColor.copy(alpha = 0.8f)

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        // 1. Background Layer: Blurred MetaTrader Logo
        AsyncImage(
            model = R.drawable.mt_logo_premium,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(30.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
        )
        
        // 2. Dark Overlay Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        SoftGlassLoadingOverlay(
            visible = isConnecting,
            text = connectionStatusText,
            accentColor = accentColor
        )
        
        // Connect badge at top right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            StatusBadge(isConnected = isConnected)
        }

        // Center Content
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    val isError = data.visuals.message.contains("Failed") || 
                                 data.visuals.message.contains("failed") || 
                                 data.visuals.message.contains("offline") ||
                                 data.visuals.message.contains("Error")
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (isError) Color(0xFFE53935) else accentColor.copy(alpha = 0.9f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                
                // Hero Icon with Glow: MetaTrader Official Logo
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .blur(12.dp)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.mt_logo_premium),
                        contentDescription = "MetaTrader 5",
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "BROKER TERMINAL",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "Direct bridge to your broker's execution nodes.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(Modifier.height(40.dp))
                
                // 3. Container Overhaul: Sleek Tab Selector
                var selectedPlatform by remember { mutableStateOf("MT5") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    listOf("MT4", "MT5").forEach { platform ->
                        val isSelected = selectedPlatform == platform
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .clickable { selectedPlatform = platform }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                platform,
                                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // 4. Input Cards: Translucent Dark Surfaces (Competitor Depth)
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        DarkOutlinedField(
                            value = server,
                            onValueChange = {},
                            label = "SELECT SERVER",
                            icon = Icons.Rounded.Dns,
                            readOnly = true,
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(16.dp))

                        @OptIn(ExperimentalMaterial3Api::class)
                        ExposedDropdownMenuBox(
                            expanded = expandedAccountType,
                            onExpandedChange = { expandedAccountType = !expandedAccountType }
                        ) {
                            DarkOutlinedField(
                                value = accountType,
                                onValueChange = {},
                                label = "ACCOUNT TYPE",
                                icon = Icons.Rounded.Analytics,
                                readOnly = true,
                                modifier = Modifier.menuAnchor(),
                                accentColor = accentColor,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountType) }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAccountType,
                                onDismissRequest = { expandedAccountType = false },
                                modifier = Modifier.background(Color(0xFF1A1A1A))
                            ) {
                                accountGroups.forEach { group ->
                                    // Section Header
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                group.header.uppercase(),
                                                color = accentColor,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                letterSpacing = 1.5.sp
                                            )
                                        },
                                        onClick = { navController.popBackStack() },
                                        enabled = false,
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                                    // Items in each group
                                    group.types.forEach { typeName ->
                                        val isSelected = accountType == typeName
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    typeName,
                                                    color = if (isSelected) accentColor else Color.White,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                viewModel.updateAccountType(typeName)
                                                expandedAccountType = false
                                            },
                                            trailingIcon = if (isSelected) {
                                                { Icon(Icons.Rounded.CheckCircle, null, tint = accentColor, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        DarkOutlinedField(
                            value = accountId,
                            onValueChange = { viewModel.updateAccountId(it) },
                            label = "LOGIN",
                            icon = Icons.Rounded.Person,
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(16.dp))
                        DarkOutlinedField(
                            value = password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = "PASSWORD",
                            icon = Icons.Rounded.Lock,
                            isPassword = true,
                            accentColor = accentColor
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Find this in your broker email or MT5 terminal settings",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(48.dp))
                
                // 5. The "Connect" Button: Full-width Gradient Pill
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                Button(
                    onClick = {
                        if (accountId.isNotBlank() && password.isNotBlank() && server.isNotBlank()) {
                            // Broker-agnostic sanity check only. The old gate
                            // required the login to start with "245", which
                            // rejected every broker except one.
                            if (!MetaAPIManager.isPlausibleAccountNumber(accountId)) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Enter the account number from your broker (digits only).")
                                }
                                return@Button
                            }
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            isConnecting = true
                            connectionStatusText = "Deploying cloud terminal container..."
                            coroutineScope.launch {
                                try {
                                    val textJob = launch {
                                        delay(2500)
                                        connectionStatusText = "Connecting to broker gateway..."
                                        delay(3500)
                                        connectionStatusText = "Verification complete"
                                        delay(1000)
                                    }
                                    
                                    val symbolSuffix = viewModel.getSymbolSuffix()
                                    
                                    val success = MetaAPIManager.testBrokerConnection(
                                        context = context,
                                        server = server,
                                        accountId = accountId,
                                        passwordRaw = password,
                                        platform = selectedPlatform.lowercase(),
                                        accountType = accountType,
                                        symbolSuffix = symbolSuffix
                                    )
                                    
                                    textJob.join() // wait for UX sequence to finish
                                    
                                    success.onSuccess { sessionToken ->
                                        val config = TerminalConfig(
                                            server = server, 
                                            account = accountId, 
                                            token = sessionToken
                                        )
                                        TerminalPrefs.saveConfig(context, config)
                                        MetaAPIManager.startBalanceSync(accountId)
                                        snackbarHostState.showSnackbar("Neural Handshake Confirmed")
                                    }.onFailure { error ->
                                        val errorMessage = when {
                                            error.message?.contains("Invalid Broker Server") == true -> 
                                                "Failed: Ensure Account ID and Server exactly match your broker details."
                                            else -> error.message ?: "Sync Failed"
                                        }
                                        snackbarHostState.showSnackbar(errorMessage)
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Critical Error: ${e.message}")
                                } finally {
                                    isConnecting = false
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Error: Missing account details. Please complete all fields.")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = accentColor, spotColor = accentColor)
                        .clip(RoundedCornerShape(32.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "CONNECT ACCOUNT",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(40.dp))
                
                // 6. Security Footer
                Surface(
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.GppGood, 
                            contentDescription = null, 
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Your credentials are encrypted and securely stored. We do not have direct access to your trading account.",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isPassword: Boolean = false,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Red,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) },
        readOnly = readOnly,
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = trailingIcon ?: if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle Password Visibility",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = Color(0x30FFFFFF),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        singleLine = true
    )
}
