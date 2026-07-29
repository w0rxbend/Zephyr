package com.worxbend.zephyr

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worxbend.zephyr.settings.UiDensity

internal data class ZephyrMetrics(
    val navigationWidth: Dp,
    val toolbarHeight: Dp,
    val statusBarHeight: Dp,
    val pagePadding: Dp,
    val panelPadding: Dp,
    val controlHeight: Dp,
    val cornerRadius: Dp,
    val spacing: Dp,
)

internal val LocalZephyrMetrics = staticCompositionLocalOf { CompactMetrics }

@Composable
internal fun ZephyrTheme(
    darkTheme: Boolean,
    density: UiDensity = UiDensity.Compact,
    content: @Composable () -> Unit,
) {
    val metrics = if (density == UiDensity.Compact) CompactMetrics else ComfortableMetrics
    CompositionLocalProvider(LocalZephyrMetrics provides metrics) {
        MaterialTheme(
            colorScheme = if (darkTheme) ZephyrDarkColors else ZephyrLightColors,
            typography = ZephyrTypography,
            content = content,
        )
    }
}

private val ZephyrLightColors = lightColorScheme(
    primary = Color(0xFF3574F0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF163A70),
    secondary = Color(0xFF5A5D63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EBF2),
    onSecondaryContainer = Color(0xFF2B2D30),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF2B2D30),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2D30),
    surfaceVariant = Color(0xFFF0F1F3),
    onSurfaceVariant = Color(0xFF6C7078),
    outline = Color(0xFFC9CCD1),
    outlineVariant = Color(0xFFE1E3E6),
    error = Color(0xFFC9362B),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F1F3),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    inverseSurface = Color(0xFF2B2D30),
    inverseOnSurface = Color(0xFFF8FAFC),
)

private val ZephyrDarkColors = darkColorScheme(
    primary = Color(0xFF6B9BFA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2E436E),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFB8BCC6),
    onSecondary = Color(0xFF1E1F22),
    secondaryContainer = Color(0xFF393B40),
    onSecondaryContainer = Color(0xFFE7E9EE),
    background = Color(0xFF1E1F22),
    onBackground = Color(0xFFDFE1E5),
    surface = Color(0xFF2B2D30),
    onSurface = Color(0xFFDFE1E5),
    surfaceVariant = Color(0xFF313338),
    onSurfaceVariant = Color(0xFFAEB2BA),
    outline = Color(0xFF4E5157),
    outlineVariant = Color(0xFF393B40),
    error = Color(0xFFFF6B68),
    surfaceContainerLow = Color(0xFF25262A),
    surfaceContainer = Color(0xFF2B2D30),
    surfaceContainerHigh = Color(0xFF35373B),
    inverseSurface = Color(0xFFDFE1E5),
    inverseOnSurface = Color(0xFF2B2D30),
)

private val ZephyrTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

private val CompactMetrics = ZephyrMetrics(
    navigationWidth = 238.dp,
    toolbarHeight = 50.dp,
    statusBarHeight = 26.dp,
    pagePadding = 20.dp,
    panelPadding = 14.dp,
    controlHeight = 32.dp,
    cornerRadius = 7.dp,
    spacing = 8.dp,
)

private val ComfortableMetrics = ZephyrMetrics(
    navigationWidth = 264.dp,
    toolbarHeight = 58.dp,
    statusBarHeight = 30.dp,
    pagePadding = 28.dp,
    panelPadding = 18.dp,
    controlHeight = 38.dp,
    cornerRadius = 9.dp,
    spacing = 12.dp,
)
