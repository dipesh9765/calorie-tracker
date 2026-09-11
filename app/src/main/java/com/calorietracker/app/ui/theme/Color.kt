package com.calorietracker.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF5B7FFF)
val PrimaryBlueVariant = Color(0xFF3B5EDB)
val BackgroundDark = Color(0xFF0F1424)
val SurfaceDark = Color(0xFF192038)
val SurfaceCard = Color(0xFF222B45)
val AccentEmerald = Color(0xFF00E676)
val AccentOrange = Color(0xFFFF9100)
val AccentPurple = Color(0xFFB388FF)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlueVariant,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)
