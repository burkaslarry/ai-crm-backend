package com.aicrm.controller

import com.aicrm.domain.MAX_SHORT
import com.aicrm.domain.MAX_TEXT
import com.aicrm.domain.sanitizeString
import com.aicrm.repository.LeadRepository
import com.aicrm.service.LeadService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/inquiries")
@RestController
class InquiriesController(
    private val leadRepository: LeadRepository,
    private val leadService: LeadService
) {

    @PostMapping
    fun create(@RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        val message = sanitizeString(body["message"], MAX_TEXT) ?: run {
            return ResponseEntity.badRequest().body(mapOf("error" to "message is required"))
        }
        val contact = sanitizeString(body["contact"]?.toString(), MAX_SHORT)
        val result = leadService.createInquiry(message, contact)
        val response = mapOf(
            "lead" to mapOf(
                "id" to result.lead.id,
                "channel" to result.lead.channel,
                "raw_message" to result.lead.rawMessage,
                "name" to result.lead.name,
                "contact" to result.lead.contact,
                "created_at" to result.lead.createdAt.toString(),
                "updated_at" to result.lead.updatedAt.toString(),
                "stage" to result.lead.stage,
                "owner_id" to result.lead.ownerId,
                "vertical" to result.lead.vertical,
                "source" to result.lead.source,
                "service_date" to result.lead.serviceDate
            ),
            "ai_triage" to result.ai_triage?.let { t ->
                mapOf(
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
            },
            "tasks" to result.tasks.map { t ->
                mapOf(
                    "id" to t.id,
                    "lead_id" to t.leadId,
                    "type" to t.type,
                    "title" to t.title,
                    "due_at" to t.dueAt?.toString(),
                    "completed_at" to t.completedAt?.toString(),
                    "created_at" to t.createdAt.toString()
                )
            }
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}
