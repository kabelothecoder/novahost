package com.novahost.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.novahost.app.navigation.Routes
import com.novahost.app.sdk.MetaAPIManager
import com.novahost.app.ui.components.GlassCard
import com.novahost.app.ui.components.GradientButton
import com.novahost.app.ui.components.PaywallOverlay
import com.novahost.app.ui.home.HomeActions
import com.novahost.app.ui.home.HomeLayoutHost
import com.novahost.app.ui.home.HomeUiState
import com.novahost.app.ui.theme.Crimson
import com.novahost.app.ui.theme.LocalNovaHostTheme
import com.novahost.app.ui.theme.LocalRobotBranding
import com.novahost.app.ui.theme.LocalRobotBrandingUpdater
import com.novahost.app.ui.theme.LocalNovaHostThemeUpdater
import com.novahost.app.ui.theme.parseRobotAccent
import com.novahost.app.ui.viewmodels.HomeViewModel

/**
 * The Home screen: state, chrome and overlays. The layout itself is chosen at
 * runtime and rendered by [HomeLayoutHost].
 *
 * This composable deliberately owns no run state. `isRunning`, `isConnecting`
 * and the TTS engine live in [HomeViewModel] so that switching interface preset
 * -- which swaps the entire subtree below -- cannot strand a running bot behind
 * a button that has flipped back to START.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    mainViewModel: com.novahost.app.ui.viewmodels.MainViewModel,
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    val themeState = LocalNovaHostTheme.current
    val themeUpdater = LocalNovaHostThemeUpdater.current
    val branding = LocalRobotBranding.current
    val brandingUpdater = LocalRobotBrandingUpdater.current

    val isPremium by mainViewModel.isPremium.collectAsState()
    val adminDisplayName by mainViewModel.adminDisplayName.collectAsState()
    val robotNameRaw by mainViewModel.robotName.collectAsState()
    val userLicenses by mainViewModel.userLicenses.collectAsState()
    val licenseError by mainViewModel.licenseError.collectAsState()
    val licensesLoading by mainViewModel.licensesLoading.collectAsState()
    val activeLicenseKey by mainViewModel.activeLicenseKey.collectAsState()

    val isRunning by homeViewModel.isRunning.collectAsState()
    val isConnecting by homeViewModel.isConnecting.collectAsState()
    val activationError by homeViewModel.activationError.collectAsState()
    val transientMessage by homeViewModel.transientMessage.collectAsState()
    val customization by homeViewModel.customization.collectAsState()

    val brokerConnected by MetaAPIManager.isConnected.collectAsState()
    val linkChecking by MetaAPIManager.isProbingLink.collectAsState()
    val terminalLog by MetaAPIManager.logs.collectAsState()

    var showRobotPicker by remember { mutableStateOf(false) }
    var showRobotDialog by remember { mutableStateOf(false) }

    /**
     * The key currently being switched to, or null.
     *
     * A swap is a round trip to `validate-license`, not a local state change, so
     * the row has to say it is working. It also serialises the switch: two taps
     * in flight would race to write `license_key` and the loser's identity would
     * land on top of the winner's.
     */
    var switchingKey by remember { mutableStateOf<String?>(null) }

    // Toasts are raised by the ViewModel and consumed here, so a recomposition
    // caused by a layout switch cannot re-show one the user already dismissed.
    LaunchedEffect(transientMessage) {
        transientMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            homeViewModel.consumeTransientMessage()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val prefs = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)
                // Pick up the robot stored by licence activation / the robot
                // picker, so the dashboard shows the robot actually licensed.
                mainViewModel.refreshRobotIdentity()

                // The broker link is a server fact, and isConnected defaults to
                // false. Without this the header asserted NOT LINKED on every
                // cold start until the user pressed START -- a linked account
                // reading as broken, from a default rather than an answer.
                MetaAPIManager.probeLinkStatus(context)

                // Keyed on the device, not an email. Licence activation never
                // writes user_email -- only the paygate does -- so gating this
                // on an email meant a key-only user's drawer never even asked.
                mainViewModel.fetchUserLicenses()

                val email = prefs.getString("user_email", "") ?: ""
                if (email.isNotEmpty()) {
                    // Silent re-check: never puts a message on screen, and a
                    // failed call leaves the cached entitlement alone.
                    mainViewModel.refreshEntitlements(email)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState = HomeUiState(
        isRunning = isRunning,
        isConnecting = isConnecting,
        brokerConnected = brokerConnected,
        linkChecking = linkChecking,
        robotName = sanitizeRobotName(robotNameRaw),
        licenseKey = activeLicenseKey,
        mentorName = adminDisplayName.takeUnless { it == "SYSTEM_ADMIN" } ?: branding.mentorName,
        tagline = branding.tagline.ifBlank { branding.allowedSymbols.joinToString(", ") },
        artUrl = branding.avatarUrl,
        accent = themeState.primaryColor,
        glow = themeState.glow,
        licenses = userLicenses,
        activityLog = terminalLog,
        licenceSummary = licenceSummary(isRunning, brokerConnected, linkChecking, activeLicenseKey)
    )

    val actions = HomeActions(
        onToggleRun = { homeViewModel.toggleRun() },
        onQuotes = { navController.navigate(Routes.PAIRS) },
        onAssetHub = { showRobotPicker = true },
        onSettings = { navController.navigate(Routes.SETTINGS) },
        onScanner = { navController.navigate(Routes.SCANNER) },
        onAddKey = { navController.navigate(Routes.ACTIVATE) },
        onRobotSelected = { license ->
            if (switchingKey == null) {
                switchingKey = license.license_key
                mainViewModel.switchRobot(license) { failure ->
                    switchingKey = null
                    if (failure == null) {
                        applyRobotBranding(
                            context = context,
                            themeState = themeState,
                            themeUpdater = themeUpdater,
                            branding = branding,
                            brandingUpdater = brandingUpdater,
                            speak = homeViewModel::speak
                        )
                        // The link belongs to the licence, not to the app, so a
                        // different key can mean a different broker account.
                        MetaAPIManager.probeLinkStatus(context)
                        showRobotPicker = false
                    } else {
                        // Left open on purpose: the user needs to see which row
                        // failed and be able to pick another.
                        homeViewModel.raiseTransientMessage(failure)
                    }
                }
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // No Scaffold inset here on purpose. Full-Bleed Art and Glass Stack are
        // only full-bleed if the art reaches the top of the window, so each
        // layout applies system-bar padding to its own content column and
        // leaves the art behind it edge to edge.
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!isPremium) Modifier.blur(15.dp) else Modifier)
            ) {
                HomeLayoutHost(
                    layout = customization.layout,
                    arrangement = customization.active,
                    state = uiState,
                    actions = actions
                )
            }

            if (isRunning && isPremium) {
                DraggableFloatingAvatar(
                    robotAvatarUrl = branding.avatarUrl,
                    onClick = { showRobotDialog = true }
                )
            }

            if (showRobotDialog) {
                RobotActiveDialog(
                    robotName = uiState.robotName,
                    adminName = adminDisplayName,
                    onDismiss = { showRobotDialog = false },
                    onStop = { homeViewModel.toggleRun() }
                )
            }

            if (!isPremium) {
                val appGate by mainViewModel.appGate.collectAsState()
                val savedEmail by mainViewModel.userEmail.collectAsState()

                PaywallOverlay(
                    gate = appGate,
                    initialEmail = savedEmail,
                    onCheckAccess = { email -> mainViewModel.checkAppAccess(email) },
                    onBuy = { email -> mainViewModel.buyAppAccess(email) },
                    onGranted = {
                        mainViewModel.unlockApp()
                        navController.navigate(Routes.HOME) { popUpTo(0) }
                    },
                    onCheckoutOpened = { mainViewModel.resetAppGate() }
                )
            }
        }

        if (showRobotPicker) {
            RobotPickerSheet(
                licenses = userLicenses,
                isPremium = isPremium,
                accent = themeState.primaryColor,
                activeLicenseKey = activeLicenseKey,
                switchingKey = switchingKey,
                loading = licensesLoading,
                error = licenseError,
                onDismiss = { if (switchingKey == null) showRobotPicker = false },
                onRetry = { mainViewModel.fetchUserLicenses() },
                onAddKey = {
                    showRobotPicker = false
                    navController.navigate(Routes.ACTIVATE)
                },
                onRobotSelected = actions.onRobotSelected
            )
        }

        // Ignition refused. The dialog names which of the two preconditions
        // failed and takes the user to the screen that fixes it -- the old copy
        // said "contact support" for problems the user resolves themselves.
        activationError?.let { failure ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { homeViewModel.dismissActivationError() },
                contentAlignment = Alignment.Center
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth(0.85f)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = "Error", tint = Crimson, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(failure.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            failure.body,
                            color = Color(0xFFEDEDED),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        GradientButton(
                            failure.actionLabel,
                            {
                                homeViewModel.dismissActivationError()
                                navController.navigate(failure.actionRoute)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Not now",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { homeViewModel.dismissActivationError() }
                                .padding(vertical = 6.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

/** One line of plain status for the layouts that show a sentence rather than tiles. */
private fun licenceSummary(
    isRunning: Boolean,
    brokerConnected: Boolean,
    linkChecking: Boolean,
    licenseKey: String
): String = buildString {
    append(if (isRunning) "Running" else "Standby")
    append(" · ")
    append(
        when {
            linkChecking -> "checking broker"
            brokerConnected -> "broker linked"
            else -> "broker not linked"
        }
    )
    // The key the robot is running under. Several keys can sit behind one robot
    // name, so the name alone cannot tell the user which one is live -- and
    // until now nothing on the dashboard showed the key at all.
    if (licenseKey.isNotBlank()) {
        append(" · ")
        append(licenseKey)
    }
}

/**
 * Re-reads the active robot out of preferences and applies it to the deck.
 *
 * This used to take the picked [LicenseRecord] and write prefs from it, which
 * was wrong twice over. It never wrote `license_key` -- the value
 * `sync-symbol-config` and `metacopier-execute` actually key off -- so a swap
 * re-branded the dashboard to one robot while trades kept executing under the
 * previous one. And the list row is deliberately art-stripped by `my-licenses`
 * (avatars are 216KB-3MB base64 blobs), so branding from it left the new robot
 * with no art at all.
 *
 * `MainViewModel.switchRobot` now owns the write: it re-runs activation for the
 * chosen key, which returns the full identity and rebinds the device seat. This
 * only reflects what that wrote.
 *
 * Identity and theme are separate stores, so this touches both and neither one
 * disturbs the user's layout, art mode or widget order.
 */
private fun applyRobotBranding(
    context: Context,
    themeState: com.novahost.app.ui.theme.NovaHostThemeState,
    themeUpdater: (com.novahost.app.ui.theme.NovaHostThemeState) -> Unit,
    branding: com.novahost.app.ui.theme.RobotBranding,
    brandingUpdater: (com.novahost.app.ui.theme.RobotBranding) -> Unit,
    speak: (String) -> Unit
) {
    val prefs = context.getSharedPreferences("metahost_prefs", Context.MODE_PRIVATE)

    val displayName = prefs.getString("display_name", "")?.takeIf { it.isNotBlank() } ?: "TRADING BOT"
    val avatarUrl = prefs.getString("avatar_url", null)
    val accentColor = prefs.getString("accent_color", null)
    val symbols = (prefs.getString("allowed_symbols", "") ?: "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    speak(
        prefs.getString("tts_script", null)?.takeIf { it.isNotBlank() }
            ?: "Neural link established for $displayName."
    )

    brandingUpdater(
        branding.copy(
            name = displayName,
            avatarUrl = avatarUrl,
            allowedSymbols = symbols
        )
    )
    // Re-theme to the selected robot's accent so switching robots visibly
    // changes the deck.
    themeUpdater(themeState.copy(primaryColor = parseRobotAccent(accentColor)))
}

/** The Asset Hub sheet: licence countdown, robot list, add-key. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobotPickerSheet(
    licenses: List<com.novahost.app.sdk.LicenseRecord>,
    isPremium: Boolean,
    accent: Color,
    activeLicenseKey: String,
    switchingKey: String?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAddKey: () -> Unit,
    onRobotSelected: (com.novahost.app.sdk.LicenseRecord) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CONNECTED ROBOTS",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    // App access is a single once-off payment now, so there is
                    // no countdown to show and nothing to renew.
                    Text(
                        text = if (isPremium) "LIFETIME ACCESS" else "APP LOCKED",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .border(1.dp, accent, RoundedCornerShape(8.dp))
                        .clickable { onAddKey() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ADD KEY", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            EA_InventoryList(
                licenses = licenses,
                activeLicenseKey = activeLicenseKey,
                switchingKey = switchingKey,
                loading = loading,
                error = error,
                onRetry = onRetry,
                onAddKeyClick = onAddKey,
                onRobotSelected = onRobotSelected
            )
        }
    }
}
