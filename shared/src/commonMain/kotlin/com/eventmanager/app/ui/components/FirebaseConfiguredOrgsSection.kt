package com.eventmanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.remote.FirebaseConfiguredOrg
import com.eventmanager.app.data.remote.FirebaseConfiguredOrgCodec
import com.eventmanager.app.data.remote.FirebaseOrgBootstrap
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.firebase_configured_org_add
import com.eventmanager.app.resources.firebase_configured_org_color_cd
import com.eventmanager.app.resources.firebase_configured_org_duplicate
import com.eventmanager.app.resources.firebase_configured_org_remove_cd
import com.eventmanager.app.resources.firebase_configured_orgs_body
import com.eventmanager.app.resources.firebase_configured_orgs_title
import com.eventmanager.app.resources.firebase_org_id_hint
import com.eventmanager.app.resources.firebase_org_id_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun FirebaseConfiguredOrgsSection(
    configuredOrgs: List<FirebaseConfiguredOrg>,
    onConfiguredOrgsChange: (List<FirebaseConfiguredOrg>) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showGuidedCard: Boolean = true,
    onOrgIdCommitted: (String) -> Unit = {},
) {
    var duplicateWarning by remember { mutableStateOf(false) }

    fun updateOrg(index: Int, orgId: String) {
        duplicateWarning = false
        val updated = configuredOrgs.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(orgId = orgId)
            onConfiguredOrgsChange(updated)
        }
    }

    fun updateColor(index: Int, colorArgb: Long) {
        val updated = configuredOrgs.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(colorArgb = colorArgb)
            onConfiguredOrgsChange(updated)
        }
    }

    fun removeOrg(index: Int) {
        if (configuredOrgs.size <= 1) return
        onConfiguredOrgsChange(configuredOrgs.filterIndexed { i, _ -> i != index })
    }

    fun addOrg() {
        val usedColors = configuredOrgs.map { it.colorArgb }
        val color = FirebaseConfiguredOrgCodec.nextAvailableColor(usedColors)
        onConfiguredOrgsChange(configuredOrgs + FirebaseConfiguredOrg(orgId = "", colorArgb = color))
    }

    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            configuredOrgs.forEachIndexed { index, entry ->
                val trimmedIds = configuredOrgs.map { it.orgId.trim() }
                val isDuplicate = entry.orgId.trim().isNotBlank() &&
                    trimmedIds.count { it == entry.orgId.trim() } > 1
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = entry.orgId,
                            onValueChange = {
                                duplicateWarning = false
                                updateOrg(index, it)
                            },
                            label = { Text(stringResource(Res.string.firebase_org_id_label)) },
                            supportingText = {
                                if (index == 0) {
                                    Text(stringResource(Res.string.firebase_org_id_hint))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focus ->
                                    if (!focus.isFocused && !readOnly) {
                                        val trimmed = entry.orgId.trim()
                                        if (FirebaseOrgBootstrap.isValidOrgId(trimmed)) {
                                            onOrgIdCommitted(trimmed)
                                        }
                                    }
                                },
                            singleLine = true,
                            readOnly = readOnly,
                            enabled = !readOnly,
                            isError = isDuplicate,
                        )
                        if (!readOnly && configuredOrgs.size > 1) {
                            IconButton(onClick = { removeOrg(index) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.firebase_configured_org_remove_cd),
                                )
                            }
                        }
                    }
                    OrgColorSwatchRow(
                        selectedColorArgb = entry.colorArgb,
                        onColorSelected = { if (!readOnly) updateColor(index, it) },
                        enabled = !readOnly,
                    )
                    if (isDuplicate) {
                        duplicateWarning = true
                        Text(
                            stringResource(Res.string.firebase_configured_org_duplicate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (!readOnly) {
                OutlinedButton(
                    onClick = { addOrg() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(Res.string.firebase_configured_org_add))
                }
            }
        }
    }

    if (showGuidedCard) {
        GuidedStepCard(
            title = stringResource(Res.string.firebase_configured_orgs_title),
            body = stringResource(Res.string.firebase_configured_orgs_body),
            modifier = modifier,
        ) {
            content()
        }
    } else {
        Column(modifier = modifier) {
            content()
        }
    }
}

@Composable
private fun OrgColorSwatchRow(
    selectedColorArgb: Long,
    onColorSelected: (Long) -> Unit,
    enabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FirebaseConfiguredOrgCodec.paletteArgb.forEach { argb ->
            val color = Color(argb)
            val selected = argb == selectedColorArgb
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = enabled) { onColorSelected(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }
    }
}

fun firebaseConfiguredOrgsReady(orgs: List<FirebaseConfiguredOrg>): Boolean =
    orgs.any { it.orgId.isNotBlank() }
