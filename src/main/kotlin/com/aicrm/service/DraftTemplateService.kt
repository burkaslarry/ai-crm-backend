package com.aicrm.service

import org.springframework.stereotype.Service

@Service
class DraftTemplateService {

    private val safetyDisclaimer = "⚠️ This is not medical advice. If you have severe redness, swelling, breathing difficulty or chest pain, please seek immediate medical attention."

    fun getDraft(vertical: String, intent: String, vars: Map<String, String>): String {
        val v = vertical.ifBlank { "med_spa" }
        val name = vars["name"].orEmpty()
        val service = vars["service"].orEmpty().ifBlank { if (v == "training") "our course" else "our service" }
        val location = vars["location"].orEmpty().ifBlank { "our clinic" }
        val slots = vars["slots"].orEmpty().ifBlank { "Slot 1, Slot 2, Slot 3" }
        val bookingLink = vars["bookingLink"].orEmpty().ifBlank { "[Booking link]" }
        val serviceDate = vars["service_date"].orEmpty().ifBlank { "scheduled date" }

        return when (v) {
            "med_spa" -> when (intent) {
                "book" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for your interest in $service. Here are 3 suggested slots:\n\n$slots\n\nPlease reply with your preferred time, or book here: $bookingLink"
                "price" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Here's our price list for $service. We'll send the PDF shortly. Any questions, just reply."
                "info" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for reaching out. For $service at $location, we'd be happy to share details. Reply with your preferred date/time and we'll send availability."
                "complaint" -> "Hi${if (name.isNotEmpty()) " $name" else ""}, we're sorry to hear you're experiencing this.\n\n$safetyDisclaimer\n\nWe recommend you contact our clinic for a follow-up. Please call us or reply here and we'll arrange for someone to assist you."
                else -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for reaching out. For $service at $location, we'd be happy to share details."
            }
            "training" -> when (intent) {
                "book" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for your interest in $service. Next intake slots:\n\n$slots\n\nReply with your preference or book here: $bookingLink"
                "price" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Course fee for $service depends on level and format. We'll send the fee schedule shortly."
                "info" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! For $service we have weekday/weekend options. Tell us your preferred schedule and we'll send the next intake dates."
                "complaint" -> "Hi${if (name.isNotEmpty()) " $name" else ""}, we're sorry for any inconvenience. We'll have our team follow up with you shortly."
                else -> "Hi${if (name.isNotEmpty()) " $name" else ""}! For $service we have weekday/weekend options."
            }
            "scheduled" -> when (intent) {
                "reminder_2d" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Reminder: your appointment for $service is in 2 days ($serviceDate). See you soon!"
                "reminder_24h" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Reminder: your appointment is tomorrow. We look forward to seeing you."
                "feedback_1d" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thank you for visiting us. We'd love your feedback — please take 1 min: [Feedback link]. Thank you!"
                else -> getDraft("med_spa", "info", vars)
            }
            else -> getDraft("med_spa", if (intent.isBlank()) "info" else intent, vars)
        }
    }
}
