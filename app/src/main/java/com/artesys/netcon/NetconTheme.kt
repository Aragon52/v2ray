package com.artesys.netcon

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object NetconColors {
    val DeepInk = Color(0xFF07090D)
    val OceanDark = Color(0xFF0A0F17)
    val OceanDeep = Color(0xFF0E1522)
    val ChassisDark = Color(0xFF131820)
    val PanelSurface = Color(0xFF1A212B)
    val PanelElevated = Color(0xFF222A36)
    val BevelBorder = Color(0xFF313B4B)
    val BevelHighlight = Color(0xFF475469)

    val LandBase = Color(0xFF1E2736)
    val LandHub = Color(0xFF283548)
    val LandStroke = Color(0xFF334155)
    val LandHubStroke = Color(0xFF4A5D78)

    val Copper = Color(0xFFD97D48)
    val CopperLight = Color(0xFFF0C99E)
    val CopperDark = Color(0xFF8C4A22)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val Online = Color(0xFF22C55E)
    val OnlineSoft = Color(0xFF4ADE80)
    val CyberCyan = Color(0xFF38BDF8)
    val ErrorRed = Color(0xFFEF4444)
}

internal val SpaceGroteskFont = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

internal val JetBrainsMonoFont = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

private val NetconColorScheme = darkColorScheme(
    primary = NetconColors.Copper,
    onPrimary = Color(0xFF1D0E05),
    primaryContainer = NetconColors.CopperDark,
    onPrimaryContainer = NetconColors.CopperLight,
    secondary = NetconColors.CyberCyan,
    onSecondary = NetconColors.DeepInk,
    background = NetconColors.DeepInk,
    onBackground = NetconColors.TextPrimary,
    surface = NetconColors.PanelSurface,
    onSurface = NetconColors.TextPrimary,
    surfaceVariant = NetconColors.PanelElevated,
    onSurfaceVariant = NetconColors.TextSecondary,
    outline = NetconColors.BevelBorder,
    error = NetconColors.ErrorRed,
    onError = NetconColors.TextPrimary,
)

private val NetconTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceGroteskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
internal fun NetconTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NetconColorScheme,
        typography = NetconTypography,
        content = content,
    )
}
