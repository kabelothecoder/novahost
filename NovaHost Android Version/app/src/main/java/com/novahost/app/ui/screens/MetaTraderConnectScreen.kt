package com.novahost.app.ui.screens

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
import com.novahost.app.ui.components.*
import com.novahost.app.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.sdk.SymbolPlanStore
import com.novahost.app.sdk.TerminalConfig
import com.novahost.app.sdk.TerminalPrefs
import androidx.compose.ui.platform.LocalContext
import com.novahost.app.R

import androidx.lifecycle.viewmodel.compose.viewModel
import com.novahost.app.ui.viewmodels.MetaTraderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaTraderConnectScreen(
    navController: NavController,
    viewModel: MetaTraderViewModel = viewModel()
) {
    val accountId by viewModel.accountId.collectAsState()
    val password by viewModel.password.collectAsState()
    val server by viewModel.server.collectAsState()
    // Held by the ViewModel, not by `remember`, so the tab survives leaving the
    // screen. A silently reset platform is indistinguishable from a wrong
    // password once the broker rejects the login.
    val selectedPlatform by viewModel.platform.collectAsState()

    // One value for the whole link attempt, so the button, the progress panel
    // and the result card can never disagree about what is happening.
    var linkState by remember { mutableStateOf<LinkState>(LinkState.Idle) }
    val isConnecting = linkState is LinkState.Working

    val isConnected by MetaAPIManager.isConnected.collectAsState()
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dynamic Accent System
    val themeState = LocalNovaHostTheme.current
    val accentColor = themeState.primaryColor
    val darkAccent = accentColor.copy(alpha = 0.8f)

    Box(
        modifier = Modifier.fillMaxSize().background(com.novahost.app.ui.theme.NeonCanvas)
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
                            com.novahost.app.ui.theme.NeonCanvas.copy(alpha = 0.75f),
                            com.novahost.app.ui.theme.NeonCanvas.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        // The full-screen loading overlay that used to sit here is gone. It
        // blanked the screen for what can be a two-minute wait, and it could
        // only ever say "working" -- the outcome arrived later in a snackbar the
        // user had usually stopped watching for. LinkStatusPanel, below the
        // button, carries the whole attempt: progress, elapsed time, and the
        // result, in one place that stays put.

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.novahost.app.ui.theme.NeonSurfaceSunken)
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
                                .clickable { viewModel.updatePlatform(platform) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                platform,
                                color = if (isSelected) com.novahost.app.ui.theme.NeonCanvas else com.novahost.app.ui.theme.NeonTextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // 4. Credentials — NovaCard + NovaTextField from the Neon Glow set.
                // Inputs stay matte at rest and take the accent rim on focus, so
                // the accent doubles as the focus indicator.
                com.novahost.app.ui.components.NovaCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Free-text: the server name comes from the user's own
                        // broker and is passed straight to MetaCopier, which
                        // validates it. This was readOnly with a no-op
                        // onValueChange, so it could never be filled in.
                        com.novahost.app.ui.components.NovaTextField(
                            value = server,
                            onValueChange = { viewModel.updateServer(it) },
                            label = "Broker server",
                            placeholder = "Exness-MT5Real8",
                            helperText = "Exactly as shown in MetaTrader",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        com.novahost.app.ui.components.NovaTextField(
                            value = accountId,
                            onValueChange = { viewModel.updateAccountId(it) },
                            label = "Login",
                            placeholder = "Account number",
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        com.novahost.app.ui.components.NovaTextField(
                            value = password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = "Password",
                            helperText = "From your broker email or MT5 terminal settings",
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                Spacer(Modifier.height(48.dp))
                
                // 5. Connect — NovaButton (Primary). Pill, accent rim, press
                // collapses the bloom inward.
                val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                val onConnect: () -> Unit = onConnect@{
                        if (accountId.isNotBlank() && password.isNotBlank() && server.isNotBlank()) {
                            // Broker-agnostic sanity check only. The old gate
                            // required the login to start with "245", which
                            // rejected every broker except one.
                            if (!MetaAPIManager.isPlausibleAccountNumber(accountId)) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Enter the account number from your broker (digits only).")
                                }
                                return@onConnect
                            }
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            linkState = LinkState.Working(
                                phase = MetaAPIManager.LinkPhase.REGISTERING,
                                startedAt = System.currentTimeMillis()
                            )
                            coroutineScope.launch {
                                // No scripted status sequence and no artificial
                                // floor on how long this takes. The phase below
                                // comes from the call itself, and a fast link
                                // now reports itself immediately instead of
                                // waiting out a seven-second animation.
                                val startedAt = (linkState as? LinkState.Working)?.startedAt
                                    ?: System.currentTimeMillis()
                                try {
                                    val symbolSuffix = viewModel.getSymbolSuffix()

                                    // accountType is sent after all. MetaCopier does not
                                    // key off it, but the SUFFIX it implies is the
                                    // difference between an order for XAUUSD and one for
                                    // XAUUSD.m, and a micro book only lists the second.
                                    // Both travel to the licence so the executor can name
                                    // instruments the way this broker does.
                                    val success = MetaAPIManager.testBrokerConnection(
                                        context = context,
                                        server = server,
                                        accountId = accountId,
                                        passwordRaw = password,
                                        platform = selectedPlatform.lowercase(),
                                        symbolSuffix = symbolSuffix,
                                        accountType = viewModel.accountType.value,
                                        onPhase = { phase ->
                                            linkState = LinkState.Working(phase, startedAt)
                                        }
                                    )

                                    success.onSuccess { sessionToken ->
                                        // Store what was actually sent, not what
                                        // sits in the field. The manager strips
                                        // whitespace before submitting, and a
                                        // config saved from the raw field would
                                        // disagree with the account that is now
                                        // linked server-side.
                                        val linkedServer = server.trim()
                                        val linkedAccount = accountId.filterNot { it.isWhitespace() }
                                        val config = TerminalConfig(
                                            server = linkedServer,
                                            account = linkedAccount,
                                            token = sessionToken
                                        )
                                        TerminalPrefs.saveConfig(context, config)
                                        MetaAPIManager.startBalanceSync(linkedAccount)
                                        linkState = LinkState.Linked(
                                            server = linkedServer,
                                            platform = selectedPlatform,
                                            account = linkedAccount
                                        )

                                        // ---- Learn this broker's spelling now ----
                                        //
                                        // The account has just come up, so its
                                        // Market Watch is readable for the first
                                        // time. Asking here means the mapping is
                                        // already in place before the first signal
                                        // arrives, on a screen the user is on
                                        // anyway -- rather than being discovered
                                        // by a rejected order later.
                                        //
                                        // It matters because canonical names are
                                        // frequently not what the book carries:
                                        // one live account lists gold as `Gold`,
                                        // the Nasdaq as `.USTECH.` and the Dow as
                                        // `.US30.`, none of which is guessable.
                                        //
                                        // Deliberately not awaited into the link
                                        // state and deliberately silent on
                                        // failure: the account IS connected, and
                                        // turning a symbol-list hiccup into a
                                        // failed connection would be a lie. The
                                        // executor still resolves names on its
                                        // own, and Trading Symbols has a MATCH
                                        // button for a second attempt.
                                        launch {
                                            SymbolPlanStore.discover(context)
                                                .onSuccess { found ->
                                                    android.util.Log.i(
                                                        "NovaHost",
                                                        "[Connect] broker symbols: " +
                                                            "${found.matched.size} matched, " +
                                                            "${found.unmatched.size} not offered"
                                                    )
                                                    SymbolPlanStore.sync(context)
                                                }
                                        }
                                    }.onFailure { error ->
                                        // Show the server's own reason. It already
                                        // distinguishes a broker rejection from a
                                        // provider-side problem, and rewriting it
                                        // here is how "check your credentials" got
                                        // shown for an unfunded MetaCopier project.
                                        linkState = LinkState.Failed(
                                            error.message ?: "Could not connect this account."
                                        )
                                    }
                                } catch (e: Exception) {
                                    linkState = LinkState.Failed(
                                        e.message ?: "Something went wrong connecting this account."
                                    )
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Enter your server, login and password to connect.")
                            }
                        }
                }

                com.novahost.app.ui.components.NovaButton(
                    text = when (linkState) {
                        is LinkState.Working -> "CONNECTING"
                        is LinkState.Linked -> "RECONNECT ACCOUNT"
                        else -> "CONNECT ACCOUNT"
                    },
                    onClick = onConnect,
                    variant = com.novahost.app.ui.components.NovaButtonVariant.Primary,
                    icon = Icons.Rounded.Link,
                    loading = isConnecting,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                LinkStatusPanel(
                    state = linkState,
                    accent = LocalNovaHostTheme.current.primaryColor,
                    onRetry = onConnect,
                    onContinue = { navController.popBackStack() }
                )

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

