package com.aicrm.repository

import com.aicrm.config.DbTableNames
import com.aicrm.domain.AiTriage
import com.aicrm.domain.Lead
import com.aicrm.domain.SlotSuggestion
import com.aicrm.domain.Task
import com.aicrm.domain.TimelineEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Repository
class LeadRepository(
    private val jdbc: JdbcTemplate,
    @Value("\${app.db.dialect:h2}") private val dialect: String,
    private val tableNames: DbTableNames
) {
    private val leadsTable = tableNames.table("leads")
    private val triageTable = tableNames.table("ai_triage")
    private val tasksTable = tableNames.table("tasks")
    private val timelineTable = tableNames.table("timeline")
    private val slotSuggestionsTable = tableNames.table("slot_suggestions")

    fun findAll(channel: String? = null, stage: String? = null): List<Lead> {
        return when {
            channel != null && stage != null -> jdbc.query(
                "SELECT * FROM $leadsTable WHERE channel = ? AND stage = ? ORDER BY created_at DESC",
                leadRowMapper, channel, stage
            )
            channel != null -> jdbc.query(
                "SELECT * FROM $leadsTable WHERE channel = ? ORDER BY created_at DESC",
                leadRowMapper, channel
            )
            stage != null -> jdbc.query(
                "SELECT * FROM $leadsTable WHERE stage = ? ORDER BY created_at DESC",
                leadRowMapper, stage
            )
            else -> jdbc.query("SELECT * FROM $leadsTable ORDER BY created_at DESC", leadRowMapper)
        }
    }

    fun findById(id: String): Lead? = jdbc.query(
        "SELECT * FROM $leadsTable WHERE id = ?",
        leadRowMapper, id
    ).firstOrNull()

    fun countLeads(): Long = jdbc.queryForObject("SELECT COUNT(*) FROM $leadsTable", Long::class.java) ?: 0L

    fun insert(lead: Lead) {
        jdbc.update(
            """INSERT INTO $leadsTable (id, channel, raw_message, name, contact, stage, vertical, source, service_date)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            lead.id, lead.channel, lead.rawMessage, lead.name, lead.contact, lead.stage, lead.vertical, lead.source, lead.serviceDate
        )
    }

    fun updateStage(id: String, stage: String) {
        jdbc.update("UPDATE $leadsTable SET stage = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", stage, id)
    }

    fun updateOwner(id: String, ownerId: String?) {
        jdbc.update("UPDATE $leadsTable SET owner_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", ownerId, id)
    }

    fun updateServiceDate(id: String, serviceDate: String?) {
        jdbc.update("UPDATE $leadsTable SET service_date = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", serviceDate, id)
    }

    fun updateVertical(id: String, vertical: String?) {
        jdbc.update("UPDATE $leadsTable SET vertical = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", vertical, id)
    }

    fun getTriage(leadId: String): AiTriage? = jdbc.query(
        "SELECT * FROM $triageTable WHERE lead_id = ?",
        triageRowMapper, leadId
    ).firstOrNull()

    fun insertOrReplaceTriage(t: AiTriage) {
        if (dialect == "postgresql") {
            jdbc.update(
                """INSERT INTO $triageTable (lead_id, vertical, category, subcategory, intent, urgency_score,
                   extracted_fields, missing_fields, summary, recommended_actions, safety_escalate)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT (lead_id) DO UPDATE SET
                   vertical = EXCLUDED.vertical, category = EXCLUDED.category, subcategory = EXCLUDED.subcategory,
                   intent = EXCLUDED.intent, urgency_score = EXCLUDED.urgency_score,
                   extracted_fields = EXCLUDED.extracted_fields, missing_fields = EXCLUDED.missing_fields,
                   summary = EXCLUDED.summary, recommended_actions = EXCLUDED.recommended_actions,
                   safety_escalate = EXCLUDED.safety_escalate""",
                t.leadId, t.vertical, t.category, t.subcategory, t.intent, t.urgencyScore,
                t.extractedFields, t.missingFields, t.summary, t.recommendedActions, t.safetyEscalate
            )
        } else {
            jdbc.update(
                """MERGE INTO $triageTable (lead_id, vertical, category, subcategory, intent, urgency_score,
                   extracted_fields, missing_fields, summary, recommended_actions, safety_escalate)
                   KEY(lead_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                t.leadId, t.vertical, t.category, t.subcategory, t.intent, t.urgencyScore,
                t.extractedFields, t.missingFields, t.summary, t.recommendedActions, t.safetyEscalate
            )
        }
    }

    fun getTasks(leadId: String): List<Task> = jdbc.query(
        "SELECT * FROM $tasksTable WHERE lead_id = ? ORDER BY due_at",
        taskRowMapper, leadId
    )

    fun getTimeline(leadId: String): List<TimelineEvent> = jdbc.query(
        "SELECT * FROM $timelineTable WHERE lead_id = ? ORDER BY created_at DESC",
        timelineRowMapper, leadId
    )

    fun getLatestSlotSuggestion(leadId: String): SlotSuggestion? = jdbc.query(
        "SELECT * FROM $slotSuggestionsTable WHERE lead_id = ? ORDER BY created_at DESC LIMIT 1",
        slotRowMapper, leadId
    ).firstOrNull()

    fun insertTimeline(id: String, leadId: String, eventType: String, payload: String?) {
        jdbc.update(
            "INSERT INTO $timelineTable (id, lead_id, event_type, payload) VALUES (?, ?, ?, ?)",
            id, leadId, eventType, payload
        )
    }

    fun insertTask(id: String, leadId: String, type: String, title: String, dueAt: String?) {
        val dueTs = parseToTimestamp(dueAt)
        jdbc.update(
            "INSERT INTO $tasksTable (id, lead_id, type, title, due_at) VALUES (?, ?, ?, ?, ?)",
            id, leadId, type, title, dueTs
        )
    }

    /** Parse string to java.sql.Timestamp so Postgres receives timestamp type, not varchar. */
    private fun parseToTimestamp(s: String?): Timestamp? {
        if (s.isNullOrBlank()) return null
        return try {
            Timestamp.from(Instant.parse(s))
        } catch (_: DateTimeParseException) {
            try {
                val ldt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Timestamp.valueOf(ldt)
            } catch (_: DateTimeParseException) {
                try {
                    Timestamp.valueOf(LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun completeTask(taskId: String, leadId: String) {
        jdbc.update(
            "UPDATE $tasksTable SET completed_at = CURRENT_TIMESTAMP WHERE id = ? AND lead_id = ?",
            taskId, leadId
        )
    }

    fun getTask(taskId: String): Task? = jdbc.query(
        "SELECT * FROM $tasksTable WHERE id = ?",
        taskRowMapper, taskId
    ).firstOrNull()

    fun insertSlotSuggestion(id: String, leadId: String, slotsJson: String) {
        jdbc.update("INSERT INTO $slotSuggestionsTable (id, lead_id, slots) VALUES (?, ?, ?)", id, leadId, slotsJson)
    }

    private val leadRowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
        Lead(
            id = rs.getString("id"),
            channel = rs.getString("channel"),
            rawMessage = rs.getString("raw_message"),
            name = rs.getString("name"),
            contact = rs.getString("contact"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            stage = rs.getString("stage"),
            ownerId = rs.getString("owner_id"),
            vertical = rs.getString("vertical"),
            source = rs.getString("source"),
            serviceDate = rs.getString("service_date")
        )
    }

    private val triageRowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
        AiTriage(
            leadId = rs.getString("lead_id"),
            vertical = rs.getString("vertical"),
            category = rs.getString("category"),
            subcategory = rs.getString("subcategory"),
            intent = rs.getString("intent"),
            urgencyScore = rs.getObject("urgency_score") as? Int,
            extractedFields = rs.getString("extracted_fields"),
            missingFields = rs.getString("missing_fields"),
            summary = rs.getString("summary"),
            recommendedActions = rs.getString("recommended_actions"),
            safetyEscalate = rs.getInt("safety_escalate")
        )
    }

    private val taskRowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
        Task(
            id = rs.getString("id"),
            leadId = rs.getString("lead_id"),
            type = rs.getString("type"),
            title = rs.getString("title"),
            dueAt = rs.getTimestamp("due_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val timelineRowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
        TimelineEvent(
            id = rs.getString("id"),
            leadId = rs.getString("lead_id"),
            eventType = rs.getString("event_type"),
            payload = rs.getString("payload"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private val slotRowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
        SlotSuggestion(
            id = rs.getString("id"),
            leadId = rs.getString("lead_id"),
            slots = rs.getString("slots"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
