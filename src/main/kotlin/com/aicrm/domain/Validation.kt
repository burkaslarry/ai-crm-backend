package com.aicrm.domain

const val MAX_TEXT = 10000
const val MAX_SHORT = 500
const val MAX_ID = 64

val ALLOWED_STAGES = setOf(
    "New", "Needs Info", "Qualified", "Offered Slots", "Booked", "Paid/Deposit", "Completed", "Lost"
)

val ALLOWED_CHANNELS = setOf("web", "whatsapp")

private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-fy]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

fun sanitizeString(value: Any?, maxLen: Int = MAX_TEXT): String? {
    if (value == null) return null
    val s = value.toString().trim()
    return if (s.length > maxLen) s.take(maxLen) else s.ifBlank { null }
}

fun isValidLeadId(id: String?): Boolean =
    id != null && id.length <= MAX_ID && UUID_REGEX.matches(id)

fun validateStage(stage: String?): String? {
    val s = sanitizeString(stage, 50)
    return if (s != null && s in ALLOWED_STAGES) s else null
}

fun validateChannel(channel: String?): String? {
    val c = sanitizeString(channel, 20)
    return if (c != null && c in ALLOWED_CHANNELS) c else null
}

fun validateISODate(dateStr: String?): String? {
    val s = sanitizeString(dateStr, 10) ?: return null
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return null
    try {
        java.time.LocalDate.parse(s)
        return s
    } catch (_: Exception) {
        return null
    }
}

data class CreateLeadRequest(
    val channel: String,
    val raw_message: String,
    val name: String?,
    val contact: String?,
    val vertical: String?,
    val source: String?
)

data class CreateInquiryRequest(
    val message: String,
    val contact: String?
)

data class PatchLeadRequest(
    val stage: String?,
    val owner_id: String?,
    val service_date: String?
)
