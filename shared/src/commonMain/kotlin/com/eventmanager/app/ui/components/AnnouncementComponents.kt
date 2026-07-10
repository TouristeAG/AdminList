package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

@Composable
fun SendAnnouncementButton(isPhone: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Campaign, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.announcement_button_label),
            style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendAnnouncementDialog(
    venues: List<VenueEntity>,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (targetVenueIds: List<Long>, title: String, message: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var allVenues by remember { mutableStateOf(true) }
    var selectedVenueIds by remember { mutableStateOf(setOf<Long>()) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = phoneFractionDialogProperties(
            dismissOnBackPress = !isSending,
            dismissOnClickOutside = false,
        ),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Compact) { maxDialogWidth, maxDialogHeight ->
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth.coerceAtMost(500.dp))
                    .heightIn(max = maxDialogHeight.coerceAtMost(600.dp))
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(Res.string.announcement_send_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(onClick = { if (!isSending) onDismiss() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.announcement_all_venues),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = allVenues,
                            onCheckedChange = {
                                allVenues = it
                                if (it) selectedVenueIds = emptySet()
                            },
                            enabled = !isSending,
                        )
                    }
                    if (!allVenues) {
                        Text(
                            text = stringResource(Res.string.announcement_destination_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        venues.filter { it.isActive }.forEach { venue ->
                            val checked = venue.id in selectedVenueIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    enabled = !isSending,
                                    onCheckedChange = { isChecked ->
                                        selectedVenueIds = if (isChecked) {
                                            selectedVenueIds + venue.id
                                        } else {
                                            selectedVenueIds - venue.id
                                        }
                                    },
                                )
                                Text(venue.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(Res.string.announcement_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSending,
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(stringResource(Res.string.announcement_message_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !isSending,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isSending) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val targets = if (allVenues) {
                                    venues.filter { it.isActive }.map { it.id }
                                } else {
                                    selectedVenueIds.toList()
                                }
                                onSend(targets, title.trim(), message.trim())
                            },
                            enabled = !isSending && title.isNotBlank() && message.isNotBlank() &&
                                (allVenues || selectedVenueIds.isNotEmpty()),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(Res.string.announcement_send_button))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementPopup(
    announcement: AnnouncementDisplay,
    onDismiss: () -> Unit
) {
    val sentLabel = remember(announcement.sentAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(announcement.sentAt))
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = phoneFractionDialogProperties(),
    ) {
        DialogFractionSizer(profile = FractionalDialogProfile.Card) { maxDialogWidth, maxDialogHeight ->
            Card(
                modifier = Modifier
                    .widthIn(max = maxDialogWidth)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.announcement_popup_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                        }
                    }
                    Text(
                        text = announcement.venueName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (announcement.title.isNotBlank()) {
                        Text(
                            text = announcement.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(text = announcement.message, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = sentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(Res.string.announcement_popup_close))
                    }
                }
            }
        }
    }
}
