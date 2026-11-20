package com.gravityos.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object GravityColors {
    val ObsidianBg = Color(0xFF03060E)
    val DeepNavyBg = Color(0xFF071126)
    val GlassSurface = Color(0xCC0D1B36)
    val GlassCard = Color(0xB3101F3D)
    val GlassBorder = Color(0x4060A5FA)
    val ElectricCyan = Color(0xFF38BDF8)
    val IceCyan = Color(0xFFBAE6FD)
    val PlasmaBlue = Color(0xFF2563EB)
    val DeepCobalt = Color(0xFF1D4ED8)
    val NebulaViolet = Color(0xFF7C3AED)
    val SoftViolet = Color(0xFFA78BFA)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val SuccessGreen = Color(0xFF34D399)
    val WarningAmber = Color(0xFFFBBF24)
}

private val GravityDarkScheme = darkColorScheme(
    primary = GravityColors.ElectricCyan,
    onPrimary = GravityColors.ObsidianBg,
    secondary = GravityColors.NebulaViolet,
    onSecondary = GravityColors.TextPrimary,
    tertiary = GravityColors.PlasmaBlue,
    background = GravityColors.ObsidianBg,
    onBackground = GravityColors.TextPrimary,
    surface = GravityColors.DeepNavyBg,
    onSurface = GravityColors.TextPrimary,
    surfaceVariant = GravityColors.GlassSurface,
    onSurfaceVariant = GravityColors.TextSecondary,
    outline = GravityColors.GlassBorder
)

@Composable
fun GravityOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GravityDarkScheme,
        content = content
    )
}
