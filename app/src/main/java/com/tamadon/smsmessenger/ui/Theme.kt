package com.tamadon.smsmessenger.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.core.Loc

/** Colours offered for the app's primary colour. */
val PrimaryChoices: List<Long> = listOf(
    0xFF1E6BD6, // blue (default)
    0xFF0097A7, // teal
    0xFF2E7D32, // green
    0xFFEF6C00, // orange
    0xFFD32F2F, // red
    0xFFC2185B, // pink
    0xFF8E24AA, // purple
    0xFF5E35B1, // indigo
    0xFF6D4C41, // brown
    0xFF546E7A  // slate
)

val Shabnam = FontFamily(
    Font(R.font.shabnam_thin, FontWeight.Thin),
    Font(R.font.shabnam_light, FontWeight.Light),
    Font(R.font.shabnam_regular, FontWeight.Normal),
    Font(R.font.shabnam_medium, FontWeight.Medium),
    Font(R.font.shabnam_medium, FontWeight.SemiBold),
    Font(R.font.shabnam_bold, FontWeight.Bold),
    Font(R.font.shabnam_bold, FontWeight.ExtraBold),
    Font(R.font.shabnam_bold, FontWeight.Black)
)

private val AppTypography: Typography = run {
    val d = Typography()
    val f = { s: TextStyle -> s.copy(fontFamily = Shabnam) }
    Typography(
        displayLarge = f(d.displayLarge), displayMedium = f(d.displayMedium), displaySmall = f(d.displaySmall),
        headlineLarge = f(d.headlineLarge), headlineMedium = f(d.headlineMedium), headlineSmall = f(d.headlineSmall),
        titleLarge = f(d.titleLarge), titleMedium = f(d.titleMedium), titleSmall = f(d.titleSmall),
        bodyLarge = f(d.bodyLarge), bodyMedium = f(d.bodyMedium), bodySmall = f(d.bodySmall),
        labelLarge = f(d.labelLarge), labelMedium = f(d.labelMedium), labelSmall = f(d.labelSmall)
    )
}

private fun onColor(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF101114) else Color.White

private fun lightScheme(seed: Color): ColorScheme {
    // On a light background the raw palette entries look washed out, so the accent and the
    // outgoing bubble are pushed towards black. Bright seeds (orange) need a bigger push.
    val primary = if (seed.luminance() > 0.32f) {
        lerp(seed, Color.Black, 0.10f)
    } else {
        lerp(seed, Color.Black, 0.04f)
    }
    // The outgoing bubble is the most visible use of the accent; a 68% white mix looked almost
    // grey-pink. 55% keeps it clearly coloured while dark text stays readable.
    val container = lerp(seed, Color.White, 0.55f)
    val onContainer = lerp(seed, Color.Black, 0.78f)
    return lightColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = lerp(seed, Color(0xFF5F6368), 0.6f),
        onSecondary = Color.White,
        secondaryContainer = lerp(seed, Color.White, 0.88f),
        onSecondaryContainer = lerp(seed, Color.Black, 0.65f),
        tertiary = lerp(seed, Color(0xFF7D5260), 0.5f),
        tertiaryContainer = lerp(Color(0xFF7D5260), Color.White, 0.82f),
        onTertiaryContainer = lerp(Color(0xFF7D5260), Color.Black, 0.5f),
        background = Color(0xFFFAFAFC),
        onBackground = Color(0xFF16181C),
        surface = Color(0xFFFAFAFC),
        onSurface = Color(0xFF16181C),
        surfaceVariant = Color(0xFFEBEDF2),
        onSurfaceVariant = Color(0xFF50545C),
        outline = Color(0xFF8A8E96),
        outlineVariant = Color(0xFFE6E8EC),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF4F5F8),
        surfaceContainer = Color(0xFFF0F1F5),
        surfaceContainerHigh = Color(0xFFEAECF0),
        surfaceContainerHighest = Color(0xFFE4E6EB)
    )
}

/**
 * A deliberately dark theme: near-black surfaces, very faint dividers (outlineVariant is only a
 * shade lighter than the background), and a muted tint of the primary colour for selections and
 * outgoing bubbles.
 */
private fun darkScheme(seed: Color): ColorScheme {
    val bg = Color(0xFF0A0B0D)
    // Lift the accent above the near-black background without making it pastel: 26% towards
    // white is clearly visible but still reads as the chosen colour.
    val primary = lerp(seed, Color.White, 0.26f)
    // A saturated red for outgoing bubbles, the new-message button and coloured avatars. It has
    // to sit clearly below the accent so white text/icons stay readable.
    val container = lerp(seed, Color.Black, 0.18f)
    val onContainer = Color.White
    return darkColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = lerp(primary, Color(0xFF9AA0A6), 0.6f),
        onSecondary = Color(0xFF101114),
        secondaryContainer = lerp(seed, bg, 0.78f),
        onSecondaryContainer = lerp(seed, Color.White, 0.8f),
        tertiary = lerp(primary, Color(0xFFEFB8C8), 0.5f),
        tertiaryContainer = lerp(Color(0xFF5A3A46), bg, 0.35f),
        onTertiaryContainer = Color(0xFFF8CCD8),
        background = bg,
        onBackground = Color(0xFFE4E6EA),
        surface = bg,
        onSurface = Color(0xFFE4E6EA),
        surfaceVariant = Color(0xFF17191D),
        onSurfaceVariant = Color(0xFFA6AAB2),
        outline = Color(0xFF5A5E66),
        outlineVariant = Color(0xFF1C1E22),
        surfaceContainerLowest = Color(0xFF070809),
        surfaceContainerLow = Color(0xFF0F1012),
        surfaceContainer = Color(0xFF131518),
        surfaceContainerHigh = Color(0xFF1A1C20),
        surfaceContainerHighest = Color(0xFF212328),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF561E19),
        errorContainer = Color(0xFF4A1A15),
        onErrorContainer = Color(0xFFFFDAD4)
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val prefs = App.instance.prefs
    val dark = when (prefs.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val seed = Color(prefs.primaryColor)
    val scheme = remember(dark, seed) { if (dark) darkScheme(seed) else lightScheme(seed) }
    val persian = Loc.isPersian(prefs.language)

    // System bar icons must follow the chosen theme, not only the system one
    val activity = LocalContext.current as? ComponentActivity
    SideEffect {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        // The in-app theme can differ from the system theme; keep the window itself in sync so a
        // transition never reveals the platform's grey window background.
        activity?.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(scheme.background.toArgb())
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
