package com.aicrm.service

import com.aicrm.repository.LeadRepository
import com.aicrm.repository.ScheduledJobRepository
import com.aicrm.util.uuid
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ScheduledJobsService(
    private val leadRepository: LeadRepository,
    private val jobRepository: ScheduledJobRepository
) {

    fun scheduleJobsForLead(leadId: String, serviceDateISO: String) {
        val date = try {
            LocalDate.parse(serviceDateISO)
        } catch (_: Exception) {
            return
        }
        jobRepository.deletePendingByLeadId(leadId)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        listOf(
            -2 to "reminder_2d",
            -1 to "reminder_24h",
            1 to "feedback_1d"
        ).forEach { (offset, jobType) ->
            val runAt = date.plusDays(offset.toLong()).atStartOfDay().format(formatter)
            jobRepository.insert(uuid(), leadId, jobType, runAt, "pending")
        }
    }

    fun processDueScheduledJobs(): List<ProcessedJob> {
        val now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val due = jobRepository.findDuePending(now)
        val processed = mutableListOf<ProcessedJob>()
        for (job in due) {
            val title = when (job.jobType) {
                "reminder_2d" -> "Send reminder: appointment in 2 days"
                "reminder_24h" -> "Send reminder: appointment in 24 hours"
                "feedback_1d" -> "Send feedback questionnaire (1 day after service)"
                else -> "Scheduled: ${job.jobType}"
            }
            val runAtStr = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(job.runAt.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
            leadRepository.insertTask(uuid(), job.leadId, job.jobType, title, runAtStr)
            leadRepository.insertTimeline(
                uuid(), job.leadId, "scheduled_job_processed",
                """{"job_type":"${job.jobType}","run_at":"${job.runAt}"}"""
            )
            jobRepository.markDone(job.id)
            processed.add(ProcessedJob(job.id, job.leadId, job.jobType))
        }
        return processed
    }

    data class ProcessedJob(val jobId: String, val leadId: String, val jobType: String)
}
