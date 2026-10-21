package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The frames of the demo film (store/videos/source/demo.html): the app window in Hebrew and dark, at 3× so the camera
 * can lean in, over three made-up repositories (each with its own remote): Orbit's Claude waits for an answer and left a
 * session not committed, Nova's works with a dev server up and a key missing in .env, Pulse has a change. Then the day
 * is closed for real (the key set, commit, push, the server stopped, the sessions over) and the open loops are drawn
 * again, empty.
 * Also the folder icons of twelve projects, in each of Claude's states. Into desktop/build/film-renders/; run with
 * `gradlew :desktop:test -Prender --tests "*RenderFilm"`.
 */
class RenderFilm {
    private val out = File("build/film-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        // the film's screens in Hebrew; the site's also in English (app-*-en.png)
        screens(Lang.HE, "")
        screens(Lang.EN, "-en")
        // A folder's icon in each of Claude's states (none: no session).
        val (demo, extra) = projects()
        for (pr in demo + extra) for (mode in listOf(null) + ClaudeMode.entries) {
            ImageIO.write(FolderTag.render(pr, mode), "png", File(out, "folder-${pr.id}-${mode?.name?.lowercase() ?: "none"}.png"))
        }
    }

    /** The three demo projects (Nova, Orbit, Pulse), and nine more for the folders. */
    private fun projects(): Pair<List<Project>, List<Project>> {
        val demo = DeckJson.decode(File("../store/play/source/demo-projects.json").readText()).projects
        val pulse = demo[2]
        fun more(id: String, name: String, color: Long) = pulse.copy(id = id, name = name, mono = name.take(1), color = color.toInt(), actions = emptyList())
        return demo to listOf(more("atlas", "Atlas", 0xFF7C3AED), more("ledger", "Ledger", 0xFF2563EB), more("kite", "Kite", 0xFF16A34A),
            more("mira", "Mira", 0xFFDB2777), more("sable", "Sable", 0xFF475569), more("quill", "Quill", 0xFFF59E0B),
            more("fern", "Fern", 0xFF0D9488), more("juno", "Juno", 0xFFE11D48), more("rook", "Rook", 0xFF4F46E5))
    }

    private fun screens(lang: Lang, suffix: String) {
        I18n.forced = lang
        val (demo, extra) = projects()
        val (nova, orbit, pulse) = demo
        val deck = Deck(listOf(orbit, nova, pulse) + extra.take(3))

        val root = kotlin.io.path.createTempDirectory("ydeck-film").toFile()
        val store = File.createTempFile("receipts", ".json")
        try {
            Receipts.store = store
            fun repo(name: String, files: Map<String, String>): File {
                val bare = File(root, "$name.git")
                Git.run(root, listOf("init", "--bare", "-b", "main", bare.path))
                val dir = File(root, name).apply { mkdirs() }
                Git.run(dir, listOf("init", "-b", "main"))
                Git.run(dir, listOf("config", "user.name", "Dana"))
                Git.run(dir, listOf("config", "user.email", "dana@nova.dev"))
                files.forEach { (path, text) -> File(dir, path).apply { parentFile.mkdirs() }.writeText(text) }
                Git.run(dir, listOf("add", "-A")); Git.run(dir, listOf("commit", "-m", "Start"))
                Git.run(dir, listOf("remote", "add", "origin", bare.path))
                Git.run(dir, listOf("push", "-u", "origin", "main"))
                return dir
            }
            fun commit(dir: File, path: String, text: String, message: String) {
                File(dir, path).apply { parentFile.mkdirs() }.writeText(text)
                Git.run(dir, listOf("add", "-A")); Git.run(dir, listOf("commit", "-m", message))
            }
            val o = repo("orbit", mapOf("src/payments.ts" to "export async function charge(amount: number) {\n  return stripe.charges.create({ amount })\n}\n", "README.md" to "# Orbit\n"))
            commit(o, "src/deposit.ts", "export type Deposit = { id: string, amount: number, held: boolean }\n", "Deposit model")
            commit(o, "src/copy.ts", "export const checkout = 'Pay the deposit'\n", "Checkout copy")
            val n = repo("nova", mapOf("app/signin.tsx" to "export function SignIn() {\n  return <Form />\n}\n", ".env.example" to "STRIPE_SECRET_KEY=\nRESEND_API_KEY=\n", ".gitignore" to ".env\n"))
            File(n, ".env").writeText("RESEND_API_KEY=re_test\n")
            File(n, "app/signin.tsx").writeText("export function SignIn() {\n  return <Form tokens={theme} />\n}\n")
            val p = repo("pulse", mapOf("app/Home.kt" to "fun home() = Unit\n"))
            File(p, "app/Home.kt").writeText("fun home() = Streak()\n")

            val now = System.currentTimeMillis()
            Settings.preview(Settings.State(local = mapOf(
                orbit.id to Local(folder = o.path, note = "Ship deposits"),
                nova.id to Local(folder = n.path, note = "Move sign-in to the new design system"),
                pulse.id to Local(folder = p.path, note = "Streaks on the home screen"),
            ), welcomed = true))

            // Orbit: a session that changed three files and isn't committed, then Claude asks before it runs the tests.
            Receipts.begin("orbit-session$suffix", Agent.CLAUDE, o.path, "Add the Stripe webhook for deposits, with tests", now - 14 * 60_000)
            Receipts.settle()
            File(o, "src/payments.ts").writeText("export async function charge(amount: number, deposit = false) {\n  const intent = await stripe.paymentIntents.create({ amount, capture_method: deposit ? 'manual' : 'automatic' })\n  return intent\n}\n")
            File(o, "src/webhook.ts").writeText("export function onEvent(e: Stripe.Event) {\n  if (e.type === 'payment_intent.succeeded') markPaid(e.data.object.id)\n}\n")
            File(o, "src/webhook.test.ts").writeText("test('marks a deposit paid', () => {\n  expect(onEvent(fake('payment_intent.succeeded'))).toBeUndefined()\n})\n")
            Receipts.end("orbit-session$suffix", now - 2 * 60_000)
            Receipts.settle()
            fun hook(event: String, id: String, cwd: File, at: Long, vararg kv: Pair<String, String>) {
                val e = org.json.JSONObject().put("hook_event_name", event).put("session_id", id).put("cwd", cwd.path)
                kv.forEach { (k, v) -> e.put(k, v) }
                ClaudeLink.handle(e, at)
            }
            hook("UserPromptSubmit", "orbit-session$suffix", o, now - 14 * 60_000, "prompt" to "Add the Stripe webhook for deposits, with tests")
            hook("Notification", "orbit-session$suffix", o, now - 2 * 60_000, "notification_type" to "permission_prompt", "message" to "Claude needs your permission to use Bash")
            hook("UserPromptSubmit", "nova-session$suffix", n, now - 6 * 60_000, "prompt" to "Move the sign-in screen to the new design system")
            hook("Stop", "pulse-session$suffix", p, now - 25 * 60_000, "last_assistant_message" to "Added the streak counter to the home screen")
            ProjectFiles.writeHandoff(o, Handoff("Added the deposit flow and its webhook, with a test", "Test the webhook against Stripe's test clock", now - 5 * 60_000, "Claude"))

            val dirs = listOf(o.path, n.path, p.path)
            runBlocking { Git.refresh(dirs); Pulses.refresh(dirs) }
            // Nova's dev server: set on its pulse (no real server is started for the render).
            pulses().let { it.value = it.value + (n.path to it.value.getValue(n.path).copy(ports = listOf(5173))) }

            fun app(name: String, screen: Screen, h: Int = 780) = shot(File(out, "app-$name$suffix.png"), 1200, h) {
                DeckTheme(true) { Box(Modifier.fillMaxSize().background(LocalDeck.current.bg)) { AppContent(deck, screen, true, AppActions(null, {}, {}, {}, {}, {})) } }
            }
            app("orbit", Screen.Project(orbit.id, Tab.OVERVIEW))
            app("nova", Screen.Project(nova.id, Tab.OVERVIEW))
            app("orbit-git", Screen.Project(orbit.id, Tab.GIT))
            GitFocus.receipt.value = "orbit-session$suffix"
            app("orbit-session", Screen.Project(orbit.id, Tab.GIT))
            GitFocus.receipt.value = null
            app("loops", Screen.Loops)
            app("closeday", Screen.CloseDay, 900)

            // The day closed: the key set, everything committed and pushed, the server stopped, the sessions over.
            File(n, ".env").appendText("STRIPE_SECRET_KEY=sk_test\n")
            for (d in listOf(o, n, p)) {
                Git.run(d, listOf("add", "-A")); Git.run(d, listOf("commit", "-m", "End of day"))
                Git.run(d, listOf("push"))
            }
            hook("SessionEnd", "orbit-session$suffix", o, now); hook("SessionEnd", "nova-session$suffix", n, now); hook("SessionEnd", "pulse-session$suffix", p, now)
            runBlocking { Git.refresh(dirs); Pulses.refresh(dirs) }
            app("loops-clear", Screen.Loops)
        } finally {
            root.deleteRecursively()
            store.delete()
            I18n.forced = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pulses() = Pulses::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Pulses) as MutableStateFlow<Map<String, Pulse>>

    private fun shot(file: File, w: Int, h: Int, content: @Composable () -> Unit) {
        ImageComposeScene(width = w * 3, height = h * 3, density = Density(3f), content = content).use { scene ->
            scene.render()
            Thread.sleep(200) // favicons and tool icons load once
            file.writeBytes(scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
