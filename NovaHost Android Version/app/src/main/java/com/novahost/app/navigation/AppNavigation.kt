package com.novahost.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.novahost.app.ui.screens.*

object Routes {
    /** The reachability gate. Every cold start lands here. */
    const val SPLASH = "splash"

    /** The 7-step design flow. WELCOME is folded into it as step 01. */
    const val ONBOARDING = "onboarding"

    /**
     * Licence key entry and device binding.
     *
     * This used to be step 5 of the old 5-step OnboardingScreen, which made it
     * the only reachable place in the app where a key could be typed. The new
     * onboarding is the design's 7 steps and has no key field -- its step 07 is
     * the *binding*, not the entry -- so the activation step moved out to its
     * own route rather than being deleted along with the flow that carried it.
     */
    const val ACTIVATE = "activate"

    /**
     * The overlay and notification grants, asked for after activation.
     *
     * Its own route rather than a tail on [ACTIVATE]: the two are separate
     * gates that fail separately. A licence can bind on a device that then
     * refuses the overlay, and putting both in one screen made the second
     * failure look like the first one had not worked.
     */
    const val PERMISSIONS = "permissions"

    /**
     * Unreachable: the design's step 01 is the welcome. WelcomeScreen.kt is left
     * on disk and this constant left declared so nothing that still references
     * it fails to compile. Delete both once you have confirmed nothing links here.
     */
    @Deprecated("Folded into ONBOARDING step 01.")
    const val WELCOME = "welcome"

    const val AUTH = "auth"
    const val VAULT = "vault"
    const val HOME = "home"
    const val SETTINGS = "settings"

    /** The home interface picker: five layouts, art mode per layout. */
    const val INTERFACE = "interface"

    /**
     * Widget order and visibility for the active layout.
     *
     * Its own route rather than a section of [INTERFACE]: with all five layouts
     * reorderable, drag is the feature and it needs a full screen.
     */
    const val ARRANGE_WIDGETS = "arrange_widgets"

    /**
     * Quotes and symbols, and since the Markets screen was retired, the news,
     * hot-news calendar and session windows it used to carry.
     */
    const val PAIRS = "pairs"

    const val TERMINAL = "terminal"
    const val SCANNER = "scanner"
    const val HELP_SUPPORT = "help_support"
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    isLoggedIn: Boolean,
    hasCompletedOnboarding: Boolean,
    /**
     * True when an activated install still owes the permissions flow.
     *
     * Computed in MainActivity rather than read here so the check stays with
     * the other cold-start reads, and so this stays a pure routing function.
     */
    needsPermissions: Boolean,
    mainViewModel: com.novahost.app.ui.viewmodels.MainViewModel,
    /**
     * Activity-scoped on purpose. `viewModel()` inside a `composable {}` block
     * scopes to that NavBackStackEntry, so Home, Interface and Arrange Widgets
     * would each get their own instance: picking a layout would not reach Home,
     * and navigating away from Home and back would reset a running bot's UI
     * state. One instance, passed down.
     */
    homeViewModel: com.novahost.app.ui.viewmodels.HomeViewModel
) {
    // Where a resolved splash sends you. Onboarding first if it has never been
    // run; then the licence gate, which is what actually decides whether Home is
    // allowed. `isLoggedIn` is "a licence key is stored", so an un-activated
    // install lands on ACTIVATE rather than slipping into Home.
    fun afterSplash(): String = when {
        !hasCompletedOnboarding -> Routes.ONBOARDING
        // An activated install that still owes the overlay grant goes to the
        // permissions flow, not Home: the bubble is the product and it cannot
        // draw without it, so Home would come up looking broken.
        isLoggedIn && needsPermissions -> Routes.PERMISSIONS
        isLoggedIn -> Routes.HOME
        else -> Routes.ACTIVATE
    }

    NavHost(
        navController = navController,
        // Splash decides where to go; nothing else starts the app.
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onResolved = {
                    // The splash never hints which destination is coming -- the
                    // same 240ms exit for all three.
                    navController.navigate(afterSplash()) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                navController = navController,
                onFinished = {
                    // Splash -> onboarding -> licence, always. A stored key short-circuits
                    // at the splash only; once onboarding runs, it ends on the gate.
                    val next = Routes.ACTIVATE
                    navController.navigate(next) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ACTIVATE) { LicenseActivationScreen(navController) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(navController) }
        composable(Routes.AUTH) { AuthScreen(navController) }
        composable(Routes.VAULT) { VaultScreen(navController) }
        composable(Routes.HOME) { HomeScreen(navController, mainViewModel, homeViewModel) }
        // Settings takes the same activity-scoped view model as Interface and
        // Arrange Widgets: its Layout and Widgets rows report the live
        // arrangement, so a change made on either of those screens is already
        // on the row by the time the user is back here.
        composable(Routes.SETTINGS) { SettingsScreen(navController, homeViewModel) }
        composable(Routes.INTERFACE) { InterfaceScreen(navController, homeViewModel) }
        composable(Routes.ARRANGE_WIDGETS) { ArrangeWidgetsScreen(navController, homeViewModel) }
        composable(Routes.PAIRS) { PairManagementScreen(navController) }
        composable(Routes.TERMINAL) { MetaTraderConnectScreen(navController) }
        composable(Routes.SCANNER) { SymbolScannerScreen(navController, mainViewModel) }
        composable(Routes.HELP_SUPPORT) { HelpSupportScreen(navController) }
    }
}
