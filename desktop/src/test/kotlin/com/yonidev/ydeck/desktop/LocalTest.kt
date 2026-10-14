package com.yonidev.ydeck.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The PC-only parts: reading a project folder, git's status line, the search, the folder icon. */
class LocalTest {
    init { I18n.forced = Lang.HE }

    @Test fun gitRemotesBecomeWebPages() {
        assertEquals("https://github.com/yonidev/ydeck", Scanner.webUrl("git@github.com:yonidev/ydeck.git"))
        assertEquals("https://github.com/yonidev/ydeck", Scanner.webUrl("https://github.com/yonidev/ydeck.git"))
        assertEquals("https://gitlab.com/a/b/c", Scanner.webUrl("ssh://git@gitlab.com/a/b/c.git"))
        assertEquals("https://github.com/o/r", Scanner.webUrl("https://token@github.com/o/r"))
        assertNull(Scanner.webUrl("../local/repo"))
        val config = "[core]\n\tbare = false\n[remote \"upstream\"]\n\turl = git@github.com:x/up.git\n[remote \"origin\"]\n\turl = git@github.com:me/mine.git\n"
        assertEquals("git@github.com:me/mine.git", Scanner.originUrl(config))
    }

    @Test fun projectFilesAreRead() {
        assertEquals("nova-prod", Scanner.firebaseProject("""{"projects":{"default":"nova-prod"}}"""))
        assertEquals("dev.nova.app", Scanner.applicationId("android {\n  defaultConfig {\n    applicationId = \"dev.nova.app\"\n  }\n}"))
        assertEquals("dev.nova", Scanner.applicationId("android { namespace 'dev.nova' }"))
        assertEquals("Receipt scanner", Scanner.prettify("receipt-scanner"))
    }

    @Test fun aFolderFillsAProject() {
        val dir = Files.createTempDirectory("ydeck-scan").toFile()
        try {
            File(dir, ".git").mkdirs()
            File(dir, ".git/config").writeText("[remote \"origin\"]\n\turl = git@github.com:nova/web.git\n")
            File(dir, ".firebaserc").writeText("""{"projects":{"default":"nova-web"}}""")
            File(dir, ".vercel").mkdirs()
            File(dir, ".vercel/project.json").writeText("""{"projectId":"prj_x","orgId":"team_y","projectName":"nova-web"}""")
            File(dir, "package.json").writeText("""{"name":"nova-web","scripts":{"dev":"next dev","build":"next build","lint":"x"},"dependencies":{"next":"15"}}""")
            File(dir, "pnpm-lock.yaml").writeText("")
            val found = Scanner.scan(dir)
            assertEquals(listOf("github", "firebase", "vercel"), found.actions.map { it.service.id })
            assertEquals("https://console.firebase.google.com/project/nova-web/overview", found.actions[1].url)
            assertEquals(listOf(Command("dev", "pnpm run dev"), Command("build", "pnpm run build")), found.commands)
            assertEquals(CopyItem("Repo", "nova/web"), found.copies.single())
            assertEquals("אתר Next.js", found.kind)

            // Merging keeps what the project has: its own GitHub link stays, nothing doubles.
            val p = Project.blank().copy(name = "Nova", actions = listOf(DeckAction(Catalog.of("github"), "https://github.com/nova", "GitHub")))
            val merged = Scanner.merge(p, found)
            assertEquals("Nova", merged.name)
            assertEquals(listOf("github", "firebase", "vercel"), merged.actions.map { it.service.id })
            assertEquals(merged, Scanner.merge(merged, found))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun gitStatusLine() {
        val a = Git.parse("## main...origin/main [ahead 1]\n M a.kt\n?? b.kt\n")!!
        assertEquals(listOf("main", 2, 1, 0, "origin/main"), listOf(a.branch, a.changes, a.ahead, a.behind, a.upstream))
        assertEquals(listOf('M', 'A'), a.files.map { it.kind })
        val b = Git.parse("## dev...origin/dev [behind 3]\n")!!
        assertEquals(listOf("dev", 0, 3), listOf(b.branch, b.changes, b.behind))
        val c = Git.parse("## No commits yet on main\n")!!
        assertEquals("main", c.branch)
        assertEquals(null, c.upstream)
        assertEquals(null, Git.parse("## feature\n")!!.upstream)
        assertEquals("main · 3 שינויים · ↑1", GitInfo("main", 3, 1, 0).describe().replace("‏", ""))
    }

    @Test fun gitStatusWithNulsRenamesAndConflicts() {
        val g = Git.parse("## main...origin/main\u0000R  new name.kt\u0000old.kt\u0000UU src/app/מסך.kt\u0000 D gone.txt\u0000")!!
        assertEquals(3, g.changes)
        assertEquals(GitChange("R ", "new name.kt", "old.kt"), g.files[0])
        assertEquals(listOf('R', 'U', 'D'), g.files.map { it.kind })
        assertEquals(1, g.conflicts)
        assertEquals("מסך.kt", g.files[1].name)
        assertEquals("src/app", g.files[1].folder)
    }

    @Test fun hostsAreNamed() {
        assertEquals("GitHub", hostName("https://github.com/me/app"))
        assertEquals("git.example.com", hostName("https://git.example.com/me/app"))
    }

    @Test fun diffIsReadIntoLines() {
        val out = "diff --git a/a.kt b/a.kt\nindex 1..2 100644\n--- a/a.kt\n+++ b/a.kt\n@@ -1,3 +1,3 @@ fun main\n keep\n-old\n+new\n"
        assertEquals(listOf(DiffLine('@', "fun main"), DiffLine(' ', "keep"), DiffLine('-', "old"), DiffLine('+', "new")), GitOps.parseDiff(out))
    }

    @Test fun gitErrorsAreSaidPlainly() {
        assertTrue(GitOps.said(" ! [rejected]        main -> main (fetch first)\nerror: failed to push some refs\nhint: Updates were rejected").contains("קודם מושכים"))
        assertEquals("bad thing", GitOps.said("fatal: bad thing\n"))
    }

    /** A real repository in a temporary folder: status, a commit of chosen files, the log. */
    @Test fun commitsChosenFiles() {
        if (!Git.installed) return
        val dir = kotlin.io.path.createTempDirectory("ydeck-git").toFile()
        try {
            Git.run(dir, listOf("init", "-b", "main"))
            Git.run(dir, listOf("config", "user.name", "Test"))
            Git.run(dir, listOf("config", "user.email", "test@example.com"))
            java.io.File(dir, "a.txt").writeText("a\n")
            java.io.File(dir, "b.txt").writeText("b\n")
            Git.refreshOne(dir.path)
            assertEquals(2, Git.state.value[dir.path]!!.changes)
            assertEquals(null, GitOps.commit(dir, "Add a", listOf("a.txt")))
            val after = Git.state.value[dir.path]!!
            assertEquals(listOf("b.txt"), after.files.map { it.path })
            assertEquals("Add a", after.lastSubject)
            assertEquals(listOf("Add a"), GitOps.log(dir).map { it.subject })
            java.io.File(dir, "a.txt").writeText("a\nmore\n")
            assertEquals(listOf(DiffLine('@', "@@ -1 +1,2 @@"), DiffLine(' ', "a"), DiffLine('+', "more")).drop(1),
                GitOps.diff(dir, GitChange(" M", "a.txt")).drop(1))
            assertEquals(null, GitOps.commit(dir, "The rest", null))
            assertEquals(0, Git.state.value[dir.path]!!.changes)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun searchFindsByProjectAndService() {
        val nova = Project.sample()
        val other = Project.blank().copy(id = "o", name = "Orbit", actions = listOf(DeckAction(Catalog.of("firebase"), "https://console.firebase.google.com/project/orbit", "Firebase")))
        val all = Search.entries(Deck(listOf(nova, other)), Settings.State())
        val hits = Search.match("nova fire", all)
        assertEquals("Firebase", hits.first().title)
        assertEquals("Nova", hits.first().project.name)
        assertTrue(Search.match("fire", all).map { it.project.name }.containsAll(listOf("Nova", "Orbit")))
        assertTrue(Search.match("zzz", all).isEmpty())
    }

    @Test fun taggingAFolderAndUndoingIt() {
        val dir = Files.createTempDirectory("ydeck-tag").toFile()
        try {
            File(dir, ".git/info").mkdirs()
            File(dir, "desktop.ini").writeText("[ViewState]\r\nMode=4\r\n")
            FolderTag.apply(dir, Project.sample())
            val ini = File(dir, "desktop.ini")
            val text = String(ini.readBytes().drop(2).toByteArray(), Charsets.UTF_16LE)
            assertTrue(text.contains("IconResource=") && text.contains("InfoTip=YDeck · Nova · dana@nova.dev") && text.contains("Mode=4"))
            assertTrue(FolderTag.isTagged(dir))
            assertEquals(true, Files.getAttribute(ini.toPath(), "dos:hidden"))
            assertTrue(File(dir, ".git/info/exclude").readText().lines().contains("desktop.ini"))
            FolderTag.remove(dir)
            // The user's own settings stay; ours are gone.
            val after = String(ini.readBytes().drop(2).toByteArray(), Charsets.UTF_16LE)
            assertTrue(after.contains("Mode=4") && !after.contains("IconResource") && !FolderTag.isTagged(dir))
        } finally {
            dir.walkBottomUp().forEach { runCatching { Files.setAttribute(it.toPath(), "dos:readonly", false); Files.setAttribute(it.toPath(), "dos:system", false) } }
            dir.deleteRecursively()
        }
    }

    @Test fun icoHoldsEverySize() {
        val bytes = Ico.encode(FolderTag.render(Project.sample()))
        assertEquals(0, bytes[0].toInt()); assertEquals(1, bytes[2].toInt()); assertEquals(Ico.SIZES.size, bytes[4].toInt())
    }

    /** Prints what each real project folder next to this one yields (a look, not an assertion). */
    @Test fun scanNeighbours() {
        if (System.getProperty("render") == null) return
        File("../..").listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach { d ->
            val f = Scanner.scan(d)
            println("${d.name}: ${f.name} | ${f.kind} | ${f.summary()} | ${f.actions.joinToString { it.url }} | ${f.commands.joinToString { it.line }} | ${f.logo?.name}")
        }
    }
}

/** Icons for the user's real links (network; only with -Prender): what the PC now finds for each site. */
class SiteIconsLook {
    @Test fun realSites() {
        if (System.getProperty("render") == null) return
        val deck = DeckJson.decode(File(System.getenv("APPDATA"), "YDeck/projects.json").readText())
        deck.projects.flatMap { p -> p.actions.mapNotNull { it.faviconHost } }.distinct().forEach { host ->
            val img = runCatching { SiteIcons.best(host, 32) }.getOrNull()
            println("ICON $host -> ${img?.width ?: "none"}")
        }
        deck.projects.forEach { p ->
            val url = Logos.siteOf(p) ?: return@forEach
            val img = runCatching { SiteIcons.logo(url) }.getOrNull()
            println("LOGO ${p.name} $url -> ${img?.width ?: "none"}")
        }
    }
}
