package com.aicrm.service

import com.aicrm.domain.AutomationRule
import com.aicrm.repository.AutomationRuleRepository
import com.aicrm.repository.LeadRepository
import com.aicrm.util.uuid
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ApplyResult(val applied: List<AppliedAction>, val newStage: String?, val newOwner: String?)

data class AppliedAction(val rule: String, val action: String, val value: String? = null, val title: String? = null)

@Service
class AutomationEngineService(
    private val leadRepository: LeadRepository,
    private val ruleRepository: AutomationRuleRepository,
    private val objectMapper: ObjectMapper
) {

    fun getDefaultRules(): List<AutomationRule> = listOf(
        AutomationRule("rule-booking", "Booking intent",
            """{"intent":"book","hasTriage":true}""",
            """[{"type":"set_stage","value":"Offered Slots"},{"type":"assign_owner","valueByVertical":{"zomate_pt_1on1":"zomate-coach-lead","zomate_membership_trial":"zomate-front-desk","zomate_nutrition":"zomate-wellness","med_spa":"receptionist-a","training":"enrollment-staff"}},{"type":"create_task","taskType":"send_slots","title":"Send 3 available slots within 15 minutes","dueMinutes":15}]""",
            1, 10),
        AutomationRule("rule-missing-info", "Missing key info",
            """{"missingFieldsNotEmpty":true}""",
            """[{"type":"set_stage","value":"Needs Info"},{"type":"draft_message","askTopMissing":2}]""",
            1, 20),
        AutomationRule("rule-high-urgency", "High urgency (asap / safety)",
            """{"urgencyMin":80}""",
            """[{"type":"create_task","taskType":"call","title":"Call within 10 minutes","dueMinutes":10},{"type":"add_banner","value":"High urgency lead"}]""",
            1, 15),
        AutomationRule("rule-complaint", "Complaint / medical concern",
            """{"intent":"complaint","safetyEscalate":true}""",
            """[{"type":"set_stage","value":"Needs Info"},{"type":"assign_owner","value":"manager"},{"type":"add_banner","value":"Escalate"}]""",
            1, 5)
    )

    fun applyAutomations(leadId: String): ApplyResult {
        val lead = leadRepository.findById(leadId) ?: return ApplyResult(emptyList(), null, null)
        val triage = leadRepository.getTriage(leadId) ?: return ApplyResult(emptyList(), null, null)

        @Suppress("UNCHECKED_CAST")
        val missing = (objectMapper.readValue<List<*>>(triage.missingFields ?: "[]")).map { it.toString() }
        val vertical = triage.vertical ?: lead.vertical ?: "unknown"
        val intent = triage.intent ?: "info"
        val urgency = triage.urgencyScore ?: 0
        val safetyEscalate = triage.safetyEscalate == 1

        val rules = ruleRepository.findAllByEnabledOrderBySortOrder()
        val applied = mutableListOf<AppliedAction>()
        var newStage: String? = lead.stage
        var newOwner: String? = lead.ownerId

        for (rule in rules) {
            val cond = objectMapper.readValue<Map<String, Any?>>(rule.triggerCondition)
            var match = false
            if (cond["intent"] == intent && cond["hasTriage"] == true) match = true
            if (cond["missingFieldsNotEmpty"] == true && missing.isNotEmpty()) match = true
            if (cond["urgencyMin"] != null && urgency >= (cond["urgencyMin"] as Number).toInt()) match = true
            if (cond["intent"] == "complaint" && cond["safetyEscalate"] == true && intent == "complaint" && safetyEscalate) match = true

            if (!match) continue

            @Suppress("UNCHECKED_CAST")
            val actions = objectMapper.readValue<List<Map<String, Any?>>>(rule.actions)
            for (action in actions) {
                when (action["type"]) {
                    "set_stage" -> {
                        val value = action["value"]?.toString() ?: continue
                        newStage = value
                        leadRepository.updateStage(leadId, value)
                        applied.add(AppliedAction(rule.name, "set_stage", value))
                    }
                    "assign_owner" -> {
                        val valueByVertical = action["valueByVertical"] as? Map<*, *>
                        var owner = (valueByVertical?.get(vertical) ?: action["value"])?.toString()
                        if (owner == null && vertical.startsWith("zomate_")) {
                            owner = "zomate-front-desk"
                        }
                        if (owner != null) {
                            newOwner = owner
                            leadRepository.updateOwner(leadId, owner)
                            applied.add(AppliedAction(rule.name, "assign_owner", owner))
                        }
                    }
                    "create_task" -> {
                        val taskType = action["taskType"]?.toString() ?: "general"
                        val title = action["title"]?.toString() ?: "Task"
                        val dueMin = (action["dueMinutes"] as? Number)?.toInt() ?: 15
                        val dueAt = LocalDateTime.now().plusMinutes(dueMin.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        leadRepository.insertTask(uuid(), leadId, taskType, title, dueAt)
                        applied.add(AppliedAction(rule.name, "create_task", title = title))
                    }
                }
            }
        }

        leadRepository.insertTimeline(uuid(), leadId, "automations_applied", objectMapper.writeValueAsString(mapOf("applied" to applied)))
        return ApplyResult(applied, newStage, newOwner)
    }
}
