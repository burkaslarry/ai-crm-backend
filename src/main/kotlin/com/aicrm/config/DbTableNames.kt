package com.aicrm.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DbTableNames(
    @Value("\${app.db.table-prefix:}") rawTablePrefix: String
) {
    private val tablePrefix = rawTablePrefix.trim()

    fun table(name: String): String = qualify(tablePrefix, name)

    fun schemaSql(sql: String): String = applyPrefix(sql, tablePrefix)

    companion object {
        val TABLES =
            listOf(
                "leads",
                "ai_triage",
                "tasks",
                "timeline",
                "automation_rules",
                "slot_suggestions",
                "scheduled_jobs",
                "rag_services",
                "rag_products",
                "follow_up_cases",
                "rag_documents",
                "rag_document_links",
                "users",
            )

        private val INDEXES =
            listOf(
                "idx_leads_stage",
                "idx_leads_channel",
                "idx_leads_created",
                "idx_leads_service_date",
                "idx_tasks_lead",
                "idx_timeline_lead",
                "idx_scheduled_jobs_run_at",
                "idx_scheduled_jobs_status",
                "idx_rag_services_region",
                "idx_rag_products_region",
                "idx_follow_up_cases_status",
                "idx_follow_up_cases_created",
                "idx_rag_document_links_document",
                "idx_rag_document_links_item",
                "idx_users_role",
            )

        fun qualify(prefix: String, name: String): String {
            val normalized = prefix.trim()
            return if (normalized.isBlank()) name else "${normalized}${name}"
        }

        fun applyPrefix(sql: String, prefix: String): String {
            val normalized = prefix.trim()
            if (normalized.isBlank()) return sql

            var transformed = sql
            (TABLES + INDEXES).sortedByDescending { it.length }.forEach { name ->
                transformed = transformed.replace(
                    Regex("\\b${Regex.escape(name)}\\b"),
                    qualify(normalized, name),
                )
            }
            return transformed
        }
    }
}
