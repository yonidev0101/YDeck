package com.yonidev.ydeck.desktop

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import java.awt.Window
import java.io.File

/*
 * Windows' own picker (IFileOpenDialog, the one File Explorer's apps use), for a folder or a file: Swing's
 * JFileChooser looks like Windows 2000, and AWT's FileDialog like Windows XP. COM by hand through JNA: the calls go
 * by their place in the vtable.
 * Null when it was cancelled; [Unavailable] when it couldn't open (not Windows), for the caller to fall back.
 */
object SystemPicker {
    object Unavailable : Exception()

    private val CLSID = Guid.GUID.fromString("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}")
    private val IID_DIALOG = Guid.GUID.fromString("{d57c7288-d4ad-4768-be02-9d969532d960}")
    private val CLSID_SAVE = Guid.GUID.fromString("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}")
    private val IID_SAVE = Guid.GUID.fromString("{84bccd23-5fde-4cdb-aea4-af64b83d78ab}")
    private val IID_ITEM = Guid.GUID.fromString("{43826d1e-e718-42ee-bc55-a1e261c37bfe}")
    private const val FOS_PICKFOLDERS = 0x20
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_PATHMUSTEXIST = 0x800
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()
    private const val CANCELLED = 0x800704C7.toInt()

    /** Calls the method at [slot] of the COM object [obj], with its own pointer first. */
    private fun call(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val f = Function.getFunction(obj.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return f.invokeInt(arrayOf(obj, *args))
    }

    private fun release(obj: Pointer?) { if (obj != null) call(obj, 2) }

    /**
     * A folder ([folders]) or a file to open; with [save], a file to save, offered under that name. [types] narrows the
     * files to a kind: its name and its patterns ("*.png;*.jpg").
     */
    fun pick(window: Window?, title: String, folders: Boolean = false, start: File? = null, types: Pair<String, String>? = null, save: String? = null): File? {
        if (!System.getProperty("os.name").startsWith("Windows")) throw Unavailable
        val init = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED).toInt()
        val ref = PointerByReference()
        try {
            if (Ole32.INSTANCE.CoCreateInstance(if (save != null) CLSID_SAVE else CLSID, null, 1 /* CLSCTX_INPROC_SERVER */, if (save != null) IID_SAVE else IID_DIALOG, ref).toInt() != 0) throw Unavailable
            val dlg = ref.value
            try {
                val opts = IntArray(1).also { call(dlg, 10, it) }[0]  // GetOptions
                call(dlg, 9, opts or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST or (if (folders) FOS_PICKFOLDERS else 0))  // SetOptions
                call(dlg, 17, WString(title))  // SetTitle
                types?.let { (name, spec) ->
                    val n = Memory((name.length + 1L) * 2).apply { setWideString(0, name) }
                    val p = Memory((spec.length + 1L) * 2).apply { setWideString(0, spec) }
                    val filter = Memory(2L * Native.POINTER_SIZE).apply { setPointer(0, n); setPointer(Native.POINTER_SIZE.toLong(), p) }
                    call(dlg, 4, 1, filter)  // SetFileTypes (it copies them)
                    java.lang.ref.Reference.reachabilityFence(n); java.lang.ref.Reference.reachabilityFence(p)
                    if (save != null) call(dlg, 22, WString(spec.substringAfter("*.").substringBefore(";")))  // SetDefaultExtension
                }
                save?.let { call(dlg, 15, WString(it)) }  // SetFileName
                start?.takeIf { it.isDirectory }?.let { dir ->
                    val item = PointerByReference()
                    val parse = Function.getFunction("shell32", "SHCreateItemFromParsingName", Function.ALT_CONVENTION)
                    if (parse.invokeInt(arrayOf(WString(dir.path), null, IID_ITEM, item)) == 0) {
                        call(dlg, 11, item.value)  // SetDefaultFolder: where it opens the first time, then where the user went last
                        release(item.value)
                    }
                }
                val owner = window?.let { runCatching { Native.getComponentPointer(it) }.getOrNull() }
                when (val hr = call(dlg, 3, owner)) {  // Show
                    0 -> {}
                    CANCELLED -> return null
                    else -> throw Unavailable.also { System.err.println("Picker: Show failed 0x${hr.toUInt().toString(16)}") }
                }
                val item = PointerByReference()
                if (call(dlg, 20, item) != 0) return null  // GetResult
                try {
                    val name = PointerByReference()
                    if (call(item.value, 5, SIGDN_FILESYSPATH, name) != 0) return null  // IShellItem.GetDisplayName
                    return File(name.value.getWideString(0)).also { Ole32.INSTANCE.CoTaskMemFree(name.value) }
                } finally { release(item.value) }
            } finally { release(dlg) }
        } finally {
            if (init == 0 || init == 1) Ole32.INSTANCE.CoUninitialize()
        }
    }
}
