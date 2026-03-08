package com.aicrm.service

import org.springframework.stereotype.Service

data class ExtractedFields(
    val preferredDates: List<String>? = null,
    val preferredTime: String? = null,
    val location: String? = null,
    val dueDate: Any? = null,
    val durationDays: Int? = null,
    val languagePreference: String? = null,
    val budgetBand: String? = null,
    val serviceName: String? = null
)

@Service
class NormalizationService {

    private val locationKeywordsEn = listOf(
        "central", "kowloon", "mong kok", "tst", "tsim sha tsui", "causeway bay", "wan chai",
        "shatin", "sha tin", "tuen mun", "yuen long", "tai po", "fanling", "kwai fong", "tseung kwan o", "sai kung"
    )
    private val locationKeywordsZh = listOf(
        "中環", "尖沙咀", "旺角", "銅鑼灣", "灣仔", "沙田", "屯門", "元朗", "大埔", "粉嶺", "葵芳", "將軍澳", "西貢", "九龍", "港島", "新界"
    )

    fun normalizeFields(text: String, vertical: String): ExtractedFields {
        val lower = text.lowercase()
        val preferredDates = mutableListOf<String>()
        val dayNames = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "mon", "tue", "wed", "thu", "fri", "sat", "sun")
        for (day in dayNames) {
            if (lower.contains(day)) preferredDates.add(day.take(3).replaceFirstChar { it.uppercase() })
        }
        if (Regex("\\b(weekend|sat|sun|saturday|sunday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            if ("Sat" !in preferredDates) preferredDates.add("Sat")
            if ("Sun" !in preferredDates) preferredDates.add("Sun")
        }
        var preferredTime: String? = null
        if (Regex("\\b(after\\s+\\d{1,2})", RegexOption.IGNORE_CASE).find(text) != null) {
            preferredTime = Regex("after\\s+(\\d{1,2})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.let { "after $it" }
        }
        if (Regex("下午|晚上|朝早|早上|中午").containsMatchIn(text)) preferredTime = preferredTime ?: "afternoon/evening"
        if (Regex("今周|這周|this week|今個星期").containsMatchIn(text)) preferredDates.add("this_week")

        var location: String? = null
        for (loc in locationKeywordsEn) {
            if (lower.contains(loc)) { location = loc; break }
        }
        if (location == null) {
            for (loc in locationKeywordsZh) {
                if (text.contains(loc)) { location = loc; break }
            }
        }

        val serviceName = extractServiceName(text, vertical)

        return ExtractedFields(
            preferredDates = preferredDates.ifEmpty { null },
            preferredTime = preferredTime,
            location = location,
            serviceName = serviceName
        )
    }

    private fun extractServiceName(text: String, vertical: String): String? {
        val lower = text.lowercase()
        if (vertical == "med_spa") {
            if (Regex("laser\\s*hair|脫毛|hair removal").containsMatchIn(lower)) return "Laser hair removal"
            if (Regex("facial|面部").containsMatchIn(lower)) return "Facial"
            if (Regex("consult|consultation|諮詢").containsMatchIn(lower)) return "Consultation"
            if (Regex("pigment|斑|美白").containsMatchIn(lower)) return "Pigmentation treatment"
            if (Regex("acne|暗瘡|痘").containsMatchIn(lower)) return "Acne treatment"
        }
        if (vertical == "training") {
            if (Regex("nail|美甲|指甲").containsMatchIn(lower)) return "Nail course"
            if (Regex("beauty|美容|化妝").containsMatchIn(lower)) return "Beauty course"
        }
        return null
    }

    fun getMissingFields(extracted: ExtractedFields, vertical: String): List<String> {
        val missing = mutableListOf<String>()
        if (vertical == "med_spa") {
            if (extracted.serviceName == null) missing.add("service")
            if (extracted.preferredDates.isNullOrEmpty() && extracted.preferredTime == null) missing.add("preferred_date_time")
            if (extracted.location == null) missing.add("location")
        }
        if (vertical == "training") {
            if (extracted.serviceName == null) missing.add("course_name")
            if (extracted.preferredDates.isNullOrEmpty() && extracted.preferredTime == null) missing.add("schedule_preference")
        }
        return missing
    }
}
