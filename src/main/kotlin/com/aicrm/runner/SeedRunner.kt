package com.aicrm.runner

import com.aicrm.domain.Lead
import com.aicrm.repository.AutomationRuleRepository
import com.aicrm.repository.LeadRepository
import com.aicrm.service.AutomationEngineService
import com.aicrm.service.TriageService
import com.aicrm.util.uuid
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.Instant

/** Seeds automation rules and sample leads via CRM repositories only — never touches `bni_anchor_*`. */
@Component
@Order(1)
class SeedRunner(
    private val ruleRepository: AutomationRuleRepository,
    private val leadRepository: LeadRepository,
    private val triageService: TriageService,
    private val automationEngineService: AutomationEngineService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) {
        if (ruleRepository.count() == 0L) {
            log.info("Seeding default automation rules.")
            ruleRepository.saveAll(automationEngineService.getDefaultRules())
        }
        if (leadRepository.countLeads() > 0L) {
            log.info("Skip sample leads: database already has {} lead(s).", leadRepository.countLeads())
            return
        }
        log.info("Seeding Zomate Fitness sample leads.")
        val sampleInquiries = listOf(
            Sample(
                "whatsapp",
                "想預約一對一女教練，尖沙咀週六朝早得唔得？想知收費同試堂。",
                "+852 9515 7454",
                "陳小姐"
            ),
            Sample(
                "whatsapp",
                "Hi, I'd like a trial at Sheung Wan — women-only gym. What slots this week?",
                "+852 9123 8899",
                null
            ),
            Sample(
                "web",
                "請問會員計劃包唔包營養建議？想報名但想先了解上環分店時間。",
                "wong@example.com",
                "黃小姐"
            ),
            Sample(
                "web",
                "生酮飲食配合你哋健身課程有冇建議？純查詢，未決定報名。",
                "+852 6888 1234",
                "李小姐"
            ),
            Sample(
                "whatsapp",
                "上環週二晚上有冇女教練一對一？想減脂塑形。",
                "+852 9777 0001",
                null
            ),
            Sample(
                "web",
                "從 zoesportdiary 見到你哋，想 WhatsApp 問下柯士甸道附近點去。想約體驗。",
                "visitor@demo.com",
                "Amy"
            )
        )
        for (s in sampleInquiries) {
            val id = uuid()
            val now = Instant.now()
            val lead = Lead(
                id = id,
                channel = s.channel,
                rawMessage = s.raw,
                name = s.name,
                contact = s.contact,
                createdAt = now,
                updatedAt = now,
                stage = "New",
                ownerId = null,
                vertical = null,
                source = "zomate_demo",
                serviceDate = null
            )
            leadRepository.insert(lead)
            leadRepository.insertTimeline(uuid(), id, if (s.channel == "web") "created" else "whatsapp_paste", "{}")
            val triageResult = triageService.runTriage(s.raw, id)
            leadRepository.insertOrReplaceTriage(triageService.toAiTriage(id, triageResult))
            leadRepository.updateVertical(id, triageResult.vertical)
            automationEngineService.applyAutomations(id)
        }
        log.info("Seed done: Zomate Fitness demo — {} sample leads.", sampleInquiries.size)
    }

    private data class Sample(val channel: String, val raw: String, val contact: String?, val name: String?)
}
