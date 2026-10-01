package org.openvideo.aggregator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.openvideo.aggregator.ui.findActivity

val LocalReduceMotion = staticCompositionLocalOf { false }

@Immutable class ExtraColors(val success: Color, val warning: Color)
val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Color(0xFF2E9E6B), Color(0xFFE0A100)) }

// Semantic tokens live in the ColorScheme: background, surface, surfaceVariant, primary, secondary,
// onSurface (textPrimary), onSurfaceVariant (textSecondary), outlineVariant (divider), error.
private val Light = lightColorScheme(
    primary = Color(0xFF5B5BF0), onPrimary = Color.White, secondary = Color(0xFF0E7F74),
    background = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEBEDF2),
    onSurface = Color(0xFF14161B), onSurfaceVariant = Color(0xFF5A6070), outlineVariant = Color(0xFFD9DCE3),
    error = Color(0xFFD33A3A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA4A6FF), onPrimary = Color(0xFF14143A), secondary = Color(0xFF4FD6C7),
    background = Color(0xFF0E0F13), surface = Color(0xFF16181D), surfaceVariant = Color(0xFF22252C),
    onSurface = Color(0xFFEDEEF2), onSurfaceVariant = Color(0xFFA2A8B6), outlineVariant = Color(0xFF30343D),
    error = Color(0xFFFF7B7B),
)

@Composable
fun OvaTheme(mode: String, reduceMotion: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    // Keep status/navigation bar icons readable when the app theme differs from the system theme.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity: Activity? = view.context.findActivity()
        if (activity != null) {
            val c = WindowCompat.getInsetsController(activity.window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    val base = Typography()
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = scheme,
            typography = base.copy(
                titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold)),
            shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
            content = content,
        )
    }
}
