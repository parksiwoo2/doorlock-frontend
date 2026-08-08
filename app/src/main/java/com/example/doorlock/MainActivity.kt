package com.example.doorlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.doorlock.navigation.AppNavigation
import com.example.doorlock.theme.DoorlockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoorlockTheme {
                AppNavigation()
            }
        }
    }
}
