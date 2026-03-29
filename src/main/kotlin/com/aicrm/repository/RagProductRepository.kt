package com.aicrm.repository

import com.aicrm.domain.RagProduct
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class RagProductRepository(private val jdbc: JdbcTemplate) {

    fun findAll(region: String? = null): List<RagProduct> =
        if (region != null) {
            jdbc.query("SELECT * FROM rag_products WHERE region = ? ORDER BY created_at DESC", rowMapper, region)
        } else {
            jdbc.query("SELECT * FROM rag_products ORDER BY created_at DESC", rowMapper)
        }

    fun searchByRegionAndKeyword(region: String?, keyword: String?): List<RagProduct> {
        val keywordParam = keyword?.let { "%$it%" }
        if (region != null && keywordParam != null) {
            return jdbc.query(
                "SELECT * FROM rag_products WHERE region = ? AND (LOWER(name) LIKE LOWER(?) OR LOWER(COALESCE(description,'')) LIKE LOWER(?)) ORDER BY created_at DESC",
                rowMapper, region, keywordParam, keywordParam
            )
        }
        if (region != null) return findAll(region)
        if (keywordParam != null) {
            return jdbc.query(
                "SELECT * FROM rag_products WHERE LOWER(name) LIKE LOWER(?) OR LOWER(COALESCE(description,'')) LIKE LOWER(?) ORDER BY created_at DESC",
                rowMapper, keywordParam, keywordParam
            )
        }
        return findAll()
    }

    fun insert(p: RagProduct) {
        jdbc.update(
            "INSERT INTO rag_products (id, name, description, region) VALUES (?, ?, ?, ?)",
            p.id, p.name, p.description, p.region
        )
    }

    fun deleteAll() {
        jdbc.update("DELETE FROM rag_products")
    }

    private val rowMapper = RowMapper { rs, _ ->
        RagProduct(
            id = rs.getString("id"),
            name = rs.getString("name"),
            description = rs.getString("description"),
            region = rs.getString("region"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
