package com.yonidev.ydeck.desktop

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView

/** The tools' own Windows icons, large, for the films (store/videos/source/desktop). Only with -Prender. */
class ExportToolIcons {
    @Test fun export() {
        if (System.getProperty("render") == null) return
        val out = File("../store/videos/source/desktop").apply { mkdirs() }
        val files = mapOf("folder" to File(System.getProperty("java.io.tmpdir"))) +
            listOfNotNull(Tools.vscode, Tools.studio, Tools.cursor).associate { it.id to it.exe } +
            mapOf("terminal" to File(System.getenv("LOCALAPPDATA"), "Microsoft/WindowsApps/wt.exe"), "explorer" to Tools.explorer.exe)
        files.forEach { (name, f) ->
            val icon = FileSystemView.getFileSystemView().getSystemIcon(f, 256, 256) ?: return@forEach
            val img = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
            img.createGraphics().apply { icon.paintIcon(null, this, 0, 0); dispose() }
            ImageIO.write(img, "png", File(out, "tool-$name.png"))
            println("$name ${img.width}")
        }
    }
}
