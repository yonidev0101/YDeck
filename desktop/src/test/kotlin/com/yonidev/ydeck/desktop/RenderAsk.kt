package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/**
 * Draws Ask YDeck: the search window with a question, the conversation in it and beside the page, and the mark's
 * moods, light and dark, into desktop/build/ask-renders/. Run with -Prender (skipped in a normal test run).
 */
class RenderAsk {
    private val out = File("build/ask-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        I18n.forced = Lang.HE
        val deck = DeckJson.decode(File("../store/play/source/demo-projects.json").readText())
        val settings = Settings.State()
        val p = deck.projects[0]
        try {
            for (dark in listOf(false, true)) {
                val mode = if (dark) "dark" else "light"
                Assistant.preview(emptyList())
                shot(File(out, "palette-question-$mode.png"), 700, 520) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF14121F) else Color(0xFF3A3F47)).padding(20.dp)) {
                            PaletteCard(deck, settings, dark, {}, {}, initialQuery = "למה הטופס ב־${p.name} נשלח פעמיים?", onAsk = {})
                        }
                    }
                }
                shot(File(out, "empty-$mode.png"), 700, 640) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF14121F) else Color(0xFF3A3F47)).padding(20.dp)) {
                            val c = LocalDeck.current
                            val shape = RoundedCornerShape(22.dp)
                            Column(Modifier.fillMaxSize().clip(shape).background(c.surface).border(1.dp, c.line, shape)) { AskInWindow {} }
                        }
                    }
                }
                Assistant.preview(listOf(
                    Assistant.Turn("מה החלטנו על עדכונים ב־${p.name}, ומה עוד לא בדקנו?",
                        answer = "ב־**${p.name}** הוחלט ב־22.10 שהאפליקציה בודקת את GitHub כל 6 שעות, מודיעה פעם אחת לכל גרסה, ו״עדכון עכשיו״ מתקין את ה־MSI ופותח מחדש.\n\nמה עוד לא נבדק:\n\n- עדכון אמיתי מ־1.18.5 ל־1.18.6 במחשב נקי\n- מה קורה כש־GitHub לא זמין (`Updates.kt` לא מנסה שוב)",
                        reads = 2, seconds = 6),
                    Assistant.Turn("ומה עם הטלפון?"),
                ))
                shot(File(out, "window-$mode.png"), 700, 640) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF14121F) else Color(0xFF3A3F47)).padding(20.dp)) {
                            val c = LocalDeck.current
                            val shape = RoundedCornerShape(22.dp)
                            Column(Modifier.fillMaxSize().clip(shape).background(c.surface).border(1.dp, c.line, shape)) { AskInWindow {} }
                        }
                    }
                }
                shot(File(out, "panel-$mode.png"), 380, 780) {
                    DeckTheme(dark) { AssistantPanel(deck, Screen.Project(p.id)) }
                }
                SessionAsks.preview(listOf(
                    com.yonidev.ydeck.link.Waiting("w1", p.id, com.yonidev.ydeck.link.Waiting.TEXT,
                        "בניתי את מסך ההגדרות החדש.\n\nלשמור את ההעדפות ב־Firebase או ב־Supabase?", draft = "Firebase, כמו שהחלטנו",
                        source = "ההחלטה מ־2026-10-12: נשארים עם Firebase", sure = true, at = System.currentTimeMillis() - 4 * 60_000),
                    com.yonidev.ydeck.link.Waiting("w2", deck.projects[1].id, com.yonidev.ydeck.link.Waiting.CHOICE,
                        questions = listOf(com.yonidev.ydeck.link.Choice("איזו ספריית גרפים?", "גרפים", listOf("Vico", "MPAndroidChart", "לבנות לבד"), false)),
                        draftChoices = mapOf("איזו ספריית גרפים?" to "Vico"), source = "אין החלטה רשומה; Vico כבר בשימוש ב־build.gradle", sure = false,
                        at = System.currentTimeMillis() - 60_000),
                ))
                shot(File(out, "waiting-$mode.png"), 380, 900) {
                    DeckTheme(dark) { AssistantPanel(deck, Screen.Project(p.id)) }
                }
                SessionAsks.preview(emptyList())
                shot(File(out, "marks-$mode.png"), 440, 110) {
                    DeckTheme(dark) {
                        Row(Modifier.fillMaxSize().background(LocalDeck.current.bg).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            Mood.entries.forEach { AskMark(it, 56.dp) }
                        }
                    }
                }
            }
        } finally {
            I18n.forced = null
            Assistant.preview(emptyList())
        }
    }

    private fun shot(file: File, w: Int, h: Int, content: @Composable () -> Unit) {
        ImageComposeScene(width = w * 2, height = h * 2, density = Density(2f), content = content).use { scene ->
            scene.render()
            Thread.sleep(200)
            file.writeBytes(scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
