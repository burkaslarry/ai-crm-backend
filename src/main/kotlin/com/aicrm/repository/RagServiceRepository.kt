package com.aicrm.repository

import com.aicrm.config.DbTableNames
import com.aicrm.domain.RagService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class RagServiceRepository(
    private val jdbc: JdbcTemplate,
    tableNames: DbTableNames
) {
    private val ragServicesTable = tableNames.table("rag_services")

    fun findAll(region: String? = null): List<RagService> =
        if (region != null) {
            jdbc.query("SELECT * FROM $ragServicesTable WHERE region = ? ORDER BY created_at DESC", rowMapper, region)
        } else {
            jdbc.query("SELECT * FROM $ragServicesTable ORDER BY created_at DESC", rowMapper)
        }

    fun searchByRegionAndKeyword(region: String?, keyword: String?): List<RagService> {
        val k = keyword?.let { "%${it.lowercase()}%" }
        if (region != null && k != null) {
            return jdbc.query(
                "SELECT * FROM $ragServicesTable WHERE region = ? AND (LOWER(name) LIKE LOWER(?) OR LOWER(COALESCE(description,'')) LIKE LOWER(?)) ORDER BY created_at DESC",
                rowMapper, region, "%$keyword%", "%$keyword%"
            )
        }
        if (region != null) return findAll(region)
        if (k != null) {
            return jdbc.query(
                "SELECT * FROM $ragServicesTable WHERE LOWER(name) LIKE LOWER(?) OR LOWER(COALESCE(description,'')) LIKE LOWER(?) ORDER BY created_at DESC",
                rowMapper, "%$keyword%", "%$keyword%"
            )
        }
        return findAll()
    }

    fun insert(s: RagService) {
        jdbc.update(
            "INSERT INTO $ragServicesTable (id, name, description, region) VALUES (?, ?, ?, ?)",
            s.id, s.name, s.description, s.region
        )
    }

    fun deleteAll() {
        jdbc.update("DELETE FROM $ragServicesTable")
    }

    private val rowMapper = RowMapper { rs, _ ->
        RagService(
            id = rs.getString("id"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            region = rs.getString("region"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
