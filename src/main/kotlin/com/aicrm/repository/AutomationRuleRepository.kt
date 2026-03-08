package com.aicrm.repository

import com.aicrm.domain.AutomationRule
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class AutomationRuleRepository(private val jdbc: JdbcTemplate) {

    fun findAllByEnabledOrderBySortOrder(): List<AutomationRule> = jdbc.query(
        "SELECT * FROM automation_rules WHERE enabled = 1 ORDER BY sort_order ASC",
        ruleRowMapper
    )

    fun findAllOrderBySortOrder(): List<AutomationRule> = jdbc.query(
        "SELECT * FROM automation_rules ORDER BY sort_order",
        ruleRowMapper
    )

    fun count(): Long = jdbc.queryForObject("SELECT COUNT(*) FROM automation_rules", Long::class.java) ?: 0L

    fun save(rule: AutomationRule) {
        jdbc.update(
            """MERGE INTO automation_rules (id, name, trigger_condition, actions, enabled, sort_order)
               KEY(id) VALUES (?, ?, ?, ?, ?, ?)""",
            rule.id, rule.name, rule.triggerCondition, rule.actions, rule.enabled, rule.sortOrder
        )
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
