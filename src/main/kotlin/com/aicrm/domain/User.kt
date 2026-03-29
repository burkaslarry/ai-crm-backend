package com.aicrm.domain

import java.time.Instant

data class User(
    val id: String,
    val email: String,
    val name: String?,
    val role: String,
    val createdAt: Instant
) {
    fun isSuperAdmin(): Boolean = role == "superadmin"
    fun isOperator(): Boolean = role == "operator" || role == "superadmin"
}
