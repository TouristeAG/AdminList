package com.eventmanager.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.data.remote.FirebaseAuthResult
import com.eventmanager.app.data.remote.InstitutionBackendAnnouncement
import com.eventmanager.app.data.remote.MigrationDirection
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.backend_mismatch_soft_lock
import com.eventmanager.app.ui.screens.BackendFollowScreen
import com.eventmanager.app.ui.screens.BackendMigrationWizardScreen
import com.eventmanager.app.ui.screens.BackendMismatchBanner
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * Institution backend migration UI: blocking follow dialog on every surface (Welcome, Billeterie,
 * POS, Admin) and optional admin migration wizard.
 */
@Composable
fun BackendMigrationUiHost(
    viewModel: EventManagerViewModel,
    settingsManager: SettingsManager,
    platformContext: PlatformContext?,
    onRequestFirebaseSignIn: ((FirebaseAuthResult) -> Unit) -> Unit = { onResult ->
        onResult(FirebaseAuthResult.Error("Firebase Sign-In is not available on this screen"))
    },
    showMigrationWizard: MigrationDirection? = null,
    onDismissMigrationWizard: () -> Unit = {},
) {
    val emptyAnnouncement = remember { MutableStateFlow<InstitutionBackendAnnouncement?>(null) }
    val pendingFollow by (viewModel.pendingBackendFollow ?: emptyAnnouncement).collectAsState()
    val softLocked by (viewModel.crudSoftLocked ?: remember { MutableStateFlow(false) }).collectAsState()

    pendingFollow?.let { announcement ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            BackendFollowScreen(
                announcement = announcement,
                settingsManager = settingsManager,
                platformContext = platformContext,
                onFollow = { orgId, spreadsheetId ->
                    viewModel.followPendingBackendMigration(announcement, orgId, spreadsheetId)
                },
                onRequestFirebaseSignIn = onRequestFirebaseSignIn,
            )
        }
    }

    if (softLocked && pendingFollow == null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            BackendMismatchBanner(
                message = stringResource(Res.string.backend_mismatch_soft_lock),
            )
        }
    }

    showMigrationWizard?.let { direction ->
        Dialog(
            onDismissRequest = onDismissMigrationWizard,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BackendMigrationWizardScreen(
                direction = direction,
                settingsManager = settingsManager,
                platformContext = platformContext,
                onMigrateToFirebase = { orgId -> viewModel.migrateBackendToFirebase(orgId) },
                onMigrateToSheets = { spreadsheetId -> viewModel.migrateBackendToSheets(spreadsheetId) },
                onDismiss = onDismissMigrationWizard,
                onCancelInFlight = { viewModel.cancelBackendMigration() },
                onRequestFirebaseSignIn = onRequestFirebaseSignIn,
            )
        }
    }
}
