package com.yonidev.ydeck.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/*
 * Code in colour, as VS Code's Dark+ shows it: a small scanner per kind of language (C-like, Python and the like,
 * JSON, YAML/TOML, markup, CSS, Markdown) that finds comments, strings, numbers, keywords, types, calls and tags.
 * Not a parser: good enough to read code on a phone.
 */
object DarkPlus {
    val bg = Color(0xFF1E1E1E)
    val bar = Color(0xFF252526)
    val fg = Color(0xFFD4D4D4)
    val gutter = Color(0xFF858585)
    val line = Color(0xFF333333)
    val keyword = Color(0xFF569CD6)
    val control = Color(0xFFC586C0)
    val string = Color(0xFFCE9178)
    val comment = Color(0xFF6A9955)
    val number = Color(0xFFB5CEA8)
    val type = Color(0xFF4EC9B0)
    val function = Color(0xFFDCDCAA)
    val property = Color(0xFF9CDCFE)
    val selector = Color(0xFFD7BA7D)
    val added = Color(0x3323863A)
    val addedMark = Color(0xFF3FB950)
    val removed = Color(0x33DA3633)
    val removedMark = Color(0xFFF85149)
    val hunk = Color(0xFF264F78)
}

enum class Tok(val style: SpanStyle) {
    KEYWORD(SpanStyle(color = DarkPlus.keyword)),
    CONTROL(SpanStyle(color = DarkPlus.control)),
    STRING(SpanStyle(color = DarkPlus.string)),
    COMMENT(SpanStyle(color = DarkPlus.comment, fontStyle = FontStyle.Italic)),
    NUMBER(SpanStyle(color = DarkPlus.number)),
    TYPE(SpanStyle(color = DarkPlus.type)),
    FUNCTION(SpanStyle(color = DarkPlus.function)),
    PROPERTY(SpanStyle(color = DarkPlus.property)),
    SELECTOR(SpanStyle(color = DarkPlus.selector)),
    HEADING(SpanStyle(color = DarkPlus.keyword, fontWeight = FontWeight.Bold)),
    BOLD(SpanStyle(fontWeight = FontWeight.Bold)),
}

private class Span(val start: Int, val end: Int, val tok: Tok)

private class Lang(
    val lineComments: List<String> = emptyList(),
    val block: Pair<String, String>? = null,
    val strings: List<String> = listOf("\"", "'"),
    val keywords: Set<String> = emptySet(),
    val control: Set<String> = emptySet(),
    val annotations: Boolean = false,
)

private fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()

private val CONTROL_C = words("if else when while for do return break continue throw try catch finally switch case default yield await goto")
private val CONST = "true false null undefined None True False nil this self super it"

private val KOTLIN = Lang(listOf("//"), "/*" to "*/", listOf("\"\"\"", "\"", "'"),
    words("package import class interface object fun val var typealias constructor init companion data sealed enum annotation open abstract override private protected public internal inline suspend operator infix lateinit const by in is as out vararg reified crossinline noinline tailrec external expect actual value where get set $CONST"),
    CONTROL_C, annotations = true)
private val JAVA = Lang(listOf("//"), "/*" to "*/", listOf("\"\"\"", "\"", "'"),
    words("package import class interface enum extends implements new public private protected static final abstract void int long short byte char boolean float double var record sealed permits synchronized volatile transient native instanceof throws $CONST"),
    CONTROL_C, annotations = true)
private val JS = Lang(listOf("//"), "/*" to "*/", listOf("`", "\"", "'"),
    words("import export from as default class extends new function const let var typeof instanceof in of delete void async static get set interface type enum implements public private protected readonly declare namespace module keyof infer satisfies abstract $CONST"),
    CONTROL_C, annotations = true)
private val C_LIKE = Lang(listOf("//"), "/*" to "*/", listOf("\"", "'", "`"),
    words("fn let mut pub use mod struct enum impl trait type where const static ref move as dyn crate unsafe extern func package import var go defer chan map interface select struct namespace using class public private protected internal static readonly void int long string bool float double char byte var new override virtual abstract sealed async include define typedef unsigned signed auto sizeof template typename extends implements final late required dynamic library part $CONST"),
    CONTROL_C + words("loop match unless elif"), annotations = true)
private val PYTHON = Lang(listOf("#"), null, listOf("\"\"\"", "'''", "\"", "'"),
    words("def class import from as lambda global nonlocal pass with async not and or is in del print $CONST"),
    words("if elif else while for return break continue raise try except finally yield await assert"), annotations = true)
private val SHELL = Lang(listOf("#"), null, listOf("\"", "'"),
    words("function local export echo set unset source alias readonly declare param begin process end"),
    words("if then else elif fi for while do done case esac in return exit foreach switch"))
private val SQL = Lang(listOf("--"), "/*" to "*/", listOf("'", "\""),
    words("select from where insert into values update set delete create table index view drop alter add column primary key foreign references not null default unique and or as join left right inner outer on group by order having limit offset distinct union all case when then else end returning begin commit integer text real blob varchar int"))

private val C_EXT = setOf("rs", "go", "c", "h", "cpp", "hpp", "cc", "cs", "swift", "dart", "php", "scala", "groovy", "gradle")
private val SHELL_EXT = setOf("sh", "bash", "zsh", "ps1", "psm1", "bat", "cmd", "rb", "r", "pl", "makefile", "dockerfile")

/** Colours [text] as a file named [name] would be; the spans, in order. */
private fun spans(text: String, name: String): List<Span> {
    val n = name.lowercase()
    val ext = n.substringAfterLast('.', n)
    return when {
        ext == "kt" || ext == "kts" -> code(text, KOTLIN)
        ext == "java" -> code(text, JAVA)
        ext in setOf("js", "mjs", "cjs", "jsx", "ts", "tsx", "mts", "cts") -> code(text, JS)
        ext in C_EXT -> code(text, C_LIKE)
        ext == "py" -> code(text, PYTHON)
        ext in SHELL_EXT || n == "dockerfile" || n == "makefile" || n == "gradlew" -> code(text, SHELL)
        ext == "sql" -> code(text, SQL)
        ext in setOf("json", "jsonc", "json5", "webmanifest") -> json(text)
        ext in setOf("yml", "yaml", "toml", "ini", "cfg", "conf", "properties", "env", "gitignore", "gitattributes", "editorconfig", "example") -> keys(text)
        ext in setOf("html", "htm", "xml", "svg", "vue", "svelte", "xaml", "plist") -> markup(text)
        ext in setOf("css", "scss", "sass", "less") -> css(text)
        ext in setOf("md", "markdown", "mdx") -> markdown(text)
        else -> emptyList()
    }
}

private fun Char.ident() = isLetterOrDigit() || this == '_' || this == '$'

/** The end of a string opened by [q] at [i] (past its closing quote); one-quote strings end at the line's end too. */
private fun stringEnd(t: String, i: Int, q: String): Int {
    var j = i + q.length
    val multi = q.length == 3 || q == "`"
    while (j < t.length) {
        if (t[j] == '\\' && q != "'''" ) { j += 2; continue }
        if (t.startsWith(q, j)) return j + q.length
        if (!multi && t[j] == '\n') return j
        j++
    }
    return t.length
}

private fun code(t: String, l: Lang): List<Span> {
    val out = ArrayList<Span>()
    var i = 0
    while (i < t.length) {
        val ch = t[i]
        val block = l.block
        when {
            block != null && t.startsWith(block.first, i) -> {
                val e = t.indexOf(block.second, i + block.first.length).let { if (it < 0) t.length else it + block.second.length }
                out += Span(i, e, Tok.COMMENT); i = e
            }
            l.lineComments.any { t.startsWith(it, i) } -> {
                val e = t.indexOf('\n', i).let { if (it < 0) t.length else it }
                out += Span(i, e, Tok.COMMENT); i = e
            }
            l.strings.any { t.startsWith(it, i) } -> {
                val q = l.strings.first { t.startsWith(it, i) }
                val e = stringEnd(t, i, q)
                out += Span(i, e, Tok.STRING); i = maxOf(e, i + 1)
            }
            ch.isDigit() && (i == 0 || !t[i - 1].ident()) -> {
                var e = i + 1
                while (e < t.length && (t[e].isLetterOrDigit() || t[e] == '_' || (t[e] == '.' && e + 1 < t.length && t[e + 1].isDigit()))) e++
                out += Span(i, e, Tok.NUMBER); i = e
            }
            ch == '@' && l.annotations && i + 1 < t.length && t[i + 1].isLetter() -> {
                var e = i + 1
                while (e < t.length && (t[e].ident() || t[e] == '.')) e++
                out += Span(i, e, Tok.FUNCTION); i = e
            }
            ch.isLetter() || ch == '_' || ch == '$' -> {
                var e = i + 1
                while (e < t.length && t[e].ident()) e++
                val w = t.substring(i, e)
                var k = e
                while (k < t.length && t[k] == ' ') k++
                val tok = when {
                    w in l.control -> Tok.CONTROL
                    w in l.keywords -> Tok.KEYWORD
                    k < t.length && t[k] == '(' -> Tok.FUNCTION
                    w[0].isUpperCase() && w.length > 1 -> Tok.TYPE
                    else -> null
                }
                if (tok != null) out += Span(i, e, tok)
                i = e
            }
            else -> i++
        }
    }
    return out
}

private fun json(t: String): List<Span> {
    val out = ArrayList<Span>()
    var i = 0
    while (i < t.length) {
        val ch = t[i]
        when {
            t.startsWith("//", i) -> { val e = t.indexOf('\n', i).let { if (it < 0) t.length else it }; out += Span(i, e, Tok.COMMENT); i = e }
            ch == '"' -> {
                val e = stringEnd(t, i, "\"")
                var k = e
                while (k < t.length && t[k].isWhitespace()) k++
                out += Span(i, e, if (k < t.length && t[k] == ':') Tok.PROPERTY else Tok.STRING); i = maxOf(e, i + 1)
            }
            ch == '-' || ch.isDigit() -> { var e = i + 1; while (e < t.length && (t[e].isDigit() || t[e] in ".eE+-")) e++; out += Span(i, e, Tok.NUMBER); i = e }
            t.startsWith("true", i) || t.startsWith("null", i) -> { out += Span(i, i + 4, Tok.KEYWORD); i += 4 }
            t.startsWith("false", i) -> { out += Span(i, i + 5, Tok.KEYWORD); i += 5 }
            else -> i++
        }
    }
    return out
}

/** YAML, TOML, .ini, .properties, .env: comments, keys, sections, strings, numbers. */
private fun keys(t: String): List<Span> {
    val out = ArrayList<Span>()
    var start = 0
    for (line in t.split('\n')) {
        val trimmed = line.trimStart()
        val lead = line.length - trimmed.length
        when {
            trimmed.startsWith("#") || trimmed.startsWith(";") -> out += Span(start + lead, start + line.length, Tok.COMMENT)
            trimmed.startsWith("[") -> out += Span(start + lead, start + line.length, Tok.TYPE)
            else -> {
                val m = Regex("^(\\s*-?\\s*)([\\w.\"'@/-]+)(\\s*[:=])").find(line)
                var rest = 0
                if (m != null) {
                    out += Span(start + m.groups[2]!!.range.first, start + m.groups[2]!!.range.last + 1, Tok.PROPERTY)
                    rest = m.range.last + 1
                }
                val value = line.substring(rest)
                val v = value.trim()
                val vs = start + rest + value.indexOf(v)
                when {
                    v.isEmpty() -> {}
                    v.startsWith("\"") || v.startsWith("'") -> out += Span(vs, vs + v.length, Tok.STRING)
                    v.matches(Regex("-?[0-9][0-9._]*")) -> out += Span(vs, vs + v.length, Tok.NUMBER)
                    v in setOf("true", "false", "null", "yes", "no", "on", "off", "~") -> out += Span(vs, vs + v.length, Tok.KEYWORD)
                    m != null -> out += Span(vs, vs + v.length, Tok.STRING)
                }
            }
        }
        start += line.length + 1
    }
    return out
}

private fun markup(t: String): List<Span> {
    val out = ArrayList<Span>()
    var i = 0
    while (i < t.length) {
        when {
            t.startsWith("<!--", i) -> { val e = t.indexOf("-->", i).let { if (it < 0) t.length else it + 3 }; out += Span(i, e, Tok.COMMENT); i = e }
            t[i] == '<' && i + 1 < t.length && (t[i + 1].isLetter() || t[i + 1] == '/' || t[i + 1] == '!' || t[i + 1] == '?') -> {
                var j = i + 1
                if (t[j] == '/' || t[j] == '!' || t[j] == '?') j++
                val ns = j
                while (j < t.length && (t[j].isLetterOrDigit() || t[j] in "-_:.")) j++
                out += Span(ns, j, Tok.KEYWORD)
                // Inside the tag: attributes and their values, up to '>'.
                while (j < t.length && t[j] != '>') {
                    when {
                        t[j] == '"' || t[j] == '\'' -> { val e = t.indexOf(t[j], j + 1).let { if (it < 0) t.length else it + 1 }; out += Span(j, e, Tok.STRING); j = e }
                        t[j].isLetter() -> { val s = j; while (j < t.length && (t[j].isLetterOrDigit() || t[j] in "-_:.@")) j++; out += Span(s, j, Tok.PROPERTY) }
                        else -> j++
                    }
                }
                i = j + 1
            }
            else -> i++
        }
    }
    return out
}

private fun css(t: String): List<Span> {
    val out = ArrayList<Span>()
    var i = 0
    var depth = 0
    while (i < t.length) {
        val ch = t[i]
        when {
            t.startsWith("/*", i) -> { val e = t.indexOf("*/", i + 2).let { if (it < 0) t.length else it + 2 }; out += Span(i, e, Tok.COMMENT); i = e }
            t.startsWith("//", i) -> { val e = t.indexOf('\n', i).let { if (it < 0) t.length else it }; out += Span(i, e, Tok.COMMENT); i = e }
            ch == '"' || ch == '\'' -> { val e = stringEnd(t, i, ch.toString()); out += Span(i, e, Tok.STRING); i = maxOf(e, i + 1) }
            ch == '{' -> { depth++; i++ }
            ch == '}' -> { depth = (depth - 1).coerceAtLeast(0); i++ }
            ch == '@' -> { var e = i + 1; while (e < t.length && t[e].ident()) e++; out += Span(i, e, Tok.CONTROL); i = e }
            ch.isDigit() || (ch == '#' && depth > 0) -> { var e = i + 1; while (e < t.length && (t[e].isLetterOrDigit() || t[e] == '.' || t[e] == '%')) e++; out += Span(i, e, Tok.NUMBER); i = e }
            depth > 0 && (ch.isLetter() || ch == '-') -> {
                var e = i + 1
                while (e < t.length && (t[e].isLetterOrDigit() || t[e] == '-')) e++
                var k = e; while (k < t.length && t[k] == ' ') k++
                out += Span(i, e, if (k < t.length && t[k] == ':') Tok.PROPERTY else Tok.STRING); i = e
            }
            depth == 0 && !ch.isWhitespace() -> {
                val e = t.indexOfAny(charArrayOf('{', '\n'), i).let { if (it < 0) t.length else it }
                out += Span(i, e, Tok.SELECTOR); i = e
            }
            else -> i++
        }
    }
    return out
}

private fun markdown(t: String): List<Span> {
    val out = ArrayList<Span>()
    var start = 0
    var fence = false
    for (line in t.split('\n')) {
        val s = line.trimStart()
        when {
            s.startsWith("```") || s.startsWith("~~~") -> { fence = !fence; out += Span(start, start + line.length, Tok.COMMENT) }
            fence -> out += Span(start, start + line.length, Tok.STRING)
            s.startsWith("#") -> out += Span(start, start + line.length, Tok.HEADING)
            s.startsWith(">") -> out += Span(start, start + line.length, Tok.COMMENT)
            else -> {
                Regex("^\\s*([-*+]|\\d+\\.)\\s").find(line)?.let { out += Span(start + it.groups[1]!!.range.first, start + it.groups[1]!!.range.last + 1, Tok.KEYWORD) }
                for (m in Regex("`[^`]+`|\\*\\*[^*]+\\*\\*|\\[[^\\]]*\\]\\([^)]*\\)").findAll(line)) {
                    val v = m.value
                    val tok = when { v.startsWith("`") -> Tok.STRING; v.startsWith("**") -> Tok.BOLD; else -> Tok.TYPE }
                    out += Span(start + m.range.first, start + m.range.last + 1, tok)
                }
            }
        }
        start += line.length + 1
    }
    return out.sortedBy { it.start }
}

/** The whole text in colour (for the editor). */
fun highlight(text: String, name: String): AnnotatedString {
    val b = AnnotatedString.Builder(text)
    for (s in spans(text, name)) if (s.end > s.start) b.addStyle(s.tok.style, s.start, minOf(s.end, text.length))
    return b.toAnnotatedString()
}

/** Each line in colour (for the reader: a long file draws only the lines in sight). */
fun highlightLines(text: String, name: String): List<AnnotatedString> {
    val all = spans(text, name)
    val lines = text.split('\n')
    val out = ArrayList<AnnotatedString>(lines.size)
    var start = 0
    var k = 0
    for (line in lines) {
        val end = start + line.length
        val b = AnnotatedString.Builder(line)
        while (k < all.size && all[k].end <= start) k++
        var j = k
        while (j < all.size && all[j].start < end) {
            val s = all[j]
            val a = maxOf(s.start, start) - start
            val z = minOf(s.end, end) - start
            if (z > a) b.addStyle(s.tok.style, a, z)
            j++
        }
        out += b.toAnnotatedString()
        start = end + 1
    }
    return out
}
