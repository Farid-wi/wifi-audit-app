package com.wifiaudit.app.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wifiaudit.app.presentation.AuditCreationViewModel
import com.wifiaudit.app.presentation.screen.equipment.EquipmentPlacementScreen
import com.wifiaudit.app.presentation.screen.measure.MeasureScreen
import com.wifiaudit.app.presentation.screen.measure.ScanModeScreen
import com.wifiaudit.app.presentation.screen.network.NetworkSelectionScreen
import com.wifiaudit.app.presentation.screen.plan.PlanCaptureScreen
import com.wifiaudit.app.presentation.screen.results.ResultsScreen

sealed class Screen(val route: String) {
    data object Home      : Screen("home")
    data object Plan      : Screen("plan")
    data object Equipment : Screen("equipment")
    data object Network   : Screen("network")
    data object ScanMode  : Screen("scan_mode")
    data object Measure   : Screen("measure")
    data object Results   : Screen("results")
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    // ViewModel partagé entre tous les écrans — créé ici, même instance pour tout le stepper
    val auditCreationViewModel: AuditCreationViewModel = hiltViewModel()

    NavHost(
        navController    = navController,
        startDestination = Screen.Plan.route,
        enterTransition  = { slideInHorizontally(tween(500)) { it }  + fadeIn(tween(500))  },
        exitTransition   = { slideOutHorizontally(tween(350)) { -it } + fadeOut(tween(350)) },
        popEnterTransition  = { slideInHorizontally(tween(500)) { -it } + fadeIn(tween(500))  },
        popExitTransition   = { slideOutHorizontally(tween(350)) { it }  + fadeOut(tween(350)) }
    ) {
        composable(Screen.Plan.route) {
            PlanCaptureScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Equipment.route) },
                onOpenAudit = { auditId ->
                    navController.navigate("${Screen.Results.route}?auditId=$auditId")
                }
            )
        }
        composable(Screen.Equipment.route) {
            EquipmentPlacementScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Network.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Network.route) {
            NetworkSelectionScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.ScanMode.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ScanMode.route) {
            ScanModeScreen(
                onNext = { navController.navigate(Screen.Measure.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Measure.route) {
            MeasureScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Results.route) },
                onBack = { navController.popBackStack() }
            )
        }
        // Results: optional auditId — null = latest (new audit), non-null = past audit from Home
        composable(
            route     = "${Screen.Results.route}?auditId={auditId}",
            arguments = listOf(navArgument("auditId") { nullable = true; defaultValue = null; type = NavType.StringType })
        ) { entry ->
            val auditId = entry.arguments?.getString("auditId")
            ResultsScreen(
                onNewAudit = {
                    auditCreationViewModel.reset()
                    navController.navigate(Screen.Plan.route) {
                        popUpTo(Screen.Plan.route)
                    }
                },
                onBack = auditId?.let { { navController.popBackStack() } }
            )
        }
    }
}
