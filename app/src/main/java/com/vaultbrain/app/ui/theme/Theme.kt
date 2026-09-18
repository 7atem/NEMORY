package com.vaultbrain.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vaultbrain.core.common.theme.*

/**
 * VaultBrain Dark color scheme — the default experience.
 * Evokes a secure, premium vault aesthetic.
 */
private val VaultBrainDarkScheme = darkColorScheme(
    primary = Color(0xFF79D5CE),
    onPrimary = Color(0xFF003735),
    primaryContainer = VaultTealContainer,
    onPrimaryContainer = OnVaultTealContainer,

    secondary = VaultAmber,
    onSecondary = Color.Black,
    secondaryContainer = VaultAmberContainer,
    onSecondaryContainer = OnVaultAmberContainer,

    tertiary = VaultLavender,
    onTertiary = Color.Black,
    tertiaryContainer = VaultLavenderContainer,
    onTertiaryContainer = OnVaultLavenderContainer,

    background = VaultBlack,
    onBackground = VaultOnDarkPrimary,
    surface = VaultCharcoal,
    onSurface = VaultOnDarkPrimary,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultOnDarkSecondary,

    error = VaultError,
    onError = Color.Black,
    errorContainer = VaultErrorContainer,
    onErrorContainer = OnVaultErrorContainer,

    surfaceContainerLowest = VaultBlack,
    surfaceContainerLow = VaultCharcoal,
    surfaceContainer = VaultSurface,
    surfaceContainerHigh = VaultSurfaceVariant,
    surfaceContainerHighest = VaultSurfaceBright,
    inverseSurface = VaultOnDarkPrimary,
    inverseOnSurface = VaultBlack,
    inversePrimary = VaultTealDark,
    outline = Color(0xFF7D8993),
    outlineVariant = VaultOutlineVariant
)

/**
 * VaultBrain Light color scheme — opt-in for users who prefer light mode.
 */
private val VaultBrainLightScheme = lightColorScheme(
    primary = VaultTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F1EC),
    onPrimaryContainer = VaultTealDark,

    secondary = Color(0xFF785900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7A7),
    onSecondaryContainer = Color(0xFF261A00),

    tertiary = VaultLavenderDark,
    onTertiary = Color.White,
    tertiaryContainer = OnVaultLavenderContainer,
    onTertiaryContainer = VaultLavenderDark,

    background = VaultWhite,
    onBackground = VaultOnLightPrimary,
    surface = VaultWhite,
    onSurface = VaultOnLightPrimary,
    surfaceVariant = VaultLightSurfaceVariant,
    onSurfaceVariant = VaultOnLightSecondary,

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = OnVaultErrorContainer,
    onErrorContainer = VaultErrorContainer,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = VaultWhite,
    surfaceContainer = VaultLightSurface,
    surfaceContainerHigh = Color(0xFFE9EEF0),
    surfaceContainerHighest = VaultLightSurfaceVariant,
    inverseSurface = VaultCharcoal,
    inverseOnSurface = VaultOnDarkPrimary,
    inversePrimary = Color(0xFF79D5CE),
    outline = Color(0xFF707B83),
    outlineVariant = VaultLightSurfaceVariant
)

/**
 * Top-level VaultBrain theme.
 *
 * @param darkTheme defaults to true for a vault/security aesthetic. Users can toggle in settings.
 * @param dynamicColor when true, overrides the palette with Material You wallpaper-derived colors.
 */
@Composable
fun VaultBrainTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VaultBrainDarkScheme
        else -> VaultBrainLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultBrainTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        ),
        content = {
            // Apply subtle gradient background for premium aesthetic
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.background(
                    brush = if (darkTheme) VaultBrainGradients.darkBackgroundGradient
                    else VaultBrainGradients.lightBackgroundGradient
                )
            ) {
                content()
            }
        }
    )
}
