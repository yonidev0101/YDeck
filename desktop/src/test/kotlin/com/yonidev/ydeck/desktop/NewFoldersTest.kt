package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A project made on the phone gets its folder next to the others. */
class NewFoldersTest {
    @Test fun nameIsNeverAPath() {
        assertEquals("a b", NewFolders.safeName("../a\\b"))
        assertEquals("New project", NewFolders.safeName(".."))
        assertEquals("New project", NewFolders.safeName("  "))
        assertEquals("con project", NewFolders.safeName("con"))
        assertEquals("אפליקציה חדשה", NewFolders.safeName("אפליקציה חדשה"))
        assertEquals("a b", NewFolders.safeName("a: b?"))
    }

    @Test fun homeIsWhereMostProjectsAre() {
        val root = Files.createTempDirectory("ydeck").toFile()
        val a = File(root, "projects").apply { mkdirs() }
        val b = File(root, "other").apply { mkdirs() }
        val home = NewFolders.home(listOf(File(a, "x").path, File(a, "y").path, File(b, "z").path))
        assertEquals(a.canonicalPath, home!!.canonicalPath)
        assertNull(NewFolders.home(emptyList()))
    }

    @Test fun takenNamesGetANumber() {
        val home = Files.createTempDirectory("ydeck").toFile()
        val first = NewFolders.make(home, "Shop")!!
        val second = NewFolders.make(home, "Shop")!!
        assertEquals("Shop", first.name)
        assertEquals("Shop 2", second.name)
        assertTrue(second.isDirectory)
    }
}
