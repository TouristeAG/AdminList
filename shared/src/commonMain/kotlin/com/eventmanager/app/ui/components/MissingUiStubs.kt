package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.models.Volunteer

@Composable
fun DeleteVolunteerDialog(
    volunteer: Volunteer,
    shiftCount: Int,
    onConfirm: (deleteShifts: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    var deleteShifts by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.delete_volunteer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.delete_volunteer_confirm, volunteer.name))
                if (shiftCount > 0) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = deleteShifts, onCheckedChange = { deleteShifts = it })
                        Text(stringResource(Res.string.delete_volunteer_also_delete_shifts_other, shiftCount))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteShifts) }) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
