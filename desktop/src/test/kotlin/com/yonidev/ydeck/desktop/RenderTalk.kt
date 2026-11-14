package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Draws the conversation on the project's page, working and asking, light and dark, into desktop/build/talk-renders/. Run with -Prender. */
class RenderTalk {
    private val out = File("build/talk-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        I18n.forced = Lang.HE
        val a = Files.createTempDirectory("ydeck-talk-a").toFile()
        val b = Files.createTempDirectory("ydeck-talk-b").toFile()
        try {
            val lines = listOf(
                Talk.Line(Talk.Kind.USER, "תתקן את הכותרת בעמוד הבית, היא נחתכת בטלפון"),
                Talk.Line(Talk.Kind.STEP, "קרא את src/pages/Home.tsx"),
                Talk.Line(Talk.Kind.STEP, "חיפש ״hero-title״"),
                Talk.Line(Talk.Kind.STEP, "שינה את src/styles/home.css"),
                Talk.Line(Talk.Kind.CLAUDE, "תיקנתי: הכותרת נחתכה כי היה לה **רוחב קבוע** של 640px. עכשיו היא מתכווצת עם המסך, ובטלפון היא יורדת לשתי שורות.\n\nרוצה שאריץ את הבדיקות?"),
                Talk.Line(Talk.Kind.USER, "כן, ותעלה את זה"),
            )
            Talk.preview(a, Talk.Chat("s1", "תתקן את הכותרת בעמוד הבית", lines, busy = true, at = System.currentTimeMillis() - 60_000,
                ask = Talk.Ask("r", "Bash", "npm run test", "Bash(npm run test:*)", JSONObject(), JSONArray())))
            Talk.preview(b, Talk.Chat())
            val p = Project.sample()
            for (dark in listOf(false, true)) {
                shot(File(out, "talk-${if (dark) "dark" else "light"}.png"), 820, 1100) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(LocalDeck.current.bg).padding(36.dp)) {
                            Column(Modifier.width(748.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) { TalkCard(p, b, dark); TalkCard(p, a, dark) }
                        }
                    }
                }
            }
        } finally {
            I18n.forced = null
            a.deleteRecursively(); b.deleteRecursively()
        }
    }

    private fun shot(file: File, w: Int, h: Int, content: @Composable () -> Unit) {
        ImageComposeScene(width = w * 2, height = h * 2, density = Density(2f), content = content).use { scene ->
            scene.render()
            Thread.sleep(300)
            file.writeBytes(scene.render(1_500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
