package com.yonidev.ydeck.desktop

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Claude really ends a recommendation with buttons YDeck can read. Run with -Plive. */
class PartnerLive {
    @Test fun live() {
        if (System.getProperty("live") == null) return
        val dir = Files.createTempDirectory("ydeck-partner").toFile()
        try {
            val prompt = buildString {
                appendLine("You are YDeck, the user's partner across their projects. Answer in Hebrew, short.")
                appendLine(Partner.FORMAT)
                appendLine()
                appendLine("The projects:\n## Shop\n- next: לחבר תשלום עם Stripe\n- left: לחבר תשלום | לבדוק בטלפון\n## Blog\n- next: (none)\n- left: (none)")
                appendLine()
                appendLine("The question:")
                append(Partner.WHAT_NOW)
            }
            val answer = Assistant.read(prompt, dir, null, {}, {}, {}, tools = false)
            println("ANSWER:\n$answer")
            val (text, acts) = Partner.split(answer, listOf(Project.sample().copy(id = "shop", name = "Shop"), Project.sample().copy(id = "blog", name = "Blog")))
            println("TEXT: $text\nACTS: $acts")
            assertTrue(acts.any { it.kind == Partner.GO && it.project == "shop" })
        } finally { dir.deleteRecursively() }
    }
}
