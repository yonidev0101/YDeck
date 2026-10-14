package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws the widget in each size (with and without a project folder), the search window and the editor, light and
 * dark, into desktop/build/widget-renders/ (a visual check without opening windows). Run with -Prender (it's
 * skipped in a normal test run).
 */
class RenderWidgets {
    private val out = File("build/widget-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        val deck = DeckJson.decode(File("../store/play/source/demo-projects.json").readText())
        val noop = WidgetActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, { _, _, _ -> })
        // This repo as the project folder: its git state and tools show on the widget.
        val local = Local(folder = File("..").canonicalPath, commands = listOf(Command("dev", "pnpm run dev"), Command("deploy", "npx wrangler deploy")))
        runBlocking { Git.refresh(listOf(local.folder!!)) }

        for (dark in listOf(false, true)) for (withFolder in listOf(false, true)) {
            for ((name, w, h) in listOf(Triple("wide", 380, 234), Triple("small", 214, 214), Triple("strip", 380, 84))) {
                val project = deck.projects[if (name == "small") 2 else if (name == "strip") 1 else 0]
                val spec = WidgetSpec("r", project.id, 0f, 0f, w.toFloat(), h.toFloat())
                val pad = 40
                shot(File(out, "$name${if (withFolder) "-folder" else ""}-${mode(dark)}.png"), w + pad * 2, h + pad * 2) {
                    DeckTheme(dark) {
                        Box(Modifier.fillMaxSize().background(wallpaper(dark)), contentAlignment = Alignment.Center) {
                            val shape = RoundedCornerShape(26.dp)
                            Box(Modifier.size((w - 20).dp, (h - 20).dp).clip(shape).background(glass(project.color, dark))
                                .border(1.dp, if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f), shape)) {
                                CompositionLocalProvider(LocalPreviewLocal provides local.takeIf { withFolder }) { WidgetLayout(spec, deck, project, dark, noop) {} }
                            }
                        }
                    }
                }
            }
        }

        // The search window over a wallpaper.
        val settings = Settings.State(local = mapOf(deck.projects[0].id to local))
        val first = deck.projects[0].name.take(3).lowercase()
        for (dark in listOf(false, true)) for ((q, tag) in listOf("" to "empty", "$first " to "project", "fire" to "service")) {
            shot(File(out, "palette-$tag-${mode(dark)}.png"), 700, 520) {
                DeckTheme(dark) {
                    Box(Modifier.fillMaxSize().background(wallpaper(dark)).padding(20.dp)) {
                        PaletteCard(deck, settings, dark, {}, {}, initialQuery = q)
                    }
                }
            }
        }

        // The strip inside File Explorer (on Explorer's own page colour), wide and narrow.
        runBlocking { Pulses.refresh(listOf(local.folder!!)) }
        val stripActions = StripActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {})
        for (dark in listOf(false, true)) for (w in listOf(520, 400)) {
            val p = deck.projects[0]
            shot(File(out, "strip-$w-${mode(dark)}.png"), w + 40, 120) {
                DeckTheme(dark) {
                    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF191919) else Color(0xFFF9F9F9)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(w.dp, FolderStrip.HEIGHT.dp)) {
                            FolderStripCard(p, Attach(0, p.id, local.folder!!, 0f, 0f, w.toFloat(), FolderStrip.HEIGHT), dark, stripActions)
                        }
                    }
                }
            }
        }

        // The app window's screens, in English and in Hebrew, from a state shown without being saved: the first project
        // has this repo as its folder, a next step, and Claude Code at work in it.
        val p0 = deck.projects[0]
        Settings.preview(Settings.State(local = mapOf(p0.id to local.copy(note = "Test the webhook in production", files = listOf(File("../README.md").canonicalPath))), welcomed = true))
        ClaudeLink.handle(org.json.JSONObject().put("hook_event_name", "UserPromptSubmit").put("session_id", "render").put("cwd", local.folder!!)
            .put("prompt", "Add the Stripe webhook for deposits, with tests"), System.currentTimeMillis() - 4 * 60_000)
        val actions = AppActions(null, {}, {}, {}, {}, {})
        val screens = listOf(
            "overview" to Screen.Project(p0.id, Tab.OVERVIEW),
            "git" to Screen.Project(p0.id, Tab.GIT),
            "links" to Screen.Project(p0.id, Tab.LINKS),
            "files" to Screen.Project(p0.id, Tab.FILES),
            "commands" to Screen.Project(p0.id, Tab.COMMANDS),
            "details" to Screen.Details(p0.id),
            "new" to Screen.NewProject(),
            "settings" to Screen.Settings(SettingsSection.GENERAL),
            "settings-claude" to Screen.Settings(SettingsSection.CLAUDE),
            "about" to Screen.Settings(SettingsSection.ABOUT),
        )
        for (lang in Lang.entries) {
            I18n.forced = lang
            for ((name, screen) in screens) for (dark in if (name == "overview" || name == "git") listOf(false, true) else listOf(false)) {
                shot(File(out, "app-$name-${lang.id}-${mode(dark)}.png"), 1200, 780) { DeckTheme(dark) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, screen, dark, actions) } } }
            }
            for (step in 1..3) shot(File(out, "welcome-$step-${lang.id}.png"), 1200, 780) {
                DeckTheme(false) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, Screen.Welcome, false, actions, step) } }
            }
            for (dark in listOf(false, true)) shot(File(out, "quicklook-${lang.id}-${mode(dark)}.png"), Panel.WIDTH.toInt(), Panel.MORE_HEIGHT.toInt()) {
                DeckTheme(dark) {
                    Box(Modifier.fillMaxSize().background(LocalDeck.current.surface)) { QuickLook(p0, dark, {}, {}) }
                }
            }
            for (dark in listOf(false, true)) shot(File(out, "gitsheet-${lang.id}-${mode(dark)}.png"), Panel.WIDTH.toInt(), Panel.GIT_HEIGHT.toInt()) {
                DeckTheme(dark) {
                    Box(Modifier.fillMaxSize().background(LocalDeck.current.surface)) { GitSheet(p0, dark, {}, {}) }
                }
            }
        }

        // An agent session in a project of its own (a repository made for the render): its receipt, the loops it leaves,
        // and the page of every open loop.
        val demo = kotlin.io.path.createTempDirectory("ydeck-render").toFile()
        val store = File.createTempFile("receipts", ".json")
        try {
            Receipts.store = store
            Git.run(demo, listOf("init", "-b", "main"))
            Git.run(demo, listOf("config", "user.name", "Render"))
            Git.run(demo, listOf("config", "user.email", "render@example.com"))
            File(demo, "src").mkdirs()
            File(demo, "src/payments.ts").writeText("export async function charge(amount: number) {\n  return stripe.charges.create({ amount })\n}\n")
            File(demo, "README.md").writeText("# Orbit\n")
            Git.run(demo, listOf("add", "-A")); Git.run(demo, listOf("commit", "-m", "Start"))
            val p1 = deck.projects[1]
            Settings.preview(Settings.State(local = mapOf(p0.id to local, p1.id to Local(folder = demo.path, note = "Ship deposits")), welcomed = true))
            Receipts.begin("render-session", Agent.CLAUDE, demo.path, "Add the Stripe webhook for deposits, with tests", System.currentTimeMillis() - 40 * 60_000)
            Receipts.settle()
            File(demo, "src/payments.ts").writeText("export async function charge(amount: number, deposit = false) {\n  const intent = await stripe.paymentIntents.create({ amount, capture_method: deposit ? 'manual' : 'automatic' })\n  return intent\n}\n")
            File(demo, "src/webhook.ts").writeText("export function onEvent(e: Stripe.Event) {\n  if (e.type === 'payment_intent.succeeded') markPaid(e.data.object.id)\n}\n")
            File(demo, "src/webhook.test.ts").writeText("test('marks a deposit paid', () => {\n  expect(onEvent(fake('payment_intent.succeeded'))).toBeUndefined()\n})\n")
            Receipts.end("render-session", System.currentTimeMillis() - 6 * 60_000)
            Receipts.settle()
            Git.refreshOne(demo.path)
            ProjectFiles.writeDecisions(demo, listOf(Decision("Payments go through Stripe only; no PayPal", "2026-10-12"), Decision("Deposits are held, then captured at check-in", "2026-10-13")))
            ProjectFiles.writeHandoff(demo, Handoff("Added the deposit flow and its webhook, with a test", "Test the webhook against Stripe's test clock", System.currentTimeMillis() - 5 * 60_000, "Claude"))
            runBlocking { Pulses.refresh(listOf(local.folder!!, demo.path)) }
            for (lang in Lang.entries) {
                I18n.forced = lang
                for (dark in listOf(false, true)) {
                    shot(File(out, "app-loops-${lang.id}-${mode(dark)}.png"), 1200, 780) { DeckTheme(dark) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, Screen.Loops, dark, actions) } } }
                    shot(File(out, "app-closeday-${lang.id}-${mode(dark)}.png"), 1200, 780) { DeckTheme(dark) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, Screen.CloseDay, dark, actions) } } }
                    shot(File(out, "app-overview2-${lang.id}-${mode(dark)}.png"), 1200, 1100) { DeckTheme(dark) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, Screen.Project(p1.id), dark, actions) } } }
                    GitFocus.receipt.value = "render-session"
                    shot(File(out, "app-session-${lang.id}-${mode(dark)}.png"), 1200, 780) { DeckTheme(dark) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, Screen.Project(p1.id, Tab.GIT), dark, actions) } } }
                }
            }
        } finally {
            demo.deleteRecursively()
            store.delete()
        }
        I18n.forced = null

        // The icon written on a tagged folder.
        ImageIO.write(FolderTag.render(deck.projects[0]), "png", File(out, "folder-icon-0.png"))
        ImageIO.write(FolderTag.render(deck.projects[1]), "png", File(out, "folder-icon-1.png"))
        ImageIO.write(FolderTag.render(deck.projects[1], ClaudeMode.WORKING), "png", File(out, "folder-icon-working.png"))
        ImageIO.write(FolderTag.render(deck.projects[0], ClaudeMode.WAITING), "png", File(out, "folder-icon-waiting.png"))
        File(out, "folder-icon.ico").writeBytes(Ico.encode(FolderTag.render(deck.projects[1])))
    }

    private fun mode(dark: Boolean) = if (dark) "dark" else "light"

    private fun wallpaper(dark: Boolean) = Brush.linearGradient(
        if (dark) listOf(Color(0xFF2A2560), Color(0xFF14121F), Color(0xFF3A2320)) else listOf(Color(0xFFC9C4FF), Color(0xFFEEEDF6), Color(0xFFFFD3C2)),
    )

    private fun shot(file: File, w: Int, h: Int, content: @Composable () -> Unit) {
        ImageComposeScene(width = w * 2, height = h * 2, density = Density(2f), content = content).use { scene ->
            scene.render()
            Thread.sleep(200) // favicons and tool icons load once
            file.writeBytes(scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
