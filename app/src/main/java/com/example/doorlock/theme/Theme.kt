package com.example.doorlock.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 요구사항: 연보라/보라 계열 색상을 UI에서 완전히 제거하고,
 * White / Light Gray / Gray / Dark Gray-Black / KHLUG Blue(+ Light Blue) 중심으로 구성.
 *
 * 단순히 primary만 바꾸는 것으로는 부족합니다 — Material3의 기본 baseline 팔레트는
 * surfaceVariant, primaryContainer, secondaryContainer 등에 보라 계열 기본값을 쓰기 때문에,
 * Card/NavigationBar/OutlinedTextField 등이 참조하는 주요 color role을 전부 명시적으로
 * 지정해야 실제 화면에서 연보라가 사라집니다.
 */
private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = NeutralWhite,
    primaryContainer = LightBlue,
    onPrimaryContainer = NeutralAlmostBlack,

    secondary = NeutralDarkGray,
    onSecondary = NeutralWhite,
    secondaryContainer = NeutralLightGray,
    onSecondaryContainer = NeutralAlmostBlack,

    background = NeutralLightGray,
    onBackground = NeutralAlmostBlack,

    surface = NeutralWhite,
    onSurface = NeutralAlmostBlack,
    surfaceVariant = NeutralLightGray,
    onSurfaceVariant = NeutralDarkGray,

    outline = NeutralGray,

    error = ErrorRed,
    onError = NeutralWhite,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = NeutralWhite,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = NeutralWhite,

    secondary = NeutralLightGray,
    onSecondary = NeutralAlmostBlack,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = NeutralWhite,

    background = DarkBackground,
    onBackground = NeutralWhite,

    surface = DarkSurface,
    onSurface = NeutralWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = NeutralGray,

    outline = NeutralDarkGray,

    error = ErrorRed,
    onError = NeutralWhite,
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
