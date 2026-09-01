package com.rahulgorai.remiit.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
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

/** Left-to-right, matching the bottom bar — the order the slide direction reads. */
private val tabRoutes = tabs.map { it.route }

/**
 * Pre-measure fallback for the bar's body, from the Material token. Replaced by
 * the real measurement on the first frame the bar is laid out; it only has to be
 * close enough that nothing jumps before then.
 */
private val NavigationBarBodyHeight = 64.dp

@Composable
fun RemiitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The bottom bar belongs to the three top-level screens only. Showing it on
    // the builder would compete with that screen's own save action.
    val showBottomBar = currentRoute in tabRoutes

    val density = LocalDensity.current
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barBody by remember { mutableStateOf(NavigationBarBodyHeight) }

    Surface(color = MaterialTheme.colorScheme.background) {
        // A Box, not a Scaffold with a bottomBar slot — and that is the whole
        // point of this layout.
        //
        // In the slot, the bar's height was part of the layout, so showing or
        // hiding it resized every screen. Whichever way that resize was
        // animated it went wrong: animating it re-measured the incoming screen
        // on every frame and dropped it downwards as the bar finished
        // collapsing, and not animating it made the drop instant instead of
        // gradual. Either way the content moved vertically during a transition
        // that is meant to be purely horizontal.
        //
        // As an overlay the bar has no say in anyone's layout. It slides over
        // the top, screens keep a fixed bottom inset whether it happens to be
        // on screen or not, and nothing re-measures during a transition.
        Box(Modifier.fillMaxSize()) {
            RemiitNavHost(navController, tabBottomInset = barBody)

            AnimatedVisibility(
                visible = showBottomBar,
                // Safe to slide again now that this costs nothing but an offset.
                enter = slideInVertically(Push.spec()) { it } + fadeIn(Push.spec()),
                exit = slideOutVertically(Push.spec()) { it } + fadeOut(Push.spec()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RemiitNavigationBar(
                    currentRoute = currentRoute,
                    onSelect = { route -> navController.switchTab(route, currentRoute) },
                    // Measured rather than assumed: the reserved space and the
                    // bar have to agree exactly, and the bar's real height
                    // depends on the theme's token and the device's gesture
                    // inset. The inset is subtracted because each screen's own
                    // Scaffold already accounts for it.
                    modifier = Modifier.onSizeChanged { size ->
                        val body = with(density) { size.height.toDp() } - systemBottomInset
                        if (body > 0.dp) barBody = body
                    },
                )
            }
        }
    }
}

@Composable
private fun RemiitNavigationBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
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

/**
 * Motion for the whole app: horizontal travel, and nothing else.
 *
 * Two shapes, both slides. Pushing the editor onto the stack brings it in from
 * the right over a list that drifts a third of the way left; moving between the
 * three tabs slides both screens the full width, in whichever direction the
 * bottom bar says those tabs sit relative to each other.
 *
 * This replaces a shared-element container transform that grew the tapped card
 * into the editor. That was the right idea and the wrong trade. It scaled and
 * re-laid-out an entire screen every frame, which dropped frames going in, and
 * it needed a second shared element for the title — which travelled on its own
 * path and tore the card into pieces on the way back.
 *
 * A horizontal translation is a matrix applied to an already-composed layer.
 * Nothing remeasures, nothing relayouts, there is exactly one moving part, and
 * it cannot come apart because there is nothing to come apart from. It is also
 * immediately legible: content arrives from the direction you will send it back.
 */
private object Push {

    /**
     * Ease-in-out: eases away from rest, covers the middle of the screen
     * quickly, then settles rather than stopping dead.
     *
     * The previous curve was an ease-*out* — it left at full speed and only
     * decelerated, which is why the push read as a snap rather than a slide.
     * Both ends are weighted here, so the motion has somewhere to accelerate
     * from and somewhere to arrive at.
     *
     * These are the two numbers to reach for if it still is not right:
     * pull the outer handles further apart (0.76 / 0.24) for a more pronounced
     * slow-fast-slow, or closer together (0.45 / 0.55) for something more even.
     */
    private val Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /**
     * Long enough for the eased ends to be felt. Under about 350ms an
     * ease-in-out has no room to express itself and collapses back into
     * looking linear, which defeats the point of the curve.
     */
    private const val DURATION_MILLIS = 420

    /**
     * How far the outgoing screen drifts when a screen is pushed on top of it.
     * iOS moves it about a third of the way rather than fully off, so the two
     * read as a stack with depth rather than a filmstrip — and a shorter
     * distance is less to composite.
     */
    private const val PARALLAX_DIVISOR = 3

    fun <T> spec() = tween<T>(durationMillis = DURATION_MILLIS, easing = Easing)

    /**
     * Which way the tabs are travelling, or null when this is not a tab change.
     *
     * The bottom bar is laid out left to right, so that order is the one the
     * user has in mind: picking Settings from Rules should move the world
     * leftwards, and coming back should move it right again. Deriving the sign
     * from the tab positions rather than from push/pop is what makes it
     * consistent — navigating between tabs pops as often as it pushes, so
     * push/pop says nothing about which way the user went.
     */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int? {
        val from = tabRoutes.indexOf(initialState.destination.route)
        val to = tabRoutes.indexOf(targetState.destination.route)
        if (from < 0 || to < 0 || from == to) return null
        return if (to > from) 1 else -1
    }

    /** A tab arriving comes from the side it sits on relative to the old one. */
    private fun arriving(direction: Int) =
        slideInHorizontally(spec()) { width -> if (direction > 0) width else -width }

    /** ...and the tab it replaces leaves the opposite way, in lockstep. */
    private fun leaving(direction: Int) =
        slideOutHorizontally(spec()) { width -> if (direction > 0) -width else width }

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>) = with(scope) {
        tabDirection()?.let(::arriving) ?: slideInHorizontally(spec()) { width -> width }
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>) = with(scope) {
        tabDirection()?.let(::leaving)
            ?: slideOutHorizontally(spec()) { width -> -width / PARALLAX_DIVISOR }
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>) = with(scope) {
        // A tab change is often a pop — switching tabs pops back to the start
        // destination to keep one entry per tab — so it takes the same
        // left-to-right treatment here rather than the stack's.
        tabDirection()?.let(::arriving)
            ?: slideInHorizontally(spec()) { width -> -width / PARALLAX_DIVISOR }
    }

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>) = with(scope) {
        tabDirection()?.let(::leaving) ?: slideOutHorizontally(spec()) { width -> width }
    }
}

@Composable
private fun RemiitNavHost(navController: NavHostController, tabBottomInset: Dp) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { Push.enter(this) },
        exitTransition = { Push.exit(this) },
        popEnterTransition = { Push.popEnter(this) },
        popExitTransition = { Push.popExit(this) },
    ) {
        composable(Routes.HOME) {
            TabScreen(tabBottomInset) {
                HomeScreen(
                    onAddRule = { navController.navigate(Routes.builder()) },
                    onEditRule = { ruleId, title ->
                        navController.navigate(Routes.builder(ruleId, title))
                    },
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                )
            }
        }

        composable(Routes.HISTORY) { TabScreen(tabBottomInset) { HistoryScreen() } }

        composable(Routes.SETTINGS) {
            TabScreen(tabBottomInset) {
                SettingsScreen(onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) })
            }
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
                },
                navArgument(Routes.BUILDER_ARG_TITLE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { entry ->
            RuleBuilderScreen(
                ruleId = entry.arguments?.getString(Routes.BUILDER_ARG_RULE_ID),
                initialTitle = entry.arguments?.getString(Routes.BUILDER_ARG_TITLE),
                onDone = { navController.popBackStack() },
            )
        }
    }
}

/**
 * A top-level screen, holding open the space the bottom bar occupies.
 *
 * The inset is constant — reserved whether the bar is currently on screen or
 * sliding away — so a tab screen is measured exactly once and never shifts
 * underneath a transition.
 */
@Composable
private fun TabScreen(bottomInset: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomInset)
    ) { content() }
}
