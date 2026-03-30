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

    /** Zomate Fitness (zoesportdiary.com) — women-first gym, TST / Sheung Wan */
    /** Order matters on equal keyword scores: PT before membership so 一對一+試堂 → PT */
    private val verticalKeywords = linkedMapOf(
        "zomate_nutrition" to listOf(
            "生酮", "keto", "飲食", "餐廳", "營養", "健康飲食", "膳食", "減肥餐", "餐單"
        ),
        "zomate_pt_1on1" to listOf(
            "一對一", "私教", "私人教練", "personal trainer", "pt", "gym", "健身", "女教練", "女子健身",
            "身形", "減脂", "增肌", "zomate", "器械", "教練", "運動", "workout", "fitness", "女健身",
            "女子", "塑造", "理想身形", "課程", "中心"
        ),
        "zomate_membership_trial" to listOf(
            "會員", "報名", "試堂", "體驗", "體驗課", "收費", "membership", "trial", "月費", "package",
            "join", "火熱招聘", "成為", "成功榜樣"
        )
    )

    private val intentKeywords = mapOf(
        "book" to listOf(
            "book", "booking", "預約", "想約", "想請", "join", "報名", "想上", "schedule", "appointment",
            "試堂", "體驗"
        ),
        "price" to listOf("price", "cost", "fee", "價錢", "幾錢", "費用", "how much", "budget", "預算"),
        "info" to listOf("info", "information", "了解", "想知", "介紹", "what do you", "inquiry", "查詢"),
        "complaint" to listOf("complaint", "problem", "issue", "redness", "swelling", "pain", "不適", "紅腫", "痛", "有問題", "what should i do"),
        "reschedule" to listOf("reschedule", "change", "改期", "取消", "cancel", "postpone")
    )

    private val safetyRedFlags = listOf(
        "severe allergic", "anaphylaxis", "fainting", "faint", "breathing", "chest pain", "severe infection",
        "紅腫", "腫脹", "過敏", "呼吸", "胸痛", "頭暈", "暈倒", "發燒", "感染", "發炎", "redness and swelling",
        "what should i do", "emergency", "urgent",
        "受傷", "扭傷", "拉傷", "暈厥", "運動創傷", "training injury", "passed out"
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
        return if (best.second > 0) best.first else "zomate_pt_1on1"
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
        if (vertical.startsWith("zomate_") && Regex("急|urgent|盡快|asap").containsMatchIn(text)) score = maxOf(score, 85)
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
        if (intent == "complaint") parts.add("Safety / injury concern – consider escalation.")
        if (missing.isNotEmpty()) parts.add("Missing: ${missing.joinToString(", ")}")
        return parts.ifEmpty { listOf("Inquiry received; triage completed.") }.joinToString(". ")
    }

    private fun recommendedActions(intent: String, missing: List<String>, safetyEscalate: Boolean, vertical: String): List<String> {
        val actions = mutableListOf<String>()
        if (safetyEscalate) actions.add("Escalate to manager / medical team")
        if (intent == "book") {
            actions.add("Offer 3 time slots")
            if (vertical.startsWith("zomate_")) actions.add("Assign to front desk / coach team")
        }
        if (intent == "price") actions.add("Send price list / quote")
        if (missing.isNotEmpty()) actions.add("Ask for top missing fields: ${missing.take(2).joinToString(", ")}")
        if (intent == "complaint" && !safetyEscalate) {
            actions.add("Reply with care + suggest medical attention if pain or injury persists")
        }
        return actions
    }
}
