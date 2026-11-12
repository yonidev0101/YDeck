package com.yonidev.ydeck.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import com.yonidev.ydeck.link.RunCommand
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/** Draws a command from the phone in its three states (asking, running, ended), light and dark, into desktop/build/run-renders/. Run with -Prender. */
class RenderRuns {
    private val out = File("build/run-renders")

    @Test fun render() {
        if (System.getProperty("render") == null) return
        out.mkdirs()
        I18n.forced = Lang.HE
        try {
            val now = System.currentTimeMillis()
            val ask = Runs.Request(RunCommand("a", "p", "whoami", now, window = false), "YDeck", File("."))
            val ping = (1..6).joinToString("\n") { "Reply from 2a00:1450:4028:805::200e: time=${4 + it % 3}ms" }
            val running = Runs.Shown("b", "YDeck", "ping -n 10 google.com", "Pinging google.com [2a00:1450:4028:805::200e]\n$ping", false, 0, since = now - 4_000)
            val done = Runs.Shown("c", "YDeck", "git no-such-command", "git: 'no-such-command' is not a git command. See 'git --help'.", true, 1, since = now - 2_000, ended = now)
            for (dark in listOf(false, true)) {
                shot(File(out, "runs-${if (dark) "dark" else "light"}.png"), 1560, 460) {
                    DeckTheme(dark) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Box(Modifier.fillMaxSize().background(LocalDeck.current.bg).padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                                    Box(Modifier.width(504.dp)) { RunAsk(ask, 1) }
                                    Box(Modifier.width(504.dp)) { RunOut(running) }
                                    Box(Modifier.width(504.dp)) { RunOut(done) }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            I18n.forced = null
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
