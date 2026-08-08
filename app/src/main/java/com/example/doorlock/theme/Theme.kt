package com.example.doorlock.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = LightBlue,
    onSecondary = Color.White,
    background = BackgroundGray,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = LightBlue,
    onSecondary = Color.White,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun DoorlockTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
