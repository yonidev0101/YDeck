package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.MemTalk
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.link.Note
import com.yonidev.ydeck.link.Snapshot
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The dossier, saving and the conversation travel to the phone whole, and the phone's requests come back. */
class PhoneDossierTest {
    @Test fun theDossierReachesThePhone() {
        val m = Memory(saving = "קובץ אחד לא נשמר", savingProposal = "לשמור", savingSteps = "שמירה · העלאה", savingBusy = true, savingResult = "ok",
            suggestedGoal = "גרסה ראשונה", suggestedSteps = listOf("מסך הרשמה", "תשלום"), dossierBusy = true, dossierError = "x",
            talk = listOf(MemTalk(MemTalk.USER, "תתקן"), MemTalk(MemTalk.STEP, "שינה את a.css"), MemTalk(MemTalk.CLAUDE, "תיקנתי.\n\nעוד משהו?")),
            talkTitle = "תתקן", talkBusy = true, talkAsk = "להריץ פקודה: npm test", talkError = "e", talkElsewhere = true,
            talkAskId = "r1", talkAskAlways = "Bash(npm test:*)", talkSession = "s1", talkUrl = "https://claude.ai/code/session_x",
            insight = "כמעט גמור", commands = listOf("git status", "npm run build"))
        val back = Snapshot.decode(Snapshot(1, "PC", JSONObject(), mapOf("p" to m)).encode()).memory.getValue("p")
        assertEquals(m, back)
    }

    @Test fun aMessageToClaudeKeepsItsLines() {
        val n = Note("1", "p", Note.TALK_SAY, "תתקן את הכותרת\nובדוק בטלפון", 5)
        assertEquals("תתקן את הכותרת\nובדוק בטלפון", Note.decode(n.encode())!!.text)
        assertEquals("", Note.decode(Note("2", "p", Note.TALK_NEW, "", 5).encode())!!.text)
        assertEquals(Note.SAVE_DO, Note.decode(Note("3", "p", Note.SAVE_DO, "", 5).encode())!!.kind)
        assertNull(Note.decode(Note("4", "p", "talk.run", "rm -rf", 5).encode()))
    }
}
