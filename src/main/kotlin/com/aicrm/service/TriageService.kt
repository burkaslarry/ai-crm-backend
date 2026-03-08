package com.aicrm.service

import com.aicrm.domain.AiTriage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

data class TriageResult(
    val vertical: String,
    val category: String,
    val subcategory: String?,
    val intent: String,
    val urgencyScore: Int,
    val extractedFields: Map<String, Any?>,
    val missingFields: List<String>,
    val summary: String,
    val recommendedActions: List<String>,
    val safetyEscalate: Boolean
)

@Service
class TriageService(
    private val normalizationService: NormalizationService,
    private val objectMapper: ObjectMapper
) {

    private val verticalKeywords = mapOf(
        "med_spa" to listOf(
            "laser", "facial", "hair removal", "consultation", "skin", "aesthetic", "clinic", "med spa", "treatment",
            "脫毛", "面部", "激光", "美容", "療程", "皮膚", "諮詢", "醫美", "pigment", "acne", "slimming",
            "雷射", "微針", "填充", "肉毒", "玻尿酸", "皮膚科", "美容醫學"
        ),
        "training" to listOf(
            "course", "training", "class", "certification", "learn", "intake", "nail course", "beauty course",
            "課程", "培訓", "學", "證書", "入讀", "美甲", "美容課程"
        )
    )

    private val intentKeywords = mapOf(
        "book" to listOf("book", "booking", "預約", "想約", "想請", "join", "報名", "想上", "schedule", "appointment"),
        "price" to listOf("price", "cost", "fee", "價錢", "幾錢", "費用", "how much", "budget", "預算"),
        "info" to listOf("info", "information", "了解", "想知", "介紹", "what do you", "inquiry", "查詢"),
        "complaint" to listOf("complaint", "problem", "issue", "redness", "swelling", "pain", "不適", "紅腫", "痛", "有問題", "what should i do"),
        "reschedule" to listOf("reschedule", "change", "改期", "取消", "cancel", "postpone")
    )

    private val safetyRedFlags = listOf(
        "severe allergic", "anaphylaxis", "fainting", "faint", "breathing", "chest pain", "severe infection",
        "紅腫", "腫脹", "過敏", "呼吸", "胸痛", "頭暈", "暈倒", "發燒", "感染", "發炎", "redness and swelling",
        "what should i do", "emergency", "urgent"
    )

    fun runTriage(rawMessage: String, leadId: String): TriageResult {
        val vertical = detectVertical(rawMessage)
        val intent = detectIntent(rawMessage)
        val extracted = normalizationService.normalizeFields(rawMessage, vertical)
        val missing = normalizationService.getMissingFields(extracted, vertical)
        val urgency = urgencyScore(rawMessage, vertical, intent, extracted)
        val escalate = safetyEscalate(rawMessage)
        val summary = buildSummary(vertical, intent, extracted, missing)
        val actions = recommendedActions(intent, missing, escalate, vertical)

        val category = vertical
        val subcategory = extracted.serviceName

        val extractedMap = mapOf(
            "preferredDates" to extracted.preferredDates,
            "preferredTime" to extracted.preferredTime,
            "location" to extracted.location,
            "serviceName" to extracted.serviceName
        ).filterValues { it != null }

        return TriageResult(
            vertical = vertical,
            category = category,
            subcategory = subcategory,
            intent = intent,
            urgencyScore = urgency,
            extractedFields = extractedMap,
            missingFields = missing,
            summary = summary,
            recommendedActions = actions,
            safetyEscalate = escalate
        )
    }

    fun toAiTriage(leadId: String, r: TriageResult): AiTriage = AiTriage(
        leadId = leadId,
        vertical = r.vertical,
        category = r.category,
        subcategory = r.subcategory,
        intent = r.intent,
        urgencyScore = r.urgencyScore,
        extractedFields = objectMapper.writeValueAsString(r.extractedFields),
        missingFields = objectMapper.writeValueAsString(r.missingFields),
        summary = r.summary,
        recommendedActions = objectMapper.writeValueAsString(r.recommendedActions),
        safetyEscalate = if (r.safetyEscalate) 1 else 0
    )

    private fun detectVertical(text: String): String {
        val lower = text.lowercase()
        var best = "unknown" to 0
        for ((v, keywords) in verticalKeywords) {
            var score = 0
            for (k in keywords) {
                if (lower.contains(k.lowercase()) || text.contains(k)) score++
            }
            if (score > best.second) best = v to score
        }
        return best.first
    }

    private fun detectIntent(text: String): String {
        val lower = text.lowercase()
        val found = mutableListOf<String>()
        for ((intent, keywords) in intentKeywords) {
            if (keywords.any { lower.contains(it) || text.contains(it) }) found.add(intent)
        }
        if ("complaint" in found) return "complaint"
        if ("book" in found) return "book"
        if ("reschedule" in found) return "reschedule"
        if ("price" in found) return "price"
        if ("info" in found) return "info"
        return found.firstOrNull() ?: "info"
    }

    private fun urgencyScore(text: String, vertical: String, intent: String, extracted: ExtractedFields): Int {
        var score = 50
        if (intent == "complaint") score = 90
        if (intent == "book") score += 15
        if (Regex("urgent|急|盡快|asap|within\\s*\\d+\\s*(day|week)", RegexOption.IGNORE_CASE).containsMatchIn(text)) score += 20
        if (vertical == "med_spa" && Regex("急|urgent|盡快|asap").containsMatchIn(text)) score = maxOf(score, 85)
        return score.coerceIn(0, 100)
    }

    private fun safetyEscalate(text: String): Boolean {
        val lower = text.lowercase()
        return safetyRedFlags.any { lower.contains(it.lowercase()) || text.contains(it) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun buildSummary(vertical: String, intent: String, extracted: ExtractedFields, missing: List<String>): String {
        val parts = mutableListOf<String>()
        extracted.serviceName?.let { parts.add("Service: $it") }
        extracted.location?.let { parts.add("Location: $it") }
        if (extracted.preferredTime != null || !extracted.preferredDates.isNullOrEmpty()) {
            parts.add("Preference: ${listOfNotNull(extracted.preferredTime, *extracted.preferredDates?.toTypedArray() ?: emptyArray()).filter { it.isNotEmpty() }.joinToString(", ")}")
        }
        if (intent == "complaint") parts.add("Medical/safety concern – consider escalation.")
        if (missing.isNotEmpty()) parts.add("Missing: ${missing.joinToString(", ")}")
        return parts.ifEmpty { listOf("Inquiry received; triage completed.") }.joinToString(". ")
    }

    private fun recommendedActions(intent: String, missing: List<String>, safetyEscalate: Boolean, vertical: String): List<String> {
        val actions = mutableListOf<String>()
        if (safetyEscalate) actions.add("Escalate to manager / medical team")
        if (intent == "book") {
            actions.add("Offer 3 time slots")
            if (vertical == "med_spa") actions.add("Assign to receptionist")
            if (vertical == "training") actions.add("Assign to enrollment staff")
        }
        if (intent == "price") actions.add("Send price list / quote")
        if (missing.isNotEmpty()) actions.add("Ask for top missing fields: ${missing.take(2).joinToString(", ")}")
        if (intent == "complaint" && !safetyEscalate) actions.add("Reply with care instructions + suggest clinic visit")
        return actions
    }
}
