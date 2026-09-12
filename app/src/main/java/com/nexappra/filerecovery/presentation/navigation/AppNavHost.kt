package com.nexappra.filerecovery.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.util.Log
import com.nexappra.filerecovery.R
import com.nexappra.filerecovery.core.utils.FILE_RECOVERY_DEBUG_TAG
import com.nexappra.filerecovery.core.ui.components.BottomBarItem
import com.nexappra.filerecovery.core.ui.components.RecoveryBottomNavigationBar
import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.RecoveryCategory
import com.nexappra.filerecovery.presentation.home.CategoryPlaceholderScreen
import com.nexappra.filerecovery.presentation.home.HomeRoute
import com.nexappra.filerecovery.presentation.premium.PremiumPlan
import com.nexappra.filerecovery.presentation.premium.PremiumRoute
import com.nexappra.filerecovery.presentation.recovered.RecoveredScreen
import com.nexappra.filerecovery.presentation.scan.FullDeviceScanRoute
import com.nexappra.filerecovery.presentation.scan.FullRecoveryResultsRoute
import com.nexappra.filerecovery.presentation.scan.PhotoRecoveryScreen
import com.nexappra.filerecovery.presentation.scan.PhotoResultsRoute
import com.nexappra.filerecovery.presentation.scan.PhotoScanRoute
import com.nexappra.filerecovery.presentation.settings.SettingsRoute
import com.nexappra.filerecovery.presentation.tools.ToolsScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = navBackStackEntry
        ?.destination
        ?.route

    val showBottomBar = currentRoute in TopLevelRoutes

    val bottomItems = listOf(
        BottomBarItem(
            route = AppDestination.Home.route,
            label = stringResource(R.string.nav_home),
            icon = Icons.Rounded.Home,
        ),
        BottomBarItem(
            route = AppDestination.Recovered.route,
            label = stringResource(R.string.nav_recovered),
            icon = Icons.Rounded.History,
        ),
        BottomBarItem(
            route = AppDestination.Tools.route,
            label = stringResource(R.string.nav_tools),
            icon = Icons.Rounded.Build,
        ),
        BottomBarItem(
            route = AppDestination.Settings.route,
            label = stringResource(R.string.nav_settings),
            icon = Icons.Rounded.Settings,
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal +
                    WindowInsetsSides.Top +
                    WindowInsetsSides.Bottom,
        ),
        bottomBar = {
            if (showBottomBar) {
                RecoveryBottomNavigationBar(
                    items = bottomItems,
                    currentRoute = currentRoute,
                    onNavigate = navController::navigateToTopLevel,
                )
            }
        },
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {

            /*
             * HOME
             */
            composable(AppDestination.Home.route) {

                HomeRoute(
                    onCategoryClick = { category ->
                        navController.navigate(AppDestination.FullDeviceScan.createRoute(category))
                    },
                    onScanClick = {
                        Log.d(
                            FILE_RECOVERY_DEBUG_TAG,
                            "SCAN DEVICE CLICKED",
                        )
                        navController.navigate(AppDestination.FullDeviceScan.createRoute())
                    },
                    onSettingsClick = {
                        navController.navigateToTopLevel(
                            AppDestination.Settings.route
                        )
                    },
                    onPremiumClick = {
                        navController.navigate(
                            AppDestination.Premium.route
                        )
                    },
                )
            }

            /*
             * RECOVERED
             */
            composable(AppDestination.Recovered.route) {

                RecoveredScreen()
            }

            /*
             * TOOLS
             */
            composable(AppDestination.Tools.route) {

                ToolsScreen()
            }

            /*
             * SETTINGS
             */
            composable(AppDestination.Settings.route) {

                SettingsRoute(
                    onOpenPremium = {
                        navController.navigate(
                            AppDestination.Premium.route
                        )
                    },
                )
            }

            /*
             * PREMIUM
             */
            composable(AppDestination.Premium.route) {

                PremiumRoute(
                    onClose = {
                        navController.popBackStack()
                    },
                    onContinueWithFree = {
                        navController.popBackStack()
                    },
                    onContinueWithPremium = { selectedPlan ->

                        when (selectedPlan) {

                            PremiumPlan.MONTHLY -> {
                                /*
                                 * Google Play Billing
                                 * monthly subscription
                                 * will be launched here.
                                 */
                            }

                            PremiumPlan.YEARLY -> {
                                /*
                                 * Google Play Billing
                                 * yearly subscription
                                 * will be launched here.
                                 */
                            }
                        }
                    },
                )
            }

            /*
             * FULL DEVICE SCAN
             */
            composable(
                route = AppDestination.FullDeviceScan.route,
                arguments = listOf(
                    navArgument(AppDestination.FullDeviceScan.ArgCategory) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {

                FullDeviceScanRoute(
                    onBack = {
                        navController.popBackStack()
                    },
                    onNavigateToResults = { sessionId ->
                        Log.d(
                            FILE_RECOVERY_DEBUG_TAG,
                            "Navigating using session $sessionId",
                        )
                        navController.navigate(
                            AppDestination.FullRecoveryResults.createRoute(
                                sessionId
                            )
                        ) {

                            popUpTo(
                                AppDestination.FullDeviceScan.route
                            ) {
                                inclusive = true
                            }
                        }
                    },
                )
            }

            /*
             * PHOTO RECOVERY ENTRY
             */
            composable(AppDestination.Scan.route) {

                PhotoRecoveryScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onStartQuickScan = {

                        navController.navigate(
                            AppDestination.PhotoScan.createRoute(
                                PhotoScanMode.Quick
                            )
                        )
                    },
                    onStartDeepScan = {

                        navController.navigate(
                            AppDestination.PhotoScan.createRoute(
                                PhotoScanMode.Deep
                            )
                        )
                    },
                )
            }

            /*
             * PHOTO SCANNING
             */
            composable(
                route = AppDestination.PhotoScan.route,
                arguments = listOf(
                    navArgument(
                        AppDestination.PhotoScan.ArgMode
                    ) {
                        type = NavType.StringType
                    },
                ),
            ) {

                PhotoScanRoute(
                    onBack = {
                        navController.popBackStack()
                    },
                    onNavigateToResults = { sessionId ->

                        navController.navigate(
                            AppDestination.PhotoResults.createRoute(
                                sessionId
                            )
                        ) {

                            popUpTo(
                                AppDestination.PhotoScan.route
                            ) {
                                inclusive = true
                            }
                        }
                    },
                )
            }

            /*
             * PHOTO RESULTS
             */
            composable(
                route = AppDestination.PhotoResults.route,
                arguments = listOf(
                    navArgument(
                        AppDestination.PhotoResults.ArgSessionId
                    ) {
                        type = NavType.StringType
                    },
                ),
            ) {

                PhotoResultsRoute(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            /*
             * FULL RECOVERY RESULTS
             */
            composable(
                route = AppDestination.FullRecoveryResults.route,
                arguments = listOf(
                    navArgument(
                        AppDestination.FullRecoveryResults.ArgSessionId
                    ) {
                        type = NavType.StringType
                    },
                ),
            ) {

                FullRecoveryResultsRoute(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            /*
             * CATEGORY DETAIL
             */
            composable(
                route = AppDestination.Category.route,
                arguments = listOf(
                    navArgument(
                        AppDestination.Category.ArgCategory
                    ) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->

                val category = backStackEntry.arguments
                    ?.getString(
                        AppDestination.Category.ArgCategory
                    )
                    ?.let { categoryName ->

                        RecoveryCategory.entries.firstOrNull {
                            it.name == categoryName
                        }
                    }
                    ?: RecoveryCategory.Photos

                CategoryPlaceholderScreen(
                    category = category,
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(
    route: String,
) {
    navigate(route) {

        popUpTo(
            graph.findStartDestination().id
        ) {
            saveState = true
        }

        launchSingleTop = true
        restoreState = true
    }
}
