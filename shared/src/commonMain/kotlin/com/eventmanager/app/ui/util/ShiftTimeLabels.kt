package com.eventmanager.app.ui.util

import androidx.compose.runtime.Composable
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.NovaJobType
import com.eventmanager.app.data.models.ShiftTime
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun shiftTimeLabel(shiftTime: ShiftTime): String = stringResource(
    when (shiftTime) {
        ShiftTime.BEFORE_MIDNIGHT -> Res.string.shift_time_evening_profited
        ShiftTime.AFTER_MIDNIGHT -> Res.string.shift_time_evening_non_profited
    }
)

fun isShiftTimeRelevantForJob(job: Job, jobTypeConfigs: List<JobTypeConfig>): Boolean {
    val config = jobTypeConfigs.firstOrNull { it.name.equals(job.jobTypeName, ignoreCase = true) }
    return config?.let { it.novaJobType == NovaJobType.DEFAULT_SHIFT && it.requiresShiftTime } ?: true
}

@Composable
fun shiftTimeLabelIfRelevant(job: Job, jobTypeConfigs: List<JobTypeConfig>): String? {
    return if (isShiftTimeRelevantForJob(job, jobTypeConfigs)) shiftTimeLabel(job.shiftTime) else null
}
