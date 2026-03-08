package com.aicrm.domain

import java.time.Instant

data class Lead(
    val id: String,
    val channel: String,
    val rawMessage: String?,
    val name: String?,
    val contact: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val stage: String,
    val ownerId: String?,
    val vertical: String?,
    val source: String?,
    val serviceDate: String?
)

data class AiTriage(
    val leadId: String,
    val vertical: String?,
    val category: String?,
    val subcategory: String?,
    val intent: String?,
    val urgencyScore: Int?,
    val extractedFields: String?,
    val missingFields: String?,
    val summary: String?,
    val recommendedActions: String?,
    val safetyEscalate: Int
)

data class Task(
    val id: String,
    val leadId: String,
    val type: String,
    val title: String,
    val dueAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant
)

data class TimelineEvent(
    val id: String,
    val leadId: String,
    val eventType: String,
    val payload: String?,
    val createdAt: Instant
)

data class AutomationRule(
    val id: String,
    val name: String,
    val triggerCondition: String,
    val actions: String,
    val enabled: Int,
    val sortOrder: Int
)

data class SlotSuggestion(
    val id: String,
    val leadId: String,
    val slots: String,
    val createdAt: Instant
)

data class ScheduledJob(
    val id: String,
    val leadId: String,
    val jobType: String,
    val runAt: Instant,
    val status: String,
    val createdAt: Instant
)
