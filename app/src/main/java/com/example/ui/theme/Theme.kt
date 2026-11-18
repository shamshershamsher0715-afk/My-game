package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ApexDarkColorScheme = darkColorScheme(
    primary = ApexCrimson,
    onPrimary = TitaniumSilver,
    primaryContainer = Color(0xFF4A0808),
    onPrimaryContainer = TitaniumSilver,
    secondary = TelemetryCyan,
    onSecondary = CarbonObsidian,
    secondaryContainer = Color(0xFF0A333D),
    onSecondaryContainer = TelemetryCyan,
    tertiary = ShiftAmber,
    onTertiary = CarbonObsidian,
    background = CarbonObsidian,
    onBackground = TitaniumSilver,
    surface = PitSlate,
    onSurface = TitaniumSilver,
    surfaceVariant = GarageSurface,
    onSurfaceVariant = MutedSteel,
    outline = HairlineBorder
)

private val ApexShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun Apex300Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ApexDarkColorScheme,
        typography = AppTypography,
        shapes = ApexShapes,
        content = content
    )
}
