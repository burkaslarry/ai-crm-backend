package com.aicrm.service

import org.springframework.stereotype.Service

@Service
class DraftTemplateService {

    private val safetyDisclaimer = "⚠️ This is not medical advice. If you have severe redness, swelling, breathing difficulty or chest pain, please seek immediate medical attention."

    private val zomateDisclaimer =
        "⚠️ If you have severe pain, chest pain, fainting, or trouble breathing after exercise, seek medical attention immediately."

    fun getDraft(vertical: String, intent: String, vars: Map<String, String>): String {
        val v = vertical.ifBlank { "zomate_pt_1on1" }
        val name = vars["name"].orEmpty()
        val service = vars["service"].orEmpty().ifBlank {
            when {
                v.startsWith("zomate_") -> "our women’s 1:1 training"
                v == "training" -> "our course"
                else -> "our service"
            }
        }
        val location = vars["location"].orEmpty().ifBlank {
            if (v.startsWith("zomate_")) "Zomate Fitness (Tsim Sha Tsui / Sheung Wan 尖沙咀／上環)" else "our clinic"
        }
        val slots = vars["slots"].orEmpty().ifBlank { "Slot 1, Slot 2, Slot 3" }
        val bookingLink = vars["bookingLink"].orEmpty().ifBlank { "[Booking link]" }
        val serviceDate = vars["service_date"].orEmpty().ifBlank { "scheduled date" }

        if (v.startsWith("zomate_")) {
            return when (intent) {
                "reminder_2d" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Reminder: your Zomate Fitness session for $service is in 2 days ($serviceDate). See you at your branch (TST / Sheung Wan)!"
                "reminder_24h" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Reminder: your training session is tomorrow. We look forward to seeing you!"
                "feedback_1d" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for training with us today. We’d love your feedback — about 1 min: [Feedback link]."
                "book" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for your interest in $service at Zomate Fitness. Here are 3 suggested times:\n\n$slots\n\nPlease reply with your preference, or WhatsApp us to confirm. Branches: TST / Sheung Wan."
                "price" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! For $service, we’ll send our latest package options and trial details shortly. Reply if you prefer 尖沙咀 or 上環."
                "info" -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Zomate Fitness is women-focused — 1:1 coaching with female trainers, equipment tailored for women. Tell us your goal and preferred branch ($location) and we’ll follow up."
                "complaint" -> "Hi${if (name.isNotEmpty()) " $name" else ""}, we’re sorry you’re going through this.\n\n$zomateDisclaimer\n\nPlease reply here or WhatsApp us and we’ll arrange a team member to assist."
                else -> "Hi${if (name.isNotEmpty()) " $name" else ""}! Thanks for contacting Zomate Fitness — happy to help with $service."
            }
        }

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
                else -> getDraft("zomate_pt_1on1", "info", vars)
            }
            else -> getDraft("zomate_pt_1on1", if (intent.isBlank()) "info" else intent, vars)
        }
    }
}
