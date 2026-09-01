package com.rahulgorai.remiit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
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
import com.rahulgorai.remiit.ui.theme.RemiitBorders

private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TopLevelTab(Routes.HOME, "Rules", Icons.Outlined.Alarm),
    TopLevelTab(Routes.HISTORY, "History", Icons.Outlined.History),
    TopLevelTab(Routes.SETTINGS, "Settings", Icons.Outlined.Tune),
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
        // Wraps the whole graph: a shared element can only travel between two
        // destinations that sit inside the same SharedTransitionLayout, so this
        // has to be outside the NavHost rather than inside any one screen.
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Scaffold(
                    bottomBar = {
                        // Animated rather than conditionally composed, so leaving a
                        // top-level screen slides the bar out instead of the content
                        // jumping down by its height.
                        AnimatedVisibility(
                            visible = showBottomBar,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            RemiitNavigationBar(
                                currentRoute = currentRoute,
                                onSelect = { route -> navController.switchTab(route, currentRoute) },
                            )
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
    }
}

@Composable
private fun RemiitNavigationBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Box {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        // A filled pill behind the selected icon, so the current
                        // tab is legible without relying on the icon's tint.
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
        // The bar is a distinct surface from the content above it and says so,
        // rather than relying on a one-step tonal difference.
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .height(RemiitBorders.CONTAINER_WIDTH),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** Single instance per tab, with back leaving the app rather than walking the tabs. */
private fun NavHostController.switchTab(route: String, currentRoute: String?) {
    if (route == currentRoute) return
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RemiitNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Fade-through with a hair of scale, and nothing sliding.
        //
        // The old horizontal slide competed with the shared elements: the card
        // travelling into place was moving one way while the screen behind it
        // moved another, and the eye could not decide which to follow. Keeping
        // the backdrop still lets the connected element carry the whole
        // transition. Tween rather than a spring because a NavHost transition
        // needs a bounded duration to settle.
        enterTransition = { fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(320), 0.97f) },
        exitTransition = { fadeOut(tween(140)) + scaleOut(tween(320), 1.01f) },
        popEnterTransition = { fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(320), 1.01f) },
        popExitTransition = { fadeOut(tween(140)) + scaleOut(tween(320), 0.97f) },
    ) {
        composable(Routes.HOME) {
            WithNavAnimation {
                HomeScreen(
                    onAddRule = { navController.navigate(Routes.builder()) },
                    onEditRule = { ruleId -> navController.navigate(Routes.builder(ruleId)) },
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                )
            }
        }

        composable(Routes.HISTORY) { WithNavAnimation { HistoryScreen() } }

        composable(Routes.SETTINGS) {
            WithNavAnimation {
                SettingsScreen(onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
            }
        }

        composable(Routes.PERMISSIONS) {
            WithNavAnimation {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.BUILDER_ROUTE,
            // No transition of its own. The shared container is already
            // animating this destination's bounds from the card (or the "new
            // rule" button) it grew out of; layering a second fade-and-scale on
            // top makes two motions fight over the same pixels, which is what
            // reads as cheap. The shared element owns this transition outright.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            arguments = listOf(
                navArgument(Routes.BUILDER_ARG_RULE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { entry ->
            WithNavAnimation {
                RuleBuilderScreen(
                    ruleId = entry.arguments?.getString(Routes.BUILDER_ARG_RULE_ID),
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Publishes the destination's own animation scope.
 *
 * Every `composable` block runs inside an [androidx.compose.animation.AnimatedContentScope];
 * a shared element needs it to know whether it is arriving or leaving. Handing
 * it down through a CompositionLocal keeps it out of every screen's signature.
 */
@Composable
private fun androidx.compose.animation.AnimatedContentScope.WithNavAnimation(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNavAnimatedScope provides this, content = content)
}
