package com.eventmanager.app.ui.screens

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.models.*

@Composable
expect fun BilleterieScannerScreen(
    volunteers: List<Volunteer>,
    guests: List<Guest>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    onBack: () -> Unit,
    onConfirmEntry: (Job, Int) -> Unit
)
