package com.example.doorlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.doorlock.ble.BleSetupCoordinator
import com.example.doorlock.navigation.AppNavigation
import com.example.doorlock.theme.DoorlockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // registerForActivityResult()는 Activity가 STARTED 상태가 되기 전에 등록해야 하므로,
        // setContent() 를 호출하는 이 onCreate() 안에서 코디네이터를 생성합니다.
        val bleSetupCoordinator = BleSetupCoordinator(this)

        setContent {
            DoorlockTheme {
                AppNavigation(bleSetupCoordinator = bleSetupCoordinator)
            }
        }
    }
}
