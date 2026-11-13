package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Ask
import com.yonidev.ydeck.link.Morning
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningTest {

    @Test fun theMorningAndItsHourTravelInTheState() {
        val m = Morning("m1", "morning-abc", "**YDeck**: בוא נסיים היום את השלט. מה אתה אומר?", 1_700_000_000_000)
        val back = Snapshot.decode(Snapshot(1, "PC", JSONObject(), emptyMap(), morning = m, morningAt = 480).encode())
        assertEquals(m, back.morning)
        assertEquals(480, back.morningAt)
    }

    @Test fun anOlderStateHasNoMorning() {
        val back = Snapshot.decode(Snapshot(1, "PC", JSONObject(), emptyMap()).encode())
        assertNull(back.morning)
        assertEquals(-1, back.morningAt)
    }

    @Test fun aMorningAskMayHaveNoWordsButAQuestionMust() {
        val asked = Ask.decode(Ask("a1", "c1", "", "", 5, morning = true).encode())!!
        assertTrue(asked.morning)
        assertNull(Ask.decode(Ask("a2", "c1", "  ", "", 5).encode()))
        assertEquals(false, Ask.decode(Ask("a3", "c1", "מה מחכה לי?", "", 5).encode())!!.morning)
    }

    @Test fun claudesAnswerIsReadIntoTopicsOfKnownProjectsOnly() {
        val nova = Project.sample().copy(id = "nova", name = "Nova")
        val answer = """Sure:
            {"hello": "בוקר טוב!", "topics": [
              {"project": "nova", "kind": "finish", "say": "הייצוא כמעט גמור.", "ask": "מסיימים היום?", "step": "Finish the CSV export."},
              {"project": "Nova", "kind": "idea", "say": "ווידג׳ט לרצף?"},
              {"project": "ghost", "kind": "stuck", "say": "לא קיים"}
            ]}"""
        val (hello, topics) = GoodMorning.read(answer, listOf(nova))!!
        assertEquals("בוקר טוב!", hello)
        assertEquals(listOf("nova", "nova"), topics.map { it.project })
        assertEquals("Finish the CSV export.", topics[0].step)
        assertNull(GoodMorning.read("no json here", listOf(nova)))
    }

    @Test fun anEmptyMorningIsDropped() {
        assertNull(Morning.of(JSONObject().put("id", "x").put("chat", "c").put("text", " ")))
    }
}
