package com.mclauncher.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mclauncher.ui.screens.*

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    object Versions : Screen(
        route = "versions",
        title = "Versions",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2
    )
    
    object Profiles : Screen(
        route = "profiles",
        title = "Profiles",
        selectedIcon = Icons.Filled.FolderCopy,
        unselectedIcon = Icons.Outlined.FolderCopy
    )
    
    object Mods : Screen(
        route = "mods",
        title = "Mods",
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension
    )
    
    object Accounts : Screen(
        route = "accounts",
        title = "Accounts",
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle
    )
    
    object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    // Detail screens (not in bottom nav)
    object VersionDetail : Screen(
        route = "version/{versionId}",
        title = "Version Details",
        selectedIcon = Icons.Filled.Info,
        unselectedIcon = Icons.Outlined.Info
    ) {
        fun createRoute(versionId: String) = "version/$versionId"
    }
    
    object ProfileEdit : Screen(
        route = "profile/edit/{profileId}",
        title = "Edit Profile",
        selectedIcon = Icons.Filled.Edit,
        unselectedIcon = Icons.Outlined.Edit
    ) {
        fun createRoute(profileId: String) = "profile/edit/$profileId"
        const val NEW_PROFILE = "new"
    }
    
    object ProfileCreate : Screen(
        route = "profile/create",
        title = "Create Profile",
        selectedIcon = Icons.Filled.Add,
        unselectedIcon = Icons.Outlined.Add
    )
    
    object ModDetail : Screen(
        route = "mod/{modId}",
        title = "Mod Details",
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension
    ) {
        fun createRoute(modId: String) = "mod/$modId"
    }
    
    object Console : Screen(
        route = "console",
        title = "Console",
        selectedIcon = Icons.Filled.Terminal,
        unselectedIcon = Icons.Outlined.Terminal
    )
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Versions,
    Screen.Profiles,
    Screen.Mods,
    Screen.Accounts
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCLauncherNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // Determine if we should show bottom bar
    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { 
                            it.route == screen.route 
                        } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    initialOffsetX = { 30 },
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutHorizontally(
                    targetOffsetX = { 30 },
                    animationSpec = tween(300)
                )
            }
        ) {
            // Main bottom nav destinations
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToVersions = { navController.navigate(Screen.Versions.route) },
                    onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                    onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToConsole = { navController.navigate(Screen.Console.route) },
                    onNavigateToProfile = { profileId ->
                        navController.navigate(Screen.ProfileEdit.createRoute(profileId))
                    }
                )
            }
            
            composable(Screen.Versions.route) {
                VersionsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onVersionClick = { versionId ->
                        navController.navigate(Screen.VersionDetail.createRoute(versionId))
                    }
                )
            }
            
            composable(Screen.Profiles.route) {
                ProfilesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onProfileClick = { profileId ->
                        navController.navigate(Screen.ProfileEdit.createRoute(profileId))
                    },
                    onCreateProfile = {
                        navController.navigate(Screen.ProfileCreate.route)
                    }
                )
            }
            
            composable(Screen.Mods.route) {
                ModsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onModClick = { modId ->
                        navController.navigate(Screen.ModDetail.createRoute(modId))
                    }
                )
            }
            
            composable(Screen.Accounts.route) {
                AccountScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // Detail screens
            composable(
                route = Screen.VersionDetail.route,
                arguments = listOf(navArgument("versionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val versionId = backStackEntry.arguments?.getString("versionId") ?: ""
                VersionDetailScreen(
                    versionId = versionId,
                    onNavigateBack = { navController.popBackStack() },
                    onCreateProfile = { 
                        navController.navigate(Screen.ProfileCreate.route)
                    }
                )
            }
            
            composable(Screen.ProfileCreate.route) {
                ProfileEditScreen(
                    profileId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.ProfileEdit.route,
                arguments = listOf(navArgument("profileId") { type = NavType.StringType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getString("profileId")
                ProfileEditScreen(
                    profileId = profileId,
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            
            composable(
                route = Screen.ModDetail.route,
                arguments = listOf(navArgument("modId") { type = NavType.StringType })
            ) { backStackEntry ->
                val modId = backStackEntry.arguments?.getString("modId") ?: ""
                ModDetailScreen(
                    modId = modId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Console.route) {
                ConsoleScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
