package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** Draws the project's dossier (goal, the path, saving, what was built), light and dark, into desktop/build/dossier-renders/. Run with -Prender. */
class RenderDossier {
    private val out = File("build/dossier-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        I18n.forced = Lang.HE
        Saving.auto = false
        val dir = Files.createTempDirectory("ydeck-dossier-render").toFile()
        try {
            fun git(vararg a: String) = Git.run(dir, a.toList(), errors = false)
            git("init", "-q", "-b", "main"); git("config", "user.email", "t@example.com"); git("config", "user.name", "T")
            File(dir, "README.md").writeText("x\n"); git("add", "-A"); git("commit", "-q", "-m", "First")
            File(dir, "beta.png").writeText("png")
            File(dir, ProjectFiles.LOCAL).writeText("""{
              "now": "תיק הפרויקט, שלב 1", "next": "לבנות את מסך התיק",
              "left": ["לבנות את מסך התיק", "להציג את התיק בטלפון", "לשחרר את 1.29"],
              "ideas": ["גרסת Mac", "מחקר מתחרים"],
              "done": ["נתוני התיק: מה נבנה, יעד ואחוז"],
              "built": ["מסך \"עכשיו\" שממיין לפי מי צריך אותך", "ווידג'טים על שולחן העבודה", "פס בסייר הקבצים", "לשאול את YDeck", "בריף בוקר", "אפליקציית טלפון עם ממסר מוצפן", "סגירת יום", "תשובה לסשן שמחכה", "עדכונים אוטומטיים"],
              "goal": {"title": "גרסה 1.29 עם תיק הפרויקט", "steps": [{"text":"רשימת מה נבנה ויעד","done":true},{"text":"כרטיס שמירה במילים","done":true},{"text":"הנתונים עוברים לטלפון","done":true},{"text":"העמוד החדש במחשב","done":false},{"text":"העמוד החדש בטלפון","done":false}]}
            }""")
            runBlocking { Pulses.refresh(listOf(dir.path)); Git.refresh(listOf(dir.path)) }
            Saving.preview(dir, SaveCard("קובץ אחד לא נשמר: beta.png, נראה כמו צילום מסך שנשאר בטעות. ענף אחד מחכה למיזוג ל־main.",
                "לא לשמור את התמונה, ולמזג את project-home.", listOf(SaveStep(SaveStep.IGNORE, listOf("beta.png")), SaveStep(SaveStep.MERGE, branch = "project-home")), "x"))
            val p = Project.sample()
            val local = Local(note = "לבנות את מסך התיק")
            for (dark in listOf(false, true)) {
                shot(File(out, "dossier-${if (dark) "dark" else "light"}.png"), 1240, 1250) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(LocalDeck.current.bg).padding(36.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                Column(Modifier.weight(1.55f), verticalArrangement = Arrangement.spacedBy(28.dp)) { GoalCard(dir); PlanLists(p, local, dir) }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { SaveCardView(p, dir); BuiltCard(dir) }
                            }
                        }
                    }
                }
            }
        } finally {
            I18n.forced = null
            Saving.auto = true
            dir.deleteRecursively()
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
