package com.aicrm.controller

import com.aicrm.domain.isValidLeadId
import com.aicrm.repository.AutomationRuleRepository
import com.aicrm.repository.ScheduledJobRepository
import com.aicrm.service.AutomationEngineService
import com.aicrm.service.ScheduledJobsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/automations")
@RestController
class AutomationsController(
    private val ruleRepository: AutomationRuleRepository,
    private val automationEngineService: AutomationEngineService,
    private val scheduledJobsService: ScheduledJobsService,
    private val scheduledJobRepository: ScheduledJobRepository
) {

    @GetMapping("/rules")
    fun getRules(): ResponseEntity<List<Map<String, Any?>>> {
        val rules = ruleRepository.findAllOrderBySortOrder()
        val list = rules.map { r ->
            mapOf(
                "id" to r.id,
                "name" to r.name,
                "trigger_condition" to r.triggerCondition,
                "actions" to r.actions,
                "enabled" to r.enabled,
                "sort_order" to r.sortOrder
            )
        }
        return ResponseEntity.ok(list)
    }

    @PostMapping("/rules/seed")
    fun seedRules(): ResponseEntity<List<Map<String, Any?>>> {
        val defaults = automationEngineService.getDefaultRules()
        ruleRepository.saveAll(defaults)
        val rules = ruleRepository.findAllOrderBySortOrder()
        val list = rules.map { r ->
            mapOf(
                "id" to r.id,
                "name" to r.name,
                "trigger_condition" to r.triggerCondition,
                "actions" to r.actions,
                "enabled" to r.enabled,
                "sort_order" to r.sortOrder
            )
        }
        return ResponseEntity.ok(list)
    }

    @PostMapping("/apply/{leadId}")
    fun apply(@PathVariable leadId: String): ResponseEntity<Any> {
        if (!isValidLeadId(leadId)) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid lead id"))
        val result = automationEngineService.applyAutomations(leadId)
        val response = mapOf(
            "applied" to result.applied.map { a ->
                mapOf(
                    "rule" to a.rule,
                    "action" to a.action,
                    "value" to a.value,
                    "title" to a.title
                )
            },
            "newStage" to result.newStage,
            "newOwner" to result.newOwner
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/process-scheduled")
    fun processScheduled(): ResponseEntity<Map<String, Any>> {
        val processed = scheduledJobsService.processDueScheduledJobs()
        val jobs = processed.map { mapOf("job_id" to it.jobId, "lead_id" to it.leadId, "job_type" to it.jobType) }
        return ResponseEntity.ok(mapOf("processed" to processed.size, "jobs" to jobs))
    }

    @GetMapping("/scheduled-jobs")
    fun scheduledJobs(@RequestParam(required = false) status: String?): ResponseEntity<List<Map<String, Any?>>> {
        val jobs = scheduledJobRepository.findAll(status)
        val list = jobs.map { j ->
            mapOf(
                "id" to j.id,
                "lead_id" to j.leadId,
                "job_type" to j.jobType,
                "run_at" to j.runAt.toString(),
                "status" to j.status,
                "created_at" to j.createdAt.toString()
            )
        }
        return ResponseEntity.ok(list)
    }
}
