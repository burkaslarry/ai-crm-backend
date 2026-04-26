package com.aicrm.repository

import com.aicrm.config.DbTableNames
import com.aicrm.domain.FollowUpCase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class FollowUpCaseRepository(
    private val jdbc: JdbcTemplate,
    tableNames: DbTableNames
) {
    private val followUpCasesTable = tableNames.table("follow_up_cases")

    fun findAll(): List<FollowUpCase> =
        jdbc.query("SELECT * FROM $followUpCasesTable ORDER BY created_at DESC", rowMapper)

    fun insert(c: FollowUpCase) {
        jdbc.update(
            """INSERT INTO $followUpCasesTable (id, case_name, contact, status, notes, lead_ref)
               VALUES (?, ?, ?, ?, ?, ?)""",
            c.id, c.caseName, c.contact, c.status, c.notes, c.leadRef
        )
    }

    private val rowMapper = RowMapper { rs, _ ->
        FollowUpCase(
            id = rs.getString("id"),
            caseName = rs.getString("case_name"),
            contact = rs.getString("contact"),
            status = rs.getString("status"),
            notes = rs.getString("notes"),
            leadRef = rs.getString("lead_ref"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
