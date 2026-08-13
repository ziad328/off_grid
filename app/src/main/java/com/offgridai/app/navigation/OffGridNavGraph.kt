package com.offgridai.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.offgridai.app.ui.screens.*

// ══════════════════════════════════════════════════════
//  OffGrid AI — Navigation Graph
// ══════════════════════════════════════════════════════

@Composable
fun OffGridNavGraph(navController: NavHostController) {

    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route,
        enterTransition  = { fadeIn(animationSpec = tween(300)) },
        exitTransition   = { fadeOut(animationSpec = tween(200)) }
    ) {

        // ── Splash Screen ───────────────────────────────
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Home Screen ─────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProcessing = { uri, feature ->
                    navController.navigate(Screen.Processing.createRoute(uri, feature))
                },
                onNavigateToBrushCanvas = { uri ->
                    navController.navigate(Screen.BrushCanvas.createRoute(uri))
                },
                onNavigateToPrivacy = {
                    navController.navigate(Screen.Privacy.route)
                },
                onNavigateToModelSetup = {
                    navController.navigate(Screen.ModelSetup.route)
                }
            )
        }

        // ── Brush Canvas Screen (Magic Eraser Selection) ──
        composable(
            route = Screen.BrushCanvas.route,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri") ?: ""
            BrushCanvasScreen(
                imageUri = imageUri,
                onConfirmMask = {
                    navController.navigate(Screen.Processing.createRoute(imageUri, AIFeature.PERSON_REMOVAL.name))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Processing Screen ───────────────────────────
        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("mediaUri")     { type = NavType.StringType },
                navArgument("featureType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediaUri    = backStackEntry.arguments?.getString("mediaUri") ?: ""
            val featureType = backStackEntry.arguments?.getString("featureType") ?: ""
            ProcessingScreen(
                mediaUri    = mediaUri,
                featureType = featureType,
                onDone = { resultUri ->
                    navController.navigate(Screen.Preview.createRoute(resultUri)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Preview Screen ──────────────────────────────
        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument("resultUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val resultUri = backStackEntry.arguments?.getString("resultUri") ?: ""
            PreviewScreen(
                resultUri = resultUri,
                onBack    = { navController.popBackStack() },
                onHome    = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Privacy Policy Screen ───────────────────────
        composable(Screen.Privacy.route) {
            PrivacyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Model Setup Screen ──────────────────────────
        composable(Screen.ModelSetup.route) {
            ModelSetupScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
