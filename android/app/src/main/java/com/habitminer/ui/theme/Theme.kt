@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFF4ADE80),
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFF243042),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFB0C4D8),
        primaryContainer = Color(0xFF0C3557),
        onPrimaryContainer = Color(0xFF93CEFE),
        secondaryContainer = Color(0xFF1A3A2A),
        onSecondaryContainer = Color(0xFF86EFAC),
        error = Color(0xFFEF4444),
        errorContainer = Color(0x33EF4444),
        onErrorContainer = Color(0xFFFCA5A5),
        outline = Color(0xFF3D5166),
        outlineVariant = Color(0xFF243042),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF0284C7),
        secondary = Color(0xFF16A34A),
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE2E8F0),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF475569),
        primaryContainer = Color(0xFFE0F2FE),
        onPrimaryContainer = Color(0xFF075985),
        secondaryContainer = Color(0xFFDCFCE7),
        onSecondaryContainer = Color(0xFF14532D),
        error = Color(0xFFDC2626),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF991B1B),
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFF1F5F9),
    )

val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusError = Color(0xFFEF4444)

@Composable
fun HabitMinerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HabitMinerTypography,
        content = content,
    )
}
