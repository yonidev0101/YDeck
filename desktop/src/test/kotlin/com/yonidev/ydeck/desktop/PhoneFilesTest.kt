package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.FileAsk
import com.yonidev.ydeck.link.FilePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The phone looks at a project's files: only inside its folder, never a secret, in pieces that fit the line. */
class PhoneFilesTest {
    private val root = Files.createTempDirectory("ydeck-files").toFile().also {
        File(it, "src").mkdirs(); File(it, "src/a.txt").writeText("hello"); File(it, ".env").writeText("KEY=1")
    }

    @Test fun neverOutsideTheFolder() {
        assertEquals(root.canonicalFile, PhoneFiles.resolve(root, ""))
        assertEquals(File(root, "src/a.txt").canonicalFile, PhoneFiles.resolve(root, "src/a.txt"))
        assertNull(PhoneFiles.resolve(root, "../x"))
        assertNull(PhoneFiles.resolve(root, "src/../../x"))
        assertNull(PhoneFiles.resolve(root, "C:/Windows"))
        assertNull(PhoneFiles.resolve(root, "src\\..\\..\\x"))
        assertNull(PhoneFiles.resolve(root, ".git/config"))
        assertNull(PhoneFiles.resolve(root, ".env"))
    }

    @Test fun secretsAreHidden() {
        listOf(".env", ".env.local", "id_rsa", "server.pem", "release.jks", "local.properties", ".npmrc", "credentials.json", ".git").forEach {
            assertTrue(it, PhoneFiles.hidden(it))
        }
        listOf(".env.example", "README.md", "main.kt", "key.png", "secretary.txt").forEach { assertFalse(it, PhoneFiles.hidden(it)) }
    }

    /** Saving from the phone: in pieces, only when editing is on, never over a file that changed here meanwhile. */
    @Test fun savesOnlyOverWhatThePhoneOpened() {
        val was = Settings.state.value
        try {
            val f = File(root, "src/a.txt")
            val opened = FileAsk.hash(f.readBytes())
            val new = ByteArray(FilePart.PIECE + 10) { 'x'.code.toByte() }
            fun put(id: String, up: String, from: Int, to: Int, base: String = opened) =
                PhoneFiles.answer(FileAsk(id, "p", "src/a.txt", FileAsk.PUT, from.toLong(), up, base, new.size.toLong(), new.copyOfRange(from, to)))

            Settings.preview(Settings.State(local = mapOf("p" to Local(folder = root.path)), phoneFiles = true, phoneEdit = false))
            assertEquals("editoff", put("1", "u0", 0, FilePart.PIECE).error)

            Settings.preview(Settings.State(local = mapOf("p" to Local(folder = root.path)), phoneFiles = true, phoneEdit = true))
            val first = put("2", "u1", 0, FilePart.PIECE)
            assertEquals("", first.error); assertEquals(FilePart.PIECE.toLong(), first.from)
            assertEquals("hello", f.readText())
            val last = put("3", "u1", FilePart.PIECE, new.size)
            assertEquals(FileAsk.hash(new), last.hash)
            assertTrue(f.readBytes().contentEquals(new))

            // Opened before a change here: refused, and what's here stays.
            f.writeText("changed here")
            val stale = PhoneFiles.answer(FileAsk("4", "p", "src/a.txt", FileAsk.PUT, 0, "u2", opened, 3, "abc".toByteArray()))
            assertEquals("changed", stale.error)
            assertEquals("changed here", f.readText())

            // Never a secret, never a new file.
            assertTrue(PhoneFiles.answer(FileAsk("5", "p", ".env", FileAsk.PUT, 0, "u3", "", 1, "x".toByteArray())).error.isNotEmpty())
            assertTrue(PhoneFiles.answer(FileAsk("6", "p", "src/new.txt", FileAsk.PUT, 0, "u4", "", 1, "x".toByteArray())).error.isNotEmpty())
            assertFalse(File(root, "src/new.txt").exists())
        } finally {
            Settings.preview(was)
        }
    }

    @Test fun piecesFitTheLine() {
        // Sealed (gzip + AES) a piece of random bytes, as base64 in JSON, stays under the relay's 64 KB.
        val b = ByteArray(FilePart.PIECE).also { java.util.Random(1).nextBytes(it) }
        val sealed = com.yonidev.ydeck.link.Pairing.create().seal("filepart", FilePart("x", "a/b", FileAsk.READ, data = b, total = 9_999_999).encode())
        assertTrue(sealed.size.toString(), sealed.size < 64_000)
        val back = FilePart.decode(FilePart("x", "a", FileAsk.READ, data = b, total = b.size.toLong()).encode())!!
        assertTrue(back.data.contentEquals(b))
        assertTrue(back.done)
    }
}
