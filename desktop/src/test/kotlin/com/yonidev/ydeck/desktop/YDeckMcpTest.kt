package com.yonidev.ydeck.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YDeckMcpTest {
    private fun call(method: String, params: JSONObject = JSONObject()) =
        YDeckMcp.answer(JSONObject().put("jsonrpc", "2.0").put("id", 7).put("method", method).put("params", params))

    @Test fun initializeAgreesOnAVersionItKnows() {
        val r = call("initialize", JSONObject().put("protocolVersion", "2025-03-26")).getJSONObject("result")
        assertEquals("2025-03-26", r.getString("protocolVersion"))
        assertEquals("ydeck", r.getJSONObject("serverInfo").getString("name"))
        assertTrue(r.getJSONObject("capabilities").has("tools"))
        // An unknown version gets the newest one known.
        assertEquals("2025-06-18", call("initialize", JSONObject().put("protocolVersion", "1999-01-01")).getJSONObject("result").getString("protocolVersion"))
    }

    @Test fun everyToolOnlyReads() {
        val tools = call("tools/list").getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }
        assertEquals(listOf("projects", "project", "decisions", "sessions", "search"), names)
        (0 until tools.length()).forEach { assertTrue(tools.getJSONObject(it).getJSONObject("annotations").getBoolean("readOnlyHint")) }
    }

    @Test fun unknownCallsAreErrors() {
        assertEquals(-32601, call("resources/list").getJSONObject("error").getInt("code"))
        assertEquals(-32602, call("tools/call", JSONObject().put("name", "delete_everything")).getJSONObject("error").getInt("code"))
        assertEquals(7, call("ping").getInt("id"))
    }

    @Test fun theFilesThatHoldKeysAreNeverSearched() {
        listOf(".env", ".env.local", "server.pem", "upload.jks", "release.keystore", "client_secret.json", "credentials.json",
            "local.properties", "keystore.properties", "id_rsa", ".npmrc").forEach { assertTrue(it, YDeckMcp.secret(File(it))) }
        listOf("Main.kt", "README.md", "package.json", "gradle.properties", "notes.md").forEach { assertFalse(it, YDeckMcp.secret(File(it))) }
    }
}
