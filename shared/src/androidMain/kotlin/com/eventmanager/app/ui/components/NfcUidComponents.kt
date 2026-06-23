package com.eventmanager.app.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eventmanager.app.R

@Composable
fun NfcUidInfoRow(uid: String, isPhone: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (uid.isBlank()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(
                text = context.getString(R.string.nfc_uid_label),
                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = uid,
                style = if (isPhone) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AddNfcUidDialog(
    onDismiss: () -> Unit,
    onConfirmUid: (String) -> Unit
) {
    val context = LocalContext.current
    var manualUid by remember { mutableStateOf("") }
    var scannedUid by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        ctx as? Activity
    }
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(activity, nfcAdapter) {
        if (activity == null || nfcAdapter == null || !nfcAdapter.isEnabled) {
            onDispose { }
        } else {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val uid = tag.id?.joinToString(separator = "") { "%02X".format(it) }.orEmpty()
                mainHandler.post {
                    if (uid.isNotBlank()) {
                        scannedUid = uid
                        statusMessage = null
                    }
                }
            }
            try {
                nfcAdapter.enableReaderMode(
                    activity,
                    callback,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null
                )
            } catch (_: Exception) { }
            onDispose {
                try {
                    nfcAdapter.disableReaderMode(activity)
                } catch (_: Exception) { }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(
                    text = context.getString(R.string.add_nfc_card_uid_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = context.getString(R.string.enter_nfc_uid_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                scannedUid?.let { uid ->
                    Text(
                        text = context.getString(R.string.last_nfc_uid_scanned, uid),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = manualUid,
                    onValueChange = { manualUid = it.uppercase() },
                    label = { Text(context.getString(R.string.nfc_uid_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                statusMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(context.getString(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val uid = (scannedUid ?: manualUid).trim()
                                .replace(" ", "")
                                .replace(":", "")
                                .uppercase()
                            if (uid.isBlank()) {
                                statusMessage = context.getString(R.string.nfc_uid_read_failed)
                            } else {
                                onConfirmUid(uid)
                            }
                        }
                    ) {
                        Text(context.getString(R.string.confirm))
                    }
                }
            }
        }
    }
}
