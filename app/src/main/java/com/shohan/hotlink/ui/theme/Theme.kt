package com.shohan.hotlink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HotLinkColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF444444),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF1C1C1C),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF)
)

private val HotLinkTypography = Typography()

@Composable
fun HotLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HotLinkColors,
        typography = HotLinkTypography,
        content = content
    )
}
