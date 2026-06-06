package com.wifiaudit.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wifiaudit.app.presentation.AuditCreationViewModel
import com.wifiaudit.app.presentation.screen.equipment.EquipmentPlacementScreen
import com.wifiaudit.app.presentation.screen.measure.MeasureScreen
import com.wifiaudit.app.presentation.screen.network.NetworkSelectionScreen
import com.wifiaudit.app.presentation.screen.plan.PlanCaptureScreen
import com.wifiaudit.app.presentation.screen.results.ResultsScreen

sealed class Screen(val route: String) {
    data object Plan      : Screen("plan")
    data object Equipment : Screen("equipment")
    data object Network   : Screen("network")
    data object Measure   : Screen("measure")
    data object Results   : Screen("results")
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    // ViewModel partagé entre tous les écrans — créé ici, même instance pour tout le stepper
    val auditCreationViewModel: AuditCreationViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Plan.route
    ) {
        composable(Screen.Plan.route) {
            PlanCaptureScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Equipment.route) }
            )
        }
        composable(Screen.Equipment.route) {
            EquipmentPlacementScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Network.route) }
            )
        }
        composable(Screen.Network.route) {
            NetworkSelectionScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Measure.route) }
            )
        }
        composable(Screen.Measure.route) {
            MeasureScreen(
                auditCreationViewModel = auditCreationViewModel,
                onNext = { navController.navigate(Screen.Results.route) }
            )
        }
        composable(Screen.Results.route) {
            ResultsScreen(
                onNewAudit = {
                    navController.popBackStack(Screen.Plan.route, inclusive = false)
                }
            )
        }
    }
}
