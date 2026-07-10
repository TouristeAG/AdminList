package com.eventmanager.app.platform.biometric

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import java.io.File

internal object DesktopBiometricResult {
    const val SUCCESS = 0
    const val FAILED = 1
    const val HARDWARE_UNAVAILABLE = 2
    const val NOT_SET = 3
    const val FEATURE_UNAVAILABLE = 4
}

private const val OS_NAME_KEY = "os.name"
private const val WINDOWS_OS = "Windows"
private const val MACOS = "Mac OS"

internal interface NativeBiometricEngine : Library {
    fun requestAuth(reason: WString): Int

    companion object {
        private val instance: NativeBiometricEngine? by lazy {
            runCatching { loadEngine() }.getOrNull()
        }

        fun getOrNull(): NativeBiometricEngine? = instance

        private fun loadEngine(): NativeBiometricEngine {
            val currentOs = System.getProperty(OS_NAME_KEY).orEmpty()
            val (dllName, suffix) = when {
                currentOs.startsWith(WINDOWS_OS) -> "WindowsHelloEngine" to ".dll"
                currentOs.startsWith(MACOS) -> "LocalAuthenticationEngine" to ".dylib"
                else -> "LinuxPolkitEngine" to ".so"
            }
            val path = extractNativeLibraryPath(dllName, suffix)
            return Native.load(path, NativeBiometricEngine::class.java)
        }
    }
}

private fun extractNativeLibraryPath(baseName: String, suffix: String): String {
    val resourceName = baseName + suffix
    val loaders = listOfNotNull(
        NativeBiometricEngine::class.java.classLoader,
        Thread.currentThread().contextClassLoader,
        ClassLoader.getSystemClassLoader()
    )
    val stream = loaders.firstNotNullOfOrNull { loader ->
        loader.getResourceAsStream(resourceName)
    } ?: error("Native biometric library not found on classpath: $resourceName")
    val tmp = File.createTempFile(baseName, suffix)
    tmp.deleteOnExit()
    stream.use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
    }
    return tmp.absolutePath
}
