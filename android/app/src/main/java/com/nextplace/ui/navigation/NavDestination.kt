package com.nextplace.ui.navigation

sealed class NavDestination(val route: String) {
    object Discover : NavDestination("discover")
    object Events : NavDestination("events")
    object Locations : NavDestination("locations")
    object Profile : NavDestination("profile")
}
