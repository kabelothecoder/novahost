package com.novaedge.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.viewModels
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.novaedge.app.navigation.AppNavigation
import com.novaedge.app.service.NanoBananaService
import com.novaedge.app.ui.components.TopNavMenuOverlay
import com.novaedge.app.ui.components.GlobalVideoBackground
import com.novaedge.app.ui.theme.NovaEdgeTheme
import com.novaedge.app.ui.theme.NovaEdgeThemeState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.novaedge.app.ui.viewmodels.MainViewModel

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            installSplashScreen()
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()

            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                }
            }

            // Start Nano Banana foreground watcher
            startService(Intent(this, NanoBananaService::class.java))

            // Check for SYSTEM_ALERT_WINDOW permission before showing Home
            if (!android.provider.Settings.canDrawOverlays(this)) {
                startActivity(Intent(this, com.novaedge.app.ui.screens.OverlayPermissionActivity::class.java))
                finish()
                return
            }

            // Start Floating Pulse Service
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, com.novaedge.app.service.NovaEdgePulseService::class.java))
            } else {
                startService(Intent(this, com.novaedge.app.service.NovaEdgePulseService::class.java))
            }

            // Check if there's a saved license key to bypass the gate
            val prefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
            val hasLicense = !prefs.getString("license_key", null).isNullOrBlank()

            val mainViewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]

            // Handle deep link if app was launched fresh from the browser
            if (intent != null && intent.data != null) {
                handleDeepLink(intent)
            }

            setContent {
                // Use actual saved state instead of hardcoded false
                val isLoggedIn = remember { mutableStateOf(hasLicense) }

                // Vibe switcher state — passed down to SettingsScreen via CompositionLocal
                var themeState by remember { 
                    val savedModeStr = com.novaedge.app.sdk.TerminalPrefs.getGlowMode(this@MainActivity)
                    val glowMode = try { com.novaedge.app.ui.theme.HolographicGlowMode.valueOf(savedModeStr) } catch(e: Exception) { com.novaedge.app.ui.theme.HolographicGlowMode.MEDIUM }
                    
                    val appPrefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
                    val symsStr = appPrefs.getString("allowed_symbols", "") ?: ""
                    val syms = if (symsStr.isNotBlank()) symsStr.split(",").filter { it.isNotBlank() } else emptyList()
                    val savedColorValue = com.novaedge.app.sdk.TerminalPrefs.getThemeColorValue(this@MainActivity)
                    val primaryColor = if (savedColorValue != null) androidx.compose.ui.graphics.Color(savedColorValue.toULong()) else com.novaedge.app.ui.theme.Crimson
                    
                    val savedSecBgValue = com.novaedge.app.sdk.TerminalPrefs.getSecondaryBgColor(this@MainActivity)
                    val secBgColor = if (savedSecBgValue != null) androidx.compose.ui.graphics.Color(savedSecBgValue.toULong()) else null
                    
                    val homeButtonScale = appPrefs.getFloat("home_button_scale", 1.0f)
                    val robotNameFontSize = appPrefs.getFloat("robot_name_font_size", 24f)
                    val savedFontColor = appPrefs.getLong("robot_name_font_color", -1L)
                    val fontColor = if (savedFontColor != -1L) androidx.compose.ui.graphics.Color(savedFontColor.toULong()) else androidx.compose.ui.graphics.Color.White

                    val shapeStr = com.novaedge.app.sdk.TerminalPrefs.getHomeButtonShape(this@MainActivity)
                    val homeShape = try { com.novaedge.app.ui.theme.HomeButtonShape.valueOf(shapeStr) } catch(e: Exception) { com.novaedge.app.ui.theme.HomeButtonShape.CIRCLE }

                    val isTradeCalculatorEnabled = com.novaedge.app.sdk.TerminalPrefs.getTradeCalculatorEnabled(this@MainActivity)

                    mutableStateOf(NovaEdgeThemeState(primaryColor = primaryColor, holographicGlowMode = glowMode, secondaryBackgroundColor = secBgColor, allowedSymbols = syms, homeButtonScale = homeButtonScale, robotNameFontSize = robotNameFontSize, robotNameFontColor = fontColor, homeButtonShape = homeShape, isTradeCalculatorEnabled = isTradeCalculatorEnabled)) 
                }

                // Global ExoPlayer saturation (Screen 5 toggle drives this)
                var videoSaturation by remember { mutableFloatStateOf(1f) }

                NovaEdgeTheme(themeState = themeState) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.novaedge.app.ui.theme.LocalNovaEdgeThemeUpdater provides { themeState = it }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(themeState.secondaryBackgroundColor ?: androidx.compose.ui.graphics.Color.Black)) {
                            // Layer 0: Full-screen looping video background
                            GlobalVideoBackground(
                                saturationMultiplier = videoSaturation,
                                videoUrl = themeState.promoVideoUrl
                            )

                            // Layer 1: Navigation graph over video
                            val navController = rememberNavController()
                            Box(modifier = Modifier.fillMaxSize()) {
                                   AppNavigation(navController = navController, isLoggedIn = isLoggedIn.value, mainViewModel = mainViewModel)
                                
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentRoute = navBackStackEntry?.destination?.route
                                TopNavMenuOverlay(navController = navController, currentRoute = currentRoute)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FATAL_CRASH", e.stackTraceToString())
        }
    }

    /**
     * Called when the app is already running (singleTask) and a deep link arrives.
     * Payfast redirects to metahost://payment/success or metahost://payment/cancel
     * after the user completes or cancels checkout in the browser.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri = intent.data ?: return
        val mainViewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]

        when (data.path) {
            "/success" -> {
                android.util.Log.i("Nova Edge", "[DeepLink] Payment success received: $data")
                // Retrieve the email saved when the user tapped Subscribe
                val prefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
                val savedEmail = prefs.getString("pending_payment_email", null)
                if (!savedEmail.isNullOrBlank()) {
                    mainViewModel.verifyPaymentStatus(savedEmail)
                } else {
                    android.util.Log.w("Nova Edge", "[DeepLink] No saved email found for payment verification.")
                }
            }
            "/cancel" -> {
                android.util.Log.i("Nova Edge", "[DeepLink] Payment cancelled by user.")
                mainViewModel.resetSubscriptionState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
        val script = prefs.getString("tts_script", null)
        if (!script.isNullOrBlank()) {
            tts?.speak(script, TextToSpeech.QUEUE_FLUSH, null, "tts_init")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
