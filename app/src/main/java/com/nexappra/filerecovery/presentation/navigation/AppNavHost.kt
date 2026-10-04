package com.nexappra.filerecovery.presentation.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nexappra.filerecovery.core.ui.components.*
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.domain.model.RecoveryScanMode
import com.nexappra.filerecovery.presentation.home.HomeRoute
import com.nexappra.filerecovery.presentation.premium.PremiumRoute
import com.nexappra.filerecovery.presentation.recovered.RecoveredScreen
import com.nexappra.filerecovery.presentation.scan.*
import com.nexappra.filerecovery.presentation.settings.SettingsRoute
import com.nexappra.filerecovery.presentation.tools.ToolsScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val premium: () -> Unit = { nav.navigate(AppDestination.Premium.route) { launchSingleTop = true } }
    val deep: (RecoveryCategory?) -> Unit = { category -> nav.navigate(AppDestination.FullDeviceScan.createRoute(category, RecoveryScanMode.Deep)) }
    val back: () -> Unit = { nav.popBackStack() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (route in TopLevelRoutes) RecoveryBottomNavigationBar(
                items = listOf(
                    BottomBarItem(AppDestination.Home.route, "Home", Icons.Rounded.Home),
                    BottomBarItem(AppDestination.Recovered.route, "Saved", Icons.Rounded.History),
                    BottomBarItem(AppDestination.Tools.route, "Tools", Icons.Rounded.Build),
                    BottomBarItem(AppDestination.Settings.route, "Settings", Icons.Rounded.Settings)),
                currentRoute = route, onNavigate = nav::navigateToTopLevel)
        },
    ) { padding ->
        NavHost(nav, AppDestination.Home.route, Modifier.fillMaxSize().padding(padding)) {
            composable(AppDestination.Home.route) {
                HomeRoute(
                    onCategoryClick = { nav.navigate(AppDestination.FullDeviceScan.createRoute(it)) },
                    onScanClick = { nav.navigate(AppDestination.FullDeviceScan.createRoute()) },
                    onSettingsClick = { nav.navigateToTopLevel(AppDestination.Settings.route) },
                    onPremiumClick = premium,
                    onDeepScan = { deep(null) })
            }
            composable(AppDestination.Recovered.route) { RecoveredScreen(onScan = { nav.navigate(AppDestination.FullDeviceScan.createRoute()) }) }
            composable(AppDestination.Tools.route) { ToolsScreen(onDeepScan = { deep(null) }, onScan = { nav.navigate(AppDestination.FullDeviceScan.createRoute()) }) }
            composable(AppDestination.Settings.route) { SettingsRoute(onOpenPremium = premium) }
            composable(AppDestination.Premium.route) { PremiumRoute(onClose = back) }
            composable(AppDestination.FullDeviceScan.route, arguments = listOf(
                navArgument(AppDestination.FullDeviceScan.ArgCategory) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(AppDestination.FullDeviceScan.ArgMode) { type = NavType.StringType; defaultValue = RecoveryScanMode.Quick.name },
            )) {
                FullDeviceScanRoute(onBack = back, onOpenPremium = premium, onNavigateToResults = { id ->
                    nav.navigate(AppDestination.FullRecoveryResults.createRoute(id)) {
                        popUpTo(AppDestination.FullDeviceScan.route) { inclusive = true }
                    }
                })
            }
            composable(AppDestination.FullRecoveryResults.route, arguments = listOf(
                navArgument(AppDestination.FullRecoveryResults.ArgSessionId) { type = NavType.StringType },
            )) {
                FullRecoveryResultsRoute(onBack = back, onOpenPremium = premium, onDeepScan = deep)
            }
        }
    }
}
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
