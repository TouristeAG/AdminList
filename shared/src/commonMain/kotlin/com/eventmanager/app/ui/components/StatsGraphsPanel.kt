package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.platform.PlatformContext

@Composable
expect fun StatsGraphsPanel(
    platformContext: PlatformContext,
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    venues: List<VenueEntity> = emptyList(),
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    isPhone: Boolean = true,
    modifier: Modifier = Modifier
)
