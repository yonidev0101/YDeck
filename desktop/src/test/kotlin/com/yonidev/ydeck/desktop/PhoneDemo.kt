package com.yonidev.ydeck.desktop

import com.yonidev.ydeck.link.Pairing
import com.yonidev.ydeck.link.Relay
import org.junit.Test
import java.io.File

/**
 * `-Pphone`: puts this PC's real projects and memory in a fresh box on the relay (not the app's own pairing) and
 * writes its QR text to build/phone-qr.txt, to pair an emulator through adb. `-Pphone=delete` empties that box again.
 */
class PhoneDemo {
    @Test fun demo() {
        val arg = System.getProperty("phone") ?: return
        val out = File("build/phone-qr.txt")
        if (arg == "delete") {
            val p = Pairing.parse(out.readText().trim())!!.pairing
            val r = Relay(p)
            val ok = r.delete()
            println("deleted: $ok (${r.lastCode})")
            if (ok) out.delete()
            return
        }
        val p = Pairing.create()
        val snapshot = PhoneLink.snapshot()
        val sealed = p.seal("state", snapshot.encode())
        check(Relay(p).putState(sealed)) { "relay refused" }
        out.writeText(p.qrText(PhoneLink.computer))
        if (arg == "roundtrip") { val r = Relay(p); println("get: ${r.getState(null)?.sealed?.size}"); println("deleted: ${r.delete()} (${r.lastCode})") }
        println("projects: ${snapshot.deck.optJSONArray("projects")?.length()}, with memory: ${snapshot.memory.count { it.value.folder.isNotEmpty() }}, sealed bytes: ${sealed.size}")
    }
}
