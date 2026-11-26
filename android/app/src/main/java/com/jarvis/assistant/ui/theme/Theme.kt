package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val JarvisBlack = Color(0xFF030712)
val JarvisNavy = Color(0xFF0B132B)
val JarvisNavySurface = Color(0xFF111C38)
val JarvisCyan = Color(0xFF22D3EE)
val JarvisCyanDim = Color(0xFF0E7490)
val JarvisAmber = Color(0xFFF59E0B)
val JarvisCrimson = Color(0xFFEF4444)
val JarvisTextPrimary = Color(0xFFF1F5F9)
val JarvisTextSecondary = Color(0xFF94A3B8)

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBlack,
    secondary = JarvisNavySurface,
    onSecondary = JarvisTextPrimary,
    background = JarvisBlack,
    onBackground = JarvisTextPrimary,
    surface = JarvisNavy,
    onSurface = JarvisTextPrimary,
    error = JarvisCrimson,
    onError = JarvisTextPrimary
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        content = content
    )
}
