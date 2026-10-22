package dev.netvalve.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.netvalve.ui.appdetail.AppDetailScreen
import dev.netvalve.ui.apps.AppsScreen
import dev.netvalve.ui.dashboard.DashboardScreen
import dev.netvalve.ui.home.HomeScreen
import dev.netvalve.ui.logs.LogsScreen
import dev.netvalve.ui.priority.PriorityScreen
import dev.netvalve.ui.sim.SimulatorScreen
import dev.netvalve.ui.demo.DemoScreen
import dev.netvalve.ui.stats.StatsScreen
import dev.netvalve.ui.stats.TechnicalStatsScreen
import dev.netvalve.ui.tools.ToolsScreen

/**
 * Activity-provided callbacks that require an Activity/Context (VPN consent,
 * settings intents, share sheet). Passed down so ViewModels/screens stay pure.
 */
data class AppActions(
    val toggleVpn: (Boolean) -> Unit,
    val restartTunnel: () -> Unit,
    val requestUsageAccess: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    val openVendorSettings: () -> Unit,
    val exportLogs: (String) -> Unit,
)

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Destination("dashboard", "Home", Icons.Filled.Shield)
    data object Apps : Destination("apps", "Apps", Icons.Filled.Apps)
    data object Stats : Destination("stats", "Stats", Icons.Filled.QueryStats)
    data object Simulator : Destination("sim", "Load", Icons.Filled.Speed)
    data object Logs : Destination("logs", "Logs", Icons.Filled.Article)

    // Test tools (hidden: opened by seven taps on the small "NetValve" text on the Home screen).
    data object Tools : Destination("tools", "Test tools", Icons.Filled.Speed)
    data object AdvancedHome : Destination("tools/home", "Advanced home", Icons.Filled.Shield)
    data object AdvancedApps : Destination("tools/apps", "Technical apps", Icons.Filled.Apps)
    data object TechnicalStats : Destination("tools/stats", "Technical statistics", Icons.Filled.QueryStats)
    data object Demo : Destination("demo", "Live demo", Icons.Filled.QueryStats)

    companion object {
        /** Production stage: three tabs. Simulator and Logs are reached from Test tools only. */
        val bottomBar = listOf(Dashboard, Apps, Stats)
        fun appDetail(pkg: String) = "appdetail/$pkg"
        const val APP_DETAIL_ROUTE = "appdetail/{package}"
    }
}

/** Bottom-bar tab switch. Dashboard is the start destination, so pop back to it. */
private fun NavHostController.switchTab(dest: Destination) {
    if (dest == Destination.Dashboard) {
        if (!popBackStack(Destination.Dashboard.route, inclusive = false)) {
            navigate(Destination.Dashboard.route) { launchSingleTop = true }
        }
    } else {
        navigate(dest.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun NetValveNavGraph(actions: AppActions) {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            NavigationBar {
                Destination.bottomBar.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { nav.switchTab(dest) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Dashboard.route) {
                HomeScreen(
                    actions = actions,
                    onOpenTools = { nav.navigate(Destination.Tools.route) },
                )
            }
            composable(Destination.Apps.route) { PriorityScreen() }
            composable(Destination.Tools.route) {
                ToolsScreen(
                    onOpenAdvancedHome = { nav.navigate(Destination.AdvancedHome.route) },
                    onOpenAdvancedApps = { nav.navigate(Destination.AdvancedApps.route) },
                    onOpenLoad = { nav.navigate(Destination.Simulator.route) },
                    onOpenLogs = { nav.navigate(Destination.Logs.route) },
                    onOpenTechnicalStats = { nav.navigate(Destination.TechnicalStats.route) },
                )
            }
            composable(Destination.AdvancedHome.route) {
                DashboardScreen(
                    actions = actions,
                    onOpenApps = { nav.navigate(Destination.AdvancedApps.route) },
                    onOpenApp = { pkg -> nav.navigate(Destination.appDetail(pkg)) },
                )
            }
            composable(Destination.AdvancedApps.route) {
                AppsScreen(onOpenApp = { pkg -> nav.navigate(Destination.appDetail(pkg)) })
            }
            composable(Destination.Stats.route) { StatsScreen(onShare = actions.exportLogs, onOpenDemo = { nav.navigate(Destination.Demo.route) }) }
            composable(Destination.Demo.route) { DemoScreen(onShare = actions.exportLogs) }
            composable(Destination.TechnicalStats.route) { TechnicalStatsScreen() }
            composable(Destination.Simulator.route) { SimulatorScreen() }
            composable(Destination.Logs.route) { LogsScreen(actions = actions) }
            composable(
                route = Destination.APP_DETAIL_ROUTE,
                arguments = listOf(navArgument("package") { type = NavType.StringType }),
            ) { entry ->
                AppDetailScreen(
                    packageName = entry.arguments?.getString("package").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
