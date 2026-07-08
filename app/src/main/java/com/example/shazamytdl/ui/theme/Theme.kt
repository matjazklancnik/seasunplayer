package com.example.shazamytdl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class AppVisualStyle(
    val storageKey: String,
    val displayName: String,
    val description: String
) {
    SUNSET("sunset", "Sončni zahod", "Temno morje s toplim sončnim sijem"),
    OCEAN("ocean", "Ocean", "Mirni turkizni in modri odtenki"),
    CLEAN("clean", "Svetla", "Čisto in svetlo ozadje za dnevno uporabo");

    companion object {
        fun fromStorage(value: String?): AppVisualStyle =
            entries.firstOrNull { it.storageKey == value } ?: SUNSET
    }
}

private val SunsetColors = darkColorScheme(
    primary = Color(0xFFFF8A5B),
    onPrimary = Color(0xFF3B1308),
    primaryContainer = Color(0xFF6B2C1B),
    secondary = Color(0xFF5DCAE2),
    background = Color(0xFF061A2F),
    surface = Color(0xFF102A43),
    surfaceVariant = Color(0xFF1A3A55),
    onSurface = Color(0xFFF1F6FA),
    onSurfaceVariant = Color(0xFFC6D8E5)
)

private val OceanColors = darkColorScheme(
    primary = Color(0xFF55D6C2),
    onPrimary = Color(0xFF00372F),
    primaryContainer = Color(0xFF005047),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF052D36),
    surface = Color(0xFF0C3C46),
    surfaceVariant = Color(0xFF174E58),
    onSurface = Color(0xFFE7FAF7),
    onSurfaceVariant = Color(0xFFB7DCD7)
)

private val CleanColors = lightColorScheme(
    primary = Color(0xFF006C84),
    primaryContainer = Color(0xFFB8EAF5),
    secondary = Color(0xFFB34B25),
    background = Color(0xFFF4F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE0EBEF),
    onSurface = Color(0xFF142126),
    onSurfaceVariant = Color(0xFF40545C)
)

@Composable
fun ShazamYtdlTheme(
    style: AppVisualStyle = AppVisualStyle.SUNSET,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = when (style) {
            AppVisualStyle.SUNSET -> SunsetColors
            AppVisualStyle.OCEAN -> OceanColors
            AppVisualStyle.CLEAN -> CleanColors
        },
        content = content
    )
}

fun appBackgroundBrush(style: AppVisualStyle): Brush = when (style) {
    AppVisualStyle.SUNSET -> Brush.verticalGradient(
        listOf(Color(0xFF06162A), Color(0xFF0C3B57), Color(0xFF122A44))
    )
    AppVisualStyle.OCEAN -> Brush.verticalGradient(
        listOf(Color(0xFF063A44), Color(0xFF08777B), Color(0xFF0B3445))
    )
    AppVisualStyle.CLEAN -> Brush.verticalGradient(
        listOf(Color(0xFFF7FBFC), Color(0xFFDDEEF2), Color(0xFFF4F8FA))
    )
}
