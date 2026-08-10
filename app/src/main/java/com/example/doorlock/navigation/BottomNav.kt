package com.example.doorlock.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.doorlock.R

sealed class BottomNavDestination(
    val route: String,
    val title: String,
    val iconRes: Int
) {
    object Home : BottomNavDestination("home", "홈", R.drawable.ic_home)
    object EntryHistory : BottomNavDestination("history", "기록", R.drawable.ic_schedule)
    object Settings : BottomNavDestination("settings", "설정", R.drawable.ic_settings)
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    destinations: List<BottomNavDestination>
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    NavigationBar {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Image(
                        painter = painterResource(id = destination.iconRes),
                        contentDescription = destination.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(destination.title)
                }
            )
        }
    }
}
