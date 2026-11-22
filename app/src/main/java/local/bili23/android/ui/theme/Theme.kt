package local.bili23.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class BiliColors(
    val pink: Color = Color(0xFFFB7299),
    val pinkDeep: Color = Color(0xFFE45C84),
    val pinkSoft: Color = Color(0xFF3A2229),
    val background: Color,
    val card: Color,
    val elevated: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val divider: Color,
    val fill: Color,
    val blue: Color = Color(0xFF00AEEC),
    val gold: Color = Color(0xFFFFB027),
    val orange: Color = Color(0xFFFF7F24),
)

private val DarkColors = BiliColors(
    background = Color(0xFF17181A),
    card = Color(0xFF1F2022),
    elevated = Color(0xFF2A2B2E),
    text1 = Color(0xFFE7E9EB),
    text2 = Color(0xFFA2A7AE),
    text3 = Color(0xFF757A81),
    divider = Color(0xFF2F3134),
    fill = Color(0xFF2A2B2E),
)

private val LightColors = BiliColors(
    background = Color(0xFFF4F4F4),
    card = Color(0xFFFFFFFF),
    elevated = Color(0xFFFFFFFF),
    text1 = Color(0xFF18191C),
    text2 = Color(0xFF61666D),
    text3 = Color(0xFF9499A0),
    divider = Color(0xFFE3E5E7),
    fill = Color(0xFFF1F2F3),
    pinkSoft = Color(0xFFFFECF1),
)

val LocalBiliColors = staticCompositionLocalOf { DarkColors }

object AppColors {
    val current: BiliColors
        @Composable get() = LocalBiliColors.current
}

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun BiliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.pink,
            onPrimary = Color.White,
            secondary = colors.blue,
            background = colors.background,
            onBackground = colors.text1,
            surface = colors.card,
            onSurface = colors.text1,
            surfaceVariant = colors.elevated,
            onSurfaceVariant = colors.text2,
            outline = colors.divider,
        )
    } else {
        lightColorScheme(
            primary = colors.pink,
            onPrimary = Color.White,
            secondary = colors.blue,
            background = colors.background,
            onBackground = colors.text1,
            surface = colors.card,
            onSurface = colors.text1,
            surfaceVariant = colors.elevated,
            onSurfaceVariant = colors.text2,
            outline = colors.divider,
        )
    }
    CompositionLocalProvider(LocalBiliColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
