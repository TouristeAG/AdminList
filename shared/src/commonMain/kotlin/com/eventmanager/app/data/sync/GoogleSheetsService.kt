package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.platform.createAppStorage
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.utils.NanoIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formats 1-based sheet row numbers for logs, e.g. "3", "10-15", "3, 10-15". */
private fun formatSheetRowNumberRanges(rowNumbers: Collection<Int>): String {
    val sorted = rowNumbers.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var start = sorted[0]
    var end = sorted[0]
    for (i in 1 until sorted.size) {
        val v = sorted[i]
        if (v == end + 1) {
            end = v
        } else {
            parts.add(if (start == end) "$start" else "$start-$end")
            start = v
            end = v
        }
    }
    parts.add(if (start == end) "$start" else "$start-$end")
    return parts.joinToString(", ")
}

/** One log line for many skipped rows: (sheet row, column count) pairs. */
private fun logSkippedSheetRows(entityLabel: String, skips: List<Pair<Int, Int>>) {
    if (skips.isEmpty()) return
    val rowNums = skips.map { it.first }
    val colCounts = skips.map { it.second }.distinct().sorted()
    val colPart = if (colCounts.size == 1) {
        "insufficient columns: ${colCounts.first()}"
    } else {
        "insufficient columns (column counts: ${colCounts.joinToString()})"
    }
    println(
        "Skipping ${skips.size} $entityLabel rows (rows ${formatSheetRowNumberRanges(rowNums)}) — $colPart"
    )
}

/** Volunteers sheet "Rank" column: computed benefit rank when provided, else persisted [Volunteer.currentRank]. */
private fun volunteerRankLabelForSheet(volunteer: Volunteer, benefitPrimaryRank: VolunteerRank?): String =
    (benefitPrimaryRank ?: volunteer.currentRank)?.name ?: "No Rank"

/** Display string for the shifts (jobs) sheet next to volunteer NanoID — matches roster style (name + abbreviation). */
private fun volunteerDisplayNameForJobSheet(volunteer: Volunteer): String =
    "${volunteer.name} ${volunteer.lastNameAbbreviation}".trim()

private fun volunteerDisplayNameForJobSheet(volunteerId: String, volunteers: List<Volunteer>): String =
    volunteers.find { it.id == volunteerId }?.let { volunteerDisplayNameForJobSheet(it) } ?: ""

private val JOBS_SHEET_HEADERS_V2 = listOf(
    "Volunteer ID", "Volunteer Name", "Job Type", "Venue", "Date", "Shift Time", "Notes", "Last Modified", "Entries left"
)

/** Shifts sheet column layout: v1 = NanoID then job type; v2 = NanoID, display name, then job type. */
private enum class JobsSheetLayout { LEGACY_EIGHT_COL, VOLUNTEER_NAME_NINE_COL }

private fun padJobSheetRowToEightLegacyCells(row: List<Any>): List<Any> {
    val out = row.map { it }.toMutableList()
    while (out.size < 8) out.add("")
    if (out.size > 8) return out.take(8)
    return out
}

private fun legacyEightColRowToV2Row(row: List<Any>, volunteerName: String): List<Any> {
    val eight = padJobSheetRowToEightLegacyCells(row)
    return listOf(eight[0], volunteerName) + eight.drop(1)
}

class GoogleSheetsService(private val platformContext: PlatformContext) {

    companion object {
        /** 0-based index of last column with app data (before the two blank columns + epoch panel). */
        private const val SHEET_LAST_COL_GUEST_LIST = 10 // K
        private const val SHEET_LAST_COL_VOLUNTEER_GUEST_DATA = 7 // H
        private const val SHEET_LAST_COL_VOLUNTEER = 11 // L
        private const val SHEET_LAST_COL_JOBS = 8 // I (v2 shifts sheet)
        private const val SHEET_LAST_COL_JOB_TYPES = 10 // K
        private const val SHEET_LAST_COL_VENUES = 10 // K
        private const val SHEET_LAST_COL_SALES_ITEMS = 8 // I
        private const val SHEET_LAST_COL_TRANSFERS = 18 // S
        private const val SHEET_LAST_COL_SETTINGS = 2 // C (Key, Value, Last Modified)

        /** 0-based column where the 2-column epoch helper starts (last data col + 2 blanks). */
        private fun epochPanelColumn0(lastDataColumnZeroBased: Int) = lastDataColumnZeroBased + 3

        /** Epoch helper: 0-based column index, 1-based top row (product layout per tab). */
        private const val EPOCH_COL_GUEST_LIST = 13 // N
        private const val EPOCH_ROW_GUEST_LIST = 2
        private const val EPOCH_COL_VOLUNTEER_GUEST = 10 // K
        private const val EPOCH_ROW_VOLUNTEER_GUEST = 9
        private const val EPOCH_COL_VOLUNTEERS = 14 // O
        private const val EPOCH_ROW_VOLUNTEERS = 2
        private const val EPOCH_COL_JOBS = 11 // L (v2: data A–I, blanks J–K, epoch here)
        private const val EPOCH_ROW_JOBS = 2
        private const val EPOCH_COL_VENUES = 13 // N
        private const val EPOCH_ROW_VENUES = 2
        private const val EPOCH_COL_JOB_TYPES = 12 // M
        private const val EPOCH_ROW_JOB_TYPES = 2
        private const val EPOCH_ROW_SALES = 2
        private const val EPOCH_ROW_TRANSFERS = 2

        /**
         * Parse a lastModified timestamp from a Google Sheets cell value.
         * Handles plain longs AND scientific notation (e.g. "1.7132E12") which
         * the Sheets API may produce for large numbers.
         * Falls back to 0 so that the merge-before-upload logic never treats an
         * unparseable remote value as "newer than local."
         */
        fun parseLastModified(raw: String): Long {
            return raw.toLongOrNull()
                ?: raw.toDoubleOrNull()?.toLong()
                ?: 0L
        }

        /**
         * Parses the shifts-sheet **event date** cell (not necessarily epoch millis).
         * Supports epoch milliseconds (and scientific notation via [toDoubleOrNull]),
         * ISO-8601 dates/datetimes, and Google Sheets / Excel **serial day** numbers (typical range 1–60000).
         */
        fun parseJobEventDateFromSheets(raw: String): Long {
            val s = raw.trim()
            if (s.isEmpty()) return 0L
            val zone = ZoneId.of("Europe/Zurich")
            s.toLongOrNull()?.takeIf { it >= 100_000_000_000L }?.let { return it }
            val asDouble = s.toDoubleOrNull()
            if (asDouble != null && !asDouble.isNaN()) {
                when {
                    asDouble >= 100_000_000_000.0 -> return asDouble.toLong()
                    asDouble >= 1.0 && asDouble < 1_000_000.0 -> {
                        val base = LocalDate.of(1899, 12, 30)
                        val dayIndex = asDouble.toLong()
                        return base.plusDays(dayIndex)
                            .atStartOfDay(zone)
                            .toInstant()
                            .toEpochMilli()
                    }
                }
            }
            try {
                return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
            }
            try {
                return ZonedDateTime.parse(s).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
            try {
                return Instant.parse(s).toEpochMilli()
            } catch (_: Exception) {
            }
            return parseLastModified(s).takeIf { it > 0L } ?: 0L
        }
    }

    /**
     * Creates a user-friendly error message for network connectivity issues
     */
    private fun createNetworkErrorMessage(operation: String, originalException: Exception): String {
        val errorMessage = originalException.message ?: ""
        val cause = originalException.cause
        
        // Check for network connectivity issues
        val isNetworkError = originalException is UnknownHostException ||
                cause is UnknownHostException ||
                errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
                errorMessage.contains("No address associated with hostname", ignoreCase = true) ||
                errorMessage.contains("Network is unreachable", ignoreCase = true) ||
                errorMessage.contains("Connection refused", ignoreCase = true) ||
                errorMessage.contains("Connection timed out", ignoreCase = true) ||
                errorMessage.contains("No route to host", ignoreCase = true)
        
        return if (isNetworkError) {
            "Your internet connection might not be working correctly. Please check your Wi-Fi or mobile data connection and try again."
        } else {
            "Failed to $operation: ${errorMessage}"
        }
    }

    /**
     * Wrap a Sheets failure as [IOException], but never swallow coroutine cancellation —
     * wrapping [CancellationException] turns a normal leave-composition cancel into a fatal crash.
     */
    private fun networkFailure(operation: String, e: Exception): IOException {
        if (e is CancellationException) throw e
        var cause = e.cause
        while (cause != null) {
            if (cause is CancellationException) throw cause
            cause = cause.cause
        }
        return IOException(createNetworkErrorMessage(operation, e), e)
    }
    private var sheetsService: Sheets? = null
    private val settingsManager = SettingsManager(createAppStorage(platformContext))
    private val fileManager = PlatformFileManager(platformContext)

    // ── API-call reduction caches ───────────────────────────────────────────
    //
    // The Google Sheets API is rate-limited. Several helpers (structure
    // validation, the epoch calculator panel, the volunteer-guest banner,
    // etc.) are decorative/structural and rarely need to run more than once
    // per session on the same spreadsheet. Caching their result in memory
    // removes dozens of API calls per sync cycle without changing behaviour
    // for the user: structure still gets checked the first time we talk to
    // the sheet, and after [invalidateSessionCaches] (e.g. when the sheet
    // ID / tab names change) the caches are rebuilt.

    /** Spreadsheet id a cached structure validation was performed against. */
    @Volatile
    private var lastStructureValidationSpreadsheetId: String? = null

    /** Unix millis of the last successful [validateAndRepairSheetsStructure] run. */
    @Volatile
    private var lastStructureValidationAtMs: Long = 0L

    /** Structure validation TTL: after this the sheet is re-checked (1 hour). */
    private val structureValidationTtlMs: Long = 60L * 60L * 1000L

    /** Tabs that already received [applyEpochCalculatorPanel] this session (spreadsheetId|tabTitle). */
    private val epochPanelAppliedTabs: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /** Volunteer-guest read-only banner keys (spreadsheetId|tabTitle) already applied this session. */
    private val volunteerGuestBannerAppliedTabs: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /**
     * Drops session caches; call after the spreadsheet id or any tab name
     * changes so the next sync re-validates structure and reapplies banners
     * on the new target. Safe to call many times.
     */
    @Suppress("unused")
    fun invalidateSessionCaches() {
        lastStructureValidationSpreadsheetId = null
        lastStructureValidationAtMs = 0L
        epochPanelAppliedTabs.clear()
        volunteerGuestBannerAppliedTabs.clear()
    }

    private fun structureValidationCacheIsFresh(spreadsheetId: String): Boolean {
        if (spreadsheetId.isBlank()) return false
        if (lastStructureValidationSpreadsheetId != spreadsheetId) return false
        val age = System.currentTimeMillis() - lastStructureValidationAtMs
        return age in 0..structureValidationTtlMs
    }

    private fun markStructureValidationFresh(spreadsheetId: String) {
        lastStructureValidationSpreadsheetId = spreadsheetId
        lastStructureValidationAtMs = System.currentTimeMillis()
    }

    private fun epochPanelCacheKey(spreadsheetId: String, tabTitle: String, leftCol0: Int): String =
        "$spreadsheetId|$tabTitle|$leftCol0"

    private fun volunteerGuestBannerCacheKey(spreadsheetId: String, tabTitle: String): String =
        "$spreadsheetId|$tabTitle"

    /** Matches [com.eventmanager.app.MainActivity] language → locale mapping (incl. en-GB). */
    private fun localeFromAppLanguageSetting(): Locale {
        val code = settingsManager.getLanguage()
        return when {
            code.equals("en", ignoreCase = true) -> Locale("en", "GB")
            code.contains("-") -> {
                val parts = code.split("-")
                if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(code)
            }
            code.contains("_") -> {
                val parts = code.split("_")
                if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(code)
            }
            else -> Locale(code)
        }
    }

    /** A1 column letters for 0-based column index (0 = A). */
    private fun a1ColumnLetterFromIndex0(zeroBasedIndex: Int): String {
        require(zeroBasedIndex >= 0)
        var n = zeroBasedIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString().reversed()
    }

    private data class ResolvedSheetTab(val sheetId: Int, val title: String)

    /**
     * Resolves a configured tab title to the spreadsheet's actual tab (trim + exact match, then case-insensitive).
     */
    private fun resolveSheetTab(spreadsheet: Spreadsheet, configuredTitle: String): ResolvedSheetTab? {
        val want = configuredTitle.trim()
        val list = spreadsheet.sheets ?: return null
        for (sh in list) {
            val t = sh.properties?.title?.trim() ?: continue
            if (t == want) {
                val sid = sh.properties?.sheetId?.toInt() ?: continue
                return ResolvedSheetTab(sid, t)
            }
        }
        for (sh in list) {
            val t = sh.properties?.title?.trim() ?: continue
            if (t.equals(want, ignoreCase = true)) {
                val sid = sh.properties?.sheetId?.toInt() ?: continue
                return ResolvedSheetTab(sid, t)
            }
        }
        return null
    }

    /**
     * Google Sheets uses `;` between function arguments in many European locales (e.g. fr_FR)
     * and `,` in en_US / en_GB. Formulas written with the wrong separator show #ERROR! / parse errors.
     */
    private fun sheetFormulaListSeparator(spreadsheetLocale: String?): Char {
        if (spreadsheetLocale.isNullOrBlank()) return ','
        val l = spreadsheetLocale.lowercase(Locale.ROOT).replace('-', '_')
        val lang = l.substringBefore('_').trim()
        val semiLanguages = setOf(
            "fr", "de", "it", "nl", "ru", "pl", "cs", "sk", "hu", "ro", "da", "sv", "no", "nb", "nn",
            "fi", "el", "pt", "ca", "eu", "gl", "is", "sq", "sl", "hr", "sr", "bs", "bg", "uk", "be",
            "et", "lv", "lt", "lb", "mt", "ga", "cy", "mk", "tr"
        )
        if (lang in semiLanguages) return ';'
        if (lang == "es") {
            if (l.contains("_us", ignoreCase = true) || l.contains("_mx", ignoreCase = true)) return ','
            return ';'
        }
        return ','
    }

    /**
     * Clears only the main data rectangle so the epoch helper panel (right of [lastDataCol0] + 2 blanks) survives.
     * Uses [quoteSheetTabForRange] so tab names with spaces (e.g. "Volunteer Guest List") clear correctly.
     */
    private suspend fun clearSheetDataColumns(sheetName: String, lastDataColumnZeroBased: Int) {
        val tab = quoteSheetTabForRange(sheetName)
        val endLetter = a1ColumnLetterFromIndex0(lastDataColumnZeroBased + 2)
        clearSheetRange("$tab!A:$endLetter")
    }

    /**
     * Clears values in the sidecar zone (blank columns + epoch panel area) so a misplaced
     * epoch helper from an older schema does not overlap new data columns.
     */
    private suspend fun clearStaleEpochSidecar(sheetName: String, lastDataColumnZeroBased: Int) {
        val tab = quoteSheetTabForRange(sheetName)
        val startLetter = a1ColumnLetterFromIndex0(lastDataColumnZeroBased + 1)
        val endLetter = a1ColumnLetterFromIndex0(lastDataColumnZeroBased + 5)
        clearSheetRange("$tab!${startLetter}1:${endLetter}10")
    }

    private suspend fun repairEpochCalculatorPanel(
        spreadsheetId: String,
        sheetTitle: String,
        lastDataColumnZeroBased: Int,
        topRow1Based: Int,
    ) {
        clearStaleEpochSidecar(sheetTitle, lastDataColumnZeroBased)
        applyEpochCalculatorPanel(
            spreadsheetId,
            sheetTitle,
            epochPanelColumn0(lastDataColumnZeroBased),
            topRow1Based,
        )
    }

    /**
     * Two-column epoch helper in a fixed 4-row block (e.g. N2:O5): merged section titles (with short hints),
     * then input | formula for ms→datetime and date→ms. [leftCol0] is 0-based column (N=13); [topRow1Based] is the first row.
     */
    private suspend fun applyEpochCalculatorPanel(
        spreadsheetId: String,
        sheetTitle: String,
        leftCol0: Int,
        topRow1Based: Int
    ) = withContext(Dispatchers.IO) {
        if (sheetsService == null) {
            initializeSheetsService()
        }
        val service = sheetsService ?: return@withContext

        // Skip entirely when we already wrote this purely-decorative panel to
        // this tab in this process session. The panel layout never changes, so
        // re-running it on every upload just burns 3-4 API calls per tab.
        val cacheKey = epochPanelCacheKey(spreadsheetId, sheetTitle, leftCol0)
        if (epochPanelAppliedTabs.contains(cacheKey)) {
            return@withContext
        }
        val ss = service.spreadsheets().get(spreadsheetId).execute()
        val epochResolved = resolveSheetTab(ss, sheetTitle)
            ?: run {
                println("⚠️ No sheetId for tab '$sheetTitle' — skipping epoch calculator panel")
                return@withContext
            }
        val sheetId = epochResolved.sheetId
        val tabQuoted = quoteSheetTabForRange(epochResolved.title)

        val c0 = a1ColumnLetterFromIndex0(leftCol0)
        val c1 = a1ColumnLetterFromIndex0(leftCol0 + 1)
        val rTitle1 = topRow1Based
        val rMs = topRow1Based + 1
        val rTitle2 = topRow1Based + 2
        val rDt = topRow1Based + 3
        val row0Panel = topRow1Based - 1
        val rowEndExclusive = row0Panel + 4

        try {
            service.spreadsheets().batchUpdate(
                spreadsheetId,
                BatchUpdateSpreadsheetRequest().setRequests(
                    listOf(
                        Request().setUnmergeCells(
                            UnmergeCellsRequest().setRange(
                                GridRange()
                                    .setSheetId(sheetId)
                                    .setStartRowIndex(row0Panel)
                                    .setEndRowIndex(rowEndExclusive + 1)
                                    .setStartColumnIndex(leftCol0)
                                    .setEndColumnIndex(leftCol0 + 3)
                            )
                        )
                    )
                )
            ).execute()
        } catch (e: Exception) {
            println("⚠️ Epoch panel unmerge (non-fatal): ${e.message}")
        }

        val mergeT1 = Request().setMergeCells(
            MergeCellsRequest()
                .setMergeType("MERGE_ALL")
                .setRange(
                    GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(rTitle1 - 1)
                        .setEndRowIndex(rTitle1)
                        .setStartColumnIndex(leftCol0)
                        .setEndColumnIndex(leftCol0 + 2)
                )
        )
        val mergeT2 = Request().setMergeCells(
            MergeCellsRequest()
                .setMergeType("MERGE_ALL")
                .setRange(
                    GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(rTitle2 - 1)
                        .setEndRowIndex(rTitle2)
                        .setStartColumnIndex(leftCol0)
                        .setEndColumnIndex(leftCol0 + 2)
                )
        )
        service.spreadsheets().batchUpdate(
            spreadsheetId,
            BatchUpdateSpreadsheetRequest().setRequests(listOf(mergeT1, mergeT2))
        ).execute()

        val language = settingsManager.getLanguage()
        val hintMs = SheetsLocalizedStrings.epochHintMs(language)
        val hintDt = SheetsLocalizedStrings.epochHintDateTime(language)
        val title1 = SheetsLocalizedStrings.epochTitleMsToDate(language) + "\n" + hintMs
        val title2 = SheetsLocalizedStrings.epochTitleDateToMs(language) + "\n" + hintDt
        val sep = sheetFormulaListSeparator(ss.properties?.locale)
        val msToDateFormula =
            "=IF(COUNTBLANK($c0$rMs)=1${sep}\"\"${sep}(($c0$rMs)+0)/(1000*60*60*24)+DATE(1970${sep}1${sep}1))"
        val dateToMsFormula =
            "=IF(COUNTBLANK($c0$rDt)=1${sep}\"\"${sep}(($c0$rDt)-DATE(1970${sep}1${sep}1))*86400000)"

        service.spreadsheets().values().batchUpdate(
            spreadsheetId,
            BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setData(
                    listOf(
                        ValueRange().setRange("$tabQuoted!$c0$rTitle1").setValues(listOf(listOf(title1))),
                        ValueRange().setRange("$tabQuoted!$c0$rMs").setValues(listOf(listOf(""))),
                        ValueRange().setRange("$tabQuoted!$c1$rMs").setValues(listOf(listOf(msToDateFormula))),
                        ValueRange().setRange("$tabQuoted!$c0$rTitle2").setValues(listOf(listOf(title2))),
                        ValueRange().setRange("$tabQuoted!$c0$rDt").setValues(listOf(listOf(""))),
                        ValueRange().setRange("$tabQuoted!$c1$rDt").setValues(listOf(listOf(dateToMsFormula)))
                    )
                )
        ).execute()

        val panelBg = Color().setRed(0.97f).setGreen(0.98f).setBlue(1f)
        val titleBg = Color().setRed(0.88f).setGreen(0.92f).setBlue(0.99f)
        val inputBg = Color().setRed(1f).setGreen(1f).setBlue(1f)
        val outBg = Color().setRed(0.94f).setGreen(0.96f).setBlue(0.99f)
        val borderColor = Color().setRed(0.55f).setGreen(0.62f).setBlue(0.75f)
        val textDark = Color().setRed(0.14f).setGreen(0.18f).setBlue(0.26f)
        val borderSolid = Border().setStyle("SOLID").setWidth(1).setColor(borderColor)

        val fmtTitle = CellFormat()
            .setBackgroundColor(titleBg)
            .setHorizontalAlignment("CENTER")
            .setVerticalAlignment("MIDDLE")
            .setWrapStrategy("WRAP")
            .setTextFormat(TextFormat().setBold(true).setFontSize(10).setForegroundColor(textDark))

        val fmtInput = CellFormat()
            .setBackgroundColor(inputBg)
            .setHorizontalAlignment("LEFT")
            .setVerticalAlignment("MIDDLE")
            .setWrapStrategy("WRAP")
            .setTextFormat(TextFormat().setBold(false).setFontSize(10).setForegroundColor(textDark))

        val fmtOutMs = CellFormat()
            .setBackgroundColor(outBg)
            .setHorizontalAlignment("LEFT")
            .setVerticalAlignment("MIDDLE")
            .setNumberFormat(NumberFormat().setType("DATE_TIME").setPattern("yyyy-MM-dd HH:mm:ss"))

        val fmtOutMsNum = CellFormat()
            .setBackgroundColor(outBg)
            .setHorizontalAlignment("RIGHT")
            .setVerticalAlignment("MIDDLE")
            .setNumberFormat(NumberFormat().setType("NUMBER").setPattern("0"))

        val formatRequests = listOf(
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(row0Panel)
                            .setEndRowIndex(rowEndExclusive)
                            .setStartColumnIndex(leftCol0)
                            .setEndColumnIndex(leftCol0 + 2)
                    )
                    .setCell(
                        CellData().setUserEnteredFormat(
                            CellFormat()
                                .setBackgroundColor(panelBg)
                                .setWrapStrategy("WRAP")
                                .setVerticalAlignment("TOP")
                        )
                    )
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.wrapStrategy," +
                            "userEnteredFormat.verticalAlignment"
                    )
            ),
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(rTitle1 - 1)
                            .setEndRowIndex(rTitle1)
                            .setStartColumnIndex(leftCol0)
                            .setEndColumnIndex(leftCol0 + 2)
                    )
                    .setCell(CellData().setUserEnteredFormat(fmtTitle))
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                            "userEnteredFormat.verticalAlignment,userEnteredFormat.wrapStrategy," +
                            "userEnteredFormat.textFormat"
                    )
            ),
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(rTitle2 - 1)
                            .setEndRowIndex(rTitle2)
                            .setStartColumnIndex(leftCol0)
                            .setEndColumnIndex(leftCol0 + 2)
                    )
                    .setCell(CellData().setUserEnteredFormat(fmtTitle))
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                            "userEnteredFormat.verticalAlignment,userEnteredFormat.wrapStrategy," +
                            "userEnteredFormat.textFormat"
                    )
            ),
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(rMs - 1)
                            .setEndRowIndex(rMs)
                            .setStartColumnIndex(leftCol0)
                            .setEndColumnIndex(leftCol0 + 1)
                    )
                    .setCell(CellData().setUserEnteredFormat(fmtInput))
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                            "userEnteredFormat.verticalAlignment,userEnteredFormat.wrapStrategy," +
                            "userEnteredFormat.textFormat"
                    )
            ),
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(rDt - 1)
                            .setEndRowIndex(rDt)
                            .setStartColumnIndex(leftCol0)
                            .setEndColumnIndex(leftCol0 + 1)
                    )
                    .setCell(CellData().setUserEnteredFormat(fmtInput))
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                            "userEnteredFormat.verticalAlignment,userEnteredFormat.wrapStrategy," +
                            "userEnteredFormat.textFormat"
                    )
            ),
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(rMs - 1)
                            .setEndRowIndex(rMs)
                            .setStartColumnIndex(leftCol0 + 1)
                            .setEndColumnIndex(leftCol0 + 2)
                    )
                    .setCell(CellData().setUserEnteredFormat(fmtOutMs))
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                            "userEnteredFormat.verticalAlignment,userEnteredFormat.numberFormat"
                    )
            ),
            Request().setRepeatCell(
                RepeatCellRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(rDt - 1)
                            .setEndRowIndex(rDt)
                            .setStartColumnIndex(leftCol0 + 1)
                            .setEndColumnIndex(leftCol0 + 2)
                    )
                    .setCell(CellData().setUserEnteredFormat(fmtOutMsNum))
                    .setFields(
                        "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                            "userEnteredFormat.verticalAlignment,userEnteredFormat.numberFormat"
                    )
            ),
            Request().setUpdateBorders(
                UpdateBordersRequest()
                    .setRange(
                        GridRange()
                            .setSheetId(sheetId)
                            .setStartRowIndex(row0Panel)
                            .setEndRowIndex(rowEndExclusive)
                            .setStartColumnIndex(leftCol0)
                            .setEndColumnIndex(leftCol0 + 2)
                    )
                    .setTop(borderSolid)
                    .setBottom(borderSolid)
                    .setLeft(borderSolid)
                    .setRight(borderSolid)
            )
        )
        service.spreadsheets().batchUpdate(
            spreadsheetId,
            BatchUpdateSpreadsheetRequest().setRequests(formatRequests)
        ).execute()

        // Remember that this tab is now set up so subsequent uploads in the
        // same session skip the ~4 API calls above.
        epochPanelAppliedTabs.add(epochPanelCacheKey(spreadsheetId, sheetTitle, leftCol0))
    }

    private fun requiresShiftTimeForJobType(jobTypeName: String, jobTypeConfigs: List<JobTypeConfig>): Boolean {
        val config = jobTypeConfigs.firstOrNull { it.name == jobTypeName }
        // Keep legacy behavior when config is unknown: write/parse explicit shift labels.
        return config?.requiresShiftTime ?: true
    }

    private fun toSheetShiftTimeValue(job: Job, jobTypeConfigs: List<JobTypeConfig>): String {
        return if (requiresShiftTimeForJobType(job.jobTypeName, jobTypeConfigs)) {
            job.shiftTime.toGoogleSheetsShiftTimeValue()
        } else {
            ""
        }
    }

    private fun parseSheetShiftTimeValue(
        rawShiftTime: String,
        jobTypeName: String,
        jobTypeConfigs: List<JobTypeConfig>
    ): ShiftTime {
        if (!requiresShiftTimeForJobType(jobTypeName, jobTypeConfigs) && rawShiftTime.trim().isEmpty()) {
            // Non-shift-time job types intentionally keep this cell empty in Sheets.
            return ShiftTime.BEFORE_MIDNIGHT
        }
        return try {
            parseShiftTimeFromGoogleSheets(rawShiftTime)
        } catch (_: Exception) {
            ShiftTime.BEFORE_MIDNIGHT
        }
    }

    private fun normalizeJobTypeNameFromSheets(raw: Any?): String =
        raw?.toString()?.trim().orEmpty().ifBlank { "Other" }

    private fun normalizeJobTextCellFromSheets(raw: Any?): String =
        raw?.toString()?.trim().orEmpty()

    /**
     * How to interpret one shifts-sheet data row. Rows may have 9+ cells from the API while still
     * using the legacy column map (job type in column B); treating those as v2 puts epoch dates
     * into [parseShiftTimeFromGoogleSheets] and breaks parsing.
     */
    private enum class JobSheetRowParseMode { LEGACY_EIGHT_COL, VOLUNTEER_NAME_NINE_COL }

    private fun isBareMillisTimestampString(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        if (t.all { it.isDigit() }) return t.length in 10..15
        val d = t.toDoubleOrNull() ?: return false
        val lv = kotlin.math.round(d).toLong()
        return lv in 1_000_000_000_000L..99_999_999_999_999L
    }

    /**
     * True when a cell likely holds an event date (epoch millis, Sheets serial day, ISO date).
     * Used only to disambiguate legacy vs v2 column maps when job-type names do not match configs.
     */
    private fun isLikelyJobSheetEventDateCell(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        if (isBareMillisTimestampString(t)) return true
        if (t.matches(Regex("^\\d{4}-\\d{2}-\\d{2}([Tt].*)?$"))) return true
        // Google Sheets serial days are almost always 5–7 digit numbers (avoid "3" / small codes).
        if (t.matches(Regex("^\\d{5,7}(\\.\\d+)?$"))) {
            val d = t.toDoubleOrNull() ?: return false
            if (!d.isNaN() && d >= 1.0 && d < 1_000_000.0) return true
        }
        return false
    }

    /**
     * True when a cell looks like the human-readable "Shift time" column (not a bare millis timestamp).
     */
    private fun cellLooksLikeJobSheetShiftColumn(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (isBareMillisTimestampString(t)) return false
        val low = t.lowercase()
        return low.contains("evening") || low.contains("shift") ||
            low.contains("profit") || low.contains("profité") || low.contains("profited") ||
            low.contains("non-prof") || low.contains("pas profit") || low.contains("non prof") ||
            low.contains("midnight") || low.contains("con beneficio") || low.contains("sin beneficio") ||
            low == "both" || low.contains("groove") || low.contains("terreau") ||
            low == "before_midnight" || low == "after_midnight"
    }

    private fun inferJobSheetRowParseMode(row: List<Any>, jobTypeConfigs: List<JobTypeConfig>): JobSheetRowParseMode {
        if (row.size < 7) return JobSheetRowParseMode.LEGACY_EIGHT_COL
        val shiftCol = row.getOrNull(5)?.toString()?.trim().orEmpty()
        if (isBareMillisTimestampString(shiftCol)) {
            return JobSheetRowParseMode.LEGACY_EIGHT_COL
        }
        val col1 = row.getOrNull(1)?.toString()?.trim().orEmpty()
        val col2 = row.getOrNull(2)?.toString()?.trim().orEmpty()
        val typeNames = jobTypeConfigs.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
        if (typeNames.isNotEmpty()) {
            val jtAt1 = col1 in typeNames
            val jtAt2 = col2 in typeNames
            when {
                jtAt2 && !jtAt1 -> return JobSheetRowParseMode.VOLUNTEER_NAME_NINE_COL
                jtAt1 && !jtAt2 -> return JobSheetRowParseMode.LEGACY_EIGHT_COL
                jtAt1 && jtAt2 -> {
                    val cell3 = row.getOrNull(3)?.toString()?.trim().orEmpty()
                    val cell4 = row.getOrNull(4)?.toString()?.trim().orEmpty()
                    val cell5 = row.getOrNull(5)?.toString()?.trim().orEmpty()
                    val d3 = isLikelyJobSheetEventDateCell(cell3)
                    val d4 = isLikelyJobSheetEventDateCell(cell4)
                    val s4 = cellLooksLikeJobSheetShiftColumn(cell4)
                    val s5 = cellLooksLikeJobSheetShiftColumn(cell5)
                    return resolveJobSheetParseModeFromDateShiftSignals(d3, d4, s4, s5)
                }
            }
        }

        val cell3 = row.getOrNull(3)?.toString()?.trim().orEmpty()
        val cell4 = row.getOrNull(4)?.toString()?.trim().orEmpty()
        val cell5 = row.getOrNull(5)?.toString()?.trim().orEmpty()
        val d3 = isLikelyJobSheetEventDateCell(cell3)
        val d4 = isLikelyJobSheetEventDateCell(cell4)
        val s4 = cellLooksLikeJobSheetShiftColumn(cell4)
        val s5 = cellLooksLikeJobSheetShiftColumn(cell5)
        return resolveJobSheetParseModeFromDateShiftSignals(d3, d4, s4, s5)
    }

    /**
     * When job-type cells do not match [JobTypeConfig] names (typos, locales, inactive types),
     * infer layout from where the event date and shift label sit so [Job.jobTypeName] still matches benefits logic.
     */
    private fun resolveJobSheetParseModeFromDateShiftSignals(
        dateLikelyCol3: Boolean,
        dateLikelyCol4: Boolean,
        shiftLikelyCol4: Boolean,
        shiftLikelyCol5: Boolean
    ): JobSheetRowParseMode {
        val v2Strong = dateLikelyCol4 && shiftLikelyCol5 && !(dateLikelyCol3 && shiftLikelyCol4)
        val legacyStrong = dateLikelyCol3 && shiftLikelyCol4 && !(dateLikelyCol4 && shiftLikelyCol5)
        when {
            v2Strong && !legacyStrong -> return JobSheetRowParseMode.VOLUNTEER_NAME_NINE_COL
            legacyStrong && !v2Strong -> return JobSheetRowParseMode.LEGACY_EIGHT_COL
            dateLikelyCol3 && !dateLikelyCol4 -> return JobSheetRowParseMode.LEGACY_EIGHT_COL
            dateLikelyCol4 && !dateLikelyCol3 -> return JobSheetRowParseMode.VOLUNTEER_NAME_NINE_COL
            // Prefer legacy when still ambiguous: defaulting to v2 used volunteer name as job type and broke benefits.
            else -> return JobSheetRowParseMode.LEGACY_EIGHT_COL
        }
    }

    private fun detectJobsSheetLayout(
        header: List<String>,
        anchorDataRow: List<Any>?,
        jobTypeConfigs: List<JobTypeConfig>
    ): JobsSheetLayout {
        val h1 = header.getOrNull(1)?.trim().orEmpty()
        when {
            h1.equals("Volunteer Name", ignoreCase = true) ||
                h1.equals("Nom du bénévole", ignoreCase = true) ||
                h1.equals("Nombre del voluntario", ignoreCase = true) ->
                return JobsSheetLayout.VOLUNTEER_NAME_NINE_COL
            h1.equals("Job Type", ignoreCase = true) ||
                h1.equals("Type de poste", ignoreCase = true) ||
                h1.equals("Tipo de trabajo", ignoreCase = true) ->
                return JobsSheetLayout.LEGACY_EIGHT_COL
        }
        val anchor = anchorDataRow ?: return JobsSheetLayout.LEGACY_EIGHT_COL
        val n = anchor.size
        if (n >= 9) return JobsSheetLayout.VOLUNTEER_NAME_NINE_COL
        if (n >= 7 && rowContentSuggestsVolunteerNameColumnBeforeJobType(anchor, jobTypeConfigs)) {
            return JobsSheetLayout.VOLUNTEER_NAME_NINE_COL
        }
        if (n in 1..8) return JobsSheetLayout.LEGACY_EIGHT_COL
        return JobsSheetLayout.VOLUNTEER_NAME_NINE_COL
    }

    /**
     * True when column B is already the volunteer display string and column C is the configured job type
     * (v2 layout), including rows where the API returns only 8 cells because "Entries left" is empty.
     */
    private fun rowContentSuggestsVolunteerNameColumnBeforeJobType(
        row: List<Any>,
        jobTypeConfigs: List<JobTypeConfig>
    ): Boolean {
        if (row.size < 3) return false
        val typeNames = jobTypeConfigs.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
        if (typeNames.isNotEmpty()) {
            val c1 = row.getOrNull(1)?.toString()?.trim().orEmpty()
            val c2 = row.getOrNull(2)?.toString()?.trim().orEmpty()
            when {
                c2 in typeNames && c1 !in typeNames -> return true
                c1 in typeNames && c2 !in typeNames -> return false
            }
        }
        if (row.size < 6) return false
        val cell3 = row.getOrNull(3)?.toString()?.trim().orEmpty()
        val cell4 = row.getOrNull(4)?.toString()?.trim().orEmpty()
        val cell5 = row.getOrNull(5)?.toString()?.trim().orEmpty()
        val d3 = isLikelyJobSheetEventDateCell(cell3)
        val d4 = isLikelyJobSheetEventDateCell(cell4)
        val s4 = cellLooksLikeJobSheetShiftColumn(cell4)
        val s5 = cellLooksLikeJobSheetShiftColumn(cell5)
        return resolveJobSheetParseModeFromDateShiftSignals(d3, d4, s4, s5) ==
            JobSheetRowParseMode.VOLUNTEER_NAME_NINE_COL
    }

    private fun jobsSheetNeedsV2ColumnMigration(
        layout: JobsSheetLayout,
        dataRows: List<List<Any>>,
        jobTypeConfigs: List<JobTypeConfig>
    ): Boolean {
        // v2 sheets often have 8 API cells when the last column is empty — do NOT re-run
        // [legacyEightColRowToV2Row] or every row shifts right and "Volunteer Name" is duplicated into Job Type.
        if (layout == JobsSheetLayout.VOLUNTEER_NAME_NINE_COL) return false
        if (layout == JobsSheetLayout.LEGACY_EIGHT_COL) return true
        val substantial = dataRows.filter { it.size >= 7 }
        if (substantial.isEmpty()) return false
        if (substantial.all { rowContentSuggestsVolunteerNameColumnBeforeJobType(it, jobTypeConfigs) }) return false
        return true
    }

    private fun anyJobRowsUseVolunteerNameNineColLayout(
        dataRows: List<List<Any>>,
        jobTypeConfigs: List<JobTypeConfig>
    ): Boolean = dataRows.any { row ->
        row.size >= 8 && inferJobSheetRowParseMode(row, jobTypeConfigs) == JobSheetRowParseMode.VOLUNTEER_NAME_NINE_COL
    }

    suspend fun initializeSheetsService() = withContext(Dispatchers.IO) {
        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            
            // Use the uploaded service account key file
            val keyFilePath = fileManager.getServiceAccountFile()?.absolutePath
            if (keyFilePath == null) {
                throw IOException("Service account key file not found. Please upload it in Settings.")
            }
            
            println("Initializing Google Sheets service with service account...")
            
            val credentials = GoogleCredentials.fromStream(java.io.FileInputStream(keyFilePath))
                .createScoped(listOf(GoogleSheetsConfig.SCOPES))
            val requestInitializer = HttpCredentialsAdapter(credentials)
            
            sheetsService = Sheets.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("Event Manager App")
                .build()
            
            println("Google Sheets service initialized successfully")
        } catch (e: GeneralSecurityException) {
            throw IOException("Failed to initialize Google Sheets service: ${e.message}", e)
        } catch (e: Exception) {
            throw networkFailure("initialize Google Sheets service", e)
        }
    }

    // Single Guest Operations (App Priority)
    suspend fun addGuestToSheets(guest: Guest, _venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (guest.isTemporaryGuest) {
                println("Skipping addGuestToSheets for temporary guest: ${guest.name}")
                return@withContext null
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        guest.name,
                        guest.email,
                        guest.phoneNumber,
                        guest.invitations.toString(),
                        guest.venueName,
                        guest.notes,
                        if (guest.isVolunteerBenefit) "Yes" else "No",
                        guest.lastModified.toString(),
                        guest.nfcCardUid,
                        guest.nanoId,
                        if (guest.isAdmin) "Yes" else "No"
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getGuestListSheet()}!A:K",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add guest to Google Sheets - no response received")
                    }
                    
                    // Update the guest with the sheets ID (row number)
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):[A-Z]+\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added guest to Google Sheets: ${guest.name} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add guest to sheets"
            )
        } catch (e: Exception) {
            println("Failed to add guest to sheets: ${e.message}")
            throw networkFailure("add guest to Google Sheets", e)
        }
    }
    
    /**
     * Maps a venue name from Google Sheets to the appropriate Venue enum
     * @param venueName The venue name from sheets (e.g., "Groove", "Le Terreau", "Both", "All")
     * @return The corresponding Venue enum value
     */
    private fun mapVenueNameToEnum(venueName: String): Venue {
        return when (venueName.trim().uppercase()) {
            "GROOVE" -> Venue.GROOVE
            "LE_TERREAU", "LE TERREAU" -> Venue.LE_TERREAU
            "BOTH", "ALL" -> Venue.BOTH
            else -> {
                // For custom venues, map them to available enums based on position
                val hash = venueName.hashCode()
                val enumValues = listOf(Venue.GROOVE, Venue.LE_TERREAU)
                val index = kotlin.math.abs(hash) % enumValues.size
                println("DEBUG: Mapping custom venue '$venueName' to ${enumValues[index]}")
                enumValues[index]
            }
        }
    }

    suspend fun updateGuestInSheets(guest: Guest, _venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (guest.isTemporaryGuest) {
                println("Skipping updateGuestInSheets for temporary guest: ${guest.name}")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (guest.sheetsId == null) {
                throw IOException("Guest has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        guest.name,
                        guest.email,
                        guest.phoneNumber,
                        guest.invitations.toString(),
                        guest.venueName,
                        guest.notes,
                        if (guest.isVolunteerBenefit) "Yes" else "No",
                        guest.lastModified.toString(),
                        guest.nfcCardUid,
                        guest.nanoId,
                        if (guest.isAdmin) "Yes" else "No"
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = guest.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getGuestListSheet()}!A$rowNumber:K$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update guest in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated guest in Google Sheets: ${guest.name}")
                },
                operationName = "update guest in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update guest in sheets: ${e.message}")
            throw networkFailure("update guest in Google Sheets", e)
        }
    }

    // Guest List Operations
    suspend fun syncGuestsToSheets(guests: List<Guest>, _venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                clearSheetDataColumns(settingsManager.getGuestListSheet(), SHEET_LAST_COL_GUEST_LIST)
                println("🧹 Cleared guest data columns (epoch helper panel preserved)")
                
                // Only upload regular guests here; volunteer benefits and temporary guests go to their own sheets
                val values = guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }.map { guest ->
                    listOf(
                        guest.name,
                        guest.email,
                        guest.phoneNumber,
                        guest.invitations.toString(),
                        guest.venueName,
                        guest.notes,
                        "No",
                        guest.lastModified.toString(),
                        guest.nfcCardUid,
                        guest.nanoId,
                        if (guest.isAdmin) "Yes" else "No"
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(SheetsColumnContract.GUEST_LIST) + values)
                
                val guestTab = quoteSheetTabForRange(settingsManager.getGuestListSheet())
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "$guestTab!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                
                if (response == null) {
                    throw IOException("Failed to update guests in Google Sheets - no response received")
                }
                
                println("Successfully synced ${values.size} regular guests to Google Sheets")
                    try {
                        applyEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getGuestListSheet(),
                            EPOCH_COL_GUEST_LIST,
                            EPOCH_ROW_GUEST_LIST
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on guest sheet: ${e.message}")
                    }
                },
                operationName = "sync guests to sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync guests to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync guests to Google Sheets", e)
            }
        }
    }

    /**
     * Upload-only sync for the Volunteer Guest List sheet.
     * This writes the computed volunteer benefit entries to a dedicated tab.
     */
    suspend fun syncVolunteerGuestListToSheets(volunteerGuests: List<Guest>, _venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    clearSheetDataColumns(settingsManager.getVolunteerGuestListSheet(), SHEET_LAST_COL_VOLUNTEER_GUEST_DATA)
                    println("🧹 Cleared volunteer guest list data columns A:H (side panels preserved)")
                    val values = volunteerGuests.map { guest ->
                        listOf(
                            guest.name,
                            guest.lastNameAbbreviation,
                            guest.invitations.toString(),
                            guest.venueName,
                            guest.notes,
                            "Yes",
                            guest.lastModified.toString(),
                            guest.nfcCardUid
                        )
                    }
                    val valueRange = ValueRange()
                        .setValues(listOf(SheetsColumnContract.VOLUNTEER_GUEST_LIST) + values)
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val volunteerGuestTab = settingsManager.getVolunteerGuestListSheet()
                    val vgRange = "${quoteSheetTabForRange(volunteerGuestTab)}!A1"
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        spreadsheetId,
                        vgRange,
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    if (response == null) {
                        throw IOException("Failed to update volunteer guest list in Google Sheets - no response received")
                    }
                    println("Successfully synced ${values.size} volunteer guest entries to Google Sheets")
                    try {
                        applyVolunteerGuestListReadOnlyBanner(spreadsheetId, volunteerGuestTab)
                    } catch (e: Exception) {
                        println("⚠️ Volunteer guest list read-only banner (K:Q) failed (data was written): ${e.message}")
                        e.printStackTrace()
                    }
                    try {
                        applyEpochCalculatorPanel(
                            spreadsheetId,
                            volunteerGuestTab,
                            EPOCH_COL_VOLUNTEER_GUEST,
                            EPOCH_ROW_VOLUNTEER_GUEST
                        )
                    } catch (e: Exception) {
                        println("⚠️ Volunteer guest list epoch calculator: ${e.message}")
                    }
                },
                operationName = "sync volunteer guest list to sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync volunteer guest list to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync volunteer guest list to Google Sheets", e)
            }
        }
    }

    suspend fun syncGuestsFromSheets(): List<Guest> = withContext(Dispatchers.IO) {
        try {
            println("Syncing guests from sheets...")
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val spreadsheetId = settingsManager.getSpreadsheetId()
                val sheetName = settingsManager.getGuestListSheet()
                val range = "${sheetName}!A2:K"
                
                println("Reading from spreadsheet: $spreadsheetId, range: $range")
                
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    spreadsheetId,
                    range
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve guests from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} guest rows from sheets")
                
                val guests = mutableListOf<Guest>()
                val guestsToFixInSheets = mutableListOf<Pair<Int, String>>() // (rowNumber, newNanoId)
                val skippedGuestRows = mutableListOf<Pair<Int, Int>>()

                values.forEachIndexed { index, row ->
                    val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                    if (row.size >= 3 && EncryptedSheetsCodec.isEncryptedRow(row)) {
                        try {
                            val orgId = settingsManager.getFirebaseOrgId()
                            val payload = EncryptedSheetsCodec.decodeGuestPayload(row[2].toString(), orgId)
                                ?: return@forEachIndexed
                            guests.add(
                                Guest(
                                    sheetsId = rowNumber.toString(),
                                    nanoId = payload.nanoId.ifBlank { row[0].toString() },
                                    name = payload.name,
                                    email = payload.email,
                                    phoneNumber = payload.phoneNumber,
                                    invitations = payload.invitations,
                                    venueName = payload.venueName,
                                    notes = payload.notes,
                                    isVolunteerBenefit = payload.isVolunteerBenefit,
                                    lastModified = parseLastModified(row[1].toString()),
                                    nfcCardUid = payload.nfcCardUid,
                                    isAdmin = payload.isAdmin,
                                    isTemporaryGuest = payload.isTemporaryGuest,
                                    temporaryArtistName = payload.temporaryArtistName,
                                    temporaryEventDate = payload.temporaryEventDate,
                                    temporaryContactPhone = payload.temporaryContactPhone,
                                    volunteerId = payload.volunteerId,
                                    firebaseOrgId = orgId,
                                ),
                            )
                        } catch (e: Exception) {
                            println("Failed to parse encrypted guest row $rowNumber: ${e.message}")
                        }
                        return@forEachIndexed
                    }
                    if (row.size >= 10) {
                        try {
                            val rawNanoId = row[9].toString()
                            val guestName = row[0].toString()
                            val needsFix = NanoIdGenerator.needsRegeneration(rawNanoId)
                            val validNanoId = NanoIdGenerator.ensureValidNanoId(rawNanoId, guestName)
                            if (needsFix) {
                                guestsToFixInSheets.add(Pair(rowNumber, validNanoId))
                            }
                            // Column K (index 10) = Admin — may be absent on older sheets
                            val isAdmin = if (row.size >= 11) row[10].toString().equals("Yes", ignoreCase = true) else false
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = validNanoId,
                                name = guestName,
                                email = row[1].toString(),
                                phoneNumber = row[2].toString(),
                                invitations = row[3].toString().toIntOrNull() ?: 1,
                                venueName = row[4].toString(),
                                notes = row[5].toString(),
                                isVolunteerBenefit = row[6].toString().equals("Yes", ignoreCase = true),
                                lastModified = parseLastModified(row[7].toString()),
                                nfcCardUid = row[8].toString(),
                                isAdmin = isAdmin
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber: ${e.message}")
                        }
                    } else if (row.size >= 9) {
                        // Backward compatibility: no ID column yet — assign a new NanoID and queue for fix
                        try {
                            val guestName = row[0].toString()
                            val newNanoId = NanoIdGenerator.generateGuestId()
                            guestsToFixInSheets.add(Pair(rowNumber, newNanoId))
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = newNanoId,
                                name = guestName,
                                email = row[1].toString(),
                                phoneNumber = row[2].toString(),
                                invitations = row[3].toString().toIntOrNull() ?: 1,
                                venueName = row[4].toString(),
                                notes = row[5].toString(),
                                isVolunteerBenefit = row[6].toString().equals("Yes", ignoreCase = true),
                                lastModified = parseLastModified(row[7].toString()),
                                nfcCardUid = row[8].toString()
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber (no ID format): ${e.message}")
                        }
                    } else if (row.size >= 8) {
                        // Backward compatibility: no NFC UID column
                        try {
                            val guestName = row[0].toString()
                            val newNanoId = NanoIdGenerator.generateGuestId()
                            guestsToFixInSheets.add(Pair(rowNumber, newNanoId))
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = newNanoId,
                                name = guestName,
                                email = row[1].toString(),
                                phoneNumber = row[2].toString(),
                                invitations = row[3].toString().toIntOrNull() ?: 1,
                                venueName = row[4].toString(),
                                notes = row[5].toString(),
                                isVolunteerBenefit = row[6].toString().equals("Yes", ignoreCase = true),
                                lastModified = parseLastModified(row[7].toString()),
                                nfcCardUid = ""
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber (no NFC UID format): ${e.message}")
                        }
                    } else if (row.size >= 6) {
                        // Backward compatibility: old format without email and phone
                        try {
                            val guestName = row[0].toString()
                            val newNanoId = NanoIdGenerator.generateGuestId()
                            guestsToFixInSheets.add(Pair(rowNumber, newNanoId))
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = newNanoId,
                                name = guestName,
                                email = "",
                                phoneNumber = "",
                                invitations = row[1].toString().toIntOrNull() ?: 1,
                                venueName = row[2].toString(),
                                notes = row[3].toString(),
                                isVolunteerBenefit = row[4].toString().equals("Yes", ignoreCase = true),
                                lastModified = parseLastModified(row[5].toString()),
                                nfcCardUid = ""
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber (old format): ${e.message}")
                        }
                    } else {
                        skippedGuestRows.add(rowNumber to row.size)
                    }
                }
                logSkippedSheetRows("guest", skippedGuestRows)

                // Write back any missing or invalid NanoIDs to Google Sheets (column J)
                if (guestsToFixInSheets.isNotEmpty()) {
                    println("📝 Writing ${guestsToFixInSheets.size} guest NanoID(s) to Google Sheets...")
                    guestsToFixInSheets.forEach { (row, nanoId) ->
                        try {
                            val fixRange = ValueRange().setValues(listOf(listOf(nanoId)))
                            sheetsService?.spreadsheets()?.values()?.update(
                                spreadsheetId,
                                "${sheetName}!J$row:J$row",
                                fixRange
                            )?.setValueInputOption("RAW")?.execute()
                            println("✅ Set NanoID for guest row $row: $nanoId")
                        } catch (e: Exception) {
                            println("⚠️ Failed to write NanoID for guest row $row: ${e.message}")
                        }
                    }
                }
                
                println("Successfully parsed ${guests.size} guests")
                guests
                },
                operationName = "sync guests from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync guests from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync guests from Google Sheets", e)
            }
        }
    }

    // Single Volunteer Operations (App Priority)
    suspend fun addVolunteerToSheets(
        volunteer: Volunteer,
        benefitPrimaryRank: VolunteerRank? = null
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        volunteer.id, // NanoID (String) - no conversion needed
                        volunteer.name,
                        volunteer.lastNameAbbreviation,
                        volunteer.email,
                        volunteer.phoneNumber,
                        volunteer.dateOfBirth,
                        volunteer.gender?.let { gender ->
                            when (gender) {
                                Gender.FEMALE -> "Female"
                                Gender.MALE -> "Male"
                                Gender.NON_BINARY -> "Non-binary"
                                Gender.OTHER -> "Other"
                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                            }
                        } ?: "",
                        volunteerRankLabelForSheet(volunteer, benefitPrimaryRank),
                        if (volunteer.isActive) "Yes" else "No",
                        volunteer.lastModified.toString(),
                        volunteer.nfcCardUid,
                        if (volunteer.isAdmin) "Yes" else "No"
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getVolunteerSheet()}!A:L",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add volunteer to Google Sheets - no response received")
                    }
                    
                    // Update the volunteer with the sheets ID (row number)
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):[A-Z]+\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added volunteer to Google Sheets: ${volunteer.name} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add volunteer to sheets"
            )
        } catch (e: Exception) {
            println("Failed to add volunteer to sheets: ${e.message}")
            throw networkFailure("add volunteer to Google Sheets", e)
        }
    }
    
    suspend fun updateVolunteerInSheets(
        volunteer: Volunteer,
        benefitPrimaryRank: VolunteerRank? = null
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (volunteer.sheetsId == null) {
                throw IOException("Volunteer has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        volunteer.id, // NanoID (String) - no conversion needed
                        volunteer.name,
                        volunteer.lastNameAbbreviation,
                        volunteer.email,
                        volunteer.phoneNumber,
                        volunteer.dateOfBirth,
                        volunteer.gender?.let { gender ->
                            when (gender) {
                                Gender.FEMALE -> "Female"
                                Gender.MALE -> "Male"
                                Gender.NON_BINARY -> "Non-binary"
                                Gender.OTHER -> "Other"
                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                            }
                        } ?: "",
                        volunteerRankLabelForSheet(volunteer, benefitPrimaryRank),
                        if (volunteer.isActive) "Yes" else "No",
                        volunteer.lastModified.toString(),
                        volunteer.nfcCardUid,
                        if (volunteer.isAdmin) "Yes" else "No"
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = volunteer.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getVolunteerSheet()}!A$rowNumber:L$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update volunteer in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated volunteer in Google Sheets: ${volunteer.name}")
                },
                operationName = "update volunteer in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update volunteer in sheets: ${e.message}")
            throw networkFailure("update volunteer in Google Sheets", e)
        }
    }

    // Volunteer Operations
    suspend fun syncVolunteersToSheets(
        volunteers: List<Volunteer>,
        benefitPrimaryRankByVolunteerId: Map<String, VolunteerRank?> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                clearSheetDataColumns(settingsManager.getVolunteerSheet(), SHEET_LAST_COL_VOLUNTEER)
                println("🧹 Cleared volunteer data columns (epoch helper preserved)")
                
                val values = volunteers.map { volunteer ->
                    listOf(
                        volunteer.id, // NanoID (String) - no conversion needed
                        volunteer.name,
                        volunteer.lastNameAbbreviation,
                        volunteer.email,
                        volunteer.phoneNumber,
                        volunteer.dateOfBirth,
                        volunteer.gender?.let { gender ->
                            when (gender) {
                                Gender.FEMALE -> "Female"
                                Gender.MALE -> "Male"
                                Gender.NON_BINARY -> "Non-binary"
                                Gender.OTHER -> "Other"
                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                            }
                        } ?: "",
                        volunteerRankLabelForSheet(
                            volunteer,
                            benefitPrimaryRankByVolunteerId[volunteer.id]
                        ),
                        if (volunteer.isActive) "Yes" else "No",
                        volunteer.lastModified.toString(),
                        volunteer.nfcCardUid,
                        if (volunteer.isAdmin) "Yes" else "No"
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(SheetsColumnContract.VOLUNTEERS) + values)
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVolunteerSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update volunteers in Google Sheets - no response received")
                }
                
                println("Successfully synced ${volunteers.size} volunteers to Google Sheets")
                    try {
                        applyEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getVolunteerSheet(),
                            EPOCH_COL_VOLUNTEERS,
                            EPOCH_ROW_VOLUNTEERS
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on volunteers sheet: ${e.message}")
                    }
                },
                operationName = "sync volunteers to sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync volunteers to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync volunteers to Google Sheets", e)
            }
        }
    }

    suspend fun syncVolunteersFromSheets(): List<Volunteer> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVolunteerSheet()}!A2:L"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve volunteers from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} volunteer rows from sheets")
                
                val volunteers = mutableListOf<Volunteer>()
                val volunteersToFixInSheets = mutableListOf<Pair<Int, String>>() // (rowNumber, newId)
                val skippedVolunteerRows = mutableListOf<Pair<Int, Int>>()

                values.forEachIndexed { index, row ->
                    if (row.size >= 11) {
                        try {
                            val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                            // Column A now contains NanoID (String)
                            // Validate and fix invalid IDs automatically
                            val rawId = row[0].toString()
                            val volunteerName = row[1].toString()
                            val needsFix = NanoIdGenerator.needsRegeneration(rawId)
                            val validId = NanoIdGenerator.ensureValidNanoId(rawId, volunteerName)
                            
                            // If ID was fixed, mark it for update in Google Sheets
                            if (needsFix) {
                                volunteersToFixInSheets.add(Pair(rowNumber, validId))
                            }
                            
                            // Column L (index 11) = Admin — may be absent on older sheets
                            val isAdmin = if (row.size >= 12) row[11].toString().equals("Yes", ignoreCase = true) else false
                            
                            val volunteer = Volunteer(
                                id = validId, // Validated NanoID (generated if invalid)
                                sheetsId = rowNumber.toString(),
                                name = row[1].toString(),
                                lastNameAbbreviation = row[2].toString(),
                                email = row[3].toString(),
                                phoneNumber = row[4].toString(),
                                dateOfBirth = row[5].toString(),
                                gender = try {
                                    val genderString = row[6].toString()
                                    if (genderString.isBlank()) {
                                        null
                                    } else {
                                        when (genderString) {
                                            "Female" -> Gender.FEMALE
                                            "Male" -> Gender.MALE
                                            "Non-binary" -> Gender.NON_BINARY
                                            "Other" -> Gender.OTHER
                                            "Prefer not to disclose" -> Gender.PREFER_NOT_TO_DISCLOSE
                                            else -> null
                                        }
                                    }
                                } catch (_: Exception) {
                                    println("Failed to parse volunteer gender '${row[6]}' for volunteer '${row[1]}', setting to null")
                                    null
                                },
                                currentRank = try {
                                    val rankString = row[7].toString()
                                    if (rankString == "No Rank" || rankString.isBlank()) {
                                        null
                                    } else {
                                        VolunteerRank.valueOf(rankString)
                                    }
                                } catch (_: Exception) {
                                    println("Failed to parse volunteer rank '${row[7]}' for volunteer '${row[1]}', setting to null")
                                    null
                                },
                                isActive = try {
                                    row[8].toString().equals("Yes", ignoreCase = true)
                                } catch (_: Exception) {
                                    println("Failed to parse volunteer active status for volunteer '${row[1]}', setting to true")
                                    true
                                },
                                lastModified = try {
                                    parseLastModified(row[9].toString())
                                } catch (_: Exception) {
                                    println("Failed to parse volunteer last modified for volunteer '${row[1]}', setting to current time")
                                    System.currentTimeMillis()
                                },
                                nfcCardUid = row[10].toString(),
                                isAdmin = isAdmin
                            )
                            volunteers.add(volunteer)
                        } catch (e: Exception) {
                            println("Failed to parse volunteer row ${index + 2}: ${e.message}")
                            println("Row data: ${row.joinToString(", ")}")
                        }
                    } else if (row.size >= 10) {
                        try {
                            val rowNumber = index + 2
                            val rawId = row[0].toString()
                            val volunteerName = row[1].toString()
                            val needsFix = NanoIdGenerator.needsRegeneration(rawId)
                            val validId = NanoIdGenerator.ensureValidNanoId(rawId, volunteerName)
                            if (needsFix) {
                                volunteersToFixInSheets.add(Pair(rowNumber, validId))
                            }
                            val volunteer = Volunteer(
                                id = validId,
                                sheetsId = rowNumber.toString(),
                                name = row[1].toString(),
                                lastNameAbbreviation = row[2].toString(),
                                email = row[3].toString(),
                                phoneNumber = row[4].toString(),
                                dateOfBirth = row[5].toString(),
                                gender = try {
                                    val genderString = row[6].toString()
                                    if (genderString.isBlank()) null else when (genderString) {
                                        "Female" -> Gender.FEMALE
                                        "Male" -> Gender.MALE
                                        "Non-binary" -> Gender.NON_BINARY
                                        "Other" -> Gender.OTHER
                                        "Prefer not to disclose" -> Gender.PREFER_NOT_TO_DISCLOSE
                                        else -> null
                                    }
                                } catch (_: Exception) { null },
                                currentRank = try {
                                    val rankString = row[7].toString()
                                    if (rankString == "No Rank" || rankString.isBlank()) null else VolunteerRank.valueOf(rankString)
                                } catch (_: Exception) { null },
                                isActive = row[8].toString().equals("Yes", ignoreCase = true),
                                lastModified = parseLastModified(row[9].toString()),
                                nfcCardUid = ""
                            )
                            volunteers.add(volunteer)
                        } catch (e: Exception) {
                            println("Failed to parse volunteer row ${index + 2} (no NFC UID format): ${e.message}")
                        }
                    } else {
                        skippedVolunteerRows.add((index + 2) to row.size)
                    }
                }
                logSkippedSheetRows("volunteer", skippedVolunteerRows)

                // Update Google Sheets with fixed IDs immediately
                if (volunteersToFixInSheets.isNotEmpty()) {
                    println("📝 Updating ${volunteersToFixInSheets.size} volunteer(s) with fixed NanoIDs in Google Sheets...")
                    try {
                        volunteersToFixInSheets.forEach { (rowNumber, newId) ->
                            try {
                                // Update only the ID column (Column A) for the specific row
                                val valueRange = ValueRange().setValues(listOf(listOf(newId)))
                                sheetsService?.spreadsheets()?.values()?.update(
                                    settingsManager.getSpreadsheetId(),
                                    "${settingsManager.getVolunteerSheet()}!A$rowNumber:A$rowNumber",
                                    valueRange
                                )?.setValueInputOption("RAW")?.execute()
                                println("✅ Updated row $rowNumber with new NanoID: $newId")
                            } catch (e: Exception) {
                                println("⚠️ Failed to update row $rowNumber with new NanoID: ${e.message}")
                            }
                        }
                        println("✅ Successfully updated ${volunteersToFixInSheets.size} volunteer ID(s) in Google Sheets")
                    } catch (e: Exception) {
                        println("⚠️ Failed to update some volunteer IDs in Google Sheets: ${e.message}")
                        // Don't throw - we still want to return the volunteers with fixed IDs
                    }
                }
                
                println("Successfully parsed ${volunteers.size} volunteers")
                volunteers
                },
                operationName = "sync volunteers from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync volunteers from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync volunteers from Google Sheets", e)
            }
        }
    }

    // Single Job Operations (App Priority)
    suspend fun addJobToSheets(
        job: Job,
        _venues: List<VenueEntity>,
        jobTypeConfigs: List<JobTypeConfig>,
        volunteerDisplayName: String
    ): String = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            val sheetsId = ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        job.volunteerId, // NanoID (String) - no conversion needed
                        volunteerDisplayName,
                        job.jobTypeName,
                        job.venueName,
                        job.date.toString(),
                        toSheetShiftTimeValue(job, jobTypeConfigs),
                        job.notes,
                        job.lastModified.toString(),
                        formatJobBenefitFutureEntriesForSheets(job.benefitFutureEntriesRemaining, job.benefitFutureEntryInvites)
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getJobsSheet()}!A:I",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add job to Google Sheets - no response received")
                    }
                    
                    // Update the job with the sheets ID (row number)
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):[A-Z]+\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added job to Google Sheets: ${job.jobTypeName} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add job to sheets"
            )
            
            sheetsId
        } catch (e: Exception) {
            println("Failed to add job to sheets: ${e.message}")
            throw networkFailure("add job to Google Sheets", e)
        }
    }
    
    suspend fun updateJobInSheets(
        job: Job,
        _venues: List<VenueEntity>,
        jobTypeConfigs: List<JobTypeConfig>,
        volunteerDisplayName: String
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (job.sheetsId == null) {
                throw IOException("Job has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        job.volunteerId, // NanoID (String) - no conversion needed
                        volunteerDisplayName,
                        job.jobTypeName,
                        job.venueName,
                        job.date.toString(),
                        toSheetShiftTimeValue(job, jobTypeConfigs),
                        job.notes,
                        job.lastModified.toString(),
                        formatJobBenefitFutureEntriesForSheets(job.benefitFutureEntriesRemaining, job.benefitFutureEntryInvites)
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = job.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getJobsSheet()}!A$rowNumber:I$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update job in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated job in Google Sheets: ${job.jobTypeName}")
                },
                operationName = "update job in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update job in sheets: ${e.message}")
            throw networkFailure("update job in Google Sheets", e)
        }
    }

    // Job Operations
    suspend fun syncJobsToSheets(
        jobs: List<Job>,
        _venues: List<VenueEntity>,
        jobTypeConfigs: List<JobTypeConfig>,
        volunteers: List<Volunteer>
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            println("🔄 Syncing ${jobs.size} jobs to Google Sheets (OVERWRITE MODE)...")
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetDataColumns(settingsManager.getJobsSheet(), SHEET_LAST_COL_JOBS)
                println("🧹 Cleared jobs data columns (epoch helper preserved)")
                
                val values = jobs.map { job ->
                    listOf(
                        job.volunteerId, // NanoID (String) - no conversion needed
                        volunteerDisplayNameForJobSheet(job.volunteerId, volunteers),
                        job.jobTypeName, // Use the personalized job type name
                        job.venueName,
                        job.date.toString(),
                        toSheetShiftTimeValue(job, jobTypeConfigs),
                        job.notes,
                        job.lastModified.toString(),
                        formatJobBenefitFutureEntriesForSheets(job.benefitFutureEntriesRemaining, job.benefitFutureEntryInvites)
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(JOBS_SHEET_HEADERS_V2) + values)
                
                println("📤 Sending ${values.size + 1} rows (including header) to Google Sheets...")
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getJobsSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update jobs in Google Sheets - no response received")
                }
                
                println("✅ Successfully synced ${jobs.size} jobs to Google Sheets (overwrote entire sheet)")
                    try {
                        applyEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getJobsSheet(),
                            EPOCH_COL_JOBS,
                            EPOCH_ROW_JOBS
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on jobs sheet: ${e.message}")
                    }
                },
                operationName = "sync jobs to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync jobs to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync jobs to Google Sheets", e)
            }
        }
    }

    suspend fun syncJobsFromSheets(
        jobTypeConfigs: List<JobTypeConfig>,
        volunteersForJobNameColumn: List<Volunteer> = emptyList()
    ): List<Job> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val tab = quoteSheetTabForRange(settingsManager.getJobsSheet())
                val headerAndData = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "$tab!A1:I5000"
                )?.execute()?.getValues() ?: emptyList()

                if (headerAndData.isEmpty()) {
                    println("Retrieved 0 job rows from sheets (empty tab)")
                    emptyList()
                } else {
                val header = headerAndData.first().map { it.toString().trim() }
                var dataRows = headerAndData.drop(1)
                println("Retrieved ${dataRows.size} job rows from sheets")

                val layout = detectJobsSheetLayout(header, dataRows.firstOrNull(), jobTypeConfigs)
                val needsV2Migration = jobsSheetNeedsV2ColumnMigration(layout, dataRows, jobTypeConfigs)
                val anchorBefore = dataRows.firstOrNull { it.size >= 7 }
                val physicalEightColLayout = anchorBefore != null &&
                    anchorBefore.size <= 8 &&
                    layout == JobsSheetLayout.LEGACY_EIGHT_COL &&
                    !rowContentSuggestsVolunteerNameColumnBeforeJobType(anchorBefore, jobTypeConfigs)

                var volunteersResolved = volunteersForJobNameColumn
                if (needsV2Migration && volunteersResolved.isEmpty()) {
                    try {
                        volunteersResolved = syncVolunteersFromSheets()
                        println("📇 Loaded ${volunteersResolved.size} volunteer(s) from sheets for shifts name migration")
                    } catch (e: Exception) {
                        println("⚠️ Could not load volunteers for shifts name column: ${e.message}")
                    }
                }

                if (physicalEightColLayout) {
                    try {
                        migrateLegacyShiftTimeLabelsInJobsSheet(dataRows, shiftTimeColumnIndex = 4)
                    } catch (e: Exception) {
                        println("⚠️ Shift time label migration in sheets skipped: ${e.message}")
                    }
                    try {
                        migrateLegacyEntriesLeftLabelsInJobsSheet(dataRows, entriesLeftColumnIndex = 7)
                    } catch (e: Exception) {
                        println("⚠️ Entries left (Used/Yes/No) migration in sheets skipped: ${e.message}")
                    }
                }

                if (needsV2Migration) {
                    try {
                        val migratedBody = dataRows.map { row ->
                            when {
                                row.size < 7 -> row
                                rowContentSuggestsVolunteerNameColumnBeforeJobType(row, jobTypeConfigs) -> row
                                else -> legacyEightColRowToV2Row(
                                    row,
                                    volunteerDisplayNameForJobSheet(row[0].toString(), volunteersResolved)
                                )
                            }
                        }
                        val allRows = listOf(JOBS_SHEET_HEADERS_V2) + migratedBody
                        val lastRow = allRows.size
                        sheetsService?.spreadsheets()?.values()?.update(
                            settingsManager.getSpreadsheetId(),
                            "$tab!A1:I$lastRow",
                            ValueRange().setValues(allRows)
                        )?.setValueInputOption("RAW")?.execute()
                        println("✅ Migrated shifts sheet to v2 (Volunteer Name column); ${migratedBody.size} data row(s) rewritten in A1:I (row numbers preserved)")
                        dataRows = migratedBody
                    } catch (e: Exception) {
                        println("⚠️ Shifts sheet v2 column migration failed: ${e.message}")
                    }
                }

                val runV2SheetMigrators = anyJobRowsUseVolunteerNameNineColLayout(dataRows, jobTypeConfigs)

                if (runV2SheetMigrators) {
                    try {
                        migrateLegacyShiftTimeLabelsInJobsSheet(dataRows, shiftTimeColumnIndex = 5)
                    } catch (e: Exception) {
                        println("⚠️ Shift time label migration (v2 cols) skipped: ${e.message}")
                    }
                    try {
                        migrateLegacyEntriesLeftLabelsInJobsSheet(dataRows, entriesLeftColumnIndex = 8)
                    } catch (e: Exception) {
                        println("⚠️ Entries left migration (v2 cols) skipped: ${e.message}")
                    }
                }

                val skippedJobRows = mutableListOf<Pair<Int, Int>>()
                val jobs = dataRows.mapIndexedNotNull { index, row ->
                    val rowNumber = index + 2
                    if (row.size < 7) {
                        skippedJobRows.add(rowNumber to row.size)
                        return@mapIndexedNotNull null
                    }
                    val mode = inferJobSheetRowParseMode(row, jobTypeConfigs)
                    try {
                        when (mode) {
                            JobSheetRowParseMode.VOLUNTEER_NAME_NINE_COL -> {
                                if (row.size < 8) {
                                    skippedJobRows.add(rowNumber to row.size)
                                    null
                                } else {
                                    val jobTypeName = normalizeJobTypeNameFromSheets(row[2])
                                    val rawVolunteerId = row[0].toString()
                                    val validVolunteerId = NanoIdGenerator.ensureValidNanoId(rawVolunteerId, "job_$rowNumber")
                                    val entriesLeftRaw = row.getOrNull(8)?.toString()?.trim().orEmpty()
                                    val entryData = parseJobBenefitFutureEntriesFromSheets(entriesLeftRaw)
                                    Job(
                                        sheetsId = rowNumber.toString(),
                                        volunteerId = validVolunteerId,
                                        jobType = JobType.OTHER,
                                        jobTypeName = jobTypeName,
                                        venueName = normalizeJobTextCellFromSheets(row[3]),
                                        date = parseJobEventDateFromSheets(row[4].toString()),
                                        shiftTime = parseSheetShiftTimeValue(
                                            rawShiftTime = row[5].toString(),
                                            jobTypeName = jobTypeName,
                                            jobTypeConfigs = jobTypeConfigs
                                        ),
                                        benefitFutureEntriesRemaining = entryData?.remaining,
                                        benefitFutureEntryInvites = entryData?.invites,
                                        notes = normalizeJobTextCellFromSheets(row[6]),
                                        lastModified = parseLastModified(row[7].toString())
                                    )
                                }
                            }
                            JobSheetRowParseMode.LEGACY_EIGHT_COL -> {
                                val eight = padJobSheetRowToEightLegacyCells(row)
                                val jobTypeName = normalizeJobTypeNameFromSheets(eight[1])
                                val rawVolunteerId = eight[0].toString()
                                val validVolunteerId = NanoIdGenerator.ensureValidNanoId(rawVolunteerId, "job_$rowNumber")
                                val entriesLeftRaw = eight.getOrNull(7)?.toString()?.trim().orEmpty()
                                val entryData = parseJobBenefitFutureEntriesFromSheets(entriesLeftRaw)
                                Job(
                                    sheetsId = rowNumber.toString(),
                                    volunteerId = validVolunteerId,
                                    jobType = JobType.OTHER,
                                    jobTypeName = jobTypeName,
                                    venueName = normalizeJobTextCellFromSheets(eight[2]),
                                    date = parseJobEventDateFromSheets(eight[3].toString()),
                                    shiftTime = parseSheetShiftTimeValue(
                                        rawShiftTime = eight[4].toString(),
                                        jobTypeName = jobTypeName,
                                        jobTypeConfigs = jobTypeConfigs
                                    ),
                                    benefitFutureEntriesRemaining = entryData?.remaining,
                                    benefitFutureEntryInvites = entryData?.invites,
                                    notes = normalizeJobTextCellFromSheets(eight[5]),
                                    lastModified = parseLastModified(eight[6].toString())
                                )
                            }
                        }
                    } catch (e: Exception) {
                        println("Failed to parse job row $rowNumber: ${e.message}")
                        null
                    }
                }
                logSkippedSheetRows("job", skippedJobRows)

                println("Successfully parsed ${jobs.size} jobs")
                jobs
                }
                },
                operationName = "sync jobs from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync jobs from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync jobs from Google Sheets", e)
            }
        }
    }


    private fun jobTypeConfigToSheetRow(config: JobTypeConfig): List<String> = listOf(
        config.name,
        if (config.isActive) "Active" else "Inactive",
        if (config.isShiftJob) "Yes" else "No",
        if (config.isOrionJob) "Yes" else "No",
        if (config.requiresShiftTime) "Yes" else "No",
        config.benefitSystemType.name,
        config.manualRewards?.let { rewards ->
            "${rewards.durationDays}|${rewards.freeDrinks}|${rewards.barDiscountPercentage}|${rewards.freeEntry}|${rewards.invites}|${rewards.otherNotes}|${rewards.futureSingleUseEntries}|${rewards.futureSingleUseEntryInvites}|${rewards.accountCreditChf}"
        } ?: "",
        config.description,
        config.lastModified.toString(),
        config.novaJobType.name,
        config.accountCreditChf?.toString().orEmpty()
    )

    // Single Job Type Operations (App Priority)
    suspend fun addJobTypeToSheets(config: JobTypeConfig) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = jobTypeConfigToSheetRow(config)
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getJobTypesSheet()}!A:J",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add job type to Google Sheets - no response received")
                    }
                    
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):[A-Z]+\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added job type to Google Sheets: ${config.name} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add job type to sheets"
            )
        } catch (e: Exception) {
            println("Failed to add job type to sheets: ${e.message}")
            throw networkFailure("add job type to Google Sheets", e)
        }
    }
    
    suspend fun updateJobTypeInSheets(config: JobTypeConfig) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (config.sheetsId == null) {
                throw IOException("Job type has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = jobTypeConfigToSheetRow(config)
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = config.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getJobTypesSheet()}!A$rowNumber:J$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update job type in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated job type in Google Sheets: ${config.name}")
                },
                operationName = "update job type in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update job type in sheets: ${e.message}")
            throw networkFailure("update job type in Google Sheets", e)
        }
    }

    // Job Type Config Operations
    suspend fun syncJobTypeConfigsToSheets(jobTypeConfigs: List<JobTypeConfig>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            println("🔄 Syncing ${jobTypeConfigs.size} job types to Google Sheets (OVERWRITE MODE)...")
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetDataColumns(settingsManager.getJobTypesSheet(), SHEET_LAST_COL_JOB_TYPES)
                println("🧹 Cleared job types data columns (epoch helper preserved)")
                
                val values = jobTypeConfigs.map { config -> jobTypeConfigToSheetRow(config) }
                
                val valueRange = ValueRange()
                    .setValues(listOf(listOf("Name", "Status", "Shift Type", "Orion Type", "Requires Time", "Benefit System", "Manual Rewards", "Description", "Last Modified", "Nova Job Type", "Account Credit (CHF)")) + values)
                
                println("📤 Sending ${values.size + 1} rows (including header) to Google Sheets...")
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getJobTypesSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update job type configs in Google Sheets - no response received")
                }
                
                println("✅ Successfully synced ${jobTypeConfigs.size} job types to Google Sheets (overwrote entire sheet)")
                    try {
                        applyEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getJobTypesSheet(),
                            EPOCH_COL_JOB_TYPES,
                            EPOCH_ROW_JOB_TYPES
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on job types sheet: ${e.message}")
                    }
                },
                operationName = "sync job type configs to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync job type configs to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync job type configs to Google Sheets", e)
            }
        }
    }

    suspend fun syncJobTypeConfigsFromSheets(): List<JobTypeConfig> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getJobTypesSheet()}!A2:K"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve job type configs from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} job type config rows from sheets")

                val skippedJobTypeConfigRows = mutableListOf<Pair<Int, Int>>()
                val configs = values.mapIndexedNotNull { index, row ->
                    if (row.size >= 9) {
                        try {
                            // Parse benefit system type
                            val benefitSystemType = try {
                                BenefitSystemType.valueOf(row[5].toString())
                            } catch (_: Exception) {
                                BenefitSystemType.STELLAR // Default to STELLAR for backward compatibility
                            }
                            
                            val manualRewards = if (row[6].toString().isNotEmpty()) {
                                try {
                                    val parts = row[6].toString().split("|")
                                    when {
                                        parts.size >= 9 -> ManualRewards(
                                            durationDays = parts[0].toIntOrNull() ?: 1,
                                            freeDrinks = parts[1].toIntOrNull() ?: 0,
                                            barDiscountPercentage = parts[2].toIntOrNull() ?: 0,
                                            freeEntry = parts[3].toBooleanStrictOrNull() ?: false,
                                            invites = parts[4].toIntOrNull() ?: 0,
                                            otherNotes = parts[5],
                                            futureSingleUseEntries = parts[6].toIntOrNull() ?: 0,
                                            futureSingleUseEntryInvites = parts[7].toIntOrNull() ?: 1,
                                            accountCreditChf = parts[8].toDoubleOrNull() ?: 0.0
                                        )
                                        parts.size >= 8 -> ManualRewards(
                                            durationDays = parts[0].toIntOrNull() ?: 1,
                                            freeDrinks = parts[1].toIntOrNull() ?: 0,
                                            barDiscountPercentage = parts[2].toIntOrNull() ?: 0,
                                            freeEntry = parts[3].toBooleanStrictOrNull() ?: false,
                                            invites = parts[4].toIntOrNull() ?: 0,
                                            otherNotes = parts[5],
                                            futureSingleUseEntries = parts[6].toIntOrNull() ?: 0,
                                            futureSingleUseEntryInvites = parts[7].toIntOrNull() ?: 1
                                        )
                                        parts.size == 7 -> ManualRewards(
                                            durationDays = parts[0].toIntOrNull() ?: 1,
                                            freeDrinks = parts[1].toIntOrNull() ?: 0,
                                            barDiscountPercentage = parts[2].toIntOrNull() ?: 0,
                                            freeEntry = parts[3].toBooleanStrictOrNull() ?: false,
                                            invites = parts[4].toIntOrNull() ?: 0,
                                            otherNotes = parts[5],
                                            futureSingleUseEntries = parts[6].toIntOrNull() ?: 0,
                                            futureSingleUseEntryInvites = 1
                                        )
                                        parts.size == 6 -> ManualRewards(
                                            durationDays = parts[0].toIntOrNull() ?: 1,
                                            freeDrinks = parts[1].toIntOrNull() ?: 0,
                                            barDiscountPercentage = parts[2].toIntOrNull() ?: 0,
                                            freeEntry = parts[3].toBooleanStrictOrNull() ?: false,
                                            invites = parts[4].toIntOrNull() ?: 0,
                                            otherNotes = parts[5],
                                            futureSingleUseEntries = 0,
                                            futureSingleUseEntryInvites = 1
                                        )
                                        else -> null
                                    }
                                } catch (e: Exception) {
                                    println("Failed to parse manual rewards for row ${index + 2}: ${e.message}")
                                    null
                                }
                            } else null
                            
                            val novaJobType = if (row.size >= 10 && row[9].toString().isNotEmpty()) {
                                try { NovaJobType.valueOf(row[9].toString()) } catch (_: Exception) { NovaJobType.DEFAULT_SHIFT }
                            } else NovaJobType.DEFAULT_SHIFT

                            val accountCreditChf = if (row.size >= 11) {
                                row[10].toString().trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                            } else null

                            JobTypeConfig(
                                id = 0,
                                name = row[0].toString(),
                                isActive = row[1].toString().equals("Active", ignoreCase = true),
                                isShiftJob = row[2].toString().equals("Yes", ignoreCase = true),
                                isOrionJob = row[3].toString().equals("Yes", ignoreCase = true),
                                requiresShiftTime = row[4].toString().equals("Yes", ignoreCase = true),
                                novaJobType = novaJobType,
                                benefitSystemType = benefitSystemType,
                                manualRewards = manualRewards,
                                accountCreditChf = accountCreditChf,
                                description = row[7].toString(),
                                lastModified = parseLastModified(row[8].toString())
                            )
                        } catch (e: Exception) {
                            println("Failed to parse job type config row ${index + 2}: ${e.message}")
                            null
                        }
                    } else if (row.size >= 7) {
                        // Backward compatibility for old format (7 columns)
                        try {
                            JobTypeConfig(
                                id = 0, // Will be set by database
                                name = row[0].toString(),
                                isActive = row[1].toString().equals("Active", ignoreCase = true),
                                isShiftJob = row[2].toString().equals("Yes", ignoreCase = true),
                                isOrionJob = row[3].toString().equals("Yes", ignoreCase = true),
                                requiresShiftTime = row[4].toString().equals("Yes", ignoreCase = true),
                                benefitSystemType = BenefitSystemType.STELLAR, // Default for old format
                                manualRewards = null, // No manual rewards in old format
                                description = row[5].toString(),
                                lastModified = parseLastModified(row[6].toString())
                            )
                        } catch (e: Exception) {
                            println("Failed to parse job type config row ${index + 2} (old format): ${e.message}")
                            null
                        }
                    } else {
                        skippedJobTypeConfigRows.add((index + 2) to row.size)
                        null
                    }
                }
                logSkippedSheetRows("job type config", skippedJobTypeConfigRows)

                println("Successfully parsed ${configs.size} job type configs")
                configs
                },
                operationName = "sync job type configs from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync job type configs from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync job type configs from Google Sheets", e)
            }
        }
    }

    // Venue Operations
    suspend fun syncVenuesToSheets(venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            println("🔄 Syncing ${venues.size} venues to Google Sheets (OVERWRITE MODE)...")
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetDataColumns(settingsManager.getVenuesSheet(), SHEET_LAST_COL_VENUES)
                println("🧹 Cleared venues data columns (epoch helper preserved)")
                
                val values = venues.map { venue ->
                    listOf(
                        venue.name,
                        venue.description,
                        if (venue.isActive) "Active" else "Inactive",
                        venue.lastModified.toString(),
                        venue.peopleCounterCount.toString(),
                        venue.peopleCounterWriterDeviceId,
                        venue.peopleCounterLastModified.toString(),
                        venue.announcementTitle,
                        venue.announcementMessage,
                        venue.announcementSentAt.toString(),
                        venue.announcementSenderDeviceId
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(
                        listOf(
                            listOf(
                                "Name",
                                "Description",
                                "Status",
                                "Last Modified",
                                "Number of people",
                                "Priority Device ID",
                                "Last Modified (counter)",
                                "Announcement Title",
                                "Announcement Message",
                                "Announcement Sent At",
                                "Announcement Sender Device ID"
                            )
                        ) + values
                    )
                
                println("📤 Sending ${values.size + 1} rows (including header) to Google Sheets...")
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVenuesSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update venues in Google Sheets - no response received")
                }
                
                println("✅ Successfully synced ${venues.size} venues to Google Sheets (overwrote entire sheet)")
                    try {
                        applyEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getVenuesSheet(),
                            EPOCH_COL_VENUES,
                            EPOCH_ROW_VENUES
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on venues sheet: ${e.message}")
                    }
                },
                operationName = "sync venues to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync venues to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync venues to Google Sheets", e)
            }
        }
    }

    suspend fun syncVenuesFromSheets(): List<VenueEntity> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVenuesSheet()}!A2:K"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve venues from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} venue rows from sheets")

                val skippedVenueRows = mutableListOf<Pair<Int, Int>>()
                val venues = values.mapIndexedNotNull { index, row ->
                    if (row.size >= 4) {
                        try {
                            val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                            VenueEntity(
                                id = 0,
                                sheetsId = rowNumber.toString(),
                                name = row[0].toString(),
                                description = row[1].toString(),
                                isActive = row[2].toString().equals("Active", ignoreCase = true),
                                lastModified = parseLastModified(row[3].toString()),
                                peopleCounterCount = row.getOrNull(4)?.toString()?.toIntOrNull() ?: 0,
                                peopleCounterWriterDeviceId = row.getOrNull(5)?.toString()?.trim() ?: "",
                                peopleCounterLastModified = row.getOrNull(6)?.toString()?.toLongOrNull() ?: 0L,
                                announcementTitle = row.getOrNull(7)?.toString() ?: "",
                                announcementMessage = row.getOrNull(8)?.toString() ?: "",
                                announcementSentAt = row.getOrNull(9)?.toString()?.toLongOrNull() ?: 0L,
                                announcementSenderDeviceId = row.getOrNull(10)?.toString()?.trim() ?: ""
                            )
                        } catch (e: Exception) {
                            println("Failed to parse venue row ${index + 2}: ${e.message}")
                            null
                        }
                    } else {
                        skippedVenueRows.add((index + 2) to row.size)
                        null
                    }
                }
                logSkippedSheetRows("venue", skippedVenueRows)

                println("Successfully parsed ${venues.size} venues")
                venues
                },
                operationName = "sync venues from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync venues from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync venues from Google Sheets", e)
            }
        }
    }

    // Sales Sheet Item Operations
    suspend fun syncSalesSheetItemsToSheets(items: List<SalesSheetItem>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }

            println("🔄 Syncing ${items.size} sales sheet items to Google Sheets (OVERWRITE MODE)...")

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    clearSheetDataColumns(settingsManager.getSalesItemsSheet(), SHEET_LAST_COL_SALES_ITEMS)
                    println("🧹 Cleared sales items data columns (epoch helper preserved)")

                    val values = items.map { item ->
                        listOf(
                            item.name,
                            item.price.toString(),
                            if (item.hasDiscount) "Yes" else "No",
                            item.requiredRank?.name.orEmpty(),
                            if (item.isActive) "Active" else "Inactive",
                            item.lastModified.toString(),
                            item.categories,
                            item.emoji,
                            item.availableVenues,
                        )
                    }

                    val valueRange = ValueRange()
                        .setValues(
                            listOf(
                                listOf(
                                    "Name",
                                    "Price",
                                    "Discount",
                                    "Required Rank",
                                    "Status",
                                    "Last Modified",
                                    "Categories",
                                    "Emoji",
                                    "Available Venues",
                                )
                            ) + values
                        )

                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getSalesItemsSheet()}!A1",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()

                    if (response == null) {
                        throw IOException("Failed to update sales items in Google Sheets - no response received")
                    }
                    println("✅ Successfully synced ${items.size} sales items to Google Sheets")
                    try {
                        repairEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getSalesItemsSheet(),
                            SHEET_LAST_COL_SALES_ITEMS,
                            EPOCH_ROW_SALES,
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on sales items sheet: ${e.message}")
                    }
                },
                operationName = "sync sales sheet items to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync sales sheet items to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync sales sheet items to Google Sheets", e)
            }
        }
    }

    suspend fun syncSalesSheetItemsFromSheets(): List<SalesSheetItem> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val response = sheetsService?.spreadsheets()?.values()?.get(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getSalesItemsSheet()}!A2:I"
                    )?.execute()

                    if (response == null) {
                        throw IOException("Failed to retrieve sales sheet items from Google Sheets - no response received")
                    }

                    val values = response.getValues() ?: emptyList()
                    val skippedRows = mutableListOf<Pair<Int, Int>>()
                    val items = values.mapIndexedNotNull { index, row ->
                        if (row.size >= 4) {
                            try {
                                val rowNumber = index + 2
                                val requiredRankRaw = row.getOrNull(3)?.toString().orEmpty()
                                val normalizedRequiredRank = requiredRankRaw.trim()
                                val requiredRank = if (normalizedRequiredRank.isEmpty()) {
                                    null
                                } else {
                                    try {
                                        VolunteerRank.valueOf(normalizedRequiredRank)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                val discountRaw = row.getOrNull(2)?.toString().orEmpty().trim()
                                val hasDiscount = when {
                                    discountRaw.equals("yes", ignoreCase = true) -> true
                                    discountRaw.equals("no", ignoreCase = true) -> false
                                    discountRaw.toIntOrNull()?.let { it > 0 } == true -> true
                                    else -> false
                                }
                                SalesSheetItem(
                                    id = 0,
                                    sheetsId = rowNumber.toString(),
                                    name = row[0].toString(),
                                    price = row.getOrNull(1)?.toString()?.toDoubleOrNull() ?: 0.0,
                                    hasDiscount = hasDiscount,
                                    requiredRank = requiredRank,
                                    isActive = row.getOrNull(4)?.toString()?.equals("Inactive", ignoreCase = true) != true,
                                    lastModified = parseLastModified(row.getOrNull(5)?.toString().orEmpty()),
                                    categories = row.getOrNull(6)?.toString().orEmpty(),
                                    emoji = row.getOrNull(7)?.toString().orEmpty(),
                                    availableVenues = row.getOrNull(8)?.toString().orEmpty(),
                                )
                            } catch (e: Exception) {
                                println("Failed to parse sales sheet item row ${index + 2}: ${e.message}")
                                null
                            }
                        } else {
                            skippedRows.add((index + 2) to row.size)
                            null
                        }
                    }
                    logSkippedSheetRows("sales sheet item", skippedRows)
                    println("Successfully parsed ${items.size} sales sheet items")
                    items
                },
                operationName = "sync sales sheet items from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync sales sheet items from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync sales sheet items from Google Sheets", e)
            }
        }
    }

    // Account Transfer Operations
    private val TRANSFER_SHEET_HEADERS = listOf(
        "Transfer ID", "Holder Type", "Holder ID", "Holder Name", "Amount", "Currency", "Type",
        "Source Reference", "Job Reference Key", "Job Type", "Job Date", "Description",
        "Credit Paid", "Cash Paid", "Bar Discount %", "POS Items", "Created At", "Last Modified",
        "Venue",
    )

    private val INSTITUTION_SETTINGS_HEADERS = listOf("Key", "Value", "Last Modified")

    // Institution Settings Operations
    suspend fun syncInstitutionSettingsToSheets(rows: List<InstitutionSettingRow>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }

            println("🔄 Syncing ${rows.size} institution settings to Google Sheets (OVERWRITE MODE)...")

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    clearSheetDataColumns(settingsManager.getSettingsSheet(), SHEET_LAST_COL_SETTINGS)

                    val values = rows.map { row ->
                        listOf(row.key, row.value, row.lastModified.toString())
                    }

                    val valueRange = ValueRange()
                        .setValues(listOf(INSTITUTION_SETTINGS_HEADERS) + values)

                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getSettingsSheet()}!A1",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()

                    if (response == null) {
                        throw IOException("Failed to update institution settings in Google Sheets - no response received")
                    }

                    println("✅ Successfully synced ${rows.size} institution settings to Google Sheets")
                },
                operationName = "sync institution settings to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync institution settings to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync institution settings to Google Sheets", e)
            }
        }
    }

    suspend fun syncInstitutionSettingsFromSheets(): List<InstitutionSettingRow> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val response = sheetsService?.spreadsheets()?.values()?.get(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getSettingsSheet()}!A2:C"
                    )?.execute()

                    if (response == null) {
                        throw IOException("Failed to retrieve institution settings from Google Sheets - no response received")
                    }

                    val values = response.getValues() ?: emptyList()
                    val skippedRows = mutableListOf<Pair<Int, Int>>()
                    val knownKeys = InstitutionSettingsKeys.ALL.toSet()
                    val rows = values.mapIndexedNotNull { index, row ->
                        if (row.isNotEmpty()) {
                            try {
                                val key = row[0].toString().trim()
                                if (key.isEmpty() || key !in knownKeys) {
                                    null
                                } else {
                                    InstitutionSettingRow(
                                        key = key,
                                        value = row.getOrNull(1)?.toString().orEmpty(),
                                        lastModified = parseLastModified(row.getOrNull(2)?.toString().orEmpty()),
                                    )
                                }
                            } catch (e: Exception) {
                                println("Failed to parse institution setting row ${index + 2}: ${e.message}")
                                null
                            }
                        } else {
                            skippedRows.add((index + 2) to row.size)
                            null
                        }
                    }
                    logSkippedSheetRows("institution setting", skippedRows)
                    println("Successfully parsed ${rows.size} institution settings")
                    rows
                },
                operationName = "sync institution settings from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync institution settings from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync institution settings from Google Sheets", e)
            }
        }
    }

    suspend fun syncTransfersToSheets(transfers: List<AccountTransfer>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) initializeSheetsService()
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    clearSheetDataColumns(settingsManager.getTransfersSheet(), SHEET_LAST_COL_TRANSFERS)
                    val values = transfers.map { transfer ->
                        listOf(
                            transfer.transferId,
                            transfer.holderType.name,
                            transfer.holderId,
                            transfer.holderName,
                            transfer.amount.toString(),
                            transfer.currencyCode,
                            transfer.type.name,
                            transfer.sourceReference,
                            transfer.jobReferenceKey,
                            transfer.jobTypeName,
                            transfer.jobDate?.toString().orEmpty(),
                            transfer.description,
                            transfer.creditAmountPaid?.toString().orEmpty(),
                            transfer.cashAmountPaid?.toString().orEmpty(),
                            transfer.posBarDiscountPercent?.toString().orEmpty(),
                            transfer.posItemsJson,
                            transfer.createdAt.toString(),
                            transfer.lastModified.toString(),
                            transfer.posVenueName,
                        )
                    }
                    val valueRange = ValueRange().setValues(listOf(TRANSFER_SHEET_HEADERS) + values)
                    sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getTransfersSheet()}!A1",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                        ?: throw IOException("Failed to update transfers in Google Sheets")
                    try {
                        repairEpochCalculatorPanel(
                            settingsManager.getSpreadsheetId(),
                            settingsManager.getTransfersSheet(),
                            SHEET_LAST_COL_TRANSFERS,
                            EPOCH_ROW_TRANSFERS,
                        )
                    } catch (e: Exception) {
                        println("⚠️ Epoch calculator on transfers sheet: ${e.message}")
                    }
                },
                operationName = "sync transfers to sheets"
            )
        } catch (e: Exception) {
            if (e.message?.contains("429") == true) throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            throw networkFailure("sync transfers to Google Sheets", e)
        }
    }

    suspend fun syncTransfersFromSheets(): List<AccountTransfer> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) initializeSheetsService()
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val response = sheetsService?.spreadsheets()?.values()?.get(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getTransfersSheet()}!A2:S"
                    )?.execute() ?: throw IOException("Failed to retrieve transfers from Google Sheets")
                    val values = response.getValues() ?: emptyList()
                    values.mapIndexedNotNull { index, row ->
                        if (row.size < 8) return@mapIndexedNotNull null
                        try {
                            val hasVenueColumn = row.size >= 19
                            val hasNewFormat = row.size >= 18
                            val hasCashOnlyFormat = row.size == 17
                            val cashAmountPaid = when {
                                hasNewFormat || hasCashOnlyFormat ->
                                    row.getOrNull(13)?.toString()?.toDoubleOrNull()
                                else -> null
                            }
                            val posBarDiscountPercent = if (hasNewFormat) {
                                row.getOrNull(14)?.toString()?.toIntOrNull()
                            } else {
                                null
                            }
                            val posItemsIndex = when {
                                hasNewFormat -> 15
                                hasCashOnlyFormat -> 14
                                else -> 13
                            }
                            val createdAtIndex = posItemsIndex + 1
                            val lastModifiedIndex = posItemsIndex + 2
                            AccountTransfer(
                                id = 0,
                                sheetsId = (index + 2).toString(),
                                transferId = row[0].toString(),
                                holderType = AccountHolderType.valueOf(row[1].toString()),
                                holderId = row[2].toString(),
                                holderName = row[3].toString(),
                                amount = row[4].toString().toDoubleOrNull() ?: 0.0,
                                currencyCode = row.getOrNull(5)?.toString()?.ifBlank { "CHF" } ?: "CHF",
                                type = AccountTransferType.valueOf(row[6].toString()),
                                sourceReference = row[7].toString(),
                                jobReferenceKey = row.getOrNull(8)?.toString().orEmpty(),
                                jobTypeName = row.getOrNull(9)?.toString().orEmpty(),
                                jobDate = row.getOrNull(10)?.toString()?.toLongOrNull(),
                                description = row.getOrNull(11)?.toString().orEmpty(),
                                creditAmountPaid = row.getOrNull(12)?.toString()?.toDoubleOrNull(),
                                cashAmountPaid = cashAmountPaid,
                                posBarDiscountPercent = posBarDiscountPercent,
                                posItemsJson = row.getOrNull(posItemsIndex)?.toString().orEmpty(),
                                posVenueName = if (hasVenueColumn) {
                                    row.getOrNull(18)?.toString().orEmpty()
                                } else {
                                    ""
                                },
                                createdAt = row.getOrNull(createdAtIndex)?.toString()?.toLongOrNull()
                                    ?: parseLastModified(row.getOrNull(lastModifiedIndex)?.toString().orEmpty()),
                                lastModified = parseLastModified(row.getOrNull(lastModifiedIndex)?.toString().orEmpty())
                            )
                        } catch (e: Exception) {
                            println("Failed to parse transfer row ${index + 2}: ${e.message}")
                            null
                        }
                    }
                },
                operationName = "sync transfers from sheets"
            )
        } catch (e: Exception) {
            if (e.message?.contains("429") == true) throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            throw networkFailure("sync transfers from Google Sheets", e)
        }
    }

    /**
     * Reads columns E–G (people count, writer device id, counter last modified) for one venue row.
     */
    suspend fun readVenuePeopleCounterCells(sheetRow1Based: Int): Triple<Int, String, Long>? = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            val sheet = settingsManager.getVenuesSheet()
            val response = sheetsService?.spreadsheets()?.values()?.get(
                settingsManager.getSpreadsheetId(),
                "$sheet!E$sheetRow1Based:G$sheetRow1Based"
            )?.execute()
            val row = response?.getValues()?.firstOrNull() ?: return@withContext null
            val count = row.getOrNull(0)?.toString()?.toIntOrNull() ?: 0
            val writer = row.getOrNull(1)?.toString()?.trim().orEmpty()
            val mod = row.getOrNull(2)?.toString()?.toLongOrNull() ?: 0L
            Triple(count, writer, mod)
        } catch (e: Exception) {
            println("Failed to read venue people counter cells: ${e.message}")
            null
        }
    }

    /**
     * Updates only columns E–G on the venues sheet for one row (people counter).
     * Uses a single API write to limit quota usage.
     */
    suspend fun updateVenuePeopleCounterCells(
        sheetRow1Based: Int,
        peopleCount: Int,
        writerDeviceId: String,
        counterLastModifiedMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            val sheet = settingsManager.getVenuesSheet()
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val valueRange = ValueRange().setValues(
                        listOf(
                            listOf(
                                peopleCount.toString(),
                                writerDeviceId,
                                counterLastModifiedMs.toString()
                            )
                        )
                    )
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "$sheet!E$sheetRow1Based:G$sheetRow1Based",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    if (response == null) {
                        throw IOException("Failed to update venue people counter in Google Sheets - no response")
                    }
                    println("✅ Updated venue counter row $sheetRow1Based (count=$peopleCount)")
                },
                operationName = "update venue people counter cells"
            )
        } catch (e: Exception) {
            println("Failed to update venue people counter cells: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("update venue people counter in Google Sheets", e)
            }
        }
    }

    /**
     * Reads columns H–K (announcement title, message, sent-at, sender device id) for one venue row.
     */
    suspend fun readVenueAnnouncementCells(sheetRow1Based: Int): AnnouncementCells? = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            val sheet = settingsManager.getVenuesSheet()
            val response = sheetsService?.spreadsheets()?.values()?.get(
                settingsManager.getSpreadsheetId(),
                "$sheet!H$sheetRow1Based:K$sheetRow1Based"
            )?.execute()
            val row = response?.getValues()?.firstOrNull() ?: return@withContext null
            AnnouncementCells(
                title = row.getOrNull(0)?.toString() ?: "",
                message = row.getOrNull(1)?.toString() ?: "",
                sentAt = row.getOrNull(2)?.toString()?.toLongOrNull() ?: 0L,
                senderDeviceId = row.getOrNull(3)?.toString()?.trim().orEmpty()
            )
        } catch (e: Exception) {
            println("Failed to read venue announcement cells: ${e.message}")
            null
        }
    }

    data class AnnouncementCells(
        val title: String,
        val message: String,
        val sentAt: Long,
        val senderDeviceId: String
    )

    /**
     * Updates only columns H–K on the venues sheet for one row (announcement).
     */
    suspend fun updateVenueAnnouncementCells(
        sheetRow1Based: Int,
        title: String,
        message: String,
        sentAtMs: Long,
        senderDeviceId: String
    ) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            val sheet = settingsManager.getVenuesSheet()
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val valueRange = ValueRange().setValues(
                        listOf(
                            listOf(
                                title,
                                message,
                                sentAtMs.toString(),
                                senderDeviceId
                            )
                        )
                    )
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "$sheet!H$sheetRow1Based:K$sheetRow1Based",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    if (response == null) {
                        throw IOException("Failed to update venue announcement in Google Sheets - no response")
                    }
                    println("✅ Updated venue announcement row $sheetRow1Based (title=$title)")
                },
                operationName = "update venue announcement cells"
            )
        } catch (e: Exception) {
            println("Failed to update venue announcement cells: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("update venue announcement in Google Sheets", e)
            }
        }
    }

    suspend fun syncAllFromSheetsWithJobTypes(jobTypeConfigs: List<JobTypeConfig>): Triple<List<Guest>, List<Volunteer>, List<Job>> {
        val guests = syncGuestsFromSheets()
        val volunteers = syncVolunteersFromSheets()
        val jobs = syncJobsFromSheets(jobTypeConfigs, volunteers)
        return Triple(guests, volunteers, jobs)
    }
    
    // Public access methods for validators
    fun getSheetsService() = sheetsService
    
    // Deletion methods for Google Sheets
    suspend fun deleteJobFromSheets(_jobId: String, sheetsId: String?) = withContext(Dispatchers.IO) {
        try {
            if (sheetsId == null) {
                println("Cannot delete job from sheets - no sheetsId provided")
                return@withContext
            }
            
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getJobsSheet()
                    
                    // sheetsId is the row number, so use it directly
                    val actualRowNumber = sheetsId.toIntOrNull()
                    if (actualRowNumber == null) {
                        println("Invalid sheetsId format: $sheetsId (expected row number)")
                        throw IOException("Invalid sheetsId format: $sheetsId (expected row number)")
                    }
                    
                    // Get the sheet ID first
                    val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                    val sheet = spreadsheet?.sheets?.find { it.properties?.title == sheetName }
                    val sheetId = sheet?.properties?.sheetId
                    
                    if (sheetId != null) {
                        println("Deleting job from sheet: $sheetName, sheetId: $sheetId, row: $actualRowNumber")
                        
                        // Actually delete the row using batchUpdate
                        val deleteRequest = Request()
                            .setDeleteDimension(
                                DeleteDimensionRequest()
                                    .setRange(
                                        DimensionRange()
                                            .setSheetId(sheetId)
                                            .setDimension("ROWS")
                                            .setStartIndex(actualRowNumber - 1) // 0-based index
                                            .setEndIndex(actualRowNumber) // Delete one row
                                    )
                            )
                        
                        val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                            .setRequests(listOf(deleteRequest))
                        
                        val result = sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId, batchUpdateRequest)?.execute()
                        println("Delete result: ${result?.replies?.size} replies")
                        
                        println("Successfully deleted job with sheetsId $sheetsId from row $actualRowNumber")
                    } else {
                        println("Could not find sheet ID for sheet: $sheetName")
                        throw IOException("Could not find sheet ID for sheet: $sheetName")
                    }
                },
                operationName = "delete job from sheets"
            )
        } catch (e: Exception) {
            println("Failed to delete job from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("delete job from Google Sheets", e)
            }
        }
    }
    
    // ── Sheet Structure Validation & Repair ─────────────────────────────────

    private data class SheetDefinition(val name: String, val headers: List<String>)

    private fun getSheetDefinitions(): List<SheetDefinition> = listOf(
        SheetDefinition(
            settingsManager.getGuestListSheet(),
            SheetsColumnContract.GUEST_LIST
        ),
        SheetDefinition(
            settingsManager.getVolunteerGuestListSheet(),
            SheetsColumnContract.VOLUNTEER_GUEST_LIST
        ),
        SheetDefinition(
            settingsManager.getVolunteerSheet(),
            SheetsColumnContract.VOLUNTEERS
        ),
        SheetDefinition(
            settingsManager.getJobsSheet(),
            JOBS_SHEET_HEADERS_V2
        ),
        SheetDefinition(
            settingsManager.getJobTypesSheet(),
            listOf("Name", "Status", "Shift Type", "Orion Type", "Requires Time", "Benefit System", "Manual Rewards", "Description", "Last Modified", "Nova Job Type", "Account Credit (CHF)")
        ),
        SheetDefinition(
            settingsManager.getVenuesSheet(),
            listOf(
                "Name",
                "Description",
                "Status",
                "Last Modified",
                "Number of people",
                "Priority Device ID",
                "Last Modified (counter)",
                "Announcement Title",
                "Announcement Message",
                "Announcement Sent At",
                "Announcement Sender Device ID"
            )
        ),
        SheetDefinition(
            settingsManager.getTempGuestListSheet(),
            listOf(
                "Modification Date",
                "Event Date",
                "Artist/Group",
                "Artist Contact Phone",
                "Guest Name",
                "Comment",
                "ID"
            )
        ),
        SheetDefinition(
            settingsManager.getSalesItemsSheet(),
            listOf("Name", "Price", "Discount", "Required Rank", "Status", "Last Modified", "Categories", "Emoji", "Available Venues")
        ),
        SheetDefinition(
            settingsManager.getTransfersSheet(),
            TRANSFER_SHEET_HEADERS
        ),
        SheetDefinition(
            settingsManager.getSettingsSheet(),
            INSTITUTION_SETTINGS_HEADERS
        )
    )

    data class TempGuestRow(
        val rowNumber: Int,
        val modificationDate: java.time.LocalDate,
        val eventDate: java.time.LocalDate,
        val artistName: String,
        val artistContactPhone: String,
        val guestName: String,
        val comment: String,
        val nanoId: String = ""
    )

    suspend fun syncTempGuestsFromSheets(): List<TempGuestRow> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val range = "${sheetName}!A2:G"

                    val response = sheetsService?.spreadsheets()?.values()?.get(
                        spreadsheetId,
                        range
                    )?.execute()

                    if (response == null) {
                        throw IOException("Failed to retrieve temporary guests from Google Sheets - no response received")
                    }

                    val values = response.getValues() ?: emptyList()
                    println("Retrieved ${values.size} temporary guest rows from sheets")

                    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                    val tempGuestsToFixInSheets = mutableListOf<Pair<Int, String>>()
                    val skippedTempGuestRows = mutableListOf<Pair<Int, Int>>()

                    val result = values.mapIndexedNotNull { index, row ->
                        if (row.size >= 5) {
                            try {
                                val rowNumber = index + 2
                                val modificationDate = java.time.LocalDate.parse(row[0].toString().trim(), formatter)
                                val eventDate = java.time.LocalDate.parse(row[1].toString().trim(), formatter)
                                val guestName = row[4].toString()

                                // Parse NanoID from column G (index 6)
                                val rawNanoId = if (row.size > 6) row[6].toString() else ""
                                val needsFix = NanoIdGenerator.needsRegeneration(rawNanoId)
                                val validNanoId = NanoIdGenerator.ensureValidNanoId(rawNanoId, guestName)
                                if (needsFix) {
                                    tempGuestsToFixInSheets.add(Pair(rowNumber, validNanoId))
                                }

                                TempGuestRow(
                                    rowNumber = rowNumber,
                                    modificationDate = modificationDate,
                                    eventDate = eventDate,
                                    artistName = row[2].toString(),
                                    artistContactPhone = row[3].toString(),
                                    guestName = guestName,
                                    comment = if (row.size > 5) row[5].toString() else "",
                                    nanoId = validNanoId
                                )
                            } catch (e: Exception) {
                                println("Failed to parse temp guest row ${index + 2}: ${e.message}")
                                null
                            }
                        } else {
                            skippedTempGuestRows.add((index + 2) to row.size)
                            null
                        }
                    }
                    logSkippedSheetRows("temp guest", skippedTempGuestRows)

                    // Write back missing or invalid NanoIDs to column G
                    if (tempGuestsToFixInSheets.isNotEmpty()) {
                        println("📝 Writing ${tempGuestsToFixInSheets.size} temp guest NanoID(s) to Google Sheets...")
                        tempGuestsToFixInSheets.forEach { (row, nanoId) ->
                            try {
                                val fixRange = ValueRange().setValues(listOf(listOf(nanoId)))
                                sheetsService?.spreadsheets()?.values()?.update(
                                    spreadsheetId,
                                    "${sheetName}!G$row:G$row",
                                    fixRange
                                )?.setValueInputOption("RAW")?.execute()
                                println("✅ Set NanoID for temp guest row $row: $nanoId")
                            } catch (e: Exception) {
                                println("⚠️ Failed to write NanoID for temp guest row $row: ${e.message}")
                            }
                        }
                    }

                    result
                },
                operationName = "sync temp guests from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync temp guests from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("sync temp guests from Google Sheets", e)
            }
        }
    }

    suspend fun updateTemporaryGuestInSheets(guest: Guest) = withContext(Dispatchers.IO) {
        try {
            if (!guest.isTemporaryGuest) {
                println("Skipping updateTemporaryGuestInSheets for non-temporary guest: ${guest.name}")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val zone = java.time.ZoneId.of("Europe/Zurich")
                    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

                    val rowNumber = guest.sheetsId?.toIntOrNull()
                    if (rowNumber == null) {
                        throw IOException("Temporary guest has no valid sheets row ID for update: ${guest.sheetsId}")
                    }

                    val eventDate = guest.temporaryEventDate?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(formatter)
                    } ?: throw IOException("Temporary guest has no event date")

                    val today = java.time.LocalDate.now(zone).format(formatter)
                    val values = listOf(
                        today,
                        eventDate,
                        guest.temporaryArtistName,
                        guest.temporaryContactPhone,
                        guest.name,
                        guest.notes,
                        guest.nanoId
                    )

                    val valueRange = ValueRange().setValues(listOf(values))
                    sheetsService?.spreadsheets()?.values()?.update(
                        spreadsheetId,
                        "${sheetName}!A$rowNumber:G$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()

                    println("Successfully updated temporary guest in sheets at row $rowNumber: ${guest.name}")
                },
                operationName = "update temporary guest in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update temporary guest in sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("update temporary guest in Google Sheets", e)
            }
        }
    }

    /**
     * Appends one row per name to the temporary guest sheet (columns A–G), matching
     * [syncTempGuestsFromSheets] / [updateTemporaryGuestInSheets]: modification date,
     * event date (ISO), artist, contact phone, guest name, comment, NanoID.
     */
    suspend fun appendTemporaryGuestManualBatch(batch: ManualTemporaryGuestBatch) = withContext(Dispatchers.IO) {
        try {
            if (batch.guestNames.isEmpty()) {
                println("appendTemporaryGuestManualBatch: empty guestNames, skipping")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val zone = java.time.ZoneId.of("Europe/Zurich")
                    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                    val today = java.time.LocalDate.now(zone).format(formatter)
                    val eventDate = java.time.Instant.ofEpochMilli(batch.eventDateMillis)
                        .atZone(zone).toLocalDate()
                        .format(formatter)

                    val rows = batch.guestNames.map { name ->
                        listOf(
                            today,
                            eventDate,
                            batch.artistName,
                            batch.emergencyContactPhone,
                            name,
                            batch.comments,
                            NanoIdGenerator.generateGuestId()
                        )
                    }

                    val valueRange = ValueRange().setValues(rows)
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        spreadsheetId,
                        "${sheetName}!A:G",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()

                    if (response == null) {
                        throw IOException("Failed to append temporary guests to Google Sheets - no response received")
                    }
                    println("Appended ${rows.size} temporary guest row(s) to sheet $sheetName")
                },
                operationName = "append temporary guests to sheets"
            )
        } catch (e: Exception) {
            println("Failed to append temporary guests to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("append temporary guests to Google Sheets", e)
            }
        }
    }

    suspend fun deleteTemporaryGuestFromSheets(sheetsId: String?) = withContext(Dispatchers.IO) {
        try {
            if (sheetsId == null) {
                println("Cannot delete temporary guest from sheets - no sheetsId provided")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val rowNumber = sheetsId.toIntOrNull()
                        ?: throw IOException("Invalid sheetsId format for temporary guest: $sheetsId")

                    val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                    val sheetId = spreadsheet?.sheets
                        ?.find { it.properties?.title == sheetName }
                        ?.properties?.sheetId
                        ?: throw IOException("Could not find sheet ID for sheet: $sheetName")

                    val deleteRequest = Request()
                        .setDeleteDimension(
                            DeleteDimensionRequest().setRange(
                                DimensionRange()
                                    .setSheetId(sheetId)
                                    .setDimension("ROWS")
                                    .setStartIndex(rowNumber - 1)
                                    .setEndIndex(rowNumber)
                            )
                        )

                    val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                        .setRequests(listOf(deleteRequest))

                    sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId, batchUpdateRequest)?.execute()
                    println("Successfully deleted temporary guest from sheets at row $rowNumber")
                },
                operationName = "delete temporary guest from sheets"
            )
        } catch (e: Exception) {
            println("Failed to delete temporary guest from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw networkFailure("delete temporary guest from Google Sheets", e)
            }
        }
    }

    /**
     * Repositions the ms↔date helper on Sales (columns L–M) and Transfers (V–W) after schema
     * column changes (e.g. Available Venues, Venue). Clears stale panels in the sidecar zone first.
     */
    suspend fun repairSalesAndTransfersEpochPanels(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) initializeSheetsService()
            val spreadsheetId = settingsManager.getSpreadsheetId()
            if (spreadsheetId.isBlank() || spreadsheetId == "YOUR_SPREADSHEET_ID_HERE") {
                return@withContext false
            }
            repairEpochCalculatorPanel(
                spreadsheetId,
                settingsManager.getSalesItemsSheet(),
                SHEET_LAST_COL_SALES_ITEMS,
                EPOCH_ROW_SALES,
            )
            repairEpochCalculatorPanel(
                spreadsheetId,
                settingsManager.getTransfersSheet(),
                SHEET_LAST_COL_TRANSFERS,
                EPOCH_ROW_TRANSFERS,
            )
            println("✅ Sales/Transfers epoch calculator panels repaired")
            true
        } catch (e: Exception) {
            println("⚠️ repairSalesAndTransfersEpochPanels: ${e.message}")
            false
        }
    }

    /**
     * Validates every expected sheet tab exists with correct headers, repairing
     * as needed.  Row 1 is always **overwritten** when it does not match the
     * expected header — a new row is never inserted.  Inserting would shift
     * every data row down by one, breaking all sheetsId-based row references
     * stored locally and corrupting subsequent reads that start at A2.
     *
     * The comparison trims whitespace and is case-insensitive so that minor
     * formatting differences (e.g. Google Sheets "tableau" / table features)
     * do not trigger unnecessary overwrites.
     *
     * API budget: 2 calls when everything is OK (metadata + batchGet),
     * up to 3 when repairs are needed. After repairs, applies the volunteer benefit guest list
     * read-only banner (K2:Q6 + K7:Q7 timestamp) when that tab exists — migrates existing spreadsheets.
     *
     * @return true on success (with or without repairs), false on error.
     */
    suspend fun validateAndRepairSheetsStructure(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) initializeSheetsService()

            val spreadsheetId = settingsManager.getSpreadsheetId()
            if (spreadsheetId.isBlank() || spreadsheetId == "YOUR_SPREADSHEET_ID_HERE") {
                return@withContext false
            }

            // Sheets layout (tab names, header row) only changes on first-run
            // migrations or when the user edits settings. The previous code
            // spent 3-9 API calls on every sync/backup re-proving it. Serve
            // from a short-lived session cache instead; [invalidateSessionCaches]
            // clears it when the target spreadsheet/tab names change, and the
            // TTL bounds staleness for long-running processes.
            if (structureValidationCacheIsFresh(spreadsheetId)) {
                repairSalesAndTransfersEpochPanels()
                return@withContext true
            }

            val definitions = getSheetDefinitions()

            // Step 1 — get metadata (1 API call)
            val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                ?: throw IOException("Failed to get spreadsheet metadata")

            // Match configured tab names case-insensitively (Sheets titles are case-sensitive; users
            // may have "shift types" while settings say "Shift Types"). Avoid creating duplicate tabs.
            val (existing, missing) = definitions.partition { def ->
                resolveSheetTab(spreadsheet, def.name) != null
            }

            // Step 2 — create missing tabs (0-1 API call)
            if (missing.isNotEmpty()) {
                println("➕ Creating ${missing.size} missing sheet tab(s)")
                sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId,
                    BatchUpdateSpreadsheetRequest().setRequests(missing.map { def ->
                        Request().setAddSheet(AddSheetRequest().setProperties(
                            SheetProperties().setTitle(def.name)))
                    })
                )?.execute()
            }

            // Step 3 — read row 1 from existing tabs (1 API call via batchGet)
            val currentHeaders: List<Pair<SheetDefinition, List<String>>> =
                if (existing.isNotEmpty()) {
                    val ranges = existing.map { def ->
                        val actualTitle = resolveSheetTab(spreadsheet, def.name)?.title ?: def.name
                        "${quoteSheetTabForRange(actualTitle)}!A1:Z1"
                    }
                    val batchGet = sheetsService?.spreadsheets()?.values()
                        ?.batchGet(spreadsheetId)?.setRanges(ranges)?.execute()
                    existing.mapIndexed { i, def ->
                        val row = batchGet?.valueRanges?.getOrNull(i)
                            ?.getValues()?.firstOrNull()
                            ?.map { it.toString() } ?: emptyList()
                        def to row
                    }
                } else emptyList()

            // Step 4 — decide repairs (always overwrite row 1, never insert)
            val headerWrites = mutableListOf<ValueRange>()

            for (def in missing) {
                headerWrites.add(ValueRange()
                    .setRange("${quoteSheetTabForRange(def.name)}!A1")
                    .setValues(listOf(def.headers)))
            }

            for ((def, row) in currentHeaders) {
                val trimmedRow = row.map { it.trim() }
                val trimmedExpected = def.headers.map { it.trim() }

                // Fast path: already correct (case-insensitive, trimmed)
                val isExactMatch = trimmedRow.size == trimmedExpected.size &&
                    trimmedRow.zip(trimmedExpected).all { (c, e) -> c.equals(e, ignoreCase = true) }
                if (isExactMatch) continue

                val actualTitle = resolveSheetTab(spreadsheet, def.name)?.title ?: def.name

                if (row.isEmpty()) {
                    println("🔧 '$actualTitle' (${def.name}) header empty — writing expected headers")
                } else {
                    val matchCount = trimmedRow.zip(trimmedExpected).count { (c, e) ->
                        c.equals(e, ignoreCase = true)
                    }
                    println("🔧 '$actualTitle' (${def.name}) header mismatch ($matchCount/${def.headers.size} match) — overwriting row 1")
                }

                headerWrites.add(ValueRange()
                    .setRange("${quoteSheetTabForRange(actualTitle)}!A1")
                    .setValues(listOf(def.headers)))
            }

            // Step 5 — write all headers in one batch (0-1 API call)
            if (headerWrites.isNotEmpty()) {
                sheetsService?.spreadsheets()?.values()?.batchUpdate(spreadsheetId,
                    BatchUpdateValuesRequest()
                        .setValueInputOption("RAW")
                        .setData(headerWrites)
                )?.execute()
                println("✅ Repaired ${headerWrites.size} sheet header(s)")
            }

            // Step 6 — read-only banner on volunteer benefit guest list (existing spreadsheets / post-repair migration)
            try {
                val vgConfigured = settingsManager.getVolunteerGuestListSheet()
                val metaForBanner = if (missing.isNotEmpty()) {
                    sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute() ?: spreadsheet
                } else {
                    spreadsheet
                }
                if (resolveSheetTab(metaForBanner, vgConfigured) != null) {
                    applyVolunteerGuestListReadOnlyBanner(spreadsheetId, vgConfigured)
                    println("✅ Volunteer guest list read-only banner applied (structure validation)")
                }
            } catch (e: Exception) {
                println("⚠️ Volunteer guest list banner during structure validation: ${e.message}")
                e.printStackTrace()
            }

            // Step 7 — reposition epoch helpers on tabs whose data columns grew (Sales, Transfers).
            repairSalesAndTransfersEpochPanels()

            // Structure and headers are verified/repaired for this spreadsheet;
            // subsequent calls in the same session (or within the TTL) will
            // skip the 3-9 API calls above via [structureValidationCacheIsFresh].
            markStructureValidationFresh(spreadsheetId)
            true
        } catch (e: Exception) {
            println("❌ Sheet structure validation failed: ${e.message}")
            false
        }
    }

    private fun shouldMigrateLegacyEntriesLeftCell(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        if (s.equals("yes", ignoreCase = true) || s.equals("no", ignoreCase = true)) return true
        // Also migrate old "n left" (without invites) to new "n left (+X inv.)" format
        val hasInvites = s.contains("inv.", ignoreCase = true)
        if (!hasInvites && Regex("""^\d+\s*left$""", RegexOption.IGNORE_CASE).matches(s)) return true
        return false
    }

    /**
     * Rewrites legacy column H values ("Yes" / "No", or old "n left") to "n left (+1 inv.)",
     * and updates the header cell H1 to "Entries left" when any data cell was migrated.
     * Same pattern as [migrateLegacyShiftTimeLabelsInJobsSheet]: in-place batch updates only, no row deletion.
     */
    private suspend fun migrateLegacyEntriesLeftLabelsInJobsSheet(
        values: List<List<Any>>,
        entriesLeftColumnIndex: Int
    ) = withContext(Dispatchers.IO) {
        if (values.isEmpty()) return@withContext
        if (sheetsService == null) {
            initializeSheetsService()
        }
        val spreadsheetId = settingsManager.getSpreadsheetId()
        val sheetName = settingsManager.getJobsSheet()
        val colLetter = a1ColumnLetterFromIndex0(entriesLeftColumnIndex)
        val data = mutableListOf<ValueRange>()
        values.forEachIndexed { index, row ->
            if (row.size <= entriesLeftColumnIndex) return@forEachIndexed
            val raw = row[entriesLeftColumnIndex].toString()
            if (!shouldMigrateLegacyEntriesLeftCell(raw)) return@forEachIndexed
            val rowNum = index + 2
            val parsed = parseJobBenefitFutureEntriesFromSheets(raw)
            val newText = if (parsed != null) formatJobBenefitFutureEntriesForSheets(parsed.remaining, parsed.invites) else ""
            if (newText.isNotEmpty()) {
                data.add(ValueRange().setRange("$sheetName!$colLetter$rowNum").setValues(listOf(listOf(newText))))
            }
        }
        if (data.isEmpty()) return@withContext
        data.add(0, ValueRange().setRange("$sheetName!${colLetter}1").setValues(listOf(listOf("Entries left"))))
        data.chunked(100).forEach { chunk ->
            sheetsService?.spreadsheets()?.values()?.batchUpdate(
                spreadsheetId,
                BatchUpdateValuesRequest()
                    .setValueInputOption("RAW")
                    .setData(chunk)
            )?.execute()
        }
        println("✅ Migrated ${data.size - 1} job row(s) from legacy format to 'n left (+X inv.)' in Google Sheets")
    }

    /**
     * Rewrites legacy "Shift Time" cells (e.g. BEFORE_MIDNIGHT) to English labels expected by current app versions,
     * without removing rows or changing other columns.
     */
    private suspend fun migrateLegacyShiftTimeLabelsInJobsSheet(
        values: List<List<Any>>,
        shiftTimeColumnIndex: Int
    ) = withContext(Dispatchers.IO) {
        if (values.isEmpty()) return@withContext
        if (sheetsService == null) {
            initializeSheetsService()
        }
        val spreadsheetId = settingsManager.getSpreadsheetId()
        val sheetName = settingsManager.getJobsSheet()
        val colLetter = a1ColumnLetterFromIndex0(shiftTimeColumnIndex)
        val data = mutableListOf<ValueRange>()
        values.forEachIndexed { index, row ->
            if (row.size <= shiftTimeColumnIndex) return@forEachIndexed
            val raw = row[shiftTimeColumnIndex].toString()
            if (!shouldMigrateShiftTimeSheetCell(raw)) return@forEachIndexed
            val rowNum = index + 2
            val newText = parseShiftTimeFromGoogleSheets(raw).toGoogleSheetsShiftTimeValue()
            data.add(ValueRange().setRange("$sheetName!$colLetter$rowNum").setValues(listOf(listOf(newText))))
        }
        if (data.isEmpty()) return@withContext
        data.chunked(100).forEach { chunk ->
            sheetsService?.spreadsheets()?.values()?.batchUpdate(
                spreadsheetId,
                BatchUpdateValuesRequest()
                    .setValueInputOption("RAW")
                    .setData(chunk)
            )?.execute()
        }
        println("✅ Migrated ${data.size} job row(s) to English shift time labels in Google Sheets")
    }

    /**
     * Quotes a sheet tab title for A1 notation when it contains spaces or single quotes.
     */
    private fun quoteSheetTabForRange(tabTitle: String): String =
        if (tabTitle.contains(' ') || tabTitle.contains('\''))
            "'${tabTitle.replace("'", "''")}'"
        else
            tabTitle

    /**
     * Read-only notice: merged K2:Q6 (title + body) and K7:Q7 (last refresh). Data stays in A:H from row 1.
     */
    private suspend fun applyVolunteerGuestListReadOnlyBanner(
        spreadsheetId: String,
        sheetTitleConfigured: String
    ) = withContext(Dispatchers.IO) {
        if (sheetsService == null) {
            initializeSheetsService()
        }
        val service = sheetsService ?: return@withContext

        // Cosmetic banner — the content and layout are static per tab, so
        // re-applying it on every pull/upload just wastes API calls. Cache
        // per (spreadsheet, tab) per session; [invalidateSessionCaches]
        // clears it when settings change.
        val cacheKey = volunteerGuestBannerCacheKey(spreadsheetId, sheetTitleConfigured)
        if (volunteerGuestBannerAppliedTabs.contains(cacheKey)) {
            return@withContext
        }
        val ss = service.spreadsheets().get(spreadsheetId).execute()
        val resolved = resolveSheetTab(ss, sheetTitleConfigured)
            ?: run {
                val available = ss.sheets?.mapNotNull { it.properties?.title }?.joinToString(", ") ?: "(none)"
                println("⚠️ Volunteer guest list tab not found for '${sheetTitleConfigured.trim()}'. Tabs: $available")
                return@withContext
            }
        val sheetId = resolved.sheetId
        val tabQuoted = quoteSheetTabForRange(resolved.title)

        val colK = 10
        val colAfterQ = 17

        val unmerge = Request().setUnmergeCells(
            UnmergeCellsRequest().setRange(
                GridRange()
                    .setSheetId(sheetId)
                    .setStartRowIndex(0)
                    .setEndRowIndex(30)
                    .setStartColumnIndex(colK)
                    .setEndColumnIndex(20)
            )
        )
        try {
            service.spreadsheets().batchUpdate(
                spreadsheetId,
                BatchUpdateSpreadsheetRequest().setRequests(listOf(unmerge))
            ).execute()
        } catch (e: Exception) {
            println("⚠️ Unmerge before volunteer guest banner (non-fatal): ${e.message}")
        }

        val language = settingsManager.getLanguage()
        val appLocale = localeFromAppLanguageSetting()
        val title = SheetsLocalizedStrings.volunteerGuestBannerTitle(language).trim()
        val line2 = SheetsLocalizedStrings.volunteerGuestBannerLine(language, 2).trim()
        val line3 = SheetsLocalizedStrings.volunteerGuestBannerLine(language, 3).trim()
        val line4 = SheetsLocalizedStrings.volunteerGuestBannerLine(language, 4).trim()
        val line5 = SheetsLocalizedStrings.volunteerGuestBannerLine(language, 5).trim()
        val bodyLines = listOf(line2, line3, line4, line5).filter { it.isNotEmpty() }
        val bodyText = bodyLines.joinToString("\n\n")
        val warningText = if (bodyText.isEmpty()) title else "$title\n\n$bodyText"

        val zoned = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
        val stamp = zoned.format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(appLocale)
        )
        val stampText = SheetsLocalizedStrings.volunteerGuestLastUpdated(language, stamp)

        service.spreadsheets().values().batchUpdate(
            spreadsheetId,
            BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setData(
                    listOf(
                        ValueRange().setRange("$tabQuoted!K2").setValues(listOf(listOf(warningText))),
                        ValueRange().setRange("$tabQuoted!K7").setValues(listOf(listOf(stampText)))
                    )
                )
        ).execute()

        val mergeWarning = Request().setMergeCells(
            MergeCellsRequest()
                .setMergeType("MERGE_ALL")
                .setRange(
                    GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(1)
                        .setEndRowIndex(6)
                        .setStartColumnIndex(colK)
                        .setEndColumnIndex(colAfterQ)
                )
        )
        val mergeStamp = Request().setMergeCells(
            MergeCellsRequest()
                .setMergeType("MERGE_ALL")
                .setRange(
                    GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(6)
                        .setEndRowIndex(7)
                        .setStartColumnIndex(colK)
                        .setEndColumnIndex(colAfterQ)
                )
        )
        val orange = Color().setRed(1f).setGreen(0.596f).setBlue(0.08f)
        val darkText = Color().setRed(0.13f).setGreen(0.13f).setBlue(0.13f)
        val stampBg = Color().setRed(0.93f).setGreen(0.94f).setBlue(0.95f)

        val formatWarning = Request().setRepeatCell(
            RepeatCellRequest()
                .setRange(
                    GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(1)
                        .setEndRowIndex(6)
                        .setStartColumnIndex(colK)
                        .setEndColumnIndex(colAfterQ)
                )
                .setCell(
                    CellData().setUserEnteredFormat(
                        CellFormat()
                            .setBackgroundColor(orange)
                            .setWrapStrategy("WRAP")
                            .setVerticalAlignment("TOP")
                            .setHorizontalAlignment("LEFT")
                            .setTextFormat(
                                TextFormat()
                                    .setForegroundColor(darkText)
                                    .setBold(true)
                                    .setFontSize(10)
                            )
                    )
                )
                .setFields(
                    "userEnteredFormat.backgroundColor,userEnteredFormat.wrapStrategy," +
                        "userEnteredFormat.verticalAlignment,userEnteredFormat.horizontalAlignment," +
                        "userEnteredFormat.textFormat"
                )
        )
        val formatStamp = Request().setRepeatCell(
            RepeatCellRequest()
                .setRange(
                    GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(6)
                        .setEndRowIndex(7)
                        .setStartColumnIndex(colK)
                        .setEndColumnIndex(colAfterQ)
                )
                .setCell(
                    CellData().setUserEnteredFormat(
                        CellFormat()
                            .setBackgroundColor(stampBg)
                            .setHorizontalAlignment("CENTER")
                            .setVerticalAlignment("MIDDLE")
                            .setWrapStrategy("WRAP")
                            .setTextFormat(
                                TextFormat()
                                    .setForegroundColor(darkText)
                                    .setBold(false)
                                    .setFontSize(10)
                            )
                    )
                )
                .setFields(
                    "userEnteredFormat.backgroundColor,userEnteredFormat.horizontalAlignment," +
                        "userEnteredFormat.verticalAlignment,userEnteredFormat.wrapStrategy," +
                        "userEnteredFormat.textFormat"
                )
        )

        service.spreadsheets().batchUpdate(
            spreadsheetId,
            BatchUpdateSpreadsheetRequest()
                .setRequests(listOf(mergeWarning, mergeStamp, formatWarning, formatStamp))
        ).execute()

        // Banner applied successfully; remember it for the rest of this
        // process' lifetime so subsequent syncs skip the ~5 API calls above.
        volunteerGuestBannerAppliedTabs.add(cacheKey)
    }

    /**
     * Clear a specific range in a Google Sheet to prevent duplicate data
     */
    private suspend fun clearSheetRange(range: String) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            val clearRequest = ClearValuesRequest()
            val response = sheetsService?.spreadsheets()?.values()?.clear(
                settingsManager.getSpreadsheetId(),
                range,
                clearRequest
            )?.execute()
            
            if (response == null) {
                throw IOException("Failed to clear sheet range $range - no response received")
            }
            
            println("✅ Cleared sheet range: $range")
        } catch (e: Exception) {
            println("❌ Failed to clear sheet range $range: ${e.message}")
            // Don't throw here - clearing is best effort, we can still proceed with upload
        }
    }
}