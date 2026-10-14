package app.messages.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-aware surfaces for the Figma-matched screens. The design is light-only,
 * so these return the exact Figma light values in light mode and fall back to
 * the Material dark palette in dark mode — keeping the app's dark theme working
 * instead of leaving these screens stuck on a light canvas.
 *
 * Reading the active scheme's surface luminance respects the app's ThemeMode
 * override (System/Light/Dark), unlike `isSystemInDarkTheme()` which only sees
 * the OS setting.
 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Screen canvas — the Figma gray (#F4F6FA) in light, the theme surface in dark. */
@Composable
fun appScreenBg(): Color =
    if (isDarkTheme()) MaterialTheme.colorScheme.surface else Color(0xFFF4F6FA)

/** Elevated card — white in light (per the Figma), an elevated dark surface in dark. */
@Composable
fun appCardBg(): Color =
    if (isDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else Color(0xFFFFFFFF)
