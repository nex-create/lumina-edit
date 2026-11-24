package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorOutlineSubtle = HairlineBorder.copy(alpha = 0.55f)

private val LuminaDarkColorScheme = darkColorScheme(
  primary = CoralFlame,
  onPrimary = ObsidianCanvas,
  primaryContainer = CoralFlameContainer,
  onPrimaryContainer = CoralFlameLight,
  secondary = StudioCyan,
  onSecondary = ObsidianCanvas,
  secondaryContainer = StudioCyanContainer,
  onSecondaryContainer = StudioCyan,
  tertiary = WarmAmber,
  onTertiary = ObsidianCanvas,
  tertiaryContainer = WarmAmberContainer,
  onTertiaryContainer = WarmAmber,
  background = ObsidianCanvas,
  onBackground = StudioTextPrimary,
  surface = StudioSurface,
  onSurface = StudioTextPrimary,
  surfaceVariant = StudioSurfaceElevated,
  onSurfaceVariant = StudioTextSecondary,
  surfaceContainerLow = StudioSurface,
  surfaceContainer = StudioSurfaceElevated,
  surfaceContainerHigh = StudioSurfaceHighest,
  outline = HairlineBorder,
  outlineVariant = ColorOutlineSubtle,
  error = StatusError,
  onError = ObsidianCanvas,
)

val LuminaShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LuminaEditTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LuminaDarkColorScheme,
    typography = LuminaTypography,
    shapes = LuminaShapes,
    content = content,
  )
}
