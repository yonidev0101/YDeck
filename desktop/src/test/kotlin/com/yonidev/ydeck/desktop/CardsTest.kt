package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Answer
import com.yonidev.ydeck.link.CardGroup
import com.yonidev.ydeck.link.CardItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardsTest {
    private val nova = Project.sample().copy(id = "nova", name = "Nova")
    private val lists = { id: String -> if (id == "nova") listOf("Finish the export") to listOf("A streak widget") else emptyList<String>() to emptyList() }

    @Test fun anAnswerIsReadIntoACardWithItsLinesChecked() {
        val answer = """Here:
            {"say": "שני דברים נשארו ב־Nova.", "groups": [
              {"kind": "left", "title": "נשאר", "items": [
                {"project": "Nova", "text": "Finish the export"},
                {"project": "nova", "text": "Something Claude made up"}]},
              {"kind": "idea", "title": "רעיונות", "items": [{"project": "Nova", "text": "A streak widget"}]},
              {"kind": "weird", "title": "עוד", "items": [{"project": "ghost", "text": "x"}]},
              {"kind": "done", "title": "ריק", "items": []}
            ], "more": ""}"""
        val card = Cards.read(answer, listOf(nova), lists)!!
        assertEquals("שני דברים נשארו ב־Nova.", card.say)
        assertEquals(listOf(CardGroup.LEFT, CardGroup.IDEA, CardGroup.INFO), card.groups.map { it.kind })
        assertEquals(listOf(CardItem("nova", "Finish the export", CardItem.FROM_LEFT), CardItem("nova", "Something Claude made up")), card.groups[0].items)
        assertEquals(CardItem.FROM_IDEAS, card.groups[1].items[0].from)
        // A project YDeck doesn't know: the line stays, about no project.
        assertEquals(CardItem("", "x"), card.groups[2].items[0])
    }

    @Test fun wordsWithoutACardStayWords() {
        assertNull(Cards.read("Just words.", listOf(nova), lists))
        assertNull(Cards.read("{\"say\": \"\", \"groups\": []}", listOf(nova), lists))
    }

    @Test fun theCardTravelsWithTheAnswerAndHasMarkdown() {
        val card = Cards.read("""{"say": "כן.", "groups": [{"kind": "left", "title": "נשאר", "items": [{"project": "nova", "text": "Finish the export"}]}]}""", listOf(nova), lists)!!
        val back = Answer.of(JSONObject(Answer("a", "c", done = true, answer = "x", card = card).toJson().toString()))
        assertEquals(card, back.card)
        assertEquals("כן.\n\n**נשאר**\n- Nova: Finish the export", card.markdown { "Nova" })
        assertNull(Answer.of(JSONObject(Answer("a", "c").toJson().toString())).card)
    }
}
