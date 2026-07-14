package com.eventmanager.app.platform

import java.awt.Desktop
import java.io.File
import java.util.Locale

/**
 * Opens / shares files via OS-native dialogs on desktop.
 * [Desktop.open] alone fails often on Windows when no default app is set;
 * Share was previously aliased to the same call and did nothing useful.
 */
internal object DesktopFileActions {
    private val osName: String
        get() = System.getProperty("os.name").orEmpty().lowercase(Locale.US)

    private val isWindows: Boolean get() = "win" in osName
    private val isMacOs: Boolean get() = "mac" in osName

    fun share(file: File) {
        if (!file.exists()) return
        when {
            isWindows -> shareWindows(file)
            isMacOs -> shareMac(file)
            else -> shareLinux(file)
        }
    }

    fun openWith(file: File) {
        if (!file.exists()) return
        when {
            isWindows -> openWithWindows(file)
            isMacOs -> openDefault(file)
            else -> openDefault(file)
        }
    }

    private fun shareWindows(file: File) {
        val path = file.absolutePath
        val escaped = path.replace("'", "''")
        val script = """
            ${'$'}path = '$escaped'
            try {
                Start-Process -LiteralPath ${'$'}path -Verb Share
            } catch {
                Start-Process explorer.exe -ArgumentList @('/select,', ${'$'}path)
            }
        """.trimIndent()
        val started = runCatching {
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-Command", script,
            ).redirectErrorStream(true).start()
            true
        }.getOrDefault(false)
        if (!started) revealInExplorer(file)
    }

    private fun openWithWindows(file: File) {
        val started = runCatching {
            ProcessBuilder(
                "rundll32.exe",
                "shell32.dll,OpenAs_RunDLL",
                file.absolutePath,
            ).redirectErrorStream(true).start()
            true
        }.getOrDefault(false)
        if (!started) openDefault(file)
    }

    private fun shareMac(file: File) {
        // Reveal in Finder so the user can use Share from the context menu / toolbar.
        val revealed = runCatching {
            ProcessBuilder("open", "-R", file.absolutePath).start()
            true
        }.getOrDefault(false)
        if (!revealed) openDefault(file)
    }

    private fun shareLinux(file: File) {
        val parent = file.parentFile ?: run {
            openDefault(file)
            return
        }
        val opened = listOf(
            listOf("xdg-open", parent.absolutePath),
            listOf("nautilus", "--select", file.absolutePath),
        ).any { cmd ->
            runCatching {
                ProcessBuilder(cmd).start()
                true
            }.getOrDefault(false)
        }
        if (!opened) openDefault(file)
    }

    private fun revealInExplorer(file: File) {
        runCatching {
            ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
        }.onFailure { openDefault(file) }
    }

    private fun openDefault(file: File) {
        runCatching {
            if (!Desktop.isDesktopSupported()) return
            val desktop = Desktop.getDesktop()
            when {
                desktop.isSupported(Desktop.Action.OPEN) -> desktop.open(file)
                desktop.isSupported(Desktop.Action.BROWSE) ->
                    file.parentFile?.toURI()?.let { desktop.browse(it) }
            }
        }
    }
}
