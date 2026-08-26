package com.novaedge.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novaedge.app.ui.screens.*

object Routes {
    const val WELCOME = "welcome"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val VAULT = "vault"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val PAIRS = "pairs"
    const val TERMINAL = "terminal"
    const val SCANNER = "scanner"
    const val MARKETS = "markets"
    const val HELP_SUPPORT = "help_support"
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController, isLoggedIn: Boolean, mainViewModel: com.novaedge.app.ui.viewmodels.MainViewModel) {
    val startDest = if (isLoggedIn) Routes.HOME else Routes.WELCOME

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(navController)
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(navController, mainViewModel)
        }
        composable(Routes.AUTH) {
            AuthScreen(navController)
        }
        composable(Routes.VAULT) {
            VaultScreen(navController)
        }
        composable(Routes.HOME) {
            HomeScreen(navController, mainViewModel)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController)
        }
        composable(Routes.PAIRS) {
            PairManagementScreen(navController)
        }
        composable(Routes.TERMINAL) {
            MetaTraderConnectScreen(navController)
        }
        composable(Routes.SCANNER) {
            SymbolScannerScreen(navController, mainViewModel)
        }
        composable(Routes.MARKETS) {
            MarketsScreen(navController, mainViewModel = mainViewModel)
        }
        composable(Routes.HELP_SUPPORT) {
            HelpSupportScreen(navController)
        }
    }
}
