package com.yonidev.ydeck.desktop

import java.util.Locale

/*
 * YDeck speaks English and Hebrew, both whole: every string is written in both where it's used (tr), and the layout
 * turns right to left in Hebrew. The language is the one chosen in Settings, else Windows' own. Changing it redraws
 * the app (the windows are keyed by it in Main), so plain functions can call tr too.
 */

enum class Lang(val id: String, val label: String) { EN("en", "English"), HE("he", "עברית") }

object I18n {
    /** Windows' display language: Hebrew if it's Hebrew, else English. */
    val system: Lang by lazy { if (Locale.getDefault().language in setOf("he", "iw")) Lang.HE else Lang.EN }

    /** Set by tests and renders, which must not depend on this PC or write its settings. */
    @Volatile var forced: Lang? = null

    val lang: Lang
        get() = forced ?: when (Settings.state.value.language) {
            Lang.EN.id -> Lang.EN
            Lang.HE.id -> Lang.HE
            else -> system
        }

    val rtl: Boolean get() = lang == Lang.HE
}

/**
 * The string in the current language. A Hebrew sentence that opens with a Latin word ("Claude עובד", "YDeck קורא…")
 * starts with a right-to-left mark, so it reads right to left everywhere it shows, not by its first letter.
 */
fun tr(en: String, he: String): String = if (I18n.lang == Lang.HE) rtl(he) else en

private fun rtl(he: String): String {
    val first = he.firstOrNull { it.isLetter() } ?: return he
    return if (first !in '\u0590'..'\u05FF' && he.any { it in '\u0590'..'\u05FF' }) "\u200F" + he else he
}

/** "1 link" / "3 links", "קישור אחד" / "3 קישורים". */
fun count(n: Int, oneEn: String, manyEn: String, oneHe: String, manyHe: String): String =
    if (n == 1) tr("1 $oneEn", oneHe) else tr("$n $manyEn", "$n $manyHe")
