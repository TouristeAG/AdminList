package com.eventmanager.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.remote.FirebaseJoinCodec
import com.eventmanager.app.data.remote.FirebaseJoinPayload
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.security.firebaseOAuthCredentialsReady
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.firebase_api_key_label
import com.eventmanager.app.resources.firebase_application_id_label
import com.eventmanager.app.resources.firebase_config_paste_apply
import com.eventmanager.app.resources.firebase_config_paste_hint
import com.eventmanager.app.resources.firebase_config_paste_label
import com.eventmanager.app.resources.firebase_hide_secrets
import com.eventmanager.app.resources.firebase_join_code_copied
import com.eventmanager.app.resources.firebase_join_config_code_hint
import com.eventmanager.app.resources.firebase_join_config_code_label
import com.eventmanager.app.resources.firebase_join_copy_code
import com.eventmanager.app.resources.firebase_join_error_oauth_missing
import com.eventmanager.app.resources.firebase_join_invite_admin_body
import com.eventmanager.app.resources.firebase_join_invite_code_hint
import com.eventmanager.app.resources.firebase_join_invite_code_label
import com.eventmanager.app.resources.firebase_join_invite_copied
import com.eventmanager.app.resources.firebase_join_invite_copy
import com.eventmanager.app.resources.firebase_join_or_enter_codes
import com.eventmanager.app.resources.firebase_join_paste_code
import com.eventmanager.app.resources.firebase_join_qr_body
import com.eventmanager.app.resources.firebase_join_qr_need_config
import com.eventmanager.app.resources.firebase_join_qr_restricted
import com.eventmanager.app.resources.firebase_join_qr_title
import com.eventmanager.app.resources.firebase_join_received
import com.eventmanager.app.resources.firebase_join_scan
import com.eventmanager.app.resources.firebase_join_scan_body
import com.eventmanager.app.resources.firebase_join_scan_title
import com.eventmanager.app.resources.firebase_project_configured_hidden
import com.eventmanager.app.resources.firebase_project_id_label
import com.eventmanager.app.resources.firebase_project_secrets_restricted
import com.eventmanager.app.resources.firebase_reveal_secrets
import com.eventmanager.app.resources.firebase_step_project_body
import com.eventmanager.app.resources.firebase_step_project_title
import com.eventmanager.app.resources.firebase_step_project_where
import com.eventmanager.app.resources.firebase_advanced_project
import com.eventmanager.app.resources.firebase_hide_project
import com.eventmanager.app.resources.firebase_web_client_id_label
import com.eventmanager.app.resources.firebase_web_client_secret_label
import com.eventmanager.app.utils.QRCodeUtils
import org.jetbrains.compose.resources.stringResource

/**
 * Admin: paste Firebase web config + manual project fields (masked).
 */
@Composable
fun FirebaseAdminProjectConfigCard(
    settingsManager: SettingsManager,
    projectId: String,
    applicationId: String,
    apiKey: String,
    webClientId: String,
    webClientSecret: String,
    onProjectIdChange: (String) -> Unit,
    onApplicationIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onWebClientIdChange: (String) -> Unit,
    onWebClientSecretChange: (String) -> Unit,
    onAppliedFromPaste: () -> Unit = {},
    allowProjectSecrets: Boolean = true,
) {
    var pasteText by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }
    var revealSecrets by remember { mutableStateOf(false) }
    val projectReady = projectId.isNotBlank() && applicationId.isNotBlank() && apiKey.isNotBlank()
    val oauthReady = firebaseOAuthCredentialsReady(webClientId, webClientSecret)
    var showManualFields by remember {
        mutableStateOf(allowProjectSecrets && (!projectReady || !oauthReady))
    }

    LaunchedEffect(allowProjectSecrets, oauthReady) {
        if (!allowProjectSecrets) {
            showManualFields = false
            revealSecrets = false
        } else if (!oauthReady) {
            showManualFields = true
        }
    }

    val secretTransform = if (revealSecrets) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    GuidedStepCard(
        title = stringResource(Res.string.firebase_step_project_title),
        body = if (allowProjectSecrets) {
            stringResource(Res.string.firebase_step_project_body)
        } else {
            stringResource(Res.string.firebase_project_secrets_restricted)
        },
    ) {
        if (!allowProjectSecrets) {
            Text(
                stringResource(Res.string.firebase_project_configured_hidden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            return@GuidedStepCard
        }

        GuidedStepCard(
            title = stringResource(Res.string.firebase_config_paste_label),
            body = stringResource(Res.string.firebase_config_paste_hint),
        ) {
            OutlinedTextField(
                value = pasteText,
                onValueChange = {
                    pasteText = it
                    pasteError = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )
            Button(
                onClick = {
                    FirebaseJoinCodec.parseFirebaseWebConfig(pasteText).fold(
                        onSuccess = { cfg ->
                            onProjectIdChange(cfg.projectId)
                            onApplicationIdChange(cfg.applicationId)
                            onApiKeyChange(cfg.apiKey)
                            if (cfg.webClientId.isNotBlank()) onWebClientIdChange(cfg.webClientId)
                            if (cfg.webClientSecret.isNotBlank()) onWebClientSecretChange(cfg.webClientSecret)
                            if (cfg.gcmSenderId.isNotBlank()) {
                                settingsManager.setFirebaseGcmSenderId(cfg.gcmSenderId)
                            }
                            if (cfg.storageBucket.isNotBlank()) {
                                settingsManager.setFirebaseStorageBucket(cfg.storageBucket)
                            }
                            settingsManager.setFirebaseProjectId(cfg.projectId)
                            settingsManager.setFirebaseApplicationId(cfg.applicationId)
                            settingsManager.setFirebaseApiKey(cfg.apiKey)
                            if (cfg.webClientId.isNotBlank()) {
                                settingsManager.setFirebaseWebClientId(cfg.webClientId)
                            }
                            if (cfg.webClientSecret.isNotBlank()) {
                                settingsManager.setFirebaseWebClientSecret(cfg.webClientSecret)
                            }
                            pasteError = null
                            pasteText = ""
                            showManualFields = true
                            onAppliedFromPaste()
                        },
                        onFailure = { e ->
                            pasteError = e.message ?: "Invalid config"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pasteText.isNotBlank(),
            ) {
                Text(stringResource(Res.string.firebase_config_paste_apply))
            }
            pasteError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (projectReady && !showManualFields) {
            Text(
                stringResource(Res.string.firebase_project_configured_hidden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { showManualFields = true }) {
                Text(stringResource(Res.string.firebase_advanced_project))
            }
        } else {
            TextButton(onClick = { showManualFields = !showManualFields }) {
                Text(
                    if (showManualFields) stringResource(Res.string.firebase_hide_project)
                    else stringResource(Res.string.firebase_advanced_project),
                )
            }
        }

        if (showManualFields) {
            Text(
                stringResource(Res.string.firebase_step_project_where),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { revealSecrets = !revealSecrets }) {
                Text(
                    stringResource(
                        if (revealSecrets) Res.string.firebase_hide_secrets else Res.string.firebase_reveal_secrets,
                    ),
                )
            }
            OutlinedTextField(
                value = projectId,
                onValueChange = {
                    onProjectIdChange(it)
                    settingsManager.setFirebaseProjectId(it)
                },
                label = { Text(stringResource(Res.string.firebase_project_id_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = secretTransform,
            )
            OutlinedTextField(
                value = applicationId,
                onValueChange = {
                    onApplicationIdChange(it)
                    settingsManager.setFirebaseApplicationId(it)
                },
                label = { Text(stringResource(Res.string.firebase_application_id_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = secretTransform,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    onApiKeyChange(it)
                    settingsManager.setFirebaseApiKey(it)
                },
                label = { Text(stringResource(Res.string.firebase_api_key_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = secretTransform,
            )
            OutlinedTextField(
                value = webClientId,
                onValueChange = {
                    onWebClientIdChange(it)
                    settingsManager.setFirebaseWebClientId(it)
                },
                label = { Text(stringResource(Res.string.firebase_web_client_id_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = secretTransform,
            )
            OutlinedTextField(
                value = webClientSecret,
                onValueChange = {
                    onWebClientSecretChange(it)
                    settingsManager.setFirebaseWebClientSecret(it)
                },
                label = { Text(stringResource(Res.string.firebase_web_client_secret_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = secretTransform,
            )
        }
    }
}

@Composable
fun FirebaseAdminJoinQrCard(
    orgId: String,
    projectId: String,
    applicationId: String,
    apiKey: String,
    webClientId: String,
    webClientSecret: String,
    bootstrapCode: String = "",
    allowProjectSecrets: Boolean = true,
) {
    val clipboard = LocalClipboardManager.current
    var copiedHint by remember { mutableStateOf(false) }
    var copiedInvite by remember { mutableStateOf(false) }

    val payload = remember(allowProjectSecrets, orgId, projectId, applicationId, apiKey, webClientId, webClientSecret) {
        if (!allowProjectSecrets) return@remember null
        FirebaseJoinPayload(
            orgId = orgId.trim(),
            projectId = projectId.trim(),
            applicationId = applicationId.trim(),
            apiKey = apiKey.trim(),
            webClientId = webClientId.trim(),
            webClientSecret = webClientSecret.trim(),
        ).takeIf {
            it.isComplete() && firebaseOAuthCredentialsReady(webClientId.trim(), webClientSecret.trim())
        }
    }
    val encoded = remember(payload) {
        payload?.let { runCatching { FirebaseJoinCodec.encode(it) }.getOrNull() }
    }
    val qrBitmap = remember(encoded) {
        encoded?.let { QRCodeUtils.generateQrImageBitmap(it, 512) }
    }

    GuidedStepCard(
        title = stringResource(Res.string.firebase_join_qr_title),
        body = if (allowProjectSecrets) {
            stringResource(Res.string.firebase_join_qr_body)
        } else {
            stringResource(Res.string.firebase_join_qr_restricted)
        },
    ) {
        if (!allowProjectSecrets) {
            return@GuidedStepCard
        }
        if (encoded == null || qrBitmap == null) {
            Text(
                stringResource(Res.string.firebase_join_qr_need_config),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Image(
                bitmap = qrBitmap,
                contentDescription = stringResource(Res.string.firebase_join_qr_title),
                modifier = Modifier.size(220.dp).fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(encoded))
                    copiedHint = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.firebase_join_copy_code))
            }
            if (copiedHint) {
                Text(
                    stringResource(Res.string.firebase_join_code_copied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (bootstrapCode.isNotBlank()) {
                Text(
                    stringResource(Res.string.firebase_join_invite_admin_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    bootstrapCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(bootstrapCode))
                        copiedInvite = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.firebase_join_invite_copy))
                }
                if (copiedInvite) {
                    Text(
                        stringResource(Res.string.firebase_join_invite_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Wizard helper: project config + join QR in one block. */
@Composable
fun FirebaseAdminConfigAndJoinQrSection(
    settingsManager: SettingsManager,
    orgId: String,
    projectId: String,
    applicationId: String,
    apiKey: String,
    webClientId: String,
    webClientSecret: String,
    onOrgIdChange: (String) -> Unit = {},
    onProjectIdChange: (String) -> Unit,
    onApplicationIdChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onWebClientIdChange: (String) -> Unit,
    onWebClientSecretChange: (String) -> Unit,
    onAppliedFromPaste: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FirebaseAdminProjectConfigCard(
            settingsManager = settingsManager,
            projectId = projectId,
            applicationId = applicationId,
            apiKey = apiKey,
            webClientId = webClientId,
            webClientSecret = webClientSecret,
            onProjectIdChange = onProjectIdChange,
            onApplicationIdChange = onApplicationIdChange,
            onApiKeyChange = onApiKeyChange,
            onWebClientIdChange = onWebClientIdChange,
            onWebClientSecretChange = onWebClientSecretChange,
            onAppliedFromPaste = onAppliedFromPaste,
        )
        FirebaseAdminJoinQrCard(
            orgId = orgId,
            projectId = projectId,
            applicationId = applicationId,
            apiKey = apiKey,
            webClientId = webClientId,
            webClientSecret = webClientSecret,
            bootstrapCode = settingsManager.getFirebaseBootstrapCode(),
        )
    }
}

/**
 * Join / follow: scan or paste join code; never shows apiKey fields.
 */
@Composable
fun FirebaseJoinImportSection(
    settingsManager: SettingsManager,
    onJoined: (orgId: String) -> Unit,
    onRequestScan: (() -> Unit)? = null,
    joinError: String? = null,
) {
    var pasteCode by remember { mutableStateOf("") }
    var bootstrapCode by remember { mutableStateOf(settingsManager.getFirebaseBootstrapCode()) }
    var localError by remember { mutableStateOf<String?>(null) }
    val error = joinError ?: localError
    val incompleteOAuthMsg = stringResource(Res.string.firebase_join_error_oauth_missing)

    fun applyRaw(raw: String) {
        FirebaseJoinCodec.decode(raw).fold(
            onSuccess = { payload ->
                settingsManager.applyFirebaseJoinPayload(payload)
                settingsManager.setFirebaseBootstrapCode(bootstrapCode)
                if (!firebaseOAuthCredentialsReady(
                        settingsManager.getFirebaseWebClientId(),
                        settingsManager.getFirebaseWebClientSecret(),
                    )
                ) {
                    localError = incompleteOAuthMsg
                    return@fold
                }
                localError = null
                pasteCode = ""
                onJoined(payload.orgId)
            },
            onFailure = { e ->
                localError = e.message ?: "Invalid join code"
            },
        )
    }

    GuidedStepCard(
        title = stringResource(Res.string.firebase_join_scan_title),
        body = stringResource(Res.string.firebase_join_scan_body),
    ) {
        if (onRequestScan != null) {
            Button(onClick = onRequestScan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.firebase_join_scan))
            }
        }
        Text(
            stringResource(Res.string.firebase_join_or_enter_codes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = pasteCode,
            onValueChange = {
                pasteCode = it
                localError = null
            },
            label = { Text(stringResource(Res.string.firebase_join_config_code_label)) },
            supportingText = {
                Text(stringResource(Res.string.firebase_join_config_code_hint))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedTextField(
            value = bootstrapCode,
            onValueChange = {
                bootstrapCode = it
                settingsManager.setFirebaseBootstrapCode(it)
                localError = null
            },
            label = { Text(stringResource(Res.string.firebase_join_invite_code_label)) },
            supportingText = {
                Text(stringResource(Res.string.firebase_join_invite_code_hint))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedButton(
            onClick = { applyRaw(pasteCode) },
            modifier = Modifier.fillMaxWidth(),
            enabled = pasteCode.isNotBlank() && bootstrapCode.isNotBlank(),
        ) {
            Text(stringResource(Res.string.firebase_join_paste_code))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun FirebaseConfigReceivedBanner(orgId: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.firebase_join_received),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (orgId.isNotBlank()) {
            Text(
                orgId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
