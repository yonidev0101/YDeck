package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI

/*
 * A README to read, not to edit: the Markdown most projects write (headings, paragraphs, lists, quotes, code, tables,
 * links, bold and italics) in the app's type. Each paragraph takes its own direction, so Hebrew reads right to left.
 */

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data class Item(val text: String, val marker: String, val depth: Int) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val text: String) : MdBlock()
    data class Table(val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
}

object Markdown {
    fun parse(text: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim()); para.clear() }
        val lines = text.replace("\r\n", "\n").lines()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            when {
                line.startsWith("```") || line.startsWith("~~~") -> {
                    flush()
                    val fence = line.take(3)
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) { code.appendLine(lines[i]); i++ }
                    out += MdBlock.Code(code.toString().trimEnd())
                }
                line.isEmpty() -> flush()
                Regex("^#{1,6}\\s").containsMatchIn(line) -> { flush(); out += MdBlock.Heading(line.takeWhile { it == '#' }.length, line.dropWhile { it == '#' }.trim().trimEnd('#').trim()) }
                Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(line) -> { flush(); out += MdBlock.Rule }
                line.startsWith(">") -> { flush(); out += MdBlock.Quote(line.trimStart('>').trim()) }
                line.startsWith("|") && line.endsWith("|") -> {
                    flush()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                        val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                        if (!cells.all { Regex("^:?-{2,}:?$").matches(it) }) rows += cells
                        i++
                    }
                    out += MdBlock.Table(rows)
                    continue
                }
                Regex("^(\\s*)([-*+]|\\d+[.)])\\s+").find(raw) != null -> {
                    flush()
                    val m = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)").find(raw)!!
                    val marker = m.groupValues[2].let { if (it.first().isDigit()) it.trimEnd(')').let { n -> if (n.endsWith('.')) n else "$n." } else "•" }
                    val task = m.groupValues[3].replace(Regex("^\\[( |x|X)]\\s+")) { r -> if (r.value.contains('x', true)) "☑ " else "☐ " }
                    out += MdBlock.Item(task, marker, m.groupValues[1].length / 2)
                }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(line) }
            }
            i++
        }
        flush()
        return out
    }

    /** Bold, italics, inline code and links, as spans (link targets kept as "url" annotations). */
    fun inline(text: String, code: SpanStyle, link: SpanStyle): AnnotatedString = buildAnnotatedString {
        val pattern = Regex("""(`[^`]+`)|(\*\*[^*]+\*\*|__[^_]+__)|(\*[^*\s][^*]*\*|_[^_\s][^_]*_)|(!?\[[^\]]*]\([^)]*\))""")
        var at = 0
        for (m in pattern.findAll(text)) {
            append(text.substring(at, m.range.first))
            val v = m.value
            when {
                m.groups[1] != null -> withStyle(code) { append(v.trim('`')) }
                m.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(v.drop(2).dropLast(2)) }
                m.groups[3] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.drop(1).dropLast(1)) }
                v.startsWith("!") -> {} // images: nothing to show here
                else -> {
                    val label = v.substringAfter('[').substringBefore("](")
                    val url = v.substringAfter("](").dropLast(1)
                    pushStringAnnotation("url", url)
                    withStyle(link) { append(label) }
                    pop()
                }
            }
            at = m.range.last + 1
        }
        append(text.substring(at))
    }
}

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val c = LocalDeck.current
    val blocks = remember(text) { Markdown.parse(text) }
    val code = SpanStyle(fontFamily = PlexMono, fontSize = 13.sp, background = c.surface2)
    val link = SpanStyle(color = c.accentText)
    val auto = TextStyle(textDirection = TextDirection.Content, fontFamily = PlexSans, color = c.fg)
    @Composable fun rich(s: String, style: TextStyle, mod: Modifier = Modifier) {
        val a = remember(s, c) { Markdown.inline(s, code, link) }
        ClickableText(a, style = style, modifier = mod) { off ->
            a.getStringAnnotations("url", off, off).firstOrNull()?.item?.takeIf { it.startsWith("http") }?.let { runCatching { Desktop.getDesktop().browse(URI(it)) } }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { i, b ->
            when (b) {
                is MdBlock.Heading -> rich(b.text, auto.copy(
                    fontFamily = if (b.level == 1) Display else PlexSans,
                    fontWeight = if (b.level == 1) FontWeight.Light else FontWeight.Medium,
                    fontSize = when (b.level) { 1 -> 30.sp; 2 -> 20.sp; 3 -> 17.sp; else -> 15.sp },
                    lineHeight = when (b.level) { 1 -> 38.sp; 2 -> 28.sp; else -> 24.sp },
                ), Modifier.padding(top = if (i == 0) 0.dp else if (b.level <= 2) 14.dp else 6.dp))
                is MdBlock.Para -> rich(b.text, auto.copy(fontSize = 15.sp, lineHeight = 25.sp))
                // A list item or a quote sits on the side its own text starts from (a Hebrew item's bullet on the right).
                is MdBlock.Item -> InDirectionOf(b.text) {
                    Row(Modifier.fillMaxWidth().padding(start = (b.depth * 18).dp)) {
                        Text(b.marker, color = c.muted, fontSize = 15.sp, lineHeight = 25.sp, fontFamily = PlexSans, modifier = Modifier.width(22.dp))
                        rich(b.text, auto.copy(fontSize = 15.sp, lineHeight = 25.sp), Modifier.weight(1f))
                    }
                }
                is MdBlock.Quote -> InDirectionOf(b.text) {
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
                        rich(b.text, auto.copy(fontSize = 15.sp, lineHeight = 25.sp, color = c.muted), Modifier.padding(start = 14.dp))
                    }
                }
                is MdBlock.Code -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(b.text, color = c.fg, fontSize = 13.sp, lineHeight = 21.sp, fontFamily = PlexMono, softWrap = false, style = TextStyle(textDirection = TextDirection.Ltr))
                }
                is MdBlock.Table -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.paper).padding(vertical = 6.dp)) {
                    b.rows.forEachIndexed { r, cells ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            cells.forEach { cell -> rich(cell, auto.copy(fontSize = 14.sp, fontWeight = if (r == 0) FontWeight.Medium else FontWeight.Normal), Modifier.weight(1f)) }
                        }
                    }
                }
                MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            }
        }
    }
}

/** Lays [content] out in the direction of [text]'s first letter: right to left for Hebrew or Arabic, else left to right. */
@Composable
private fun InDirectionOf(text: String, content: @Composable () -> Unit) {
    val rtl = text.firstOrNull { it.isLetter() }?.let { Character.getDirectionality(it).let { d -> d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC } } ?: false
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr, content = content)
}
