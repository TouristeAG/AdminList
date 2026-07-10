package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.data.reports.PosAccountingReport
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.isDesktop
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.close
import com.eventmanager.app.resources.file_name
import com.eventmanager.app.resources.open_with
import com.eventmanager.app.resources.pos_report_file_size
import com.eventmanager.app.resources.pos_report_preview_period
import com.eventmanager.app.resources.pos_report_preview_summary
import com.eventmanager.app.resources.pos_report_preview_title
import com.eventmanager.app.resources.pos_report_save_as
import com.eventmanager.app.resources.pos_report_share
import com.eventmanager.app.ui.utils.isTablet
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun PosReportPreviewDialog(
    file: File,
    report: PosAccountingReport,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: (() -> Unit)?,
) {
    val showSave = onSaveAs != null
    val useFixedHeight = LocalPlatformContext.current.isDesktop || isTablet()

    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Preview) { maxDialogWidth, maxDialogHeight ->
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .then(
                        if (useFixedHeight) Modifier.heightIn(min = 0.dp, max = maxDialogHeight)
                        else Modifier.heightIn(max = maxDialogHeight)
                    )
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                PreviewDialogBody(
                    file = file,
                    report = report,
                    showSave = showSave,
                    onDismiss = onDismiss,
                    onShare = onShare,
                    onOpen = onOpen,
                    onSaveAs = onSaveAs,
                    fillHeight = useFixedHeight,
                )
            }
        }
    }
}

@Composable
private fun PreviewDialogBody(
    file: File,
    report: PosAccountingReport,
    showSave: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: (() -> Unit)?,
    fillHeight: Boolean,
) {
    Column(
        modifier = Modifier
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.pos_report_preview_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
            }
        }
        Column(
            modifier = Modifier
                .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(
                            Res.string.pos_report_preview_summary,
                            report.totalPosSalesCount,
                            formatMoney(report.totalCashCollected, report.currencyCode),
                            report.totalTransferCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InfoRow(stringResource(Res.string.file_name), file.name)
            InfoRow(
                stringResource(Res.string.pos_report_file_size),
                formatReportFileSize(file.length()),
            )
            InfoRow(
                stringResource(Res.string.pos_report_preview_period),
                report.period.label,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.pos_report_share))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.open_with))
        }
        if (showSave && onSaveAs != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSaveAs,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.pos_report_save_as))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatReportFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
    else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
}

private fun formatMoney(value: Double, currency: String): String =
    String.format("%.2f %s", value, currency)
