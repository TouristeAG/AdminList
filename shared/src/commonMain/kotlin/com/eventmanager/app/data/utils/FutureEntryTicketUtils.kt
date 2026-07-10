package com.eventmanager.app.data.utils

import com.eventmanager.app.data.models.BenefitSystemType
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.NovaJobType
import com.eventmanager.app.data.models.ShiftTime

data class FutureEntryGroup(
    val invites: Int,
    val totalRemaining: Int,
    val jobs: List<Job>
)

fun groupFutureEntriesByInvites(
    jobs: List<Job>,
    configsByName: Map<String, JobTypeConfig>,
    evaluationTime: Long = System.currentTimeMillis(),
    offsetHours: Int = 0,
    meetingNovaBenefitsExcludedForOrion: Boolean = false
): List<FutureEntryGroup> {
    val eligible = jobs.filter { job ->
        val config = configsByName[job.jobTypeName]
        jobTypeSupportsTrackedFutureEntries(job, config) &&
            effectiveBenefitFutureEntriesRemaining(
                job, config, evaluationTime, offsetHours, meetingNovaBenefitsExcludedForOrion
            ) > 0
    }
    return eligible
        .groupBy { job -> effectiveBenefitFutureEntryInvites(job, configsByName[job.jobTypeName]) }
        .map { (invites, groupJobs) ->
            val sorted = groupJobs.sortedBy { it.date }
            FutureEntryGroup(
                invites = invites,
                totalRemaining = sorted.sumOf {
                    effectiveBenefitFutureEntriesRemaining(
                        it, configsByName[it.jobTypeName], evaluationTime, offsetHours, meetingNovaBenefitsExcludedForOrion
                    )
                },
                jobs = sorted
            )
        }
        .sortedBy { it.jobs.firstOrNull()?.date ?: Long.MAX_VALUE }
}

/**
 * Whether this job type tracks consumable future free entries.
 *
 * For STELLAR shift jobs the NovaJobType determines eligibility:
 * - DEFAULT_SHIFT non-profité → 1 future entry
 * - MEETING → 1 future entry
 * - PHOTOGRAPHER_VIDEOGRAPHER → 1 future entry
 * - GRAPHIC_DESIGNER_EVENT → 1 future entry
 * - GRAPHIC_DESIGNER_ASSOCIATION → 2 future entries
 * - DEFAULT_SHIFT profité → no future entry
 *
 * For MANUAL types the admin-configured futureSingleUseEntries is used.
 */
fun jobTypeSupportsTrackedFutureEntries(job: Job, config: JobTypeConfig?): Boolean =
    when {
        config == null -> job.shiftTime == ShiftTime.AFTER_MIDNIGHT
        config.benefitSystemType == BenefitSystemType.STELLAR && config.isShiftJob -> {
            when (config.novaJobType) {
                NovaJobType.DEFAULT_SHIFT -> job.shiftTime == ShiftTime.AFTER_MIDNIGHT
                NovaJobType.MEETING,
                NovaJobType.PHOTOGRAPHER_VIDEOGRAPHER,
                NovaJobType.GRAPHIC_DESIGNER_EVENT,
                NovaJobType.GRAPHIC_DESIGNER_ASSOCIATION -> true
            }
        }
        config.benefitSystemType == BenefitSystemType.MANUAL &&
            (config.manualRewards?.futureSingleUseEntries ?: 0) > 0 -> true
        else -> false
    }

fun effectiveBenefitFutureEntriesRemaining(
    job: Job,
    config: JobTypeConfig?,
    evaluationTime: Long = System.currentTimeMillis(),
    offsetHours: Int = 0,
    meetingNovaBenefitsExcludedForOrion: Boolean = false
): Int {
    if (meetingNovaBenefitsExcludedForOrion &&
        config?.benefitSystemType == BenefitSystemType.STELLAR &&
        config.isShiftJob &&
        config.novaJobType == NovaJobType.MEETING
    ) {
        return 0
    }
    val jobDayStart = DateTimeUtils.getStartOfDayWithOffset(job.date, offsetHours)
    if (evaluationTime < jobDayStart) return 0
    val raw = job.benefitFutureEntriesRemaining
    if (raw != null) return raw.coerceAtLeast(0)
    if (!jobTypeSupportsTrackedFutureEntries(job, config)) return 0
    return 0
}

fun effectiveBenefitFutureEntryInvites(job: Job, config: JobTypeConfig?): Int {
    if (config?.benefitSystemType == BenefitSystemType.MANUAL) {
        return config.manualRewards?.futureSingleUseEntryInvites ?: 1
    }
    return job.benefitFutureEntryInvites ?: 1
}
