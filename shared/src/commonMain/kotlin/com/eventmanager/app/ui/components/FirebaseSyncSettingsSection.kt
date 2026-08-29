package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.remote.MemberRole
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.firebase_checklist_auth
import com.eventmanager.app.resources.firebase_checklist_org
import com.eventmanager.app.resources.firebase_checklist_project
import com.eventmanager.app.resources.firebase_checklist_title
import com.eventmanager.app.resources.firebase_migrate_section_body
import com.eventmanager.app.resources.firebase_migrate_section_title
import com.eventmanager.app.resources.firebase_migrate_to_sheets
import com.eventmanager.app.resources.firebase_member_email_hint
import com.eventmanager.app.resources.firebase_member_email_label
import com.eventmanager.app.resources.firebase_member_uid_hint
import com.eventmanager.app.resources.firebase_member_uid_label
import com.eventmanager.app.resources.firebase_org_id_hint
import com.eventmanager.app.resources.firebase_org_id_label
import com.eventmanager.app.resources.firebase_role_admin
import com.eventmanager.app.resources.firebase_role_door
import com.eventmanager.app.resources.firebase_role_pos
import com.eventmanager.app.resources.firebase_section_hide
import com.eventmanager.app.resources.firebase_settings_intro
import com.eventmanager.app.resources.firebase_settings_section_access
import com.eventmanager.app.resources.firebase_settings_section_devices
import com.eventmanager.app.resources.firebase_settings_section_optional
import com.eventmanager.app.resources.firebase_status_need_config
import com.eventmanager.app.resources.firebase_status_need_sign_in
import com.eventmanager.app.resources.firebase_status_ready
import com.eventmanager.app.resources.firebase_status_signed_in_as
import com.eventmanager.app.resources.firebase_step_org_body
import com.eventmanager.app.resources.firebase_step_org_title
import com.eventmanager.app.resources.firebase_team_body
import com.eventmanager.app.resources.firebase_team_not_domains
import com.eventmanager.app.resources.firebase_team_role_admin_desc
import com.eventmanager.app.resources.firebase_team_role_door_desc
import com.eventmanager.app.resources.firebase_team_role_pos_desc
import com.eventmanager.app.resources.firebase_team_roles_heading
import com.eventmanager.app.resources.firebase_team_show
import com.eventmanager.app.resources.firebase_team_steps
import com.eventmanager.app.resources.firebase_team_title
import com.eventmanager.app.resources.sheets_migrate_card_body
import com.eventmanager.app.resources.sheets_migrate_card_title
import com.eventmanager.app.resources.sheets_migrate_to_firebase
import com.eventmanager.app.resources.firebase_tutorial_help_cd
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource

/**
 * Admin Full settings — Firebase sync (ordered: org → project → sign-in → devices → access → optional).
 */
@Composable
fun FirebaseSyncSettingsSection(
    configuredOrgs: List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg>,
    onConfiguredOrgsChange: (List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg>) -> Unit,
    onOrgIdCommitted: (String) -> Unit = {},
    authEmail: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onMigrateToSheets: () -> Unit,
    onMirrorExport: suspend () -> Unit,
    platformContext: PlatformContext? = null,
    onMirrorSettingsChanged: () -> Unit = {},
    settingsManager: com.eventmanager.app.data.sync.SettingsManager? = null,
    memberUid: String = "",
    memberEmail: String = "",
    onMemberUidChange: (String) -> Unit = {},
    onMemberEmailChange: (String) -> Unit = {},
    onAssignMemberRole: (MemberRole) -> Unit = {},
    projectId: String = "",
    apiKey: String = "",
    applicationId: String = "",
    webClientId: String = "",
    webClientSecret: String = "",
    onProjectIdChange: (String) -> Unit = {},
    onApiKeyChange: (String) -> Unit = {},
    onApplicationIdChange: (String) -> Unit = {},
    onWebClientIdChange: (String) -> Unit = {},
    onWebClientSecretChange: (String) -> Unit = {},
    allowedEmailDomains: List<String> = emptyList(),
    onAllowedEmailDomainsChange: (List<String>) -> Unit = {},
) {
    val ready = firebaseConnectionReady(configuredOrgs, projectId, applicationId, apiKey, authEmail)
    val projectReady = projectId.isNotBlank() && applicationId.isNotBlank() && apiKey.isNotBlank()
    val orgReady = firebaseConfiguredOrgsReady(configuredOrgs)
    val authReady = !authEmail.isNullOrBlank()
    var showTeam by remember { mutableStateOf(false) }

    val statusTitle = when {
        ready -> stringResource(Res.string.firebase_status_ready)
        projectReady && orgReady -> stringResource(Res.string.firebase_status_need_sign_in)
        else -> stringResource(Res.string.firebase_status_need_config)
    }
    val statusSubtitle = authEmail?.let { stringResource(Res.string.firebase_status_signed_in_as, it) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.firebase_settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GuidedStatusBanner(
            title = statusTitle,
            subtitle = statusSubtitle,
            ready = ready,
            warning = !ready && orgReady,
        )

        // —— Setup (steps 1–3) ——
        FirebaseConfiguredOrgsSection(
            configuredOrgs = configuredOrgs,
            onConfiguredOrgsChange = onConfiguredOrgsChange,
            onOrgIdCommitted = onOrgIdCommitted,
        )

        if (settingsManager != null) {
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
            )
        }

        FirebaseSignInStep(
            authEmail = authEmail,
            onSignIn = onSignIn,
            onSignOut = onSignOut,
        )

        GuidedStepCard(title = stringResource(Res.string.firebase_checklist_title)) {
            GuidedChecklistItem(
                label = stringResource(Res.string.firebase_checklist_org),
                done = orgReady,
            )
            GuidedChecklistItem(
                label = stringResource(Res.string.firebase_checklist_project),
                done = projectReady,
            )
            GuidedChecklistItem(
                label = stringResource(Res.string.firebase_checklist_auth),
                done = authReady,
            )
        }

        FirebaseSettingsSectionHeader(stringResource(Res.string.firebase_settings_section_devices))
        if (settingsManager != null) {
            FirebaseAdminJoinQrCard(
                orgId = configuredOrgs.firstOrNull { it.orgId.isNotBlank() }?.orgId.orEmpty(),
                projectId = projectId,
                applicationId = applicationId,
                apiKey = apiKey,
                webClientId = webClientId,
                webClientSecret = webClientSecret,
                bootstrapCode = settingsManager.getFirebaseBootstrapCode(),
            )
        }

        FirebaseSettingsSectionHeader(stringResource(Res.string.firebase_settings_section_access))
        FirebaseAllowedEmailDomainsSection(
            domains = allowedEmailDomains,
            onDomainsChange = onAllowedEmailDomainsChange,
        )

        GuidedStepCard(
            title = stringResource(Res.string.firebase_team_title),
            body = stringResource(Res.string.firebase_team_body),
        ) {
            Text(
                stringResource(Res.string.firebase_team_not_domains),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showTeam = !showTeam }) {
                Text(
                    if (showTeam) stringResource(Res.string.firebase_section_hide)
                    else stringResource(Res.string.firebase_team_show),
                )
            }
            if (showTeam) {
                Text(
                    stringResource(Res.string.firebase_team_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = memberUid,
                    onValueChange = onMemberUidChange,
                    label = { Text(stringResource(Res.string.firebase_member_uid_label)) },
                    supportingText = { Text(stringResource(Res.string.firebase_member_uid_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = memberEmail,
                    onValueChange = onMemberEmailChange,
                    label = { Text(stringResource(Res.string.firebase_member_email_label)) },
                    supportingText = { Text(stringResource(Res.string.firebase_member_email_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    stringResource(Res.string.firebase_team_roles_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Res.string.firebase_team_role_admin_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Member — sync access for team devices (Admin UI still requires local admin card).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onAssignMemberRole(MemberRole.ADMIN) },
                        enabled = memberUid.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(Res.string.firebase_role_admin)) }
                    OutlinedButton(
                        onClick = { onAssignMemberRole(MemberRole.MEMBER) },
                        enabled = memberUid.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Member") }
                }
            }
        }

        FirebaseSettingsSectionHeader(stringResource(Res.string.firebase_settings_section_optional))
        if (settingsManager != null) {
            FirebaseSheetsMirrorSettingsSection(
                settingsManager = settingsManager,
                platformContext = platformContext,
                onMirrorExport = onMirrorExport,
                onMirrorSettingsChanged = onMirrorSettingsChanged,
            )
        }

        GuidedStepCard(
            title = stringResource(Res.string.firebase_migrate_section_title),
            body = stringResource(Res.string.firebase_migrate_section_body),
        ) {
            Button(onClick = onMigrateToSheets, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.firebase_migrate_to_sheets))
            }
        }
    }
}

@Composable
private fun FirebaseSettingsSectionHeader(title: String) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SheetsMigrateToFirebaseButton(onClick: () -> Unit) {
    var showTutorial by remember { mutableStateOf(false) }
    GuidedStepCard(
        title = stringResource(Res.string.sheets_migrate_card_title),
        body = stringResource(Res.string.sheets_migrate_card_body),
        modifier = Modifier.padding(vertical = 8.dp),
        onHelpClick = { showTutorial = true },
        helpContentDescription = stringResource(Res.string.firebase_tutorial_help_cd),
    ) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.sheets_migrate_to_firebase))
        }
    }
    if (showTutorial) {
        FirebaseSetupTutorialDialog(onDismiss = { showTutorial = false })
    }
}

fun shouldShowSyncInterval(backendType: com.eventmanager.app.data.remote.BackendType): Boolean =
    backendType == com.eventmanager.app.data.remote.BackendType.SHEETS

fun shouldShowSheetsManualSyncControls(backendType: com.eventmanager.app.data.remote.BackendType): Boolean =
    backendType == com.eventmanager.app.data.remote.BackendType.SHEETS
