package com.raushan.phone.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Dark scheme — the original Stitch design tokens.
// ---------------------------------------------------------------------------------------------

val Primary = Color(0xFFA3C9FF)
val OnPrimary = Color(0xFF00315C)
val PrimaryContainer = Color(0xFF3399FF)
val OnPrimaryContainer = Color(0xFF00305A)

val Secondary = Color(0xFFC8C6C5)
val OnSecondary = Color(0xFF303030)
val SecondaryContainer = Color(0xFF474746)
val OnSecondaryContainer = Color(0xFFB7B5B4)

val Tertiary = Color(0xFFFFB77B)
val OnTertiary = Color(0xFF4D2700)
val TertiaryContainer = Color(0xFFE27D00)
val OnTertiaryContainer = Color(0xFF4B2600)

val Background = Color(0xFF131313)
val OnBackground = Color(0xFFE5E2E1)

val Surface = Color(0xFF131313)
val OnSurface = Color(0xFFE5E2E1)
val SurfaceVariant = Color(0xFF353534)
val OnSurfaceVariant = Color(0xFFC0C7D5)

val Outline = Color(0xFF8A919E)
val OutlineVariant = Color(0xFF404753)

val Error = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)
val ErrorContainer = Color(0xFF93000A)
val OnErrorContainer = Color(0xFFFFDAD6)

// Functional dark tokens from the brand description.
val DeepCharcoal = Color(0xFF121212)
val ElectricBlue = Color(0xFF3399FF)
val CardSurface = Color(0xFF1E1E1E)
val ElevationLevel2 = Color(0xFF242424)
val InputBackground = Color(0xFF2A2A2A)
val AvatarSurface = Color(0xFF201F1F)

// ---------------------------------------------------------------------------------------------
// Light scheme.
//
// Authored rather than derived: every token above is a dark value, so the previous LightColorScheme
// had nothing to point at and reused the dark ones. That left the app rendering a dark background in
// light mode while the roles it did not map — error, outline, surfaceVariant, tertiary — fell through
// to M3 stock light defaults, mixing two palettes in one screen.
//
// Keyed to the same ElectricBlue accent so the brand survives the switch.
// ---------------------------------------------------------------------------------------------

val LightPrimary = Color(0xFF0B5CAD)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD3E4FF)
val LightOnPrimaryContainer = Color(0xFF001C39)

val LightSecondary = Color(0xFF545F70)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD8E3F8)
val LightOnSecondaryContainer = Color(0xFF111C2B)

val LightTertiary = Color(0xFF8B5000)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDCC1)
val LightOnTertiaryContainer = Color(0xFF2C1700)

val LightBackground = Color(0xFFFCFCFF)
val LightOnBackground = Color(0xFF1A1C1E)

val LightSurface = Color(0xFFFCFCFF)
val LightOnSurface = Color(0xFF1A1C1E)
val LightSurfaceVariant = Color(0xFFDFE2EB)
val LightOnSurfaceVariant = Color(0xFF43474E)

val LightOutline = Color(0xFF73777F)
val LightOutlineVariant = Color(0xFFC3C7CF)

val LightErrorColor = Color(0xFFBA1A1A)
val LightOnErrorColor = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

/** Light counterpart of [AvatarSurface]; both map to `surfaceContainerLowest`. */
val LightAvatarSurface = Color(0xFFEDEFF3)

// ---------------------------------------------------------------------------------------------
// Call screen surfaces. Named tokens rather than inline Color.White.copy(alpha = ...) in the UI.
// ---------------------------------------------------------------------------------------------

val GlassFill = Color(0x14FFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val CallAccentGlow = Color(0x0D3399FF)

val LightGlassFill = Color(0x0A000000)
val LightGlassBorder = Color(0x14000000)

// Answer and decline. Fixed across both themes: saturated green and red rather than the brand blue and
// the pale salmon Error token. These are the most consequential controls in the app, often pressed
// under time pressure on a locked device, so they need to be unmistakable at a glance rather than two
// similar circles — and that reading must not change with the system theme.
val AcceptGreen = Color(0xFF16A34A)
val OnAcceptGreen = Color(0xFFFFFFFF)
val DeclineRed = Color(0xFFDC2626)
val OnDeclineRed = Color(0xFFFFFFFF)
