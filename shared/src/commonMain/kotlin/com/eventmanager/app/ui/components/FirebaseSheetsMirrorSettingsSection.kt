package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.firebase_section_hide
import com.eventmanager.app.resources.firebase_section_show
import com.eventmanager.app.resources.migration_service_account_missing
import com.eventmanager.app.resources.migration_service_account_ok
import com.eventmanager.app.resources.save_sheets_config
import com.eventmanager.app.resources.setup_json_key_description
import com.eventmanager.app.resources.sheets_mirror_configure_drawer
import com.eventmanager.app.resources.sheets_mirror_enabled_label
import com.eventmanager.app.resources.sheets_mirror_export_now
import com.eventmanager.app.resources.sheets_mirror_interval_hint
import com.eventmanager.app.resources.sheets_mirror_interval_label
import com.eventmanager.app.resources.sheets_mirror_last_export
import com.eventmanager.app.resources.sheets_mirror_section_body
import com.eventmanager.app.resources.sheets_mirror_section_title
import com.eventmanager.app.resources.sheets_mirror_spreadsheet_id_label
import com.eventmanager.app.resources.guest_list_sheet_label
import com.eventmanager.app.resources.volunteer_sheet_label
import com.eventmanager.app.resources.shifts_sheet_label
import com.eventmanager.app.resources.shift_types_sheet_label
import com.eventmanager.app.resources.venues_sheet_label
import com.eventmanager.app.resources.sales_items_sheet_label
import com.eventmanager.app.resources.transfers_sheet_label
import com.eventmanager.app.resources.temp_guest_list_sheet_label
import com.eventmanager.app.resources.settings_sheet_label
import com.eventmanager.app.ui.platform.ServiceAccountKeyUploadButton
import org.jetbrains.compose.resources.stringResource

/**
 * Firebase mode — optional one-way Google Sheets mirror with compact Sheets sync settings in a drawer.
 */
@Composable
fun FirebaseSheetsMirrorSettingsSection(
    settingsManager: SettingsManager,
    platformContext: PlatformContext?,
    onMirrorExport: suspend () -> Unit,
    onMirrorSettingsChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var mirrorEnabled by remember { mutableStateOf(settingsManager.isSheetsMirrorEnabled()) }
    var mirrorSpreadsheetId by remember { mutableStateOf(settingsManager.getSheetsMirrorSpreadsheetId()) }
    var mirrorInterval by remember {
        mutableStateOf(settingsManager.getSheetsMirrorIntervalMinutes().toString())
    }
    var guestListSheet by remember { mutableStateOf(settingsManager.getGuestListSheet()) }
    var volunteerSheet by remember { mutableStateOf(settingsManager.getVolunteerSheet()) }
    var jobsSheet by remember { mutableStateOf(settingsManager.getJobsSheet()) }
    var jobTypesSheet by remember { mutableStateOf(settingsManager.getJobTypesSheet()) }
    var venuesSheet by remember { mutableStateOf(settingsManager.getVenuesSheet()) }
    var salesItemsSheet by remember { mutableStateOf(settingsManager.getSalesItemsSheet()) }
    var transfersSheet by remember { mutableStateOf(settingsManager.getTransfersSheet()) }
    var tempGuestListSheet by remember { mutableStateOf(settingsManager.getTempGuestListSheet()) }
    var settingsSheet by remember { mutableStateOf(settingsManager.getSettingsSheet()) }
    var drawerExpanded by remember { mutableStateOf(mirrorEnabled) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var configuredTick by remember { mutableIntStateOf(0) }
    val serviceAccountReady = remember(configuredTick) { settingsManager.isConfigured() }
    val lastExportAt = remember(mirrorEnabled, configuredTick) { settingsManager.getSheetsMirrorLastExportAt() }
    val exportScope = rememberCoroutineScope()
    val compactLabel = MaterialTheme.typography.labelMedium
    val compactBody = MaterialTheme.typography.bodySmall

    fun persistMirrorToggle(enabled: Boolean) {
        mirrorEnabled = enabled
        settingsManager.setSheetsMirrorEnabled(enabled)
        if (enabled) drawerExpanded = true
        onMirrorSettingsChanged()
    }

    fun saveMirrorSheetsConfig() {
        settingsManager.setSheetsMirrorSpreadsheetId(mirrorSpreadsheetId.trim())
        mirrorInterval.trim().toIntOrNull()?.let { minutes ->
            settingsManager.setSheetsMirrorIntervalMinutes(minutes)
        }
        settingsManager.saveGuestListSheet(guestListSheet.trim())
        settingsManager.saveVolunteerSheet(volunteerSheet.trim())
        settingsManager.saveJobsSheet(jobsSheet.trim())
        settingsManager.saveJobTypesSheet(jobTypesSheet.trim())
        settingsManager.saveVenuesSheet(venuesSheet.trim())
        settingsManager.saveSalesItemsSheet(salesItemsSheet.trim())
        settingsManager.saveTransfersSheet(transfersSheet.trim())
        settingsManager.saveTempGuestListSheet(tempGuestListSheet.trim())
        settingsManager.saveSettingsSheet(settingsSheet.trim())
        configuredTick++
        onMirrorSettingsChanged()
    }

    GuidedStepCard(
        title = stringResource(Res.string.sheets_mirror_section_title),
        body = stringResource(Res.string.sheets_mirror_section_body),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.sheets_mirror_enabled_label),
                style = compactBody,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = mirrorEnabled,
                onCheckedChange = { persistMirrorToggle(it) },
            )
        }

        if (mirrorEnabled) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            TextButton(onClick = { drawerExpanded = !drawerExpanded }) {
                Text(
                    if (drawerExpanded) {
                        stringResource(Res.string.firebase_section_hide)
                    } else {
                        stringResource(Res.string.sheets_mirror_configure_drawer)
                    },
                    style = compactBody,
                )
            }

            if (drawerExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.sheets_mirror_configure_drawer),
                        style = compactLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    MirrorCompactField(
                        value = mirrorSpreadsheetId,
                        onValueChange = { mirrorSpreadsheetId = it },
                        label = stringResource(Res.string.sheets_mirror_spreadsheet_id_label),
                    )
                    MirrorCompactField(
                        value = mirrorInterval,
                        onValueChange = { mirrorInterval = it },
                        label = stringResource(Res.string.sheets_mirror_interval_label),
                        supporting = stringResource(Res.string.sheets_mirror_interval_hint),
                    )

                    if (platformContext != null) {
                        Text(
                            stringResource(Res.string.setup_json_key_description),
                            style = compactBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ServiceAccountKeyUploadButton(
                            platformContext = platformContext,
                            onStatusUpdate = { message ->
                                uploadStatus = message
                                configuredTick++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        if (serviceAccountReady) {
                            stringResource(Res.string.migration_service_account_ok)
                        } else {
                            stringResource(Res.string.migration_service_account_missing)
                        },
                        style = compactBody,
                        color = if (serviceAccountReady) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )

                    uploadStatus?.let { status ->
                        Text(status, style = compactBody, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    MirrorCompactField(
                        value = guestListSheet,
                        onValueChange = { guestListSheet = it },
                        label = stringResource(Res.string.guest_list_sheet_label),
                    )
                    MirrorCompactField(
                        value = volunteerSheet,
                        onValueChange = { volunteerSheet = it },
                        label = stringResource(Res.string.volunteer_sheet_label),
                    )
                    MirrorCompactField(
                        value = jobsSheet,
                        onValueChange = { jobsSheet = it },
                        label = stringResource(Res.string.shifts_sheet_label),
                    )
                    MirrorCompactField(
                        value = jobTypesSheet,
                        onValueChange = { jobTypesSheet = it },
                        label = stringResource(Res.string.shift_types_sheet_label),
                    )
                    MirrorCompactField(
                        value = venuesSheet,
                        onValueChange = { venuesSheet = it },
                        label = stringResource(Res.string.venues_sheet_label),
                    )
                    MirrorCompactField(
                        value = salesItemsSheet,
                        onValueChange = { salesItemsSheet = it },
                        label = stringResource(Res.string.sales_items_sheet_label),
                    )
                    MirrorCompactField(
                        value = transfersSheet,
                        onValueChange = { transfersSheet = it },
                        label = stringResource(Res.string.transfers_sheet_label),
                    )
                    MirrorCompactField(
                        value = tempGuestListSheet,
                        onValueChange = { tempGuestListSheet = it },
                        label = stringResource(Res.string.temp_guest_list_sheet_label),
                    )
                    MirrorCompactField(
                        value = settingsSheet,
                        onValueChange = { settingsSheet = it },
                        label = stringResource(Res.string.settings_sheet_label),
                    )

                    OutlinedButton(
                        onClick = { saveMirrorSheetsConfig() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(Res.string.save_sheets_config),
                            style = compactBody,
                        )
                    }

                    if (lastExportAt > 0L) {
                        val formatted = platformContext?.let {
                            DateFormatUtils.formatDateTime(lastExportAt, it)
                        } ?: lastExportAt.toString()
                        Text(
                            stringResource(
                                Res.string.sheets_mirror_last_export,
                                formatted,
                            ),
                            style = compactBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            exportScope.launch {
                                onMirrorExport()
                                configuredTick++
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = serviceAccountReady && mirrorSpreadsheetId.isNotBlank(),
                    ) {
                        Text(
                            stringResource(Res.string.sheets_mirror_export_now),
                            style = compactBody,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MirrorCompactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        supportingText = supporting?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
    )
}
