package com.aicrm.controller

import com.aicrm.domain.MAX_TEXT
import com.aicrm.domain.sanitizeString
import com.aicrm.repository.LeadRepository
import com.aicrm.service.DraftTemplateService
import com.aicrm.service.TriageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/ai")
@RestController
class AiController(
    private val leadRepository: LeadRepository,
    private val triageService: TriageService,
    private val draftTemplateService: DraftTemplateService
) {

    private val maxMessageLen = 10000

    @PostMapping("/triage")
    fun triage(@RequestBody body: Map<String, Any?>): ResponseEntity<Any> {
        var message = body["rawMessage"]?.toString()?.let { sanitizeString(it, maxMessageLen) }
        val leadId = body["leadId"]?.toString()
        if (message == null && leadId != null) {
            val row = leadRepository.findById(leadId)
            message = row?.rawMessage?.let { sanitizeString(it, maxMessageLen) }
        }
        if (message == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "rawMessage or leadId with existing message required"))
        }
        val triageResult = triageService.runTriage(message, leadId ?: "")
        if (leadId != null) {
            val aiTriage = triageService.toAiTriage(leadId, triageResult)
            leadRepository.insertOrReplaceTriage(aiTriage)
        }
        val response = mapOf(
            "vertical" to triageResult.vertical,
            "category" to triageResult.category,
            "subcategory" to triageResult.subcategory,
            "intent" to triageResult.intent,
            "urgencyScore" to triageResult.urgencyScore,
            "extractedFields" to triageResult.extractedFields,
            "missingFields" to triageResult.missingFields,
            "summary" to triageResult.summary,
            "recommendedActions" to triageResult.recommendedActions,
            "safetyEscalate" to triageResult.safetyEscalate
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/draft")
    fun draft(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        val vertical = sanitizeString(body["vertical"]?.toString(), 50) ?: "med_spa"
        val intent = sanitizeString(body["intent"]?.toString(), 50) ?: "info"
        val slotsRaw = body["slots"]
        val slotsStr = when (slotsRaw) {
            is List<*> -> slotsRaw.map { sanitizeString(it?.toString(), 200) }.filterNotNull().filter { it.isNotEmpty() }.joinToString("\n")
            else -> sanitizeString(slotsRaw?.toString(), 500) ?: ""
        }
        val vars = mapOf(
            "name" to (sanitizeString(body["name"]?.toString(), 200) ?: ""),
            "service" to (sanitizeString(body["service"]?.toString(), 200) ?: ""),
            "location" to (sanitizeString(body["location"]?.toString(), 200) ?: ""),
            "slots" to slotsStr,
            "bookingLink" to (sanitizeString(body["bookingLink"]?.toString(), 500) ?: "[Booking link]"),
            "service_date" to (sanitizeString(body["service_date"]?.toString(), 20) ?: "")
        )
        val draft = draftTemplateService.getDraft(vertical, intent, vars)
        return ResponseEntity.ok(mapOf("draft" to draft))
    }
}
