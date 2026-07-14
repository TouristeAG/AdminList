package com.eventmanager.app.platform.hardware

import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Windows-only helpers for ACS USB PC/SC stacks:
 * - Detect ACS vs Microsoft Usbccid / WUDF CCID binding
 * - Optionally set EscapeCommandEnable=1 under MS CCID Device Parameters (needs UAC)
 *
 * ACR1255 with the **ACS** driver (AC1255J1) does **not** need EscapeCommandEnable —
 * that registry key is for Microsoft CCID / WUDF only. Probe scripts must use a temp
 * .ps1 file: multiline `-Command` scripts often produce empty output under ProcessBuilder.
 */
object WindowsAcsPcscSetup {

    /** Product page with USB + Bluetooth PC/SC MSI downloads for ACR1255U-J1. */
    const val ACS_ACR1255_DRIVER_URL =
        "https://www.acs.com.hk/en/driver/340/acr1255u-j1-usb-nfc-reader-with-bluetooth-interface/"

    /** ACR122U USB PC/SC driver page (USB-only readers). */
    const val ACS_ACR122U_DRIVER_URL =
        "https://www.acs.com.hk/en/driver/3/acr122u-usb-nfc-reader/"

    enum class DriverStack {
        ACS,
        MICROSOFT_CCID,
        UNKNOWN,
        NONE,
        NOT_WINDOWS,
    }

    data class DriverProbe(
        val stack: DriverStack,
        val escapeAlreadyEnabled: Boolean,
        val escapeRelevant: Boolean,
        val devices: List<DeviceLine>,
        /** Short multi-line report for Settings / diagnostics. */
        val summary: String,
    )

    data class DeviceLine(
        val friendlyName: String,
        val service: String,
        val instanceId: String,
        val status: String = "",
    )

    data class EnableEscapeResult(
        val launched: Boolean,
        val message: String,
    )

    fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.US).contains("win")

    fun probeDrivers(): DriverProbe {
        if (!isWindows()) {
            return DriverProbe(
                stack = DriverStack.NOT_WINDOWS,
                escapeAlreadyEnabled = false,
                escapeRelevant = false,
                devices = emptyList(),
                summary = "Not Windows — ACS Escape helper is unavailable.",
            )
        }
        val output = runPowerShellFile(PROBE_SCRIPT) ?: ""
        val devices = mutableListOf<DeviceLine>()
        var stack = DriverStack.NONE
        var escape = false
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("DEVICE|") -> {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        devices += DeviceLine(
                            friendlyName = parts[1].ifBlank { "(unnamed)" },
                            service = parts.getOrElse(2) { "" },
                            instanceId = parts.getOrElse(3) { "" },
                            status = parts.getOrElse(4) { "" },
                        )
                    }
                }
                line.startsWith("STACK=") -> {
                    stack = when (line.substringAfter("=").trim().uppercase(Locale.US)) {
                        "ACS" -> DriverStack.ACS
                        "MS_CCID" -> DriverStack.MICROSOFT_CCID
                        "UNKNOWN" -> DriverStack.UNKNOWN
                        else -> DriverStack.NONE
                    }
                }
                line.startsWith("ESCAPE=") -> {
                    escape = line.substringAfter("=").trim() == "1"
                }
            }
        }
        // If PC/SC already sees an ACS terminal, treat as ACS even when PnP parsing glitched.
        if (stack == DriverStack.NONE || stack == DriverStack.UNKNOWN) {
            val pcscHint = runCatching {
                DesktopPcscCardReader().formatTerminalListing()
            }.getOrNull().orEmpty()
            if (pcscHint.contains("ACR125", ignoreCase = true) ||
                pcscHint.contains("ACS ACR", ignoreCase = true)
            ) {
                stack = DriverStack.ACS
            }
        }
        val escapeRelevant = stack == DriverStack.MICROSOFT_CCID ||
            devices.any {
                it.service.contains("WUDF", ignoreCase = true) ||
                    it.friendlyName.contains("Usbccid", ignoreCase = true)
            }
        val summary = buildString {
            appendLine(
                when (stack) {
                    DriverStack.ACS ->
                        "Driver stack: ACS PC/SC (Escape Command registry NOT needed)"
                    DriverStack.MICROSOFT_CCID ->
                        "Driver stack: Microsoft CCID / Usbccid — enable Escape if Test USB gets no UID"
                    DriverStack.UNKNOWN -> "Driver stack: unknown USB smart-card binding"
                    DriverStack.NONE ->
                        "No ACS / CCID USB reader in Device Manager (PC/SC may still list a soft reader)"
                    DriverStack.NOT_WINDOWS -> "Not Windows"
                }
            )
            when {
                stack == DriverStack.ACS ->
                    appendLine("EscapeCommandEnable: n/a (ACS native driver)")
                escape ->
                    appendLine("EscapeCommandEnable: already set (1)")
                else ->
                    appendLine("EscapeCommandEnable: not set under VID_072F MS CCID keys")
            }
            if (devices.isEmpty()) {
                append("PnP devices: (none from probe)")
            } else {
                appendLine("PnP devices (${devices.size}):")
                devices.forEach { d ->
                    val st = d.status.ifBlank { "?" }
                    appendLine(" • [$st] ${d.friendlyName} [${d.service.ifBlank { "?" }}]")
                }
            }
            if (output.isBlank()) {
                appendLine()
                append("Probe script returned empty output — see logs.")
            }
        }.trimEnd()
        return DriverProbe(
            stack = stack,
            escapeAlreadyEnabled = escape,
            escapeRelevant = escapeRelevant,
            devices = devices,
            summary = summary,
        )
    }

    /**
     * Only useful under Microsoft CCID / WUDF. With ACS AC1255J1/ACR122 drivers, skip this.
     */
    fun enableEscapeCommandElevated(): EnableEscapeResult {
        if (!isWindows()) {
            return EnableEscapeResult(false, "Escape enable is only available on Windows.")
        }
        val probe = probeDrivers()
        if (probe.stack == DriverStack.ACS && !probe.escapeRelevant) {
            return EnableEscapeResult(
                launched = false,
                message = "ACS driver detected — Escape Command registry is not used. " +
                    "Hold a card on the reader and use Test USB (UID comes from PC/SC, not Escape).",
            )
        }
        return runCatching {
            val text = runPowerShellFileElevated().orEmpty().trim()
            when {
                text.contains("written under", ignoreCase = true) ->
                    EnableEscapeResult(
                        true,
                        "$text Unplug/replug the reader (or reboot), then Test USB again.",
                    )
                text.contains("No VID_072F", ignoreCase = true) ->
                    EnableEscapeResult(
                        true,
                        "$text This is normal with the ACS native driver — Escape is not required. Use Test USB with a card held on the PICC.",
                    )
                text.isNotBlank() ->
                    EnableEscapeResult(true, text)
                else ->
                    EnableEscapeResult(
                        true,
                        "Elevated script finished with no output. If you use the ACS driver, Escape is not needed — hold a card and Test USB.",
                    )
            }
        }.getOrElse { e ->
            EnableEscapeResult(
                launched = false,
                message = e.message ?: "Failed to launch elevated Escape enable script.",
            )
        }
    }

    private fun runPowerShellFile(scriptBody: String): String? = runCatching {
        val tmp = Files.createTempFile("noctulist-acs-probe", ".ps1")
        try {
            Files.writeString(tmp, scriptBody)
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                tmp.toAbsolutePath().toString(),
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(45, TimeUnit.SECONDS)
            output
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }.getOrNull()

    /** Runs elevated; captures output via a temp result file (UAC child has no stdout pipe). */
    private fun runPowerShellFileElevated(): String? = runCatching {
        val work = Files.createTempDirectory("noctulist-acs-escape")
        val script = work.resolve("enable.ps1")
        val result = work.resolve("result.txt")
        val outPath = result.toAbsolutePath().toString().replace("'", "''")
        Files.writeString(
            script,
            ENABLE_ESCAPE_SCRIPT_WITH_OUT.replace("{{OUT}}", outPath),
        )
        val elevate = """
            Start-Process -FilePath powershell.exe -Verb RunAs -Wait -ArgumentList @(
              '-NoProfile','-ExecutionPolicy','Bypass','-File','${script.toAbsolutePath().toString().replace("'", "''")}'
            )
        """.trimIndent()
        val process = ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-Command",
            elevate,
        )
            .redirectErrorStream(true)
            .start()
        process.waitFor(120, TimeUnit.SECONDS)
        val text = runCatching { Files.readString(result) }.getOrNull()
        runCatching {
            Files.deleteIfExists(script)
            Files.deleteIfExists(result)
            Files.deleteIfExists(work)
        }
        text
    }.getOrNull()

    private val PROBE_SCRIPT = """
        ${'$'}ErrorActionPreference = 'SilentlyContinue'
        ${'$'}devs = @()
        ${'$'}devs += @(Get-PnpDevice -Class SmartCardReader -EA SilentlyContinue)
        ${'$'}devs += @(Get-PnpDevice -EA SilentlyContinue | Where-Object {
          ${'$'}_.InstanceId -match 'VID_072F' -or
          ${'$'}_.FriendlyName -match 'ACR12|ACR125|ACS ACR|Usbccid|PICC Reader'
        })
        ${'$'}devs = ${'$'}devs | Sort-Object InstanceId -Unique
        ${'$'}hasAcs = ${'$'}false
        ${'$'}hasMs = ${'$'}false
        ${'$'}escape = ${'$'}false
        Get-ChildItem 'HKLM:\SYSTEM\CurrentControlSet\Enum\USB' -EA SilentlyContinue |
          Where-Object { ${'$'}_.PSChildName -like 'VID_072F*' } |
          ForEach-Object {
            Get-ChildItem ${'$'}_.PSPath -Recurse -EA SilentlyContinue |
              Where-Object { ${'$'}_.PSChildName -in @('Device Parameters','WUDFUsbccidDriver') } |
              ForEach-Object {
                ${'$'}v = (Get-ItemProperty ${'$'}_.PSPath -Name EscapeCommandEnable -EA SilentlyContinue).EscapeCommandEnable
                if (${'$'}v -eq 1) { ${'$'}script:escape = ${'$'}true }
              }
          }
        foreach (${'$'}d in ${'$'}devs) {
          if (-not ${'$'}d) { continue }
          ${'$'}svc = ''
          try {
            ${'$'}svc = [string](Get-PnpDeviceProperty -InstanceId ${'$'}d.InstanceId -KeyName 'DEVPKEY_Device_Service' -EA SilentlyContinue).Data
          } catch {}
          ${'$'}name = [string]${'$'}d.FriendlyName
          ${'$'}status = [string]${'$'}d.Status
          if (${'$'}name -match 'ACS|Advanced Card Systems|ACR125|ACR122' -or ${'$'}svc -match '(?i)acs|aetc|AC1255|AC122') {
            ${'$'}hasAcs = ${'$'}true
          }
          if (${'$'}name -match 'Usbccid|WUDF|Microsoft' -or ${'$'}svc -match '(?i)WUDFRd|usbccid') {
            ${'$'}hasMs = ${'$'}true
          }
          Write-Output ("DEVICE|" + ${'$'}name + "|" + ${'$'}svc + "|" + ${'$'}d.InstanceId + "|" + ${'$'}status)
        }
        if (${'$'}hasAcs) { Write-Output 'STACK=ACS' }
        elseif (${'$'}hasMs) { Write-Output 'STACK=MS_CCID' }
        elseif (${'$'}devs.Count -gt 0) { Write-Output 'STACK=UNKNOWN' }
        else { Write-Output 'STACK=NONE' }
        if (${'$'}escape) { Write-Output 'ESCAPE=1' } else { Write-Output 'ESCAPE=0' }
    """.trimIndent()

    private val ENABLE_ESCAPE_SCRIPT_WITH_OUT = """
        ${'$'}ErrorActionPreference = 'Continue'
        ${'$'}outFile = '{{OUT}}'
        ${'$'}count = 0
        New-Item -LiteralPath ${'$'}outFile -ItemType File -Force | Out-Null
        Get-ChildItem 'HKLM:\SYSTEM\CurrentControlSet\Enum\USB' -ErrorAction SilentlyContinue |
          Where-Object { ${'$'}_.PSChildName -like 'VID_072F*' } |
          ForEach-Object {
            Get-ChildItem ${'$'}_.PSPath -Recurse -ErrorAction SilentlyContinue |
              Where-Object { ${'$'}_.PSChildName -in @('Device Parameters','WUDFUsbccidDriver') } |
              ForEach-Object {
                New-ItemProperty -Path ${'$'}_.PSPath -Name 'EscapeCommandEnable' -PropertyType DWord -Value 1 -Force | Out-Null
                ${'$'}count++
              }
          }
        if (${'$'}count -eq 0) {
          Set-Content -LiteralPath ${'$'}outFile -Value 'No VID_072F Device Parameters / WUDFUsbccidDriver keys found (typical with ACS native driver — Escape not required).'
          exit 2
        }
        Set-Content -LiteralPath ${'$'}outFile -Value ("EscapeCommandEnable=1 written under ${'$'}count registry key(s).")
        exit 0
    """.trimIndent()
}
