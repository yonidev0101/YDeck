package com.yonidev.ydeck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.R

/*
 * YDeck's identity since 1.10 on Windows (store/brand): "Nothing left open". Night ground, hairlines instead of boxes
 * and shadows, thin Noto Sans Hebrew titles over IBM Plex Sans Hebrew. Colour only says a state: amber = open
 * (something waits for you), indigo = working, green = closed. The same tokens as the Windows app's dark theme.
 */

data class DeckColors(
    /** The page. Lists and groups sit directly on it, separated by [line]. */
    val bg: Color,
    /** Sheets and anything that must lift off the page. */
    val surface: Color,
    /** Fields, chips, icon tiles (YVoice "surface-raised"). */
    val surface2: Color,
    val fg: Color,
    val muted: Color,
    /** Labels and captions only; not body text. */
    val faint: Color,
    val line: Color,
    val good: Color,
    val warn: Color,
    val danger: Color,
    /** Indigo fill for the app's own primary buttons (white text on top). */
    val accent: Color,
    /** Indigo for text, links and selected states on the page; lighter in dark mode. */
    val accentText: Color,
    /** Light indigo wash behind an accent icon or a selected chip. */
    val accentTint: Color,
    /** The states: open (a next step waits), closed. Working is [accent]. */
    val open: Color = Color(0xFFF5A524),
    val openText: Color = Color(0xFFF5C46B),
    val closed: Color = Color(0xFF34C77B),
    /** Grouped grounds a step above the page. */
    val paper: Color = Color(0xFF111113),
)

private val Light = DeckColors(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1F1EF),
    fg = Color(0xFF0B0B0C), muted = Color(0xFF5C5C61), faint = Color(0xFF8A8A90), line = Color(0xFFE7E7E4),
    good = Color(0xFF2F9E6A), warn = Color(0xFF9A5B00), danger = Color(0xFFD13438),
    accent = Color(0xFF3D35E0), accentText = Color(0xFF3D35E0), accentTint = Color(0xFFECEBFD),
)

private val Dark = DeckColors(
    bg = Color(0xFF0B0B0D), surface = Color(0xFF16161A), surface2 = Color(0xFF1C1C1F),
    fg = Color(0xFFF4F4F5), muted = Color(0xFFA1A1A8), faint = Color(0xFF8A8A90), line = Color(0xFF26262C),
    good = Color(0xFF4ADE80), warn = Color(0xFFF5C46B), danger = Color(0xFFFF6B6F),
    accent = Color(0xFF5A52F5), accentText = Color(0xFF9C97FF), accentTint = Color(0xFF1D1B3D),
)

val LocalDeck = staticCompositionLocalOf { Dark }

/** IBM Plex Sans Hebrew (Hebrew + Latin) for the whole interface, as in YVoice. */
val PlexSans = FontFamily(
    Font(R.font.plex_sans_hebrew_regular, FontWeight.Normal),
    Font(R.font.plex_sans_hebrew_medium, FontWeight.Medium),
    Font(R.font.plex_sans_hebrew_semibold, FontWeight.SemiBold),
    Font(R.font.plex_sans_hebrew_bold, FontWeight.Bold),
)

/** Plex Mono for addresses, ids and numbers. */
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

/** Noto Sans Hebrew, thin: the large titles. One variable file. */
@OptIn(ExperimentalTextApi::class)
val Display = FontFamily(
    Font(R.font.noto_sans_hebrew, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(R.font.noto_sans_hebrew, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.noto_sans_hebrew, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
)

private val Base = Typography()
private fun TextStyle.sans(weight: FontWeight? = null) = copy(fontFamily = PlexSans, fontWeight = weight ?: fontWeight)

private val DeckTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.02).em),
    displayMedium = Base.displayMedium.copy(fontFamily = Display, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.02).em),
    displaySmall = Base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.Light, letterSpacing = (-0.015).em),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.Light, letterSpacing = (-0.015).em),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Light, letterSpacing = (-0.01).em),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em),
    titleLarge = Base.titleLarge.sans(FontWeight.SemiBold),
    titleMedium = Base.titleMedium.sans(FontWeight.Medium),
    titleSmall = Base.titleSmall.sans(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.sans(),
    bodyMedium = Base.bodyMedium.sans(),
    bodySmall = Base.bodySmall.sans(),
    labelLarge = Base.labelLarge.sans(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.sans(FontWeight.Medium),
    labelSmall = Base.labelSmall.sans(FontWeight.Medium),
)

/** Small labels above a group ("פרויקטים", "קישורים"). */
val SectionLabelStyle = TextStyle(fontFamily = PlexSans, fontSize = 12.sp, letterSpacing = 0.02.em, fontWeight = FontWeight.Medium)

@Composable
fun DeckTheme(content: @Composable () -> Unit) {
    // Dark only: the brand's night, like the Windows app and the site.
    val dark = true
    val c = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.fg, onPrimary = c.bg, tertiary = c.accentText, onTertiary = Color.White,
            background = c.bg, onBackground = c.fg, surface = c.bg, onSurface = c.fg,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = Color(0xFF3A3A3F), outlineVariant = c.line,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, surfaceContainerLow = c.surface,
            surfaceContainerLowest = c.bg, surfaceContainerHighest = Color(0xFF242428), surfaceTint = Color.Transparent,
            error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.fg, onPrimary = c.bg, tertiary = c.accentText, onTertiary = Color.White,
            background = c.bg, onBackground = c.fg, surface = c.bg, onSurface = c.fg,
            surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = Color(0xFFD5D5D1), outlineVariant = c.line,
            surfaceContainer = Color(0xFFF6F6F4), surfaceContainerHigh = c.surface2, surfaceContainerLow = Color(0xFFFAFAF9),
            surfaceContainerLowest = Color.White, surfaceContainerHighest = Color(0xFFEBEBE8), surfaceTint = Color.Transparent,
            error = c.danger,
        )
    }
    // Hebrew UI: right-to-left even when the phone itself is set to an LTR language.
    CompositionLocalProvider(LocalDeck provides c, LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, typography = DeckTypography, content = content)
    }
}
