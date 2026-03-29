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
        if (ruleRepository.count() > 0) return
        log.info("Seeding default rules and sample leads.")
        ruleRepository.saveAll(automationEngineService.getDefaultRules())
        val sampleInquiries = listOf(
            Sample("whatsapp", "Hi can I book laser hair removal this week? Prefer Thu after 6pm. Price?", "+852 9123 4567", null),
            Sample("whatsapp", "I did facial yesterday, today redness and swelling. What should I do?", "+852 9876 5432", null),
            Sample("web", "想預約醫美諮詢，激光脫毛同面部療程，請問價錢同可約時間？地點銅鑼灣。", "chan@email.com", "陳小姐"),
            Sample("web", "請問雷射淡斑療程幾錢？想約下星期，中環或尖沙咀分店都可以。", "+852 6111 2222", "Wong"),
            Sample("whatsapp", "I want to join beginner nail course. Weekend only. When's the next intake? Cost?", "nails@mail.com", null),
            Sample("web", "想了解減肥針療程，第一次做有咩要注意？可否安排星期六。", "+852 9555 6666", "Lee"),
            Sample("whatsapp", "Can I get package pricing for acne facial + serum bundle?", "client7@example.com", "Yuki"),
            Sample("web", "想報名進階美甲班，平日晚間班是否有位？", "student@demo.com", "Mandy")
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
                source = null,
                serviceDate = null
            )
            leadRepository.insert(lead)
            leadRepository.insertTimeline(uuid(), id, if (s.channel == "web") "created" else "whatsapp_paste", "{}")
            val triageResult = triageService.runTriage(s.raw, id)
            leadRepository.insertOrReplaceTriage(triageService.toAiTriage(id, triageResult))
            leadRepository.updateVertical(id, triageResult.vertical)
            automationEngineService.applyAutomations(id)
        }
        log.info("Seed done: rules + {} sample leads.", sampleInquiries.size)
    }

    private data class Sample(val channel: String, val raw: String, val contact: String?, val name: String?)
}
