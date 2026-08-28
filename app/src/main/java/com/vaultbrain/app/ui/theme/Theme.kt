package com.vaultbrain.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vaultbrain.core.common.theme.*

/**
 * VaultBrain Dark color scheme — the default experience.
 * Evokes a secure, premium vault aesthetic.
 */
private val VaultBrainDarkScheme = darkColorScheme(
    primary = VaultTeal,
    onPrimary = Color.White,
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

    outline = VaultOutline,
    outlineVariant = VaultOutlineVariant
)

/**
 * VaultBrain Light color scheme — opt-in for users who prefer light mode.
 */
private val VaultBrainLightScheme = lightColorScheme(
    primary = VaultTeal,
    onPrimary = Color.White,
    primaryContainer = VaultTealLight,
    onPrimaryContainer = VaultTealDark,

    secondary = VaultAmber,
    onSecondary = Color.Black,
    secondaryContainer = VaultAmberLight,
    onSecondaryContainer = VaultAmberDark,

    tertiary = VaultLavender,
    onTertiary = Color.White,
    tertiaryContainer = OnVaultLavenderContainer,
    onTertiaryContainer = VaultLavenderDark,

    background = VaultWhite,
    onBackground = VaultOnLightPrimary,
    surface = VaultWhite,
    onSurface = VaultOnLightPrimary,
    surfaceVariant = VaultLightSurfaceVariant,
    onSurfaceVariant = VaultOnLightSecondary,

    error = VaultError,
    onError = Color.White,
    errorContainer = OnVaultErrorContainer,
    onErrorContainer = VaultErrorContainer,

    outline = VaultLightOutline,
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
        content = content
    )
}
