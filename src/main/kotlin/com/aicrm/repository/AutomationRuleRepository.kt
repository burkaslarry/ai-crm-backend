package com.aicrm.repository

import com.aicrm.config.DbTableNames
import com.aicrm.domain.AutomationRule
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class AutomationRuleRepository(
    private val jdbc: JdbcTemplate,
    @Value("\${app.db.dialect:h2}") private val dialect: String,
    tableNames: DbTableNames
) {
    private val automationRulesTable = tableNames.table("automation_rules")

    fun findAllByEnabledOrderBySortOrder(): List<AutomationRule> = jdbc.query(
        "SELECT * FROM $automationRulesTable WHERE enabled = 1 ORDER BY sort_order ASC",
        ruleRowMapper
    )

    fun findAllOrderBySortOrder(): List<AutomationRule> = jdbc.query(
        "SELECT * FROM $automationRulesTable ORDER BY sort_order",
        ruleRowMapper
    )

    fun count(): Long = jdbc.queryForObject("SELECT COUNT(*) FROM $automationRulesTable", Long::class.java) ?: 0L

    fun save(rule: AutomationRule) {
        if (dialect == "postgresql") {
            jdbc.update(
                """INSERT INTO $automationRulesTable (id, name, trigger_condition, actions, enabled, sort_order)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT (id) DO UPDATE SET
                   name = EXCLUDED.name, trigger_condition = EXCLUDED.trigger_condition,
                   actions = EXCLUDED.actions, enabled = EXCLUDED.enabled, sort_order = EXCLUDED.sort_order""",
                rule.id, rule.name, rule.triggerCondition, rule.actions, rule.enabled, rule.sortOrder
            )
        } else {
            jdbc.update(
                """MERGE INTO $automationRulesTable (id, name, trigger_condition, actions, enabled, sort_order)
                   KEY(id) VALUES (?, ?, ?, ?, ?, ?)""",
                rule.id, rule.name, rule.triggerCondition, rule.actions, rule.enabled, rule.sortOrder
            )
        }
    }

    fun saveAll(rules: List<AutomationRule>) {
        rules.forEach { save(it) }
    }

    private val ruleRowMapper = RowMapper { rs, _ ->
        AutomationRule(
            id = rs.getString("id"),
            name = rs.getString("name"),
            triggerCondition = rs.getString("trigger_condition"),
            actions = rs.getString("actions"),
            enabled = rs.getInt("enabled"),
            sortOrder = rs.getInt("sort_order")
        )
    }
}
