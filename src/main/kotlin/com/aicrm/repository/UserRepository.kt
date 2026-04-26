package com.aicrm.repository

import com.aicrm.config.DbTableNames
import com.aicrm.domain.User
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class UserRepository(
    private val jdbc: JdbcTemplate,
    tableNames: DbTableNames
) {
    private val usersTable = tableNames.table("users")

    fun findAll(): List<User> =
        jdbc.query("SELECT * FROM $usersTable ORDER BY created_at", rowMapper)

    fun findById(id: String): User? =
        jdbc.query("SELECT * FROM $usersTable WHERE id = ?", rowMapper, id).firstOrNull()

    fun findByEmail(email: String): User? =
        jdbc.query("SELECT * FROM $usersTable WHERE email = ?", rowMapper, email).firstOrNull()

    fun insert(u: User) {
        jdbc.update(
            "INSERT INTO $usersTable (id, email, name, role) VALUES (?, ?, ?, ?)",
            u.id, u.email, u.name, u.role
        )
    }

    fun updateRole(id: String, role: String) {
        jdbc.update("UPDATE $usersTable SET role = ? WHERE id = ?", role, id)
    }

    private val rowMapper = RowMapper { rs, _ ->
        User(
            id = rs.getString("id"),
            email = rs.getString("email"),
            name = rs.getString("name"),
            role = rs.getString("role"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
