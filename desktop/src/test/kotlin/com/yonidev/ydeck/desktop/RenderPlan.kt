package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Draws the project page's three lists (left, ideas, done), light and dark, into desktop/build/plan-renders/. Run with -Prender. */
class RenderPlan {
    private val out = File("build/plan-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        I18n.forced = Lang.HE
        val dir = Files.createTempDirectory("ydeck-plan-render").toFile()
        try {
            File(dir, ProjectFiles.LOCAL).writeText("""{
              "now": "תזכורות SMS ללקוחות", "next": "לבדוק שליחה אמיתית ללקוחה של שבילר",
              "left": ["לבדוק שליחה אמיתית ללקוחה של שבילר", "להוסיף לאתר שיש תזכורות SMS", "לסגור את התמחור של הודעות"],
              "ideas": ["חסימה אוטומטית של חגים לפי לוח עברי", "קוד קופון שמתגמל גם את מי שהפנה"],
              "done": ["חיבור sms4free ושליחת הודעת ניסיון", "בחירה בין SMS למייל אצל הלקוחה", "בעלת העסק בוחרת אילו ערוצים פתוחים", "הודעה קולית לטלפון כשר"]
            }""")
            runBlocking { Pulses.refresh(listOf(dir.path)) }
            for (dark in listOf(false, true)) {
                shot(File(out, "plan-${if (dark) "dark" else "light"}.png"), 760, 900) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(LocalDeck.current.bg).padding(32.dp)) { PlanLists(dir) }
                    }
                }
            }
        } finally {
            I18n.forced = null
            dir.deleteRecursively()
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
