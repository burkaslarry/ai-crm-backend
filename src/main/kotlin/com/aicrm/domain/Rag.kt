package com.aicrm.domain

import java.time.Instant

data class RagService(
    val id: String,
    val name: String,
    val description: String?,
    val region: String,
    val createdAt: Instant
)

data class RagProduct(
    val id: String,
    val name: String,
    val description: String?,
    val region: String,
    val createdAt: Instant
)

data class FollowUpCase(
    val id: String,
    val caseName: String,
    val contact: String?,
    val status: String?,
    val notes: String?,
    val leadRef: String?,
    val createdAt: Instant
)

data class RagDocument(
    val id: String,
    val fileName: String,
    val region: String,
    val extractedText: String,
    val createdAt: Instant
)

data class RagDocumentLink(
    val id: String,
    val documentId: String,
    val itemType: String,
    val itemId: String,
    val itemName: String,
    val score: Double,
    val createdAt: Instant
)
