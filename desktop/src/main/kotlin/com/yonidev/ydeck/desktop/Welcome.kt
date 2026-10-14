package com.yonidev.ydeck.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * The first run, three steps: the brand's moment (with the language), the first project from its folder, and where it
 * shows (the desktop, File Explorer, Claude Code). Each can be skipped; Settings changes them later.
 */

private val INDIGO = Color(0xFF3D35E0)
/** The brand's night ground (store/brand/logo): the first screen is the brand's own. */
private val NIGHT = Color(0xFF0B0B0D)

@Composable
fun WelcomeFlow(deck: Deck, actions: AppActions, startStep: Int = 1) {
    var step by remember { mutableStateOf(startStep) }
    var project by remember { mutableStateOf<Project?>(deck.projects.firstOrNull()) }
    when (step) {
        1 -> BrandStep(actions) { step = 2 }
        2 -> FirstProject(project, actions, onProject = { project = it }, onNext = { step = 3 }, onSkip = { finish(null) })
        else -> Surfaces(project, actions) { finish(project) }
    }
}

private fun finish(p: Project?) {
    Settings.update { it.copy(welcomed = true) }
    Nav.set(p?.let { Screen.Project(it.id) } ?: homeRoute(Repo.deck.value))
}

@Composable
private fun StepDots(step: Int, onDark: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..3).forEach { i ->
            val on = i == step
            Box(Modifier.size(if (on) 20.dp else 6.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(
                if (onDark) Color.White.copy(alpha = if (on) 1f else 0.35f) else if (on) LocalDeck.current.fg else LocalDeck.current.line))
        }
    }
}

@Composable
private fun StepLabel(step: Int) {
    Mono(tr("STEP $step OF 3", "שלב $step מתוך 3"), LocalDeck.current.faint, 11)
}

/** 1: the brand on its night ground, the line, and the language. */
@Composable
private fun BrandStep(actions: AppActions, onNext: () -> Unit) {
    Box(Modifier.fillMaxSize().background(NIGHT)) {
        Row(Modifier.align(Alignment.TopEnd).padding(18.dp)) {
            Lang.entries.forEach { l ->
                val on = I18n.lang == l
                Text(l.label, color = Color.White, fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, fontFamily = PlexSans,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { Settings.update { it.copy(language = l.id) } }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Column(Modifier.align(Alignment.Center).widthIn(max = 820.dp).padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource("brand/ydeck-mark-white.svg"), null, Modifier.size(96.dp))
            Spacer(Modifier.height(20.dp))
            Text("YDeck", color = Color(0xFFF4F4F5), fontSize = 30.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, letterSpacing = (-0.6).sp)
            Spacer(Modifier.height(34.dp))
            Text(tr("Nothing left open.", "שום דבר לא נשאר פתוח."), color = Color(0xFFF4F4F5), fontSize = 58.sp, lineHeight = 66.sp, fontFamily = Display, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text(tr("Your agents work. YDeck keeps track of what they did, what changed, and what still needs you, across every project.",
                "הסוכנים עובדים. YDeck זוכר מה הם עשו, מה השתנה ומה עוד מחכה לך, בכל הפרויקטים."),
                color = Color(0xFFB4B4BB), fontSize = 17.sp, lineHeight = 26.sp, fontFamily = PlexSans, textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Get started", "בואו נתחיל"), color = NIGHT, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White).clickable(onClick = onNext).padding(horizontal = 28.dp, vertical = 14.dp))
                Text(tr("Import from YDeck for Android", "ייבוא מ־YDeck לאנדרואיד"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).clickable { actions.import() }.padding(horizontal = 18.dp, vertical = 14.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp)) { StepDots(1, true) }
    }
}

/** 2: the first project, from its folder; what YDeck found in it. */
@Composable
private fun FirstProject(project: Project?, actions: AppActions, onProject: (Project) -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val c = LocalDeck.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    fun use(folder: File) {
        working = true
        scope.launch {
            val p = Settings.projectAt(folder)?.let { Repo.deck.value.byId(it.first) } ?: Project.blank().also { p ->
                found = withContext(Dispatchers.IO) { connectFolder(p, folder) }
            }
            onProject(Repo.deck.value.byId(p.id) ?: p)
            working = false
        }
    }
    val current = project?.let { Repo.deck.value.byId(it.id) }
    Box(Modifier.fillMaxSize().background(c.bg).dropTarget { d -> (d as? Dropped.Files)?.files?.firstOrNull { it.isDirectory }?.let(::use) }) {
        Row(Modifier.align(Alignment.Center).widthIn(max = 1040.dp).padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StepLabel(2)
                Spacer(Modifier.height(14.dp))
                Text(tr("Bring in your", "מביאים את"), color = c.fg, fontSize = 44.sp, lineHeight = 52.sp, fontFamily = Display, fontWeight = FontWeight.Light)
                Text(tr("first project.", "הפרויקט הראשון."), color = c.accentText, fontSize = 44.sp, lineHeight = 52.sp, fontFamily = Display, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(16.dp))
                Text(tr("Drop a code folder on this window. YDeck reads it and fills the project in: the repository, the consoles, the package name, the commands and the logo.",
                    "גוררים תיקיית קוד לחלון. YDeck קורא אותה וממלא את הפרויקט: הריפו, הקונסולות, שם החבילה, הפקודות והלוגו."),
                    color = c.muted, fontSize = 16.sp, lineHeight = 24.sp, fontFamily = PlexSans)
                Spacer(Modifier.height(8.dp))
                Text(tr("Read only. Nothing leaves your computer.", "קריאה בלבד. שום דבר לא יוצא מהמחשב."), color = c.faint, fontSize = 13.sp, fontFamily = PlexSans)
                Spacer(Modifier.height(32.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (current != null) Pill(tr("Continue", "המשך"), Tone.PRIMARY, onClick = onNext)
                    Pill(if (current == null) tr("Choose a folder", "בחירת תיקייה") else tr("Another folder", "תיקייה אחרת"), if (current == null) Tone.PRIMARY else Tone.SOFT, Glyph.FOLDER) {
                        actions.chooseFolder()?.let(::use)
                    }
                    TextAction(tr("Skip", "דילוג"), onClick = onSkip)
                }
            }
            Column(Modifier.weight(1.1f)) {
                if (current == null) Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)).background(c.paper).border(1.5.dp, c.line, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlyphIcon(Glyph.FOLDER, 34.dp, if (working) c.accentText else c.faint)
                        Spacer(Modifier.height(12.dp))
                        Text(if (working) tr("Reading the folder…", "קורא את התיקייה…") else tr("Drop a folder here", "גוררים תיקייה לכאן"), color = c.muted, fontSize = 14.sp, fontFamily = PlexSans)
                    }
                } else Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo(current, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current.name, color = c.fg, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                            Text(listOf(current.kind, current.googleEmail.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
                        }
                        GlyphIcon(Glyph.CHECK, 20.dp, CLEAN_DOT)
                    }
                    Spacer(Modifier.height(16.dp))
                    current.actions.take(6).forEach { a ->
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.surface2))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.surface2), contentAlignment = Alignment.Center) { ServiceMark(a.service, a.faviconHost, 16.dp, c == Dark) }
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                            Mono(Links.display(a.url).take(36))
                        }
                    }
                    found?.takeIf { it.isNotBlank() }?.let {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.surface2))
                        Text(tr("Found: $it", "נמצא: $it"), color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)) { StepDots(2, false) }
    }
}

/** 3: where the project shows: on the desktop, in File Explorer, with Claude Code. */
@Composable
private fun Surfaces(project: Project?, actions: AppActions, onDone: () -> Unit) {
    val c = LocalDeck.current
    var widget by remember { mutableStateOf(true) }
    var explorer by remember { mutableStateOf(true) }
    var claude by remember { mutableStateOf(Tools.claude != null) }
    val name = project?.name ?: tr("your projects", "הפרויקטים")
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 1080.dp).padding(horizontal = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            StepLabel(3)
            Spacer(Modifier.height(12.dp))
            Text(tr("Choose where $name shows.", "בוחרים איפה $name יופיע."), color = c.fg, fontSize = 40.sp, lineHeight = 48.sp, fontFamily = Display, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(tr("Turn any off now or later, in Settings.", "אפשר לכבות כל אחד עכשיו או אחר כך, בהגדרות."), color = c.muted, fontSize = 15.sp, fontFamily = PlexSans)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SurfaceCard(Modifier.weight(1f), tr("On the desktop", "על שולחן העבודה"), tr("A glass widget: what's open, what's at work, and every console one click away.", "ווידג'ט זכוכית: מה פתוח, מה בעבודה, וכל קונסולה בלחיצה."), widget, { widget = it }) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFDCD9F7)), contentAlignment = Alignment.Center) {
                        if (project != null) Box(Modifier.width(250.dp)) { WidgetPreview(project, false) } else GlyphIcon(Glyph.WIDGET, 40.dp, INDIGO)
                    }
                }
                SurfaceCard(Modifier.weight(1f), tr("In File Explorer", "בסייר הקבצים"), tr("A strip in the project's folder, and Open in YDeck on right-click.", "פס בתיקיית הפרויקט, ו״פתיחה ב־YDeck״ בלחיצה ימנית."), explorer, { explorer = it }) {
                    Box(Modifier.fillMaxSize().background(c.paper).padding(horizontal = 22.dp), contentAlignment = Alignment.BottomCenter) {
                        Column(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Color.White).border(1.dp, c.line, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(0.6f, 0.8f, 0.5f, 0.7f).forEach { w -> Box(Modifier.fillMaxWidth(w).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFF1F1EF))) }
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(11.dp)).background(Color.White).border(1.dp, Color(0xFFE7E7E4), RoundedCornerShape(11.dp)).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (project != null) Logo(project, 18.dp)
                                Spacer(Modifier.width(7.dp))
                                Text(project?.name ?: "YDeck", color = Color(0xFF0B0B0C), fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                                GlyphIcon(Glyph.CODE, 13.dp, Color(0xFF0B0B0C)); Spacer(Modifier.width(6.dp)); GlyphIcon(Glyph.CLAUDE, 13.dp, Color(0xFFD97757))
                            }
                        }
                    }
                }
                SurfaceCard(Modifier.weight(1f), tr("With Claude Code", "עם Claude Code"), tr("See when Claude works or needs you. Claude gets the project and keeps it current.", "רואים מתי Claude עובד או מחכה לך. Claude מכיר את הפרויקט ומעדכן אותו."),
                    claude, { claude = it }, enabled = Tools.claude != null) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0C)).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)) {
                        Mono("> claude", Color(0xFF8A8A90), 11)
                        Mono(tr("> add the Stripe webhook", "> תוסיף webhook של Stripe"), Color(0xFFF4F4F5), 11)
                        Mono("✻ Working…", Color(0xFFD97757), 11)
                        Text(tr("YDeck knows: ${project?.name ?: "the project"}", "YDeck יודע: ${project?.name ?: "הפרויקט"}"), color = Color(0xFF0B0B0C), fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans,
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(99.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Pill(tr("Start using YDeck", "מתחילים לעבוד"), Tone.PRIMARY) {
                if (widget && project != null && Settings.state.value.widgets.isEmpty()) actions.addWidget(project)
                Settings.update { it.copy(companion = explorer, explorerMenu = explorer) }
                Thread { ExplorerMenu.set(explorer) }.start()
                if (claude && !ClaudePlugin.installed) Thread { ClaudePlugin.set(true) }.start()
                onDone()
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)) { StepDots(3, false) }
    }
}


@Composable
private fun SurfaceCard(modifier: Modifier, title: String, text: String, on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, preview: @Composable () -> Unit) {
    val c = LocalDeck.current
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).border(1.5.dp, if (on) c.accent else c.line, shape).clickable(enabled = enabled) { onChange(!on) }) {
        Box(Modifier.fillMaxWidth().height(190.dp)) { preview() }
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                Spacer(Modifier.height(4.dp))
                Text(if (enabled) text else tr("Claude Code isn't installed on this PC.", "Claude Code לא מותקן במחשב."), color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = PlexSans)
            }
            Spacer(Modifier.width(10.dp))
            Toggle(on, enabled) { onChange(it) }
        }
    }
}
