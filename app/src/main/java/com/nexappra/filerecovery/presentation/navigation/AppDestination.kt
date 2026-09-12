package com.nexappra.filerecovery.presentation.navigation

import com.nexappra.filerecovery.domain.model.PhotoScanMode
import com.nexappra.filerecovery.domain.model.RecoveryCategory

sealed class AppDestination(
    val route: String,
) {

    object Home : AppDestination("home")

    object Recovered : AppDestination("recovered")

    object Tools : AppDestination("tools")

    object Settings : AppDestination("settings")

    object Premium : AppDestination("premium")

    object Scan : AppDestination("scan")

    object FullDeviceScan : AppDestination("full-device-scan?category={category}") {

        const val ArgCategory = "category"

        fun createRoute(category: RecoveryCategory? = null): String {
            return if (category == null) {
                "full-device-scan"
            } else {
                "full-device-scan?category=${category.name}"
            }
        }
    }

    object PhotoScan : AppDestination("photo-scan/{mode}") {

        const val ArgMode = "mode"

        fun createRoute(
            mode: PhotoScanMode,
        ): String {
            return "photo-scan/${mode.name}"
        }
    }

    object PhotoResults : AppDestination("photo-results/{sessionId}") {

        const val ArgSessionId = "sessionId"

        fun createRoute(
            sessionId: String,
        ): String {
            return "photo-results/$sessionId"
        }
    }

    object FullRecoveryResults : AppDestination("full-recovery-results/{sessionId}") {

        const val ArgSessionId = "sessionId"

        fun createRoute(
            sessionId: String,
        ): String {
            return "full-recovery-results/$sessionId"
        }
    }

    object Category : AppDestination("category/{category}") {

        const val ArgCategory = "category"

        fun createRoute(
            category: RecoveryCategory,
        ): String {
            return "category/${category.name}"
        }
    }
}

val TopLevelRoutes = setOf(
    AppDestination.Home.route,
    AppDestination.Recovered.route,
    AppDestination.Tools.route,
    AppDestination.Settings.route,
)
