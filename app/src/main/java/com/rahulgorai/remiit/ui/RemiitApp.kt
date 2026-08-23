package com.rahulgorai.remiit.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rahulgorai.remiit.ui.builder.RuleBuilderScreen
import com.rahulgorai.remiit.ui.history.HistoryScreen
import com.rahulgorai.remiit.ui.home.HomeScreen
import com.rahulgorai.remiit.ui.permissions.PermissionsScreen
import com.rahulgorai.remiit.ui.settings.SettingsScreen

private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab(Routes.HOME, "Rules", Icons.Outlined.Alarm),
    TopLevelTab(Routes.HISTORY, "History", Icons.Filled.History),
    TopLevelTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun RemiitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The bottom bar belongs to the three top-level screens only. Showing it on
    // the builder would compete with that screen's own save action.
    val showBottomBar = currentRoute in tabs.map { it.route }

    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            // Single instance per tab, and pop back
                                            // to the start so the back button leaves
                                            // the app rather than walking the tabs.
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                RemiitNavHost(navController)
            }
        }
    }
}

@Composable
private fun RemiitNavHost(navController: androidx.navigation.NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Horizontal slide paired with a fade. Tween rather than the theme's
        // spring here because a NavHost transition has to have a bounded
        // duration — a spring can overshoot past the point the destination is
        // considered settled.
        enterTransition = {
            slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(240))
        },
        exitTransition = {
            slideOutHorizontally(tween(320)) { -it / 6 } + fadeOut(tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(tween(320)) { -it / 6 } + fadeIn(tween(240))
        },
        popExitTransition = {
            slideOutHorizontally(tween(320)) { it / 6 } + fadeOut(tween(180))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddRule = { navController.navigate(Routes.builder()) },
                onEditRule = { ruleId -> navController.navigate(Routes.builder(ruleId)) },
                onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
            )
        }

        composable(Routes.HISTORY) { HistoryScreen() }

        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.BUILDER_ROUTE,
            arguments = listOf(
                navArgument(Routes.BUILDER_ARG_RULE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { entry ->
            RuleBuilderScreen(
                ruleId = entry.arguments?.getString(Routes.BUILDER_ARG_RULE_ID),
                onDone = { navController.popBackStack() },
            )
        }
    }
}
