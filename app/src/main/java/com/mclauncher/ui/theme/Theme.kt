package com.mclauncher.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Green60,
    onPrimary = Green10,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    
    secondary = Brown60,
    onSecondary = Brown10,
    secondaryContainer = Brown30,
    onSecondaryContainer = Brown90,
    
    tertiary = Blue60,
    onTertiary = Blue10,
    tertiaryContainer = Blue30,
    onTertiaryContainer = Blue90,
    
    error = Red60,
    onError = Red10,
    errorContainer = Red30,
    onErrorContainer = Red90,
    
    background = SurfaceDark,
    onBackground = Gray90,
    
    surface = SurfaceDark,
    onSurface = Gray90,
    surfaceVariant = Gray20,
    onSurfaceVariant = Gray80,
    
    outline = Gray50,
    outlineVariant = Gray30,
    
    inverseSurface = Gray90,
    inverseOnSurface = Gray20,
    inversePrimary = Green40,
    
    scrim = Color.Black.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    
    secondary = Brown40,
    onSecondary = Color.White,
    secondaryContainer = Brown90,
    onSecondaryContainer = Brown10,
    
    tertiary = Blue40,
    onTertiary = Color.White,
    tertiaryContainer = Blue90,
    onTertiaryContainer = Blue10,
    
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    
    background = SurfaceLight,
    onBackground = Gray10,
    
    surface = SurfaceLight,
    onSurface = Gray10,
    surfaceVariant = Gray95,
    onSurfaceVariant = Gray30,
    
    outline = Gray50,
    outlineVariant = Gray80,
    
    inverseSurface = Gray20,
    inverseOnSurface = Gray90,
    inversePrimary = Green80,
    
    scrim = Color.Black.copy(alpha = 0.3f)
)

@Composable
fun MCLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Extension color properties for easy access
object MCColors {
    val minecraft_green @Composable get() = MinecraftGreen
    val minecraft_brown @Composable get() = MinecraftBrown
    val minecraft_stone @Composable get() = MinecraftStone
    val minecraft_diamond @Composable get() = MinecraftDiamond
    val minecraft_gold @Composable get() = MinecraftGold
    val minecraft_redstone @Composable get() = MinecraftRedstone
    val minecraft_emerald @Composable get() = MinecraftEmerald
    val minecraft_enchant @Composable get() = MinecraftEnchant
    
    val version_release @Composable get() = Green50
    val version_snapshot @Composable get() = Blue50
    val version_old_beta @Composable get() = Brown50
    val version_old_alpha @Composable get() = Gray50
    
    val mod_fabric @Composable get() = Color(0xFFDBD0B4)
    val mod_forge @Composable get() = Color(0xFF3E4B5C)
    val mod_quilt @Composable get() = Color(0xFF9B59B6)
    val mod_neoforge @Composable get() = Color(0xFFD86518)
}
