package com.eventmanager.app.data.remote

import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.VenueEntity
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.utils.NanoIdGenerator

/**
 * Persistence-safe identity collapse. Unlike UI [MultiOrgMerge.filterForVisibleOrg], guests /
 * volunteers / jobs never drop untagged rows just because another org-tagged row exists.
 * Venues are keyed by name within an org, so an untagged row is absorbed when a tagged
 * row with the same name already exists (Desktop pull+listener otherwise shows each lieu twice).
 */
object PersistIdentityDedupe {
    fun guests(items: List<Guest>): List<Guest> {
        val byKey = items.groupBy { guest ->
            if (NanoIdGenerator.isValidNanoId(guest.nanoId)) guest.nanoId else "row:${guest.id}"
        }
        return byKey.values.map { group ->
            pickPreferringTagged(group, { it.lastModified }, { it.firebaseOrgId })
        }
    }

    fun volunteers(items: List<Volunteer>): List<Volunteer> {
        val byId = items.groupBy { it.id }
        return byId.values.map { group ->
            pickPreferringTagged(group, { it.lastModified }, { it.firebaseOrgId })
        }
    }

    fun jobs(items: List<Job>): List<Job> {
        val byKey = items.groupBy { job ->
            job.jobNanoId.trim().takeIf { it.isNotBlank() } ?: "row:${job.id}"
        }
        return byKey.values.map { group ->
            pickPreferringTagged(group, { it.lastModified }, { it.firebaseOrgId })
        }
    }

    fun jobTypes(items: List<JobTypeConfig>): List<JobTypeConfig> {
        val byId = items.groupBy { it.id }
        val uniqueById = byId.values.map { group ->
            pickPreferringTagged(group, { it.lastModified }, { it.firebaseOrgId })
        }
        val byNameOrg = uniqueById.groupBy { "${it.firebaseOrgId}\u0000${it.name}" }
        return byNameOrg.values.map { group ->
            pickPreferringTagged(group, { it.lastModified }, { it.firebaseOrgId })
        }
    }

    fun venues(items: List<VenueEntity>): List<VenueEntity> {
        val uniqueById = items.groupBy { it.id }.values.map { group ->
            pickPreferringTagged(
                group,
                { maxOf(it.lastModified, it.peopleCounterLastModified) },
                { it.firebaseOrgId },
            )
        }
        val uniqueByOrgName = uniqueById
            .groupBy { "${it.firebaseOrgId.trim()}\u0000${it.name.trim()}" }
            .values
            .map { group ->
                pickPreferringTagged(
                    group,
                    { maxOf(it.lastModified, it.peopleCounterLastModified) },
                    { it.firebaseOrgId },
                )
            }
        val taggedNames = uniqueByOrgName
            .filter { it.firebaseOrgId.isNotBlank() }
            .map { it.name.trim() }
            .toSet()
        return uniqueByOrgName.filter { venue ->
            venue.firebaseOrgId.isNotBlank() || venue.name.trim() !in taggedNames
        }
    }

    private fun <T> pickPreferringTagged(
        group: List<T>,
        lastModifiedOf: (T) -> Long,
        orgIdOf: (T) -> String,
    ): T {
        if (group.size == 1) return group.first()
        return group.maxWithOrNull(
            compareBy<T> { lastModifiedOf(it) }.thenBy { if (orgIdOf(it).isNotBlank()) 1 else 0 },
        ) ?: group.first()
    }
}
