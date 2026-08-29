package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.file_uploaded_successfully
import com.eventmanager.app.resources.migration_service_account_missing
import com.eventmanager.app.resources.migration_service_account_ok
import com.eventmanager.app.resources.migration_spreadsheet_id_label
import com.eventmanager.app.resources.setup_json_key_description
import com.eventmanager.app.ui.platform.ServiceAccountKeyUploadButton
import org.jetbrains.compose.resources.stringResource

/**
 * Spreadsheet ID + service account JSON upload for Sheets migration / follow flows.
 */
@Composable
fun SheetsMigrationCredentialsSection(
    spreadsheetId: String,
    onSpreadsheetIdChange: (String) -> Unit,
    settingsManager: SettingsManager,
    platformContext: PlatformContext?,
    uploadStatus: String?,
    onUploadStatus: (String) -> Unit,
    onConfiguredChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var configuredTick by remember { mutableIntStateOf(0) }
    val isConfigured = remember(configuredTick) { settingsManager.isConfigured() }
    val uploadedSuccess = stringResource(Res.string.file_uploaded_successfully)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = spreadsheetId,
            onValueChange = onSpreadsheetIdChange,
            label = { Text(stringResource(Res.string.migration_spreadsheet_id_label)) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (platformContext != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(Res.string.setup_json_key_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ServiceAccountKeyUploadButton(
                platformContext = platformContext,
                onStatusUpdate = { message ->
                    onUploadStatus(message)
                    if (message == uploadedSuccess) {
                        configuredTick++
                        onConfiguredChanged(settingsManager.isConfigured())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            if (isConfigured) {
                stringResource(Res.string.migration_service_account_ok)
            } else {
                stringResource(Res.string.migration_service_account_missing)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isConfigured) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        uploadStatus?.let { status ->
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
