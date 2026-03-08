package com.aicrm.controller

import com.aicrm.domain.CreateLeadRequest
import com.aicrm.domain.PatchLeadRequest
import com.aicrm.domain.isValidLeadId
import com.aicrm.domain.sanitizeString
import com.aicrm.domain.validateChannel
import com.aicrm.domain.validateISODate
import com.aicrm.domain.validateStage
import com.aicrm.repository.LeadRepository
import com.aicrm.service.LeadService
import com.aicrm.util.uuid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/leads")
@RestController
class LeadsController(
    private val leadRepository: LeadRepository,
    private val leadService: LeadService
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) stage: String?
    ): List<Map<String, Any?>> {
        val ch = if (channel != null) validateChannel(channel) else null
        val st = if (stage != null) validateStage(stage) else null
        val leads = leadRepository.findAll(ch, st)
        return leads.map { leadToMap(it) }
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> {
        if (!isValidLeadId(id)) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid lead id"))
        val lead = leadRepository.findById(id) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Lead not found"))
        val triage = leadRepository.getTriage(id)
        val tasks = leadRepository.getTasks(id)
        val timeline = leadRepository.getTimeline(id)
        val slots = leadRepository.getLatestSlotSuggestion(id)
        val map = mutableMapOf<String, Any?>()
        map.putAll(leadToMap(lead))
        map["ai_triage"] = triage?.let { triageToMap(it) }
        map["tasks"] = tasks.map { taskToMap(it) }
        map["timeline"] = timeline.map { timelineToMap(it) }
        map["slot_suggestions"] = slots?.let { slotToMap(it) }
        return ResponseEntity.ok(map)
    }

    @PostMapping
    fun create(@RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        val req = try {
            validateCreateLead(body)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid body")))
        }
        val result = leadService.createLead(req)
        val response = mapOf(
            "lead" to leadToMap(result.lead),
            "ai_triage" to result.ai_triage?.let { triageToMap(it) },
            "tasks" to result.tasks.map { taskToMap(it) }
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{id}")
    fun patch(@PathVariable id: String, @RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        if (!isValidLeadId(id)) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid lead id"))
        if (leadRepository.findById(id) == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Lead not found"))
        val req = parsePatchLead(body)
        val updated = leadService.patchLead(id, req)
        return ResponseEntity.ok(leadToMap(updated))
    }

    @PostMapping("/{id}/slots")
    fun addSlots(@PathVariable id: String, @RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        if (!isValidLeadId(id)) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid lead id"))
        if (leadRepository.findById(id) == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Lead not found"))
        val slots = validateSlots(body["slots"])
        val slotId = uuid()
        leadRepository.insertSlotSuggestion(slotId, id, com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(slots))
        leadRepository.updateStage(id, "Offered Slots")
        leadRepository.insertTimeline(uuid(), id, "slots_offered", com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mapOf("slots" to slots)))
        val slot = leadRepository.getLatestSlotSuggestion(id)!!
        return ResponseEntity.status(HttpStatus.CREATED).body(slotToMap(slot))
    }

    @PostMapping("/{id}/tasks/{taskId}/complete")
    fun completeTask(@PathVariable id: String, @PathVariable taskId: String): ResponseEntity<Any> {
        if (!isValidLeadId(id)) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid lead id"))
        leadRepository.completeTask(taskId, id)
        val task = leadRepository.getTask(taskId) ?: return ResponseEntity.ok(emptyMap<String, Any>())
        return ResponseEntity.ok(taskToMap(task))
    }

    private fun leadToMap(lead: com.aicrm.domain.Lead) = mapOf(
        "id" to lead.id,
        "channel" to lead.channel,
        "raw_message" to lead.rawMessage,
        "name" to lead.name,
        "contact" to lead.contact,
        "created_at" to lead.createdAt.toString(),
        "updated_at" to lead.updatedAt.toString(),
        "stage" to lead.stage,
        "owner_id" to lead.ownerId,
        "vertical" to lead.vertical,
        "source" to lead.source,
        "service_date" to lead.serviceDate
    )

    private fun triageToMap(t: com.aicrm.domain.AiTriage) = mapOf(
        "lead_id" to t.leadId,
        "vertical" to t.vertical,
        "category" to t.category,
        "subcategory" to t.subcategory,
        "intent" to t.intent,
        "urgency_score" to t.urgencyScore,
        "extracted_fields" to t.extractedFields,
        "missing_fields" to t.missingFields,
        "summary" to t.summary,
        "recommended_actions" to t.recommendedActions,
        "safety_escalate" to (t.safetyEscalate == 1)
    )

    private fun taskToMap(t: com.aicrm.domain.Task) = mapOf(
        "id" to t.id,
        "lead_id" to t.leadId,
        "type" to t.type,
        "title" to t.title,
        "due_at" to t.dueAt?.toString(),
        "completed_at" to t.completedAt?.toString(),
        "created_at" to t.createdAt.toString()
    )

    private fun timelineToMap(e: com.aicrm.domain.TimelineEvent) = mapOf(
        "id" to e.id,
        "lead_id" to e.leadId,
        "event_type" to e.eventType,
        "payload" to e.payload,
        "created_at" to e.createdAt.toString()
    )

    private fun slotToMap(s: com.aicrm.domain.SlotSuggestion) = mapOf(
        "id" to s.id,
        "lead_id" to s.leadId,
        "slots" to s.slots,
        "created_at" to s.createdAt.toString()
    )

    private fun validateCreateLead(body: Any?): CreateLeadRequest {
        if (body !is Map<*, *>) throw IllegalArgumentException("Invalid body")
        val raw = sanitizeString(body["raw_message"], com.aicrm.domain.MAX_TEXT) ?: throw IllegalArgumentException("raw_message is required")
        return CreateLeadRequest(
            channel = validateChannel(body["channel"]?.toString()) ?: "web",
            raw_message = raw,
            name = sanitizeString(body["name"], com.aicrm.domain.MAX_SHORT),
            contact = sanitizeString(body["contact"], com.aicrm.domain.MAX_SHORT),
            vertical = sanitizeString(body["vertical"]?.toString(), 50),
            source = sanitizeString(body["source"]?.toString(), com.aicrm.domain.MAX_SHORT)
        )
    }

    private fun parsePatchLead(body: Any?): PatchLeadRequest {
        if (body !is Map<*, *>) return PatchLeadRequest(null, null, null)
        return PatchLeadRequest(
            stage = validateStage(body["stage"]?.toString()),
            owner_id = body["owner_id"]?.let { sanitizeString(it.toString(), com.aicrm.domain.MAX_SHORT) },
            service_date = validateISODate(body["service_date"]?.toString())
        )
    }

    private fun validateSlots(slots: Any?): List<String> {
        if (slots !is List<*>) return emptyList()
        return slots.take(20).mapNotNull { sanitizeString(it?.toString(), 200) }.filter { it.isNotEmpty() }
    }
}
