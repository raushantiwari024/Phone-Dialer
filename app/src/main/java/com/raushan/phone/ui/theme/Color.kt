package com.raushan.phone.ui.theme

import androidx.compose.ui.graphics.Color

// Stitch Design Tokens - Named Colors
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

// Functional Colors from Brand Description
val DeepCharcoal = Color(0xFF121212)
val ElectricBlue = Color(0xFF3399FF)
val CardSurface = Color(0xFF1E1E1E)
val ElevationLevel2 = Color(0xFF242424)
val InputBackground = Color(0xFF2A2A2A)
val AvatarSurface = Color(0xFF201F1F)

// Call screen surfaces. Named tokens rather than inline Color.White.copy(alpha = ...) in the UI.
val GlassFill = Color(0x14FFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val CallAccentGlow = Color(0x0D3399FF)

// Answer and decline. Saturated green and red rather than the brand blue and the pale salmon Error
// token: these are the most consequential controls in the app, often pressed under time pressure on a
// locked device, so they need to be unmistakable at a glance rather than two similar circles.
val AcceptGreen = Color(0xFF16A34A)
val OnAcceptGreen = Color(0xFFFFFFFF)
val DeclineRed = Color(0xFFDC2626)
val OnDeclineRed = Color(0xFFFFFFFF)
