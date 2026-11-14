package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerTest {
    private val shop = Project.sample().copy(id = "shop", name = "Shop")

    @Test fun anAnswerEndsInButtons() {
        val answer = """עבוד על החנות: התשלום הוא הצעד שחסר לגרסה הראשונה.

```ydeck-actions
[{"kind": "go", "project": "shop", "text": "תחבר את Stripe לדף התשלום"},
 {"kind": "left", "project": "Shop", "text": "לבדוק תשלום בטלפון"},
 {"kind": "go", "project": "Nowhere", "text": "x"},
 {"kind": "delete", "project": "Shop", "text": "rm"},
 {"kind": "about", "project": "", "text": "עובד בעיקר מהטלפון"}]
```"""
        val (text, acts) = Partner.split(answer, listOf(shop))
        assertEquals("עבוד על החנות: התשלום הוא הצעד שחסר לגרסה הראשונה.", text)
        assertEquals(listOf(
            Partner.Act(Partner.GO, "shop", "תחבר את Stripe לדף התשלום"),
            Partner.Act(Partner.LEFT, "shop", "לבדוק תשלום בטלפון"),
            Partner.Act(Partner.ABOUT, "", "עובד בעיקר מהטלפון"),
        ), acts)
    }

    @Test fun anAnswerWithoutButtonsIsLeftAlone() {
        assertEquals("שלום" to emptyList<Partner.Act>(), Partner.split("שלום\n", listOf(shop)))
        // A broken block: the words stay, no buttons.
        val (text, acts) = Partner.split("כן.\n```ydeck-actions\n[{oops\n```", listOf(shop))
        assertEquals("כן.", text); assertTrue(acts.isEmpty())
    }

    @Test fun aRecapOfTheLastTurns() {
        val turns = (1..8).map { Assistant.Turn("q$it", "a$it") } + Assistant.Turn("unanswered")
        val r = Partner.recap(turns)
        assertTrue(r.startsWith("Me: q3\nYDeck: a3"))
        assertTrue(r.endsWith("Me: q8\nYDeck: a8"))
    }
}
