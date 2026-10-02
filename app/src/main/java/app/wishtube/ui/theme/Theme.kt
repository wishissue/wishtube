package app.wishtube.ui.theme

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
import app.wishtube.ui.findActivity

val LocalReduceMotion = staticCompositionLocalOf { false }

@Immutable class ExtraColors(val success: Color, val warning: Color)
val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Color(0xFF4A7C59), Color(0xFFD4A373)) }

// Cozy, warm, premium palette (cream, terracotta, sage, coffee brown)
private val Light = lightColorScheme(
    primary = Color(0xFFC25934), onPrimary = Color.White, secondary = Color(0xFF4A7C59),
    background = Color(0xFFFAF7F2), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF0ECE1),
    onSurface = Color(0xFF2C2623), onSurfaceVariant = Color(0xFF6B605A), outlineVariant = Color(0xFFE2DCD3),
    error = Color(0xFFBA3B46),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFE07A5F), onPrimary = Color(0xFF2C150D), secondary = Color(0xFF81B29A),
    background = Color(0xFF141210), surface = Color(0xFF1C1917), surfaceVariant = Color(0xFF282421),
    onSurface = Color(0xFFF4F1EA), onSurfaceVariant = Color(0xFFA89F95), outlineVariant = Color(0xFF3B3531),
    error = Color(0xFFE06D75),
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
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(36.dp)
            ),
            content = content,
        )
    }
}
