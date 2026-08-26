package com.novahost.app

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
import com.novahost.app.navigation.AppNavigation
import com.novahost.app.service.NanoBananaService
import com.novahost.app.ui.components.TopNavMenuOverlay
import com.novahost.app.ui.components.GlobalVideoBackground
import com.novahost.app.ui.theme.NovaHostTheme
import com.novahost.app.ui.theme.NovaHostThemeState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.novahost.app.ui.viewmodels.MainViewModel

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

            // The floating Pulse bubble is the ONLY thing here that needs
            // SYSTEM_ALERT_WINDOW: NovaHostPulseService adds a TYPE_APPLICATION_OVERLAY
            // view straight to the WindowManager, which throws BadTokenException
            // without the grant. So the permission gates the service, not the app.
            //
            // It used to gate the app: a missing grant redirected to a permission
            // screen and finish()ed this activity, and nothing ever brought the user
            // back -- which is the blank screen. Launch is unconditional now, and the
            // request moves to its own screen after licence activation, where there is
            // a reason to ask for it.
            if (android.provider.Settings.canDrawOverlays(this)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, com.novahost.app.service.NovaHostPulseService::class.java))
                } else {
                    startService(Intent(this, com.novahost.app.service.NovaHostPulseService::class.java))
                }
            }

            // Check if there's a saved license key to bypass the gate
            val prefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
            val hasLicense = !prefs.getString("license_key", null).isNullOrBlank()
            val hasOnboarded = prefs.getBoolean("onboarding_complete", false)

            // Two conditions, both required. The flag alone would drag every
            // existing install that already granted the overlay back through a
            // flow it has nothing left to do in; the grant alone would keep
            // sending back anyone who deliberately turned the bubble off.
            val needsPermissions =
                !com.novahost.app.ui.screens.hasSeenPermissions(this) &&
                    !android.provider.Settings.canDrawOverlays(this)

            val mainViewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
            // Activity-scoped so Home, Interface and Arrange Widgets share one
            // instance -- and so a running bot survives leaving Home.
            val homeViewModel = androidx.lifecycle.ViewModelProvider(this)[com.novahost.app.ui.viewmodels.HomeViewModel::class.java]

            // Handle deep link if app was launched fresh from the browser
            if (intent != null && intent.data != null) {
                handleDeepLink(intent)
            }

            setContent {
                // Use actual saved state instead of hardcoded false
                val isLoggedIn = remember { mutableStateOf(hasLicense) }

                // How the app looks. Every value comes from NovaPrefs, which is
                // the one home for appearance now -- these used to be read from
                // the encrypted terminal store and from raw metahost_prefs in
                // the same block.
                var themeState by remember {
                    val ctx = this@MainActivity
                    val glowMode = try {
                        com.novahost.app.ui.theme.HolographicGlowMode.valueOf(com.novahost.app.sdk.NovaPrefs.getGlowMode(ctx))
                    } catch (e: Exception) {
                        com.novahost.app.ui.theme.HolographicGlowMode.MEDIUM
                    }
                    val novaGlow = try {
                        com.novahost.app.ui.theme.NovaGlow.valueOf(com.novahost.app.sdk.NovaPrefs.getNovaGlow(ctx))
                    } catch (e: Exception) {
                        com.novahost.app.ui.theme.NovaGlow.Default
                    }

                    // The active robot's accent is the accent. It is written by
                    // licence activation and the robot picker, but was never read
                    // back -- the theme used a manual colour picker from Settings
                    // instead, so a mentor's branding never reached the screen.
                    // Falls back to SoftLightBlue when the robot defines none.
                    val robotColor = com.novahost.app.ui.theme.robotAccent(ctx)

                    // An accent picked in Settings outranks the robot's, and it
                    // has to be read here or it does not survive the process.
                    // Settings wrote it and nothing read it: every restart threw
                    // the choice away, which is why that screen needed a SAVE
                    // THEME button that could not actually help.
                    val isGloss = com.novahost.app.sdk.NovaPrefs.getGlossTheme(ctx)
                    val primaryColor = com.novahost.app.sdk.NovaPrefs.getAccentColor(ctx)
                        ?.let { androidx.compose.ui.graphics.Color(it.toULong()) }
                        ?: robotColor
                    val secondaryColor = com.novahost.app.sdk.NovaPrefs.getSecondaryAccent(ctx)
                        ?.let { androidx.compose.ui.graphics.Color(it.toULong()) }
                        ?: com.novahost.app.ui.theme.Cyan

                    val secBgColor = com.novahost.app.sdk.NovaPrefs.getSecondaryBgColor(ctx)
                        ?.let { androidx.compose.ui.graphics.Color(it.toULong()) }
                    val fontColor = com.novahost.app.sdk.NovaPrefs.getRobotNameFontColor(ctx)
                        ?.let { androidx.compose.ui.graphics.Color(it.toULong()) }
                        ?: androidx.compose.ui.graphics.Color.White

                    val homeShape = try {
                        com.novahost.app.ui.theme.HomeButtonShape.valueOf(com.novahost.app.sdk.NovaPrefs.getHomeButtonShape(ctx))
                    } catch (e: Exception) {
                        com.novahost.app.ui.theme.HomeButtonShape.CIRCLE
                    }
                    val fontStyle = try {
                        com.novahost.app.ui.theme.RobotFontStyle.valueOf(com.novahost.app.sdk.NovaPrefs.getRobotFontStyle(ctx))
                    } catch (e: Exception) {
                        com.novahost.app.ui.theme.RobotFontStyle.MONTSERRAT_GEOMETRIC
                    }

                    mutableStateOf(
                        NovaHostThemeState(
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            isGlossTheme = isGloss,
                            glow = novaGlow,
                            holographicGlowMode = glowMode,
                            secondaryBackgroundColor = secBgColor,
                            useLightScheme = com.novahost.app.sdk.NovaPrefs.getUseLightScheme(ctx),
                            immersiveMode = com.novahost.app.sdk.NovaPrefs.getImmersiveMode(ctx),
                            robotFontStyle = fontStyle,
                            homeButtonScale = com.novahost.app.sdk.NovaPrefs.getHomeButtonScale(ctx),
                            robotNameFontSize = com.novahost.app.sdk.NovaPrefs.getRobotNameFontSize(ctx),
                            robotNameFontColor = fontColor,
                            homeButtonShape = homeShape,
                        )
                    )
                }

                // Who the robot is. Separate from the theme so that switching
                // robots re-brands the app without resetting the user's layout,
                // art mode or widget order.
                var robotBranding by remember {
                    val appPrefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
                    val symsStr = appPrefs.getString("allowed_symbols", "") ?: ""
                    mutableStateOf(
                        com.novahost.app.ui.theme.RobotBranding(
                            name = appPrefs.getString("display_name", "") ?: "",
                            avatarUrl = appPrefs.getString("avatar_url", null),
                            backgroundImageUrl = appPrefs.getString("background_video_url", null),
                            mentorName = appPrefs.getString("admin_display_name", "")
                                ?.takeUnless { it == "SYSTEM_ADMIN" } ?: "",
                            allowedSymbols = symsStr.split(",").filter { it.isNotBlank() }
                        )
                    )
                }

                // Global ExoPlayer saturation (Screen 5 toggle drives this)
                var videoSaturation by remember { mutableFloatStateOf(1f) }

                NovaHostTheme(themeState = themeState, robotBranding = robotBranding) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.novahost.app.ui.theme.LocalNovaHostThemeUpdater provides { themeState = it },
                        com.novahost.app.ui.theme.LocalRobotBrandingUpdater provides { robotBranding = it }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(themeState.secondaryBackgroundColor ?: androidx.compose.ui.graphics.Color.Black)) {
                            // Layer 0: Full-screen looping video background
                            GlobalVideoBackground(
                                saturationMultiplier = videoSaturation,
                                videoUrl = robotBranding.promoVideoUrl
                            )

                            // Layer 1: Navigation graph over video
                            val navController = rememberNavController()
                            Box(modifier = Modifier.fillMaxSize()) {
                                   AppNavigation(
                                       navController = navController,
                                       isLoggedIn = isLoggedIn.value,
                                       hasCompletedOnboarding = hasOnboarded,
                                       needsPermissions = needsPermissions,
                                       mainViewModel = mainViewModel,
                                       homeViewModel = homeViewModel
                                   )
                                
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
                android.util.Log.i("NovaHost", "[DeepLink] Payment success received: $data")
                // Retrieve the email saved when the user tapped Subscribe
                val prefs = getSharedPreferences("metahost_prefs", MODE_PRIVATE)
                val savedEmail = prefs.getString("pending_payment_email", null)
                if (!savedEmail.isNullOrBlank()) {
                    mainViewModel.verifyPaymentStatus(savedEmail)
                } else {
                    android.util.Log.w("NovaHost", "[DeepLink] No saved email found for payment verification.")
                }
            }
            "/cancel" -> {
                android.util.Log.i("NovaHost", "[DeepLink] Payment cancelled by user.")
                mainViewModel.resetAppGate()
                mainViewModel.resetScannerGate()
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
