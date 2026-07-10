package com.eventmanager.app.ui.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Syncs native window chrome (title bar / traffic lights area) with dark or light appearance.
 *
 * Must call [initBeforeUiToolkit] before any AWT/Swing/Compose UI is created (start of `main`).
 */
object DesktopWindowAppearance {
    private const val MAC_OS = "Mac OS"
    private const val WINDOWS = "Windows"
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    fun initBeforeUiToolkit() {
        if (System.getProperty("os.name").orEmpty().startsWith(MAC_OS)) {
            if (System.getProperty("apple.awt.application.appearance").isNullOrBlank()) {
                System.setProperty("apple.awt.application.appearance", "system")
            }
        }
    }

    fun applyToWindow(window: Window, preferDark: Boolean) {
        when {
            isMacOs() -> applyMacOs(preferDark)
            isWindows() -> applyWindows(window, preferDark)
        }
    }

    private fun isMacOs(): Boolean = System.getProperty("os.name").orEmpty().startsWith(MAC_OS)

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith(WINDOWS)

    private fun applyMacOs(preferDark: Boolean) {
        runCatching {
            val appClass = Class.forName("com.apple.eawt.Application")
            val application = appClass.getMethod("getApplication").invoke(null)
            val appearanceClass = Class.forName("com.apple.eawt.Application\$Appearance")
            @Suppress("UNCHECKED_CAST")
            val appearanceEnum = appearanceClass as Class<out Enum<*>>
            val constantName = if (preferDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
            val appearance = java.lang.Enum.valueOf(appearanceEnum, constantName)
            appClass.getMethod("setAppearance", appearanceClass).invoke(application, appearance)
        }
    }

    private fun applyWindows(window: Window, preferDark: Boolean) {
        runCatching {
            val hwnd = Native.getWindowPointer(window)
            val flag = intArrayOf(if (preferDark) 1 else 0)
            Dwmapi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                DWMWA_USE_IMMERSIVE_DARK_MODE,
                flag,
                flag.size * Int.SIZE_BYTES
            )
        }
    }

    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(
            hwnd: Pointer,
            dwAttribute: Int,
            pvAttribute: IntArray,
            cbAttribute: Int
        ): Int

        companion object {
            val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java) as Dwmapi
        }
    }
}
