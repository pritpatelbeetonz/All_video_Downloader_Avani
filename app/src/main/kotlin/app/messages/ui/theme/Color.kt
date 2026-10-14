package app.messages.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed brand palette from the Figma design ("messages-and-launcher"): a vivid
 * blue brand (#0057CC) replacing the earlier Material-baseline lavender. Because
 * the product ships a fixed brand identity, the full Material 3 role set is
 * specified here (not just `primary`) so every surface, container, and bubble
 * harmonises to blue rather than falling back to the purple baseline.
 *
 * The seed/icon blue is [Brand]. Dynamic (Material You) color is off by default
 * so the app matches the design on every device — see [MessagesTheme].
 */
object BrandColors {
    /** The Figma brand blue — also the launcher-icon background. */
    val Brand = Color(0xFF0057CC)
    val Failure = Color(0xFFE54848)
}

// ── Light scheme ─────────────────────────────────────────────────────────────
val LightPrimary = Color(0xFF0057CC)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD8E2FF)
val LightOnPrimaryContainer = Color(0xFF001B3F)
val LightSecondary = Color(0xFF565E71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDAE2F9)
val LightOnSecondaryContainer = Color(0xFF131C2B)
val LightTertiary = Color(0xFF1C6586)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC5E7FF)
val LightOnTertiaryContainer = Color(0xFF001E2E)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightBackground = Color(0xFFF7F8FC)
val LightOnBackground = Color(0xFF1A1B20)
val LightSurface = Color(0xFFF7F8FC)
val LightOnSurface = Color(0xFF1A1B20)
val LightSurfaceVariant = Color(0xFFE0E2EC)
val LightOnSurfaceVariant = Color(0xFF43474E)
val LightOutline = Color(0xFF74777F)
val LightOutlineVariant = Color(0xFFC4C6D0)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF1F3FA)
val LightSurfaceContainer = Color(0xFFECEDF4)
val LightSurfaceContainerHigh = Color(0xFFE6E7EF)
val LightSurfaceContainerHighest = Color(0xFFE0E2E9)
val LightInverseSurface = Color(0xFF2F3036)
val LightInverseOnSurface = Color(0xFFF1F0F7)
val LightInversePrimary = Color(0xFFADC6FF)
val LightScrim = Color(0xFF000000)

// ── Dark scheme ──────────────────────────────────────────────────────────────
val DarkPrimary = Color(0xFFADC6FF)
val DarkOnPrimary = Color(0xFF002E69)
val DarkPrimaryContainer = Color(0xFF004494)
val DarkOnPrimaryContainer = Color(0xFFD8E2FF)
val DarkSecondary = Color(0xFFBEC6DC)
val DarkOnSecondary = Color(0xFF283141)
val DarkSecondaryContainer = Color(0xFF3E4759)
val DarkOnSecondaryContainer = Color(0xFFDAE2F9)
val DarkTertiary = Color(0xFF8BCEF4)
val DarkOnTertiary = Color(0xFF00344C)
val DarkTertiaryContainer = Color(0xFF004C6B)
val DarkOnTertiaryContainer = Color(0xFFC5E7FF)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF111318)
val DarkOnBackground = Color(0xFFE2E2E9)
val DarkSurface = Color(0xFF111318)
val DarkOnSurface = Color(0xFFE2E2E9)
val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
val DarkOutline = Color(0xFF8E9099)
val DarkOutlineVariant = Color(0xFF43474E)
val DarkSurfaceContainerLowest = Color(0xFF0C0E13)
val DarkSurfaceContainerLow = Color(0xFF1A1B21)
val DarkSurfaceContainer = Color(0xFF1E2025)
val DarkSurfaceContainerHigh = Color(0xFF282A2F)
val DarkSurfaceContainerHighest = Color(0xFF33353A)
val DarkInverseSurface = Color(0xFFE2E2E9)
val DarkInverseOnSurface = Color(0xFF2F3036)
val DarkInversePrimary = Color(0xFF0057CC)
val DarkScrim = Color(0xFF000000)
