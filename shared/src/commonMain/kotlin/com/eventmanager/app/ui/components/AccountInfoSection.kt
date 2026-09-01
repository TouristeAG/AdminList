package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.data.models.AccountTransfer
import com.eventmanager.app.data.utils.formatMoney
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import org.jetbrains.compose.resources.stringResource

private val QuickAdjustAmounts = listOf(5, 10, 20, 50)

private fun parseAdjustAmount(raw: String): Double? {
    val normalized = raw.trim().replace(',', '.')
    if (normalized.isBlank()) return null
    return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
}

private fun currencyPrefix(currencyCode: String): String = when (currencyCode.uppercase()) {
    "EUR" -> "€"
    "USD" -> "$"
    "CHF" -> "CHF"
    else -> currencyCode.uppercase()
}

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
                        color = if (balance < 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
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
        ManualAccountAdjustDialog(
            isCredit = isCredit,
            balance = balance,
            currencyCode = currencyCode,
            defaultNote = defaultNote,
            onDismiss = { showAdjustDialog = false },
            onContinue = { amount, note ->
                pendingAmount = amount
                pendingNote = note
                showAdjustDialog = false
                showAdminVerify = true
            },
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

@Composable
private fun ManualAccountAdjustDialog(
    isCredit: Boolean,
    balance: Double,
    currencyCode: String,
    defaultNote: String,
    onDismiss: () -> Unit,
    onContinue: (signedAmount: Double, note: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedQuickAmount by remember { mutableStateOf<Int?>(null) }

    val parsedAmount = remember(amountText) { parseAdjustAmount(amountText) }
    val signedAmount = parsedAmount?.let { if (isCredit) it else -it }
    val newBalance = signedAmount?.let { balance + it }
    val accentColor = if (isCredit) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val title = if (isCredit) {
        stringResource(Res.string.account_add_money)
    } else {
        stringResource(Res.string.account_remove_money)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Card) { maxDialogWidth, _ ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = maxDialogWidth.coerceAtMost(400.dp))
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isCredit) Icons.Default.Add else Icons.Default.Remove,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = formatMoney(balance, currencyCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickAdjustAmounts.forEach { quickAmount ->
                            val selected = selectedQuickAmount == quickAmount
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedQuickAmount = quickAmount
                                    amountText = quickAmount.toString()
                                },
                                label = {
                                    Text(
                                        text = quickAmount.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.14f),
                                    selectedLabelColor = accentColor,
                                ),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { value ->
                            selectedQuickAmount = null
                            amountText = value.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        prefix = {
                            Text(
                                text = currencyPrefix(currencyCode),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        placeholder = {
                            Text(
                                text = "0.00",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )

                    if (parsedAmount != null && newBalance != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = formatMoney(balance, currencyCode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "  →  ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = formatMoney(newBalance, currencyCode),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(Res.string.notes_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Button(
                            onClick = {
                                val amount = signedAmount ?: return@Button
                                onContinue(amount, noteText.ifBlank { defaultNote })
                            },
                            enabled = parsedAmount != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text(stringResource(Res.string.continue_label))
                        }
                    }
                }
            }
        }
    }
}
