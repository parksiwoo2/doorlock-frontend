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
import com.example.doorlock.ble.BleSetupCoordinator
import com.example.doorlock.ui.home.HomeScreen
import com.example.doorlock.ui.history.EntryHistoryScreen
import com.example.doorlock.ui.login.LoginScreen
import com.example.doorlock.ui.splash.SplashScreen
import com.example.doorlock.ui.settings.SettingsScreen
import com.example.doorlock.navigation.BottomNavDestination
import com.example.doorlock.navigation.BottomNavigationBar

sealed class AppRoute(val route: String) {
    object Splash : AppRoute("splash")

    // 기존 "로그인" 개념에서 "학번 등록" 개념으로 바뀌었으므로 라우트 이름도 함께 변경.
    // (화면 파일/함수명(LoginScreen)은 변경 범위를 최소화하기 위해 그대로 유지했습니다.)
    object Register : AppRoute("register")
    object HomeRoot : AppRoute("home_root")
}

@Composable
fun AppNavigation(bleSetupCoordinator: BleSetupCoordinator) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash.route
    ) {
        composable(AppRoute.Splash.route) {
            SplashScreen(
                coordinator = bleSetupCoordinator,
                onNavigateToRegister = {
                    navController.navigate(AppRoute.Register.route) {
                        popUpTo(AppRoute.Splash.route) {
                            inclusive = true
                        }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(AppRoute.HomeRoot.route) {
                        popUpTo(AppRoute.Splash.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }
        composable(AppRoute.Register.route) {
            LoginScreen(
                coordinator = bleSetupCoordinator,
                onRegisterSuccess = {
                    navController.navigate(AppRoute.HomeRoot.route) {
                        popUpTo(AppRoute.Register.route) {
                            inclusive = true
                        }
                    }
                }
            )
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
                SettingsScreen(onUnregistered = {
                    rootNavController.navigate(AppRoute.Register.route) {
                        popUpTo(AppRoute.HomeRoot.route) {
                            inclusive = true
                        }
                    }
                })
            }
        }
    }
}
