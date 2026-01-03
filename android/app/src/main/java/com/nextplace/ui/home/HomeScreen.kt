package com.nextplace.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nextplace.R
import com.nextplace.ui.discover.DiscoverScreen
import com.nextplace.ui.events.EventsScreen
import com.nextplace.ui.locations.LocationsScreen
import com.nextplace.ui.navigation.NavDestination
import com.nextplace.ui.profile.ProfileScreen

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int
) {
    object Discover : BottomNavItem(
        NavDestination.Discover.route,
        Icons.Default.Search,
        R.string.nav_discover
    )

    object Events : BottomNavItem(
        NavDestination.Events.route,
        Icons.Default.DateRange,
        R.string.nav_events
    )

    object FindVibe : BottomNavItem(
        NavDestination.Locations.route,
        Icons.Default.Place,
        R.string.nav_find_vibe
    )

    object Profile : BottomNavItem(
        NavDestination.Profile.route,
        Icons.Default.Person,
        R.string.nav_profile
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var selectedRoute by remember { mutableStateOf(BottomNavItem.Discover.route) }

    val bottomNavItems = listOf(
        BottomNavItem.Discover,
        BottomNavItem.Events,
        BottomNavItem.FindVibe,
        BottomNavItem.Profile
    )

    val primaryColor = Color(0xFFFF6B35)

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                contentColor = primaryColor
            ) {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelResId)) },
                        label = { Text(stringResource(item.labelResId)) },
                        selected = selectedRoute == item.route,
                        onClick = {
                            selectedRoute = item.route
                            navController.navigate(item.route) {
                                popUpTo(NavDestination.Discover.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = primaryColor,
                            selectedTextColor = primaryColor,
                            indicatorColor = Color(0xFFFFEBE5),
                            unselectedIconColor = Color(0xFF757575),
                            unselectedTextColor = Color(0xFF757575)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavDestination.Discover.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavDestination.Discover.route) {
                DiscoverScreen()
            }
            composable(NavDestination.Events.route) {
                EventsScreen()
            }
            composable(NavDestination.Locations.route) {
                LocationsScreen()
            }
            composable(NavDestination.Profile.route) {
                ProfileScreen()
            }
        }
    }
}
