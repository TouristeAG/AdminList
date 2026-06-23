package com.eventmanager.app.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.acs.smartcardio.BluetoothSmartCard
import com.acs.smartcardio.BluetoothTerminalManager
import com.acs.smartcardio.TerminalTimeouts
import com.eventmanager.app.data.sync.settingsManagerFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.smartcardio.Card
import javax.smartcardio.CardException
import javax.smartcardio.CardTerminal
import javax.smartcardio.CommandAPDU
import javax.smartcardio.ResponseAPDU

/**
 * Driver for the ACS ACR1255U-J1 over Bluetooth Low Energy, backed by the ACS "Android BLE EVK
 * (SmartCardIO)" library (`acssmcio-*.aar` + `smartcardio-*.aar`).
 *
 * The SDK exposes the reader as a [javax.smartcardio.CardTerminal] once it has been discovered
 * through [BluetoothTerminalManager.startScan]. The ACR1255U-J1 ships in two firmware variants:
 *
 *  - Legacy firmware: standard Bluetooth 4.0 profile, 16-bit custom service `0xFFF0` —
 *    matched by [BluetoothTerminalManager.TERMINAL_TYPE_ACR1255U_J1].
 *  - "SmartCardIO" firmware (newer units): 128-bit custom service `3C4AFFF0-...` —
 *    matched by [BluetoothTerminalManager.TERMINAL_TYPE_ACR1255U_J1_V2].
 *
 * Both variants are accepted: we run scans for each type and connect to whichever matches the
 * reader the user selected in settings.
 */
object Acr1255uj1BleNfcReader {

    private const val TAG = "ACR1255BLE"

    /** Per-scan timeout when looking for the configured reader. */
    private const val SCAN_TIMEOUT_MS: Long = 8_000L

    /** Overall budget for a full UID read (scan + connect + transmit + disconnect). */
    private const val SESSION_OVERALL_MS: Long = 20_000L

    /** Budget when verifying a MAC at picker time: must be shorter to stay responsive. */
    private const val VERIFY_OVERALL_MS: Long = 14_000L

    /** How long we wait for a card on the reader once it is connected. */
    private const val CARD_WAIT_TIMEOUT_MS: Long = 4_000L

    /**
     * Terminal types to probe when scanning. Order matters: we try V2 first because that is what
     * this app has been hitting the wall on (newer firmware). V1 is kept so users with older units
     * still work.
     */
    private val TERMINAL_TYPES: IntArray = intArrayOf(
        BluetoothTerminalManager.TERMINAL_TYPE_ACR1255U_J1_V2,
        BluetoothTerminalManager.TERMINAL_TYPE_ACR1255U_J1
    )

    /** PC/SC "Get Data — UID" APDU supported by all ACS contactless readers. */
    private val getUidApdu: ByteArray = byteArrayOf(
        0xFF.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x00
    )

    /**
     * Per-call timeout on the cached session's card wait. Short enough for the outer polling
     * loop in scanner screens to iterate (UI stays responsive, battery/USB watchdogs fire), long
     * enough to cover a card tap that lands mid-call.
     */
    private const val CACHED_CARD_WAIT_MS: Long = 1_200L

    /** Hard ceiling per cached read (wait + connect + transmit). */
    private const val CACHED_SESSION_MS: Long = 6_000L

    /**
     * After a successful tap, how long we block waiting for the card to physically leave the
     * reader before the next read is accepted. Kept short so the outer polling loop stays
     * responsive; it returns `false` (i.e. still present) → the driver reports "no card" and
     * the caller polls again, which is effectively the idle state while the user keeps a card
     * on the antenna.
     */
    private const val CARD_ABSENT_WAIT_MS: Long = 400L

    /**
     * ACS pseudo-APDUs sent through [Card.transmitControlCommand] with
     * [BluetoothTerminalManager.IOCTL_ESCAPE] (same pattern as ACS samples / skjolber's
     * [ACR1255BluetoothCommands] for the legacy `BluetoothReader` stack).
     *
     * LED bitmask (byte P2=29h data): see ACR1255U-J1 Reference Manual §6.6.x — matches
     * [com.github.skjolber.nfc.command.ACR1255Commands] constants.
     */
    private const val LED_1_GREEN: Int = 0x01
    private const val LED_1_RED: Int = 0x02
    private const val LED_2_BLUE: Int = 0x04

    private val ioctlEscape: Int = BluetoothTerminalManager.IOCTL_ESCAPE

    // ─── Persistent BLE session ──────────────────────────────────────────────────
    //
    // Once a reader has been scanned and authenticated it is cached below; each subsequent
    // readUid() call reuses the same CardTerminal so the underlying GATT stays open, the
    // reader's Bluetooth LED stops blinking and tap-to-read latency drops from ~3 s to
    // ~200 ms. The cache is dropped (and the session torn down) when:
    //
    //   1. The user switches to a different reader MAC in settings (detected below).
    //   2. A cached read fails with a non-recoverable error ("connect failed",
    //      "disconnected" …) — the reader may have gone to sleep; the next call will
    //      rescan and reconnect automatically, which is the user-visible reconnect loop.
    //   3. The caller invokes [shutdown] explicitly (settings → forget reader, etc.).
    //
    // Concurrency: readUid() takes [sessionMutex] so two scanner screens cannot race on
    // the same terminal object; quick reads do not starve because the lock is held for
    // at most [CACHED_SESSION_MS] before [withTimeoutOrNull] bails.

    private val sessionMutex = Mutex()

    @Volatile private var cachedTerminal: CardTerminal? = null
    @Volatile private var cachedTerminalMac: String? = null
    @Volatile private var cachedManager: BluetoothTerminalManager? = null

    /**
     * Reflects whether [cachedTerminal] is non-null. Updated only when the BLE SmartCard session is
     * established or torn down — **not** on a timer — so Compose can observe link state without
     * polling. (This does not drive the reader LED; the hardware LED follows GATT + firmware.)
     */
    private val bleSessionActiveInternal = MutableStateFlow(false)
    val bleSessionActive: StateFlow<Boolean> = bleSessionActiveInternal.asStateFlow()

    private fun publishBleSessionActive() {
        bleSessionActiveInternal.value = cachedTerminal != null
    }

    /**
     * Set to `true` immediately after a successful UID read, cleared once the SDK reports the
     * card has actually been removed (via [CardTerminal.waitForCardAbsent]). While true, the
     * driver refuses to power the antenna on again — this prevents:
     *
     *  - **Ghost re-reads**: right after the card is lifted, the SDK's cached presence state
     *    can still claim "present" for a few hundred ms; a naive `connect("*")` would re-read
     *    and return the same UID of the card that just left.
     *  - **Beep storm**: every `connect("*")` / `disconnect(false)` cycle power-cycles the
     *    antenna, which retriggers the reader's auto-polling "card detected" buzzer. Without
     *    this guard the scanner's tight polling loop would make the reader beep continuously
     *    while a single card sat on the antenna.
     */
    @Volatile private var awaitingCardRemoval: Boolean = false

    /**
     * Outcome of a UID read. [isSuccess] is `true` when [uid] is non-blank. Callers use
     * [shouldRetryPoll] to decide whether to keep polling the reader in a tight loop or stop
     * (because the failure is terminal, e.g. Bluetooth off or permission denied).
     */
    data class Result(
        val uid: String? = null,
        val error: String? = null
    ) {
        val isSuccess: Boolean get() = !uid.isNullOrBlank()

        fun shouldRetryPoll(): Boolean {
            if (isSuccess) return false
            return when (error) {
                ExternalReaderPermissions.BLUETOOTH_CONNECT_DENIED,
                "Bluetooth is off",
                "Bluetooth unavailable",
                "ACR1255U-J1 reader not paired",
                "Bluetooth reader authentication failed",
                "Bluetooth reader notification failed",
                "Bluetooth reader timeout" -> false
                else -> true
            }
        }
    }

    /** Outcome of pre-save validation triggered by the settings picker. */
    enum class VerificationStatus {
        Supported,
        NotSupported,
        Inconclusive
    }

    /** Human readable report for the "Test connection" button in settings. */
    data class DiagnosticResult(
        val success: Boolean,
        val details: String
    )

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * `true` when Bluetooth is on and the user has picked a reader in settings (a MAC/name pair
     * was stored via [SettingsManager]). We only check presence of config — the actual
     * connection handshake happens later in [readUid].
     */
    @SuppressLint("MissingPermission")
    fun isReaderAvailable(context: Context): Boolean {
        if (!hasBluetoothConnectPermission(context)) return false
        val adapter = bluetoothAdapter(context) ?: return false
        if (!adapter.isEnabled) return false
        val mac = com.eventmanager.app.data.sync.settingsManagerFor(context).getExternalBleReaderMac().trim()
        return mac.isNotEmpty() && BluetoothAdapter.checkBluetoothAddress(mac)
    }

    /** MAC address of the user-selected reader, or empty when none is configured. */
    fun getConfiguredReaderMac(context: Context): String =
        com.eventmanager.app.data.sync.settingsManagerFor(context).getExternalBleReaderMac().trim()

    /** Friendly name of the user-selected reader, or empty when none is configured. */
    fun getConfiguredReaderName(context: Context): String =
        com.eventmanager.app.data.sync.settingsManagerFor(context).getExternalBleReaderName().trim()

    /**
     * `true` when a [CardTerminal] is currently cached for the configured reader — i.e. the BLE
     * GATT session from the last successful scan + authenticate is still held open. The scanner
     * UI uses this to show whether the reader is likely awake and linked vs still reconnecting
     * after sleep.
     */
    fun isBleSessionCached(): Boolean = cachedTerminal != null

    /**
     * Main entry point: read one card's UID using the configured BLE reader. Returns a [Result]
     * with either [Result.uid] set (success) or [Result.error] set to an i18n-stable tag that
     * `ExternalAcsUidReader` / the UI know how to render.
     *
     * Uses a persistent session (see [sessionMutex] docs): the first call scans + authenticates
     * the reader, every subsequent call reuses the open BLE GATT so the reader stays in
     * "connected / reading" mode without its LED blinking. If the cached session breaks (reader
     * sleeps, radio drops) the next call rebuilds it transparently — that is the automatic
     * reconnect behaviour.
     */
    suspend fun readUid(context: Context): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val guard = prechecks(appContext)
        if (guard != null) return@withContext guard

        val settings = settingsManagerFor(appContext)
        val savedMac = settings.getExternalBleReaderMac().trim()
        val savedName = settings.getExternalBleReaderName().trim()
        if (savedMac.isEmpty() || !BluetoothAdapter.checkBluetoothAddress(savedMac)) {
            return@withContext Result(error = "ACR1255U-J1 reader not paired")
        }

        // If the user switched reader in settings, drop the previous session. The
        // configured-MAC-changed check runs outside the mutex so an in-flight read on the old
        // reader completes gracefully before we wipe state.
        if (cachedTerminalMac != null && !cachedTerminalMac.equals(savedMac, ignoreCase = true)) {
            shutdown()
        }

        sessionMutex.withLock {
            // ── Fast path: reuse the already-open GATT session ───────────────────
            val cached = cachedTerminal
            if (cached != null) {
                val fastResult = try {
                    withTimeoutOrNull(CACHED_SESSION_MS) { readOnceFromTerminal(cached) }
                } catch (_: CancellationException) {
                    return@withLock Result(error = "Bluetooth reader cancelled")
                }
                if (fastResult != null && isHealthyOutcome(fastResult)) {
                    val idleNoCard = fastResult.error == "No card detected on reader"
                    if (idleNoCard && !isGattConnectedForMac(appContext, savedMac)) {
                        // The ACS stack can keep a CardTerminal while Android's GATT is already
                        // torn down (reader powered off / out of range). Without this we would stay
                        // "healthy" forever on "no card" and the UI would show a live session.
                        Log.w(TAG, "Stale BLE cache: GATT down for $savedMac while terminal idle")
                        teardownCachedInternal()
                    } else {
                        return@withLock fastResult
                    }
                } else {
                    // Cached session broken: tear it down and fall through to a fresh scan.
                    Log.w(
                        TAG,
                        "Cached BLE session unhealthy (${fastResult?.error ?: "null"}) — rebuilding"
                    )
                    teardownCachedInternal()
                }
            }

            // ── Slow path: scan, authenticate, cache the terminal, then read ─────
            val outcome = try {
                withTimeoutOrNull(SESSION_OVERALL_MS) {
                    openSessionAndRead(appContext, savedMac, savedName)
                } ?: Result(error = "Bluetooth reader timeout")
            } catch (_: CancellationException) {
                Result(error = "Bluetooth reader cancelled")
            }

            outcome
        }
    }

    /**
     * Tear down the cached BLE session. Safe to call anytime; does nothing when no session is
     * cached. Call this when:
     *
     *  - The user forgets / changes the selected reader in settings.
     *  - The app is being torn down (scanner screen onDispose, logout, Application.onTerminate).
     */
    fun shutdown() {
        try {
            teardownCachedInternal()
        } catch (t: Throwable) {
            Log.w(TAG, "shutdown(): ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Synchronous cache wipe. Assumes the caller either holds [sessionMutex] or doesn't care. */
    private fun teardownCachedInternal() {
        val t = cachedTerminal
        val m = cachedManager
        cachedTerminal = null
        cachedManager = null
        cachedTerminalMac = null
        awaitingCardRemoval = false
        if (t != null && m != null) {
            runCatching { m.disconnect(t) }
        }
        publishBleSessionActive()
    }

    /**
     * Reads one card via an already-open [terminal]. Does NOT disconnect the terminal itself —
     * only the per-card logical channel — so the persistent session survives for the next tap.
     *
     * If the previous call produced a UID, this call first blocks on
     * [CardTerminal.waitForCardAbsent] until the same card has physically left the antenna; see
     * [awaitingCardRemoval] for why. That turns the "one tap = one read" contract into a hard
     * invariant at the driver level, irrespective of what the caller does with its own
     * deduplication.
     */
    private suspend fun readOnceFromTerminal(terminal: CardTerminal): Result {
        if (awaitingCardRemoval) {
            val removed = try {
                terminal.waitForCardAbsent(CARD_ABSENT_WAIT_MS)
            } catch (e: CardException) {
                Log.w(TAG, "waitForCardAbsent failed: ${e.message}")
                // Treat as a transient read error so the scanner polls again; do NOT reset the
                // removal flag yet — we still don't want to re-power the antenna on a card that
                // may still be there.
                return Result(error = classifyCardException(e, connectPhase = false))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "waitForCardAbsent IllegalStateException: ${e.message}")
                return Result(error = "Bluetooth reader disconnected")
            }
            if (!removed) {
                // Card is still sitting on the reader from the previous tap. Report "no card"
                // so the outer loop keeps polling without triggering a beep + re-read.
                return Result(error = "No card detected on reader")
            }
            awaitingCardRemoval = false
        }

        val cardPresent = try {
            terminal.waitForCardPresent(CACHED_CARD_WAIT_MS)
        } catch (e: CardException) {
            Log.w(TAG, "waitForCardPresent failed: ${e.message}")
            return Result(error = classifyCardException(e, connectPhase = false))
        } catch (e: IllegalStateException) {
            // SDK throws this once the underlying GATT has died (reader sleeping, out of range).
            Log.w(TAG, "waitForCardPresent IllegalStateException: ${e.message}")
            return Result(error = "Bluetooth reader disconnected")
        }
        if (!cardPresent) return Result(error = "No card detected on reader")

        val card: Card = try {
            terminal.connect("*")
        } catch (e: CardException) {
            Log.w(TAG, "connect() failed: ${e.message}")
            return Result(error = classifyCardException(e, connectPhase = true))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "connect() IllegalStateException: ${e.message}")
            return Result(error = "Bluetooth reader disconnected")
        }

        val result = try {
            val r = readUidFromCard(card) ?: Result(error = "No card detected on reader")
            if (r.isSuccess) {
                // Short blue off→on on the Bluetooth status LED pair while we still hold the
                // PICC session — gives a tap acknowledgement without waiting for business logic.
                pulseNfcTapLedFeedbackOrIgnore(card)
            }
            r
        } finally {
            runCatching { card.disconnect(false) }
        }

        // Only gate the next read when we actually delivered a UID to the caller. Transient
        // errors ("no card", auth failures...) must NOT arm the gate or we'd deadlock the
        // scanner on a reader that never gets a first successful read.
        if (result.isSuccess) {
            awaitingCardRemoval = true
        }
        return result
    }

    /**
     * Fresh-session read used when the cache is empty. Scans, installs the default master key,
     * tunes the terminal's timeouts, caches everything, and runs a single read. On any failure
     * the cache is wiped so the next call retries from scratch.
     */
    private suspend fun openSessionAndRead(
        appContext: Context,
        savedMac: String,
        savedName: String
    ): Result {
        val smartCard = BluetoothSmartCard.getInstance(appContext)
        val manager = smartCard.manager
            ?: return Result(error = "Bluetooth unavailable")

        val terminal = findTerminal(manager, savedMac, savedName)
            ?: return Result(error = "Bluetooth reader not supported")

        // Factory-default master key. Passing null makes the SDK use its built-in default
        // ("ACR1255U-J1 Auth" on V1; the V2-specific key on V2 firmware).
        runCatching { manager.setMasterKey(terminal, null) }
            .onFailure { t ->
                Log.w(TAG, "setMasterKey failed: ${t.javaClass.simpleName}: ${t.message}")
            }

        runCatching {
            val timeouts = manager.getTimeouts(terminal)
            timeouts.connectionTimeout = 6_000L
            timeouts.powerTimeout = 6_000L
            timeouts.protocolTimeout = 6_000L
            timeouts.apduTimeout = 6_000L
            timeouts.controlTimeout = 6_000L
        }

        cachedTerminal = terminal
        cachedTerminalMac = savedMac
        cachedManager = manager
        publishBleSessionActive()

        // Best-effort: ask the reader to hold LED2 blue solid while the app session is up.
        // Firmware may still override during radio events; harmless if the escape is rejected.
        tryAssertBleConnectedLed(terminal)

        val outcome = readOnceFromTerminal(terminal)
        if (!isHealthyOutcome(outcome)) {
            Log.w(TAG, "Initial read failed (${outcome.error}) — invalidating fresh session")
            teardownCachedInternal()
        }
        return outcome
    }

    /**
     * A [Result] is "healthy" when it indicates the session is still usable: either we read a
     * UID, or we just didn't have a card on the reader yet. Anything else (auth failed, GATT
     * dropped, timeout) means we should recreate the session.
     */
    private fun isHealthyOutcome(r: Result): Boolean =
        r.isSuccess || r.error == "No card detected on reader"

    /**
     * Optional UI feedback on the reader hardware (LED1 = red/green, LED2 = red/blue).
     * Call after your screen has decided access **granted** vs **denied** — only affects the
     * ACR1255U-J1 when it is the active external reader (not USB ACR122U).
     *
     * Uses the same [sessionMutex] as [readUid] with a short timeout so a slow poll does not
     * block the UI thread for long; if the reader is mid-read the feedback is skipped quietly.
     */
    suspend fun feedbackAccessOutcome(context: Context, granted: Boolean) =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            if (prechecks(app) != null) return@withContext
            if (cachedTerminal == null) return@withContext
            withTimeoutOrNull(2_800L) {
                sessionMutex.withLock {
                    val terminal = cachedTerminal ?: return@withLock
                    withDirectCardSession(terminal) { card ->
                        val prev = readLedStateOrNull(card) ?: return@withDirectCardSession
                        try {
                            if (granted) {
                                setLedStateOrIgnore(card, LED_1_GREEN)
                                delay(130)
                            } else {
                                setLedStateOrIgnore(card, LED_1_RED)
                                delay(95)
                            }
                        } finally {
                            // Return to a "session active" look: restore previous bits but bias LED2 blue on.
                            setLedStateOrIgnore(card, (prev and 0xFF) or LED_2_BLUE)
                        }
                    }
                }
            } ?: Log.d(TAG, "feedbackAccessOutcome skipped (reader busy or timeout)")
        }

    /**
     * Runs a short session end-to-end against the configured reader and returns a human-readable
     * diagnostic. Used from settings' "Test connection" button.
     */
    suspend fun runDiagnostic(context: Context): DiagnosticResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val settings = settingsManagerFor(appContext)
        val mac = settings.getExternalBleReaderMac().trim()
        val name = settings.getExternalBleReaderName().trim().ifBlank { "<unnamed>" }
        val sb = StringBuilder()
        sb.append("Reader: $name\nMAC: ${if (mac.isBlank()) "<none>" else mac}\n\n")

        prechecks(appContext)?.let { guard ->
            sb.append(describeError(guard.error))
            return@withContext DiagnosticResult(false, sb.toString())
        }
        if (mac.isBlank() || !BluetoothAdapter.checkBluetoothAddress(mac)) {
            return@withContext DiagnosticResult(
                success = false,
                details = sb.append("No valid reader selected — pick one first.").toString()
            )
        }

        // The diagnostic scans + authenticates from scratch — make sure we don't leave a stale
        // cached session from an earlier scanner run interfering with it. The next readUid() in
        // the scanner will rebuild the persistent session transparently.
        sessionMutex.withLock { teardownCachedInternal() }

        val started = SystemClock.elapsedRealtime()
        val outcome = try {
            withTimeoutOrNull(SESSION_OVERALL_MS) {
                runUidSession(appContext, mac, name, waitForCard = false)
            } ?: Result(error = "Bluetooth reader timeout")
        } catch (_: CancellationException) {
            Result(error = "Bluetooth reader cancelled")
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        sb.append("Elapsed: ${elapsed} ms\n")

        val success = outcome.isSuccess || outcome.error == "No card detected on reader"
        when {
            outcome.isSuccess -> sb.append("OK — card UID: ${outcome.uid}")
            outcome.error == "No card detected on reader" -> sb.append(
                "OK — reader connected and authenticated.\n" +
                    "Place a card on the reader and retry to read a UID."
            )
            else -> sb.append(describeError(outcome.error))
        }
        DiagnosticResult(success, sb.toString())
    }

    /**
     * Validate a BLE MAC selected in the picker. Runs a bounded session (no card wait, we just
     * want the scan + authenticate). Returns:
     *
     *  - [VerificationStatus.Supported] when the MAC resolves to an ACS V1/V2 reader that
     *    authenticates with the default master key.
     *  - [VerificationStatus.NotSupported] when the MAC is not even advertising as a known ACS
     *    terminal type (i.e. the SDK's scan never yields it).
     *  - [VerificationStatus.Inconclusive] on transient issues (radio busy, already-connected
     *    elsewhere, off, ...). We save the pick anyway and let the user retry later.
     */
    suspend fun verifyReaderAddress(
        context: Context,
        macAddress: String
    ): VerificationStatus = withContext(Dispatchers.IO) {
        if (!BluetoothAdapter.checkBluetoothAddress(macAddress)) {
            return@withContext VerificationStatus.NotSupported
        }
        prechecks(context.applicationContext)?.let { return@withContext VerificationStatus.Inconclusive }

        // Picking a reader in the UI should invalidate any prior persistent session: the user may
        // be switching hardware, and the next scanner read will rebuild a fresh session against
        // whatever ends up saved in settings.
        sessionMutex.withLock { teardownCachedInternal() }

        val outcome = try {
            withTimeoutOrNull(VERIFY_OVERALL_MS) {
                runUidSession(context.applicationContext, macAddress, nameHint = "", waitForCard = false)
            } ?: Result(error = "Bluetooth reader timeout")
        } catch (_: CancellationException) {
            Result(error = "Bluetooth reader cancelled")
        }

        when (outcome.error) {
            null -> VerificationStatus.Supported
            "No card detected on reader" -> VerificationStatus.Supported
            "Bluetooth reader not supported" -> VerificationStatus.NotSupported
            "Bluetooth connect failed",
            "Unable to open Bluetooth connection",
            "Bluetooth reader authentication failed",
            "Bluetooth reader notification failed",
            "Bluetooth reader timeout",
            "Bluetooth reader cancelled" -> VerificationStatus.Inconclusive
            else -> VerificationStatus.Inconclusive
        }
    }

    // ─── Reader peripherals (LED / escape) ───────────────────────────────────────

    private suspend fun tryAssertBleConnectedLed(terminal: CardTerminal) {
        withDirectCardSession(terminal) { card ->
            setLedStateOrIgnore(card, LED_2_BLUE)
        }
    }

    private suspend fun pulseNfcTapLedFeedbackOrIgnore(card: Card) {
        val prev = readLedStateOrNull(card) ?: return
        val withoutBlue = prev and (0xFF xor LED_2_BLUE)
        setLedStateOrIgnore(card, withoutBlue)
        delay(42)
        setLedStateOrIgnore(card, prev)
    }

    /**
     * Opens a logical "direct" connection to the reader (no ISO-14443 PICC) so host-side
     * pseudo-APDUs can be sent. This is the same `connect("direct")` mode the ACS stack uses
     * for firmware / LED escapes on contactless readers.
     */
    private suspend fun withDirectCardSession(terminal: CardTerminal, block: suspend (Card) -> Unit) {
        val c: Card = try {
            @Suppress("SpellCheckingInspection")
            terminal.connect("direct")
        } catch (e: CardException) {
            Log.d(TAG, "connect(direct) for peripherals: ${e.message}")
            return
        } catch (e: IllegalStateException) {
            Log.d(TAG, "connect(direct) IllegalState: ${e.message}")
            return
        }
        try {
            block(c)
        } finally {
            runCatching { c.disconnect(false) }
        }
    }

    private fun readLedStateOrNull(card: Card): Int? {
        val raw = try {
            card.transmitControlCommand(
                ioctlEscape,
                byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x29, 0x00)
            )
        } catch (e: CardException) {
            Log.d(TAG, "read LED escape: ${e.message}")
            return null
        }
        return parseLedMaskFromEscapeResponse(raw)
    }

    private fun setLedStateOrIgnore(card: Card, mask: Int): Boolean {
        val apdu = byteArrayOf(
            0xE0.toByte(),
            0x00,
            0x00,
            0x29,
            0x01,
            (mask and 0xFF).toByte()
        )
        val raw = try {
            card.transmitControlCommand(ioctlEscape, apdu)
        } catch (e: CardException) {
            Log.d(TAG, "set LED escape: ${e.message}")
            return false
        }
        val ok = isEscapeResponseOk(raw)
        if (!ok) Log.d(TAG, "set LED unexpected response: ${raw.joinToString("") { "%02X".format(it.toInt() and 0xFF) }}")
        return ok
    }

    private fun parseLedMaskFromEscapeResponse(raw: ByteArray): Int? {
        if (raw.isEmpty()) return null
        return try {
            val rapdu = ResponseAPDU(raw)
            if (rapdu.sw != 0x9000) return null
            rapdu.data.firstOrNull()?.toInt()?.and(0xFF)
        } catch (_: Exception) {
            if (raw.size >= 6 && isEscapeResponseOk(raw)) raw[4].toInt() and 0xFF else null
        }
    }

    /**
     * ACS escape responses are normally framed like PC/SC vendor data (`E1 00 00 00 …`) or as a
     * plain ISO7816 R-APDU with SW `9000` — accept either so we stay compatible across firmware.
     */
    private fun isEscapeResponseOk(raw: ByteArray): Boolean {
        if (raw.size >= 2) {
            val sw1 = raw[raw.size - 2].toInt() and 0xFF
            val sw2 = raw[raw.size - 1].toInt() and 0xFF
            if (sw1 == 0x90 && sw2 == 0x00) return true
        }
        return raw.size >= 4 &&
            (raw[0].toInt() and 0xFF) == 0xE1 &&
            (raw[1].toInt() and 0xFF) == 0x00 &&
            (raw[2].toInt() and 0xFF) == 0x00 &&
            (raw[3].toInt() and 0xFF) == 0x00
    }

    // ─── Internals ───────────────────────────────────────────────────────────────

    /**
     * Shared guard rails used by every public entry point. Returns a [Result] describing the
     * problem, or `null` when all prerequisites are met.
     */
    @SuppressLint("MissingPermission")
    private fun prechecks(context: Context): Result? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission(context)) {
            return Result(error = ExternalReaderPermissions.BLUETOOTH_CONNECT_DENIED)
        }
        val adapter = bluetoothAdapter(context) ?: return Result(error = "Bluetooth unavailable")
        if (!adapter.isEnabled) return Result(error = "Bluetooth is off")
        return null
    }

    /**
     * Scans for a [CardTerminal] matching [macAddress] and/or [nameHint], connects, optionally
     * waits for a card, then transmits the PC/SC Get UID APDU. Disconnects everything before
     * returning.
     *
     * Blocks the IO thread for up to ~[SESSION_OVERALL_MS]; the caller is expected to apply its
     * own [withTimeoutOrNull] on top.
     */
    private suspend fun runUidSession(
        appContext: Context,
        macAddress: String,
        nameHint: String,
        waitForCard: Boolean
    ): Result {
        val smartCard = BluetoothSmartCard.getInstance(appContext)
        val manager = smartCard.manager
            ?: return Result(error = "Bluetooth unavailable")

        val terminal = findTerminal(manager, macAddress, nameHint)
            ?: return Result(error = "Bluetooth reader not supported")

        // Use the factory-default master key. `null` tells the SDK to use its built-in default
        // ("ACR1255U-J1 Auth" as UTF-8 for V1, its V2 analogue for V2). If a customer ever rekeys
        // their reader they can extend the UI to set a custom key via `manager.setMasterKey`.
        runCatching { manager.setMasterKey(terminal, null) }
            .onFailure { t ->
                Log.w(TAG, "setMasterKey failed: ${t.javaClass.simpleName}: ${t.message}")
            }

        // Tune timeouts. These are upper bounds on each SDK operation (connection, power on,
        // APDU). Keeping them < session budget so we fail fast inside the SDK instead of the
        // outer withTimeoutOrNull.
        runCatching {
            val timeouts = manager.getTimeouts(terminal)
            timeouts.connectionTimeout = 6_000L
            timeouts.powerTimeout = 6_000L
            timeouts.protocolTimeout = 6_000L
            timeouts.apduTimeout = 6_000L
            timeouts.controlTimeout = 6_000L
        }

        var card: Card? = null
        try {
            if (waitForCard) {
                Log.d(TAG, "Waiting for card on ${terminal.name}...")
                // Poll in 1s ticks so we can react to cancellation promptly.
                val deadline = SystemClock.elapsedRealtime() + CARD_WAIT_TIMEOUT_MS
                var cardPresent = false
                while (SystemClock.elapsedRealtime() < deadline) {
                    cardPresent = try {
                        terminal.waitForCardPresent(700L)
                    } catch (e: CardException) {
                        Log.w(TAG, "waitForCardPresent error: ${e.message}")
                        false
                    }
                    if (cardPresent) break
                    // Yield to coroutine scheduler so cancellation works.
                    delay(10)
                }
                if (!cardPresent) {
                    return Result(error = "No card detected on reader")
                }
            }

            Log.d(TAG, "Connecting to card via ${terminal.name}...")
            val opened: Card = try {
                terminal.connect("*")
            } catch (e: CardException) {
                Log.w(TAG, "connect() failed: ${e.message}")
                return Result(error = classifyCardException(e, connectPhase = true))
            }
            card = opened

            val atrBytes: ByteArray = opened.atr?.bytes ?: ByteArray(0)
            if (!waitForCard) {
                // Verify-mode: a successful connect + non-empty ATR means the reader & card are OK.
                // If a card happens to be sitting on the reader we also return its UID.
                Log.d(TAG, "Reader online (ATR: ${atrBytes.toHex()})")
                return if (atrBytes.isEmpty()) {
                    Result(error = "No card detected on reader")
                } else {
                    readUidFromCard(opened) ?: Result(error = "No card detected on reader")
                }
            }

            return readUidFromCard(opened) ?: Result(error = "No card detected on reader")
        } catch (e: CardException) {
            Log.w(TAG, "Session failure: ${e.message}")
            return Result(error = classifyCardException(e, connectPhase = false))
        } finally {
            runCatching { card?.disconnect(false) }
            runCatching { manager.disconnect(terminal) }
        }
    }

    private fun readUidFromCard(card: Card): Result? {
        val channel = card.basicChannel ?: return null
        val response: ResponseAPDU = try {
            channel.transmit(CommandAPDU(getUidApdu))
        } catch (e: CardException) {
            Log.w(TAG, "Get UID APDU failed: ${e.message}")
            return Result(error = classifyCardException(e, connectPhase = false))
        }
        if (response.sw != 0x9000) {
            Log.w(TAG, "Get UID returned SW=0x${"%04X".format(response.sw)}")
            return Result(error = "No card detected on reader")
        }
        val uidBytes = response.data
        if (uidBytes.isEmpty()) return Result(error = "No card detected on reader")
        return Result(uid = uidBytes.toHex())
    }

    /**
     * Scan for a [CardTerminal] whose underlying Bluetooth device matches [macAddress] (or,
     * failing that, whose advertised name matches [nameHint]). Probes each terminal type in
     * [TERMINAL_TYPES] sequentially until a match is found.
     */
    @SuppressLint("MissingPermission")
    private suspend fun findTerminal(
        manager: BluetoothTerminalManager,
        macAddress: String,
        nameHint: String
    ): CardTerminal? {
        val normalizedMac = macAddress.uppercase(Locale.US)
        val normalizedName = nameHint.trim()
        val deadline = SystemClock.elapsedRealtime() + SCAN_TIMEOUT_MS

        for (type in TERMINAL_TYPES) {
            if (SystemClock.elapsedRealtime() >= deadline) break

            val found: CardTerminal? = scanOnce(
                manager = manager,
                type = type,
                deadlineMs = deadline,
                macAddress = normalizedMac,
                nameHint = normalizedName
            )
            if (found != null) return found
        }
        return null
    }

    /**
     * Single scan pass. Stops as soon as a matching terminal is reported or the deadline is hit.
     * Matching rules:
     *
     *   1. If the BT device address behind the terminal equals [macAddress] → match.
     *   2. Else if [nameHint] is non-blank and the terminal name contains the hint → match.
     *   3. Else if no filter was given (both blank) → return the first terminal.
     */
    @SuppressLint("MissingPermission")
    private suspend fun scanOnce(
        manager: BluetoothTerminalManager,
        type: Int,
        deadlineMs: Long,
        macAddress: String,
        nameHint: String
    ): CardTerminal? {
        val discovered = mutableListOf<CardTerminal>()
        val matchRef = arrayOfNulls<CardTerminal>(1)

        val callback = BluetoothTerminalManager.TerminalScanCallback { terminal ->
            if (terminal == null) return@TerminalScanCallback
            synchronized(discovered) {
                if (discovered.any { it === terminal }) return@TerminalScanCallback
                discovered += terminal
            }
            if (matches(terminal, macAddress, nameHint)) {
                synchronized(matchRef) {
                    if (matchRef[0] == null) matchRef[0] = terminal
                }
            }
        }

        try {
            manager.startScan(type, callback)
        } catch (t: Throwable) {
            Log.w(TAG, "startScan(type=$type) failed: ${t.javaClass.simpleName}: ${t.message}")
            return null
        }

        try {
            val typeDeadline = minOf(
                deadlineMs,
                SystemClock.elapsedRealtime() + (SCAN_TIMEOUT_MS / TERMINAL_TYPES.size).coerceAtLeast(2_000L)
            )
            while (SystemClock.elapsedRealtime() < typeDeadline) {
                val hit = synchronized(matchRef) { matchRef[0] }
                if (hit != null) return hit
                delay(150L)
            }
        } finally {
            runCatching { manager.stopScan() }
        }

        // No explicit match: if the caller supplied no filter and exactly one terminal turned up
        // we can still use it. Otherwise we return null and the caller escalates to "not supported".
        synchronized(discovered) {
            if (macAddress.isBlank() && nameHint.isBlank() && discovered.size == 1) {
                return discovered[0]
            }
        }
        return null
    }

    private fun matches(terminal: CardTerminal, macAddress: String, nameHint: String): Boolean {
        val terminalName = terminal.name?.uppercase(Locale.US).orEmpty()
        // CardTerminal names produced by this SDK typically look like
        // "ACR1255U-J1 V2(A8:C6:B7:11:22:33)" — we scan for the MAC substring for a direct match.
        if (macAddress.isNotBlank() && terminalName.contains(macAddress)) return true
        if (nameHint.isNotBlank()) {
            val hint = nameHint.uppercase(Locale.US)
            if (terminalName.contains(hint)) return true
            // Very defensive: some builds strip the BT part of the name between scan callback and
            // what we persisted. Match on a trailing fragment instead.
            val tail = hint.takeLast(6)
            if (tail.length >= 4 && terminalName.contains(tail)) return true
        }
        return false
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    /**
     * Best-effort: whether Android still considers the configured reader's BLE GATT link up.
     * When this is false while we still hold a cached [CardTerminal], the cache is stale and
     * must be dropped so the UI and reconnect logic match reality.
     */
    @SuppressLint("MissingPermission")
    private fun isGattConnectedForMac(context: Context, mac: String): Boolean {
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val adapter = bluetoothAdapter(context) ?: return true
        if (!adapter.isEnabled) return false
        val device = try {
            adapter.getRemoteDevice(mac)
        } catch (_: IllegalArgumentException) {
            return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return true
            try {
                when (bm.getConnectionState(device, BluetoothProfile.GATT)) {
                    BluetoothProfile.STATE_CONNECTED,
                    BluetoothProfile.STATE_CONNECTING -> true
                    else -> false
                }
            } catch (_: SecurityException) {
                true
            }
        } else {
            try {
                val m = BluetoothDevice::class.java.getMethod("isConnected")
                (m.invoke(device) as? Boolean) == true
            } catch (_: Throwable) {
                true
            }
        }
    }

    /**
     * Map [CardException]s raised by the ACS SDK to the stable string tags used by the rest of
     * the app for localization / retry logic. The SDK only ships a single exception type so we
     * fall back to substring matching on the message (stable across 0.6.x per ACS' changelog).
     */
    private fun classifyCardException(e: CardException, connectPhase: Boolean): String {
        val msg = (e.message ?: "").lowercase(Locale.US)
        val causeMsg = (e.cause?.message ?: "").lowercase(Locale.US)
        val blob = "$msg | $causeMsg"
        return when {
            "auth" in blob || "master key" in blob || "mac" in blob -> "Bluetooth reader authentication failed"
            "notif" in blob -> "Bluetooth reader notification failed"
            "no card" in blob || "absent" in blob -> "No card detected on reader"
            "timed out" in blob || "timeout" in blob -> "Bluetooth reader timeout"
            "disconnect" in blob -> "Bluetooth reader disconnected"
            "unsupported" in blob || "not supported" in blob -> "Bluetooth reader not supported"
            connectPhase -> "Bluetooth connect failed"
            else -> "Bluetooth reader error"
        }
    }

    /**
     * Render an error tag as an actionable diagnostic message. Mirrors the old implementation's
     * copy so the settings dialog stays predictable.
     */
    private fun describeError(error: String?): String = when (error) {
        ExternalReaderPermissions.BLUETOOTH_CONNECT_DENIED ->
            "BLUETOOTH_CONNECT permission denied."
        "Bluetooth unavailable" ->
            "Bluetooth unavailable on this device."
        "Bluetooth is off" ->
            "Bluetooth is OFF — enable it and retry."
        "ACR1255U-J1 reader not paired" ->
            "No valid reader selected — pick one first."
        "Bluetooth reader authentication failed" ->
            "Reader is recognised but master-key authentication failed.\n" +
                "If you changed the master key from the factory default, the app can't authenticate."
        "Bluetooth reader notification failed" ->
            "Reader recognised but enabling BLE notifications failed.\nUsually transient — retry."
        "Bluetooth reader not supported" ->
            "Could not find the selected reader over Bluetooth.\n\n" +
                "This usually means:\n" +
                " - The reader is asleep / out of range (wake it up by pressing its button),\n" +
                " - Another app or the system pairing dialog still holds a GATT handle,\n" +
                " - Or the selected MAC is not an ACS ACR1255U-J1 / J1 V2.\n\n" +
                "Check logcat tag \"ACR1255BLE\" for scan details."
        "Bluetooth connect failed" ->
            "Android's GATT layer failed to connect.\n" +
                "The reader may be out of range, already connected elsewhere, or its battery too low."
        "Bluetooth reader timeout" ->
            "Timed out during scan or the handshake.\n" +
                "The reader probably stayed in advertising mode (blinking LED) — wake it up and retry."
        else -> "Failed: ${error ?: "unknown"}"
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { b -> "%02X".format(b) }
}
