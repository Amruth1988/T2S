package com.t2sstudio.app

import java.util.UUID

data class T2SScriptDraft(
    val id: String,
    val title: String,
    val content: String,
    val language: String,
    val projectId: String,
    val projectName: String,
    val updatedAt: Long
)

/**
 * Creates an in-memory draft model only.
 * Draft persistence and retrieval are handled exclusively by Firebase Firestore.
 */
object ScriptDraftStore {
    fun create(
        content: String,
        language: String,
        projectId: String,
        projectName: String
    ): T2SScriptDraft {
        val clean = content.trim()
        require(clean.isNotEmpty()) { "Write some text before saving a draft." }
        val title = clean.lineSequence().firstOrNull().orEmpty()
            .take(64).ifBlank { "Untitled draft" }
        return T2SScriptDraft(
            id = UUID.randomUUID().toString(),
            title = title,
            content = clean,
            language = language,
            projectId = projectId,
            projectName = projectName,
            updatedAt = System.currentTimeMillis()
        )
    }
}
