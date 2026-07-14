package com.eventmanager.app.desktop

import java.io.File
import javax.imageio.ImageIO

/** White launcher icon shared with the Android app (`ic_launcher.png`). */
object DesktopAppIcon {
    private const val CLASSPATH_ICON = "/noctulist-icon.png"
    private const val ICON_RELATIVE_PATH = "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"

    fun resolveIconFile(): File? {
        val workingDir = File(System.getProperty("user.dir"))
        val candidates = listOf(
            workingDir.resolve(ICON_RELATIVE_PATH),
            workingDir.resolve("../$ICON_RELATIVE_PATH"),
            workingDir.resolve("../../$ICON_RELATIVE_PATH"),
            workingDir.resolve("desktopApp/icons/icon.png"),
            workingDir.resolve("icons/icon.png"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    fun loadAwtIcon(): java.awt.Image? {
        DesktopAppIcon::class.java.getResourceAsStream(CLASSPATH_ICON)?.use { stream ->
            return ImageIO.read(stream)
        }
        return resolveIconFile()?.let { file -> ImageIO.read(file) }
    }
}
