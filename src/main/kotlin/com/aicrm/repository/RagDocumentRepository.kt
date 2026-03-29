package com.aicrm.repository

import com.aicrm.domain.RagDocument
import com.aicrm.domain.RagDocumentLink
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class RagDocumentRepository(private val jdbc: JdbcTemplate) {

    fun insertDocument(doc: RagDocument) {
        jdbc.update(
            "INSERT INTO rag_documents (id, file_name, region, extracted_text) VALUES (?, ?, ?, ?)",
            doc.id, doc.fileName, doc.region, doc.extractedText
        )
    }

    fun insertLink(link: RagDocumentLink) {
        jdbc.update(
            """INSERT INTO rag_document_links (id, document_id, item_type, item_id, item_name, score)
               VALUES (?, ?, ?, ?, ?, ?)""",
            link.id, link.documentId, link.itemType, link.itemId, link.itemName, link.score
        )
    }

    fun getLinksByDocument(documentId: String): List<RagDocumentLink> =
        jdbc.query("SELECT * FROM rag_document_links WHERE document_id = ? ORDER BY score DESC, created_at DESC", linkRowMapper, documentId)

    private val linkRowMapper = RowMapper { rs, _ ->
        RagDocumentLink(
            id = rs.getString("id"),
            documentId = rs.getString("document_id"),
            itemType = rs.getString("item_type"),
            itemId = rs.getString("item_id"),
            itemName = rs.getString("item_name"),
            score = rs.getDouble("score"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }
}
