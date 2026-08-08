package com.example.doorlock.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.doorlock.ui.home.HomeScreen
import com.example.doorlock.ui.history.EntryHistoryScreen
import com.example.doorlock.ui.login.LoginScreen
import com.example.doorlock.ui.splash.SplashScreen
import com.example.doorlock.ui.settings.SettingsScreen
import com.example.doorlock.ui.settings.SettingsViewModel
import com.example.doorlock.ui.splash.SplashViewModel
import com.example.doorlock.ui.login.LoginViewModel
import com.example.doorlock.ui.home.HomeViewModel
import com.example.doorlock.ui.history.HistoryViewModel
import com.example.doorlock.navigation.BottomNavDestination
import com.example.doorlock.navigation.BottomNavigationBar

sealed class AppRoute(val route: String) {
    object Splash : AppRoute("splash")
    object Login : AppRoute("login")
    object HomeRoot : AppRoute("home_root")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash.route
    ) {
        composable(AppRoute.Splash.route) {
            SplashScreen(onContinue = {
                navController.navigate(AppRoute.Login.route) {
                    popUpTo(AppRoute.Splash.route) {
                        inclusive = true
                    }
                }
            })
        }
        composable(AppRoute.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(AppRoute.HomeRoot.route) {
                    popUpTo(AppRoute.Login.route) {
                        inclusive = true
                    }
                }
            })
        }
        composable(AppRoute.HomeRoot.route) {
            HomeRootScreen(rootNavController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRootScreen(rootNavController: NavHostController) {
    val innerNavController = rememberNavController()
    val destinations = remember {
        listOf(
            BottomNavDestination.Home,
            BottomNavDestination.EntryHistory,
            BottomNavDestination.Settings
        )
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                navController = innerNavController,
                destinations = destinations
            )
        }
    ) { contentPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = BottomNavDestination.Home.route,
            modifier = Modifier.padding(contentPadding)
        ) {
            composable(BottomNavDestination.Home.route) {
                HomeScreen()
            }
            composable(BottomNavDestination.EntryHistory.route) {
                EntryHistoryScreen()
            }
            composable(BottomNavDestination.Settings.route) {
                SettingsScreen(onLogout = {
                    rootNavController.navigate(AppRoute.Login.route) {
                        popUpTo(AppRoute.HomeRoot.route) {
                            inclusive = true
                        }
                    }
                })
            }
        }
    }
}
