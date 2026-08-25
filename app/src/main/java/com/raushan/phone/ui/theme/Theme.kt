package com.raushan.phone.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    // Lets the call screens read the avatar surface as a Material role instead of importing the raw
    // token, which is what hard-locked them to dark.
    surfaceContainerLowest = AvatarSurface,
    surfaceContainerLow = CardSurface,
    surfaceContainer = ElevationLevel2,
    surfaceContainerHigh = InputBackground,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

/**
 * Every one of the 24 Material roles is mapped here.
 *
 * The previous version mapped only 14, and pointed all of them at *dark* tokens. So light mode showed a
 * dark background while the unmapped roles — error, outline, surfaceVariant, tertiary — silently fell
 * back to M3 stock light defaults, producing two palettes on one screen.
 */
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightAvatarSurface,
    surfaceContainerLow = LightSurfaceVariant,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightOutlineVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightErrorColor,
    onError = LightOnErrorColor,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

/**
 * Call-screen colours that have no Material role.
 *
 * The glass fills and the accent glow are brand surfaces, and accept/decline are semantic rather than
 * thematic. Carried in a [CompositionLocal] instead of imported as raw top-level values, which is what
 * previously hard-locked the call screens to the dark palette.
 */
@Immutable
data class CallColors(
    val glassFill: Color,
    val glassBorder: Color,
    val accentGlow: Color,
    val accept: Color,
    val onAccept: Color,
    val decline: Color,
    val onDecline: Color,
)

private val DarkCallColors = CallColors(
    glassFill = GlassFill,
    glassBorder = GlassBorder,
    accentGlow = CallAccentGlow,
    accept = AcceptGreen,
    onAccept = OnAcceptGreen,
    decline = DeclineRed,
    onDecline = OnDeclineRed,
)

private val LightCallColors = DarkCallColors.copy(
    glassFill = LightGlassFill,
    glassBorder = LightGlassBorder,
)

private val LocalCallColors = staticCompositionLocalOf { DarkCallColors }

/** Accessor for [CallColors] inside the theme. */
val callColors: CallColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCallColors.current

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun PhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            // window.statusBarColor used to be set here. It is deprecated and a no-op from API 35,
            // and this project targets SDK 36 — the app draws its own background edge-to-edge, so the
            // only thing still needed is telling the system which icon tint to use.
            val activity = view.context.findActivity() ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalCallColors provides if (darkTheme) DarkCallColors else LightCallColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Walks the context chain instead of casting.
 *
 * `view.context as Activity` crashed outright if the theme was ever hosted in a `ComposeView` that was
 * not directly under an Activity.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
