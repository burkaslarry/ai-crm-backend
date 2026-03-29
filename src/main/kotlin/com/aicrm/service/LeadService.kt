package com.aicrm.service

import com.aicrm.domain.AiTriage
import com.aicrm.domain.CreateLeadRequest
import com.aicrm.domain.Lead
import com.aicrm.domain.PatchLeadRequest
import com.aicrm.repository.LeadRepository
import com.aicrm.util.uuid
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class LeadService(
    private val leadRepository: LeadRepository,
    private val triageService: TriageService,
    private val automationEngineService: AutomationEngineService,
    private val scheduledJobsService: ScheduledJobsService,
    private val objectMapper: ObjectMapper
) {

    fun createLead(req: CreateLeadRequest): LeadWithTriageAndTasks {
        val id = uuid()
        val now = Instant.now()
        val lead = Lead(
            id = id,
            channel = req.channel,
            rawMessage = req.raw_message,
            name = req.name,
            contact = req.contact,
            createdAt = now,
            updatedAt = now,
            stage = "New",
            ownerId = null,
            vertical = req.vertical,
            source = req.source,
            serviceDate = null
        )
        leadRepository.insert(lead)
        leadRepository.insertTimeline(uuid(), id, "created", objectMapper.writeValueAsString(mapOf("channel" to req.channel, "source" to req.source)))
        val triageResult = triageService.runTriage(req.raw_message, id)
        val aiTriage = triageService.toAiTriage(id, triageResult)
        leadRepository.insertOrReplaceTriage(aiTriage)
        leadRepository.updateVertical(id, triageResult.vertical)
        automationEngineService.applyAutomations(id)
        val updatedLead = leadRepository.findById(id)!!
        val triage = leadRepository.getTriage(id)
        val tasks = leadRepository.getTasks(id)
        return LeadWithTriageAndTasks(updatedLead, triage, tasks)
    }

    fun createInquiry(message: String, contact: String?): LeadWithTriageAndTasks {
        val id = uuid()
        val lead = Lead(
            id = id,
            channel = "whatsapp",
            rawMessage = message,
            name = null,
            contact = contact,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            stage = "New",
            ownerId = null,
            vertical = null,
            source = null,
            serviceDate = null
        )
        leadRepository.insert(lead)
        leadRepository.insertTimeline(uuid(), id, "whatsapp_paste", objectMapper.writeValueAsString(mapOf("contact" to contact)))
        val triageResult = triageService.runTriage(message, id)
        val aiTriage = triageService.toAiTriage(id, triageResult)
        leadRepository.insertOrReplaceTriage(aiTriage)
        leadRepository.updateVertical(id, triageResult.vertical)
        automationEngineService.applyAutomations(id)
        val updatedLead = leadRepository.findById(id)!!
        return LeadWithTriageAndTasks(updatedLead, leadRepository.getTriage(id), leadRepository.getTasks(id))
    }

    fun patchLead(id: String, req: PatchLeadRequest): Lead {
        if (req.stage != null) leadRepository.updateStage(id, req.stage)
        if (req.owner_id != null) leadRepository.updateOwner(id, req.owner_id)
        if (req.service_date != null) {
            leadRepository.updateServiceDate(id, req.service_date)
            if (req.service_date.isNotEmpty()) scheduledJobsService.scheduleJobsForLead(id, req.service_date)
        }
        if (req.updateVertical) leadRepository.updateVertical(id, req.vertical)
        return leadRepository.findById(id)!!
    }

    data class LeadWithTriageAndTasks(val lead: Lead, val ai_triage: AiTriage?, val tasks: List<com.aicrm.domain.Task>)
}
