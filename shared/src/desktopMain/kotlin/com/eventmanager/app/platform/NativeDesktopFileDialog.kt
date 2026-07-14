package com.eventmanager.app.platform

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native OS file dialogs:
 * - Windows → Explorer / common file dialog
 * - macOS → Finder / NSOpenPanel / NSSavePanel
 *
 * Prefer this over Swing [javax.swing.JFileChooser], which draws a cross-platform UI
 * and feels poorly integrated on desktop.
 */
internal object NativeDesktopFileDialog {

    suspend fun pickOpen(
        title: String,
        allowedExtensions: List<String> = emptyList(),
    ): File? = onAwtThread {
        showFileDialog(
            title = title,
            mode = FileDialog.LOAD,
            suggestedName = filterHint(allowedExtensions),
            allowedExtensions = allowedExtensions,
        )
    }

    suspend fun pickSave(
        title: String,
        suggestedName: String,
        forcedExtension: String? = null,
    ): File? = onAwtThread {
        val selected = showFileDialog(
            title = title,
            mode = FileDialog.SAVE,
            suggestedName = suggestedName,
            allowedExtensions = forcedExtension?.let { listOf(it) }.orEmpty(),
        ) ?: return@onAwtThread null
        if (forcedExtension.isNullOrBlank()) return@onAwtThread selected
        ensureExtension(selected, forcedExtension)
    }

    private fun showFileDialog(
        title: String,
        mode: Int,
        suggestedName: String?,
        allowedExtensions: List<String>,
    ): File? {
        val owner = Frame().apply {
            // Invisible owner keeps the dialog modal and above our Compose window.
            isUndecorated = true
            isVisible = false
        }
        return try {
            val dialog = FileDialog(owner, title, mode).apply {
                if (!suggestedName.isNullOrBlank()) {
                    file = suggestedName
                }
                if (allowedExtensions.isNotEmpty()) {
                    filenameFilter = extensionFilter(allowedExtensions)
                }
            }
            dialog.isVisible = true // blocks until dismissed (AWT EDT only)
            val dir = dialog.directory ?: return null
            val name = dialog.file ?: return null
            File(dir, name)
        } finally {
            owner.dispose()
        }
    }

    private fun extensionFilter(extensions: List<String>): FilenameFilter {
        val normalized = extensions.map { it.lowercase().removePrefix(".") }.filter { it.isNotBlank() }
        return FilenameFilter { _, name ->
            val lower = name.lowercase()
            normalized.isEmpty() || normalized.any { lower.endsWith(".$it") }
        }
    }

    /** Windows often ignores [FilenameFilter]; a `*.ext` hint still helps the classic dialog. */
    private fun filterHint(extensions: List<String>): String? {
        val one = extensions.map { it.lowercase().removePrefix(".") }.filter { it.isNotBlank() }
        return when {
            one.isEmpty() -> null
            one.size == 1 -> "*.${one.first()}"
            else -> null
        }
    }

    private fun ensureExtension(file: File, extension: String): File {
        val ext = extension.lowercase().removePrefix(".")
        if (ext.isBlank()) return file
        val lower = file.name.lowercase()
        return if (lower.endsWith(".$ext")) file else File(file.parentFile, "${file.name}.$ext")
    }

    /**
     * Always post to AWT via [EventQueue.invokeLater] — never resume a continuation
     * synchronously from inside [kotlin.coroutines.suspendCoroutine], which leaks
     * `COROUTINE_SUSPENDED` (a kotlinx Symbol) as the "result" and crashes callers.
     */
    private suspend fun <T> onAwtThread(block: () -> T): T = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<Result<T>>()
        EventQueue.invokeLater {
            deferred.complete(runCatching(block))
        }
        deferred.await().getOrThrow()
    }
}
