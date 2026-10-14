package com.yonidev.ydeck.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * On the PC, the right account is a Chrome profile: each profile is signed in to one Google account, so a project
 * opens in the profile whose account is the project's email. That replaces the phone's account chooser (Google)
 * and Chrome channels (Vercel), and works for every service: GitHub, Vercel and the rest stay signed in per profile.
 */
object Chrome {
    data class Profile(val dir: String, val name: String, val email: String?)

    private val userData = File(System.getenv("LOCALAPPDATA") ?: "", "Google/Chrome/User Data")

    /** chrome.exe: from App Paths (where the installer registers it), else the usual places. */
    val exe: File? by lazy {
        val key = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\chrome.exe"
        val fromRegistry = listOf(WinReg.HKEY_CURRENT_USER, WinReg.HKEY_LOCAL_MACHINE).firstNotNullOfOrNull { root ->
            runCatching { Advapi32Util.registryGetStringValue(root, key, "") }.getOrNull()
        }
        (listOfNotNull(fromRegistry) + listOf(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            (System.getenv("LOCALAPPDATA") ?: "") + "\\Google\\Chrome\\Application\\chrome.exe",
        )).map { File(it.trim('"')) }.firstOrNull { it.exists() }
    }

    /** The profiles listed in Chrome's "Local State", with the Google account each is signed in to. */
    fun profiles(): List<Profile> = runCatching {
        val cache = JSONObject(File(userData, "Local State").readText()).getJSONObject("profile").getJSONObject("info_cache")
        cache.keys().asSequence().map { dir ->
            val p = cache.getJSONObject(dir)
            Profile(dir, p.optString("name", dir), p.optString("user_name").takeIf { it.contains('@') })
        }.sortedWith(compareBy({ it.dir != "Default" }, { it.name.lowercase() })).toList()
    }.getOrDefault(emptyList())

    /** The profile to open [p] in: the one chosen for it on this PC, else the one signed in to its Google account. */
    fun profileFor(p: Project, all: List<Profile> = profiles()): Profile? {
        Settings.state.value.profiles[p.id]?.let { dir -> all.firstOrNull { it.dir == dir }?.let { return it } }
        val email = p.googleEmail ?: return null
        return all.firstOrNull { it.email.equals(email, ignoreCase = true) }
    }

    /** Opens [action] of [p]: in its profile when there is one; Google links without one go through the account chooser. */
    fun open(p: Project, action: DeckAction) {
        val profile = profileFor(p)
        val url = if (profile == null && action.service.route == Route.GOOGLE && p.googleEmail != null) Links.accountChooser(p.googleEmail, action.url)
        else action.url
        open(url, profile?.dir)
    }

    fun open(url: String, profileDir: String?) {
        val chrome = exe
        if (chrome != null) {
            val cmd = buildList {
                add(chrome.absolutePath)
                if (profileDir != null) add("--profile-directory=$profileDir")
                add(url)
            }
            runCatching { ProcessBuilder(cmd).start() }.onSuccess { return }
        }
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    /** What the widget says the link opens in: the profile's account, or its name. */
    fun describe(p: Project): String = profileFor(p)?.let { it.email ?: it.name } ?: p.googleEmail ?: "Chrome"
}
