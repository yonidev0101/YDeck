package com.yonidev.ydeck.desktop

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TalkTest {
    private val dir = File("C:\\Users\\user\\Desktop\\YoniDev projects\\Shop")

    @Test fun claudeCodesFolderForAProject() {
        assertEquals("C--Users-user-Desktop-YoniDev-projects-Shop", Transcript.folderOf(dir).name)
    }

    @Test fun aTranscriptReadsAsTheConversation() {
        I18n.forced = Lang.EN
        val lines = listOf(
            """{"type":"user","isSidechain":false,"message":{"role":"user","content":"תתקן את הכותרת"}}""",
            """{"type":"assistant","isSidechain":false,"message":{"content":[{"type":"thinking","thinking":"x"},{"type":"tool_use","name":"Edit","input":{"file_path":"C:\\Users\\user\\Desktop\\YoniDev projects\\Shop\\src\\Home.tsx"}}]}}""",
            """{"type":"user","isSidechain":false,"message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"t","content":"ok"}]}}""",
            """{"type":"assistant","isSidechain":true,"message":{"content":[{"type":"text","text":"a helper's words"}]}}""",
            """{"type":"user","isSidechain":false,"isMeta":true,"message":{"role":"user","content":"<command-name>/clear</command-name>"}}""",
            """{"type":"assistant","isSidechain":false,"message":{"content":[{"type":"text","text":"תיקנתי."}]}}""",
        ).flatMap { Transcript.lineOf(JSONObject(it), dir).orEmpty() }
        assertEquals(listOf(
            Talk.Line(Talk.Kind.USER, "תתקן את הכותרת"),
            Talk.Line(Talk.Kind.STEP, "Changed src/Home.tsx"),
            Talk.Line(Talk.Kind.CLAUDE, "תיקנתי."),
        ), lines)
        I18n.forced = null
    }

    @Test fun ydecksOwnRunsArentListed() {
        val f = Files.createTempFile("talk", ".jsonl").toFile()
        try {
            f.writeText("""{"type":"user","isSidechain":false,"message":{"role":"user","content":"You are YDeck, the developer's project dashboard."}}""" + "\n")
            assertNull(Transcript.titleOf(f))
            f.writeText("""{"type":"user","isSidechain":false,"message":{"role":"user","content":"<local-command-caveat>x</local-command-caveat>"}}""" + "\n" +
                """{"type":"user","isSidechain":false,"message":{"role":"user","content":"Add a login page\nwith Google"}}""" + "\n")
            assertEquals("Add a login page", Transcript.titleOf(f))
            f.appendText("""{"type":"custom-title","customTitle":"Login"}""" + "\n")
            assertEquals("Login", Transcript.titleOf(f))
        } finally { f.delete() }
    }

    @Test fun whatAlwaysWouldAllow() {
        I18n.forced = Lang.EN
        try {
            assertEquals("changing files, for this conversation", Talk.alwaysWords(JSONArray("""[{"type":"setMode","mode":"acceptEdits","destination":"session"}]""")))
            assertEquals("Bash(npm run build:*)", Talk.alwaysWords(JSONArray("""[{"type":"addRules","rules":[{"toolName":"Bash","ruleContent":"npm run build:*"}],"behavior":"allow","destination":"localSettings"}]""")))
            assertNull(Talk.alwaysWords(JSONArray("[]")))
        } finally { I18n.forced = null }
    }

    @Test fun whatAPermissionIsFor() {
        assertEquals("npm run build", Talk.permissionWhat("Bash", JSONObject().put("command", " npm run build "), dir, ""))
        assertEquals("src/a.ts", Talk.permissionWhat("Write", JSONObject().put("file_path", File(dir, "src\\a.ts").path), dir, ""))
    }
}
