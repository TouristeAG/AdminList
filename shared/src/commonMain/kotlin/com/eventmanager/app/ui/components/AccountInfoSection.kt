package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.utils.formatMoney
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountInfoSection(
    balance: Double,
    currencyCode: String,
    recentTransfers: List<AccountTransfer>,
    onManualAdjust: (amount: Double, note: String) -> Unit,
    viewModel: EventManagerViewModel,
    allowAdjustment: Boolean = true,
    compactAdjust: Boolean = false,
    modifier: Modifier = Modifier
) {
    val platformContext = LocalPlatformContext.current
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showAdminVerify by remember { mutableStateOf(false) }
    var pendingAmount by remember { mutableStateOf(0.0) }
    var pendingNote by remember { mutableStateOf("") }
    var isCredit by remember { mutableStateOf(true) }
    val defaultNote = stringResource(Res.string.manual_adjustment_default_note)
    val iconSize = if (compactAdjust) 18.dp else 22.dp
    var showAllTransfers by remember(recentTransfers) { mutableStateOf(false) }
    val sortedTransfers = remember(recentTransfers) {
        recentTransfers.sortedByDescending { it.createdAt }
    }
    val visibleTransfers = if (showAllTransfers) sortedTransfers else sortedTransfers.take(5)
    val hasMoreTransfers = sortedTransfers.size > 5

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.account_amount_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatMoney(balance, currencyCode),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (allowAdjustment) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = {
                                isCredit = true
                                showAdjustDialog = true
                            },
                            modifier = Modifier.size(if (compactAdjust) 36.dp else 40.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(Res.string.account_credit_tooltip),
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        IconButton(
                            onClick = {
                                isCredit = false
                                showAdjustDialog = true
                            },
                            modifier = Modifier.size(if (compactAdjust) 36.dp else 40.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource(Res.string.account_debit_tooltip),
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
            }
            if (sortedTransfers.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.recent_transfers_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                visibleTransfers.forEach { transfer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = transfer.description.ifBlank { transfer.type.name },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatMoney(transfer.amount, transfer.currencyCode),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (hasMoreTransfers) {
                    TextButton(
                        onClick = { showAllTransfers = !showAllTransfers },
                    ) {
                        Text(
                            if (showAllTransfers) {
                                stringResource(Res.string.show_fewer_transfers)
                            } else {
                                stringResource(Res.string.show_all_transfers)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdjustDialog) {
        var amountText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text(stringResource(Res.string.adjust_account_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isCredit,
                            onClick = { isCredit = true },
                            label = { Text(stringResource(Res.string.account_add_money)) }
                        )
                        FilterChip(
                            selected = !isCredit,
                            onClick = { isCredit = false },
                            label = { Text(stringResource(Res.string.account_remove_money)) }
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(stringResource(Res.string.amount_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(Res.string.note_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@TextButton
                    if (amount <= 0) return@TextButton
                    pendingAmount = if (isCredit) amount else -amount
                    pendingNote = noteText.ifBlank { defaultNote }
                    showAdjustDialog = false
                    showAdminVerify = true
                }) {
                    Text(stringResource(Res.string.continue_label))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (showAdminVerify) {
        BiometricAdminVerificationDialog(
            platformContext = platformContext,
            viewModel = viewModel,
            onVerified = {
                onManualAdjust(pendingAmount, pendingNote)
                showAdminVerify = false
            },
            onDismiss = { showAdminVerify = false }
        )
    }
}
