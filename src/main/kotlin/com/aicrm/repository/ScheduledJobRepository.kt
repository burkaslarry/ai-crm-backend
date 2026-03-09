package com.aicrm.repository

import com.aicrm.domain.ScheduledJob
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Repository
class ScheduledJobRepository(private val jdbc: JdbcTemplate) {

    fun deletePendingByLeadId(leadId: String) {
        jdbc.update("DELETE FROM scheduled_jobs WHERE lead_id = ? AND status = 'pending'", leadId)
    }

    fun insert(id: String, leadId: String, jobType: String, runAt: String, status: String = "pending") {
        val runAtTs = parseToTimestamp(runAt) ?: throw IllegalArgumentException("Invalid run_at format: $runAt")
        jdbc.update(
            "INSERT INTO scheduled_jobs (id, lead_id, job_type, run_at, status) VALUES (?, ?, ?, ?, ?)",
            id, leadId, jobType, runAtTs, status
        )
    }

    fun findDuePending(now: String): List<ScheduledJob> {
        val nowTs = parseToTimestamp(now) ?: return emptyList()
        return jdbc.query(
            "SELECT * FROM scheduled_jobs WHERE status = 'pending' AND run_at <= ? ORDER BY run_at",
            jobRowMapper, nowTs
        )
    }

    private fun parseToTimestamp(s: String?): Timestamp? {
        if (s.isNullOrBlank()) return null
        return try {
            Timestamp.from(Instant.parse(s))
        } catch (_: DateTimeParseException) {
            try {
                Timestamp.valueOf(LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            } catch (_: DateTimeParseException) {
                try {
                    Timestamp.valueOf(LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun markDone(id: String) {
        jdbc.update("UPDATE scheduled_jobs SET status = 'done' WHERE id = ?", id)
    }

    fun findAll(status: String? = null): List<ScheduledJob> =
        if (status == "pending") {
            jdbc.query("SELECT * FROM scheduled_jobs WHERE status = 'pending' ORDER BY run_at", jobRowMapper)
        } else {
            jdbc.query("SELECT * FROM scheduled_jobs ORDER BY run_at DESC LIMIT 200", jobRowMapper)
        }

    private val jobRowMapper = RowMapper { rs, _ ->
        ScheduledJob(
            id = rs.getString("id"),
            leadId = rs.getString("lead_id"),
            jobType = rs.getString("job_type"),
            runAt = rs.getTimestamp("run_at").toInstant(),
            status = rs.getString("status"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
