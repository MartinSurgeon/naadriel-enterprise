package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BrandGreenPrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = BrandGreenPrimary,
    onPrimaryContainer = Color.White,
    secondary = BrandAmberGoldContainer,
    onSecondary = Color.Black,
    tertiary = FarmGreen,
    background = PolishBackgroundDark,
    onBackground = Color(0xFFE2E3DE),
    surface = PolishSurfaceDark,
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = PolishSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = PolishOutlineDark,
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandGreenPrimaryContainer,
    onPrimaryContainer = BrandGreenOnContainer,
    secondary = BrandCharcoalMedium,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE5D8),
    onSecondaryContainer = BrandCharcoalDark,
    tertiary = BrandAmberGold,
    onTertiary = Color.White,
    tertiaryContainer = BrandAmberGoldContainer,
    onTertiaryContainer = Color(0xFF2E1B00),
    background = PolishBackgroundLight,
    onBackground = BrandCharcoalDark,
    surface = PolishSurfaceLight,
    onSurface = BrandCharcoalDark,
    surfaceVariant = PolishSurfaceVariantLight,
    onSurfaceVariant = BrandCharcoalMedium,
    outline = PolishOutlineLight,
    outlineVariant = PolishOutlineVariantLight,
    error = DebtRed,
    errorContainer = DebtRedContainer,
    onError = Color.White,
    onErrorContainer = Color(0xFF410002)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Use our curated Professional Polish theme consistently
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
