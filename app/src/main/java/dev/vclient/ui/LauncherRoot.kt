package dev.vclient.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vclient.ui.screens.HomeScreen
import dev.vclient.ui.screens.LogsScreen
import dev.vclient.ui.screens.ModulesScreen
import dev.vclient.ui.screens.ProfilesScreen
import dev.vclient.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val MODULES = "modules"
    const val PROFILES = "profiles"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val Tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.MODULES, "Modules", Icons.Rounded.GridView),
    Tab(Routes.PROFILES, "Profiles", Icons.Rounded.Folder),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun LauncherRoot() {
    val navController: NavHostController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != Routes.LOGS) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.MODULES) { ModulesScreen() }
            composable(Routes.PROFILES) { ProfilesScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.LOGS) { LogsScreen(navController) }
        }
    }
}
