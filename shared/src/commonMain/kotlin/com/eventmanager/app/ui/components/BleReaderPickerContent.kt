package com.eventmanager.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
data class BleReaderPickerItem(
    val pickId: String,
    val displayName: String,
    val mac: String,
    val rssi: Int,
    val bonded: Boolean,
    val isAcrReader: Boolean,
    val pcscReady: Boolean = false,
)

@Composable
fun BleReaderPickerSheet(
    scanning: Boolean,
    errorContent: @Composable (() -> Unit)?,
    acrReaders: List<BleReaderPickerItem>,
    otherReaders: List<BleReaderPickerItem>,
    onPick: (BleReaderPickerItem) -> Unit,
    onDismiss: () -> Unit,
    topContent: @Composable () -> Unit = {},
) {
    var showOtherReaders by remember { mutableStateOf(false) }
    var otherSearch by remember { mutableStateOf("") }

    val filteredOthers = remember(otherReaders, otherSearch) {
        val query = otherSearch.trim()
        val sorted = otherReaders.sortedByDescending { it.rssi }
        val filtered = if (query.isEmpty()) {
            sorted
        } else {
            sorted.filter {
                it.displayName.contains(query, ignoreCase = true) ||
                    it.mac.contains(query, ignoreCase = true)
            }
        }
        filtered.take(80)
    }
    val otherTotal = otherReaders.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Card) { maxDialogWidth, maxDialogHeight ->
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = if (scanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp).size(28.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.ble_reader_picker_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(Res.string.ble_reader_picker_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (scanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                topContent()

                if (errorContent != null) {
                    errorContent()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "acr_header") {
                            BleReaderSectionHeader(
                                title = stringResource(Res.string.ble_reader_section_acr_title),
                                subtitle = stringResource(Res.string.ble_reader_section_acr_hint),
                                count = acrReaders.size,
                                highlighted = true,
                            )
                        }
                        if (acrReaders.isEmpty()) {
                            item(key = "acr_empty") {
                                BleReaderEmptyHint(
                                    text = stringResource(Res.string.ble_reader_section_acr_empty),
                                    scanning = scanning,
                                )
                            }
                        } else {
                            items(acrReaders, key = { "acr_${it.pickId}" }) { device ->
                                BleReaderDeviceCard(device = device, onClick = { onPick(device) })
                            }
                        }

                        if (otherTotal > 0) {
                            item(key = "other_toggle") {
                                BleReaderOtherToggle(
                                    expanded = showOtherReaders,
                                    count = otherTotal,
                                    onClick = { showOtherReaders = !showOtherReaders },
                                )
                            }
                            item(key = "other_visibility") {
                                AnimatedVisibility(
                                    visible = showOtherReaders,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = otherSearch,
                                            onValueChange = { otherSearch = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            placeholder = {
                                                Text(stringResource(Res.string.ble_reader_search_other_hint))
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Search, contentDescription = null)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        if (filteredOthers.size < otherTotal && otherSearch.isBlank()) {
                                            Text(
                                                text = stringResource(
                                                    Res.string.ble_reader_other_truncated,
                                                    filteredOthers.size,
                                                    otherTotal,
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        filteredOthers.forEach { device ->
                                            BleReaderDeviceCard(
                                                device = device,
                                                onClick = { onPick(device) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun BleReaderSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    highlighted: Boolean,
) {
    val container = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BleReaderEmptyHint(text: String, scanning: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BleReaderOtherToggle(
    expanded: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.ble_reader_section_other_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (expanded) {
                        stringResource(Res.string.ble_reader_hide_other_devices)
                    } else {
                        stringResource(Res.string.ble_reader_show_other_devices, count)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BleReaderDeviceCard(
    device: BleReaderPickerItem,
    onClick: () -> Unit,
) {
    val containerColor = when {
        device.pcscReady -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        device.isAcrReader -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val leadingIcon = when {
        device.pcscReady || device.isAcrReader -> Icons.Default.Nfc
        else -> Icons.Default.Bluetooth
    }
    val rssiLabel = if (device.rssi > Int.MIN_VALUE + 1000) {
        stringResource(Res.string.ble_reader_rssi_label, device.rssi)
    } else {
        null
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.pcscReady) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = device.mac,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rssiLabel != null) {
                    Text(
                        text = rssiLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (device.bonded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(Res.string.ble_reader_bonded_label),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
