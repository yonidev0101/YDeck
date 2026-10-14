package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap

/*
 * The building blocks of the app window, so every screen speaks the same way: cards with one hairline, grouped
 * settings rows, pill buttons, segmented choices, line icons. Colours only from LocalDeck.
 */

// ── line icons (24-unit paths, drawn as 1.75 strokes) ──

enum class Glyph(val path: String) {
    SEARCH("M17 17L21 21M11 18A7 7 0 1 1 11 4A7 7 0 0 1 11 18Z"),
    PLUS("M12 6V18M6 12H18"),
    GEAR("M12 9A3 3 0 1 1 12 15A3 3 0 0 1 12 9ZM12 2V5M12 19V22M2 12H5M19 12H22M4.9 4.9L7 7M17 17L19.1 19.1M4.9 19.1L7 17M17 7L19.1 4.9"),
    BACK("M15 6L9 12L15 18"),
    NEXT("M9 6L15 12L9 18"),
    FOLDER("M3 7A2 2 0 0 1 5 5H9L11 7H19A2 2 0 0 1 21 9V17A2 2 0 0 1 19 19H5A2 2 0 0 1 3 17Z"),
    CODE("M8 7L3 12L8 17M16 7L21 12L16 17"),
    TERMINAL("M5 8L9 12L5 16M12 16H19"),
    SPARKLE("M12 3L13.8 7.6L18.5 9L13.8 10.6L12 15L10.2 10.6L5.5 9L10.2 7.4ZM19 15L19.8 17L21.8 17.8L19.8 18.6L19 20.6L18.2 18.6L16.2 17.8L18.2 17Z"),
    MORE("M5 12H5.01M12 12H12.01M19 12H19.01"),
    CHECK("M5 12L10 17L19 7"),
    PIN("M12 17V22M8 3H16L15 9L18 13H6L9 9Z"),
    PLAY("M8 5V19L19 12Z"),
    LINK("M10 14A4 4 0 0 0 15.6 14.6L18.4 11.8A4 4 0 0 0 12.8 6.2L11.4 7.6M14 10A4 4 0 0 0 8.4 9.4L5.6 12.2A4 4 0 0 0 11.2 17.8L12.6 16.4"),
    COPY("M9 9H19V19H9ZM5 15V5H15"),
    TRASH("M4 7H20M10 11V17M14 11V17M6 7L7 20H17L18 7M9 7V4H15V7"),
    EDIT("M4 20H8L19 9L15 5L4 16ZM13 7L17 11"),
    DOC("M6 3H14L18 7V21H6ZM9 12H15M9 16H13"),
    WIDGET("M3 8A4 4 0 0 1 7 4H17A4 4 0 0 1 21 8V16A4 4 0 0 1 17 20H7A4 4 0 0 1 3 16ZM7 9H11V13H7ZM13 9H17V13H13Z"),
    DOWNLOAD("M12 3V15M7 10L12 15L17 10M5 21H19"),
    DISK("M4 6C4 4.3 7.6 3 12 3S20 4.3 20 6S16.4 9 12 9S4 7.7 4 6ZM4 6V18C4 19.7 7.6 21 12 21S20 19.7 20 18V6M4 12C4 13.7 7.6 15 12 15S20 13.7 20 12"),
    CLAUDE("M12 3V21M3 12H21M5.6 5.6L18.4 18.4M18.4 5.6L5.6 18.4"),
    GLOBE("M12 3A9 9 0 1 1 12 21A9 9 0 0 1 12 3ZM3 12H21M12 3C9.5 6 9.5 18 12 21M12 3C14.5 6 14.5 18 12 21"),
    INFO("M12 3A9 9 0 1 1 12 21A9 9 0 0 1 12 3ZM12 11V16M12 8H12.01"),
    BRANCH("M6 3V15M18 9A3 3 0 1 1 18 3A3 3 0 0 1 18 9ZM6 21A3 3 0 1 1 6 15A3 3 0 0 1 6 21ZM18 9C18 13 14 14 7.5 15.5"),
    UP("M12 19V5M6 11L12 5L18 11"),
    DOWN("M12 5V19M6 13L12 19L18 13"),
    REFRESH("M20 11A8 8 0 1 0 17.7 16.6M20 5V11H14"),
    EXTERNAL("M14 4H20V10M20 4L11 13M18 14V19H5V6H10"),
    CHEVRON("M7 10L12 15L17 10"),
    MOON("M20 14.5A8 8 0 1 1 9.5 4A6.5 6.5 0 0 0 20 14.5Z"),
    PHONE("M7 4A2 2 0 0 1 9 2H15A2 2 0 0 1 17 4V20A2 2 0 0 1 15 22H9A2 2 0 0 1 7 20ZM11 18.5H13"),
}

private val glyphs = ConcurrentHashMap<Glyph, ImageVector>()

private fun vectorOf(g: Glyph): ImageVector = glyphs.getOrPut(g) {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(PathParser().parsePathString(g.path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        .build()
}

/** A line icon; [BACK] and [NEXT] turn with the reading direction. */
@Composable
fun GlyphIcon(g: Glyph, size: Dp = 18.dp, tint: Color = LocalDeck.current.fg) {
    val flip = (g == Glyph.BACK || g == Glyph.NEXT) && LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(androidx.compose.ui.graphics.vector.rememberVectorPainter(vectorOf(g)), null, tint = tint,
        modifier = Modifier.size(size).then(if (flip) Modifier.graphicsLayer { scaleX = -1f } else Modifier))
}

// ── text ──

/**
 * The direction a paragraph reads in: right to left when it holds Hebrew, even when it opens with a Latin word
 * ("YDeck הוא…"), which the first-letter rule would read left to right.
 */
fun dirOf(text: String): TextDirection =
    if (text.any { it in '\u0590'..'\u05FF' }) TextDirection.Rtl else TextDirection.Content

/** A page's title: the brand's light display face. */
@Composable
fun PageTitle(text: String, size: Int = 34) {
    Text(text, color = LocalDeck.current.fg, fontSize = size.sp, lineHeight = (size + 6).sp, fontFamily = Display, fontWeight = FontWeight.Light)
}

/** A section's heading on a page, and its one action at the end. */
@Composable
fun Section(title: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = LocalDeck.current.fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        action()
    }
}

/** A small grey label over a group. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = LocalDeck.current.faint, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
fun Mono(text: String, color: Color = LocalDeck.current.muted, size: Int = 12, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = size.sp, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(textDirection = TextDirection.Ltr), modifier = modifier)
}

// ── surfaces ──

/** A card: one hairline, soft corners. */
@Composable
fun Card(modifier: Modifier = Modifier, fill: Color? = null, padding: Dp = 22.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).then(if (fill != null) Modifier.background(fill) else Modifier.border(1.dp, c.line, shape))
        .padding(padding), content = content)
}

/** Rows grouped in one rounded box, a hairline between each (settings, lists). */
@Composable
fun Group(label: String? = null, modifier: Modifier = Modifier, content: @Composable GroupScope.() -> Unit) {
    val c = LocalDeck.current
    Column(modifier.fillMaxWidth()) {
        if (label != null) GroupLabel(label)
        val shape = RoundedCornerShape(18.dp)
        Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.line, shape)) { GroupScope(this).content() }
    }
}

class GroupScope(private val column: ColumnScope) : ColumnScope by column {
    private var first = true

    /** A row of the group; the hairline above it comes by itself. */
    @Composable
    fun Item(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
        val c = LocalDeck.current
        if (!first) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(c.surface2))
        first = false
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** A settings row: a title (and a quiet value), then its control. */
@Composable
fun GroupScope.Setting(title: String, value: String? = null, onClick: (() -> Unit)? = null, control: @Composable RowScope.() -> Unit = {}) {
    val c = LocalDeck.current
    Item(onClick) {
        Text(title, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, maxLines = 1, overflow = TextOverflow.Ellipsis)
        control()
        if (onClick != null) GlyphIcon(Glyph.NEXT, 16.dp, c.faint)
    }
}

@Composable
fun Toggle(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalDeck.current
    Switch(on, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White,
        uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.line, uncheckedThumbColor = c.faint))
}

/** One of a few: the brand's segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalDeck.current
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(c.surface2).padding(3.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(label, color = if (on) c.fg else c.muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) (if (c == Dark) c.line else Color.White) else Color.Transparent)
                    .clickable { onSelect(value) }.padding(horizontal = 13.dp, vertical = 6.dp))
        }
    }
}

// ── buttons ──

enum class Tone { PRIMARY, DARK, SOFT, TINT, PLAIN }

/** The pill button: indigo for the one main action, dark for a tool, soft grey otherwise, tint for Claude's. */
@Composable
fun Pill(text: String, tone: Tone = Tone.SOFT, glyph: Glyph? = null, glyphTint: Color? = null, enabled: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    val c = LocalDeck.current
    val (bg, fg) = when (tone) {
        Tone.PRIMARY -> c.accent to Color.White
        Tone.DARK -> c.fg to c.bg
        Tone.SOFT -> c.surface2 to c.fg
        Tone.TINT -> c.accentTint to c.accentText
        Tone.PLAIN -> Color.Transparent to c.accentText
    }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(if (!enabled) c.surface2 else if (hovered && tone != Tone.PLAIN) bg.copy(alpha = 0.88f) else bg)
        .hoverable(hover).clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (glyph != null) { GlyphIcon(glyph, if (small) 14.dp else 16.dp, glyphTint ?: fg); Spacer(Modifier.width(7.dp)) }
        Text(text, color = if (enabled) fg else c.faint, fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, maxLines = 1)
    }
}

/** A round icon-only button (with its name for screen readers in [label]). */
@Composable
fun IconButton(glyph: Glyph, label: String, size: Dp = 36.dp, tint: Color = LocalDeck.current.fg, filled: Boolean = true, onClick: () -> Unit) {
    val c = LocalDeck.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(if (filled || hovered) c.surface2 else Color.Transparent).hoverable(hover)
        .clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) { GlyphIcon(glyph, size * 0.5f, tint) }
}

/** A word that does something (a link-like action in a row). */
@Composable
fun TextAction(text: String, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalDeck.current
    Text(text, color = when { !enabled -> c.line; accent -> c.accentText; else -> c.fg }, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp))
}

// ── states ──

@Composable
fun Dot(color: Color, size: Dp = 7.dp) {
    // The brand's one motion: a state changes (open → closed) in a soft 240 ms, never a jump.
    val shown by androidx.compose.animation.animateColorAsState(color, androidx.compose.animation.core.tween(240))
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 2)).background(shown))
}

/** A row of a project's state: a dot, what it is, and a quiet detail at the end. */
@Composable
fun GroupScope.StateRow(dot: Color, text: String, detail: String? = null, detailColor: Color? = null, mono: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = LocalDeck.current
    Item(onClick) {
        Dot(dot)
        Text(text, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            if (mono) Mono(detail, detailColor ?: c.muted) else Text(detail, color = detailColor ?: c.muted, fontSize = 12.sp, fontFamily = PlexSans, maxLines = 1)
        }
    }
}

// ── fields ──

/** A label over a rounded field; [ltr] for addresses, codes and commands. */
@Composable
fun Field(title: String?, value: String, onChange: (String) -> Unit, placeholder: String = "", ltr: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalDeck.current
    Column(modifier.fillMaxWidth()) {
        if (title != null) Text(title, color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(bottom = 6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            val style = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = if (ltr) PlexMono else PlexSans, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content)
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.faint))
            CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
                BasicTextField(value, onChange, singleLine = true, textStyle = style, cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
