package com.inktone.infrastructure.database.entity

import androidx.room.DatabaseView

/**
 * Vue UNION des marque-pages et des annotations (Lot 4, tâche 4.1/4.4).
 *
 * Fusionne les deux sources en une ligne unique — type, extrait, note,
 * titre d'ouvrage résolu par LEFT JOIN, épinglage — pour que la
 * recherche et le tri de la vue globale « Marque-pages et notes »
 * restent entièrement SQL, y compris entre les deux sources. Le
 * `LEFT JOIN` sur `publications` (et non `INNER`) préserve un
 * enregistrement orphelin au lieu de le faire disparaître silencieusement.
 *
 * Vue en lecture seule : les écritures (insert/update/delete/pin)
 * passent par [AnnotationDao] et [BookmarkDao], jamais par cette vue.
 */
@DatabaseView(
    viewName = "library_items",
    value = """
        SELECT
            'bookmark' AS type,
            b.id AS id,
            b.publicationId AS publicationId,
            p.title AS publicationTitle,
            b.resourceHref AS resourceHref,
            b.chapterIndex AS chapterIndex,
            b.paragraphIndex AS paragraphIndex,
            b.charOffset AS charOffset,
            NULL AS endResourceHref,
            NULL AS endChapterIndex,
            NULL AS endParagraphIndex,
            NULL AS endCharOffset,
            NULL AS color,
            b.excerpt AS excerpt,
            b.note AS note,
            b.isPinned AS isPinned,
            b.createdAt AS createdAt
        FROM bookmarks b LEFT JOIN publications p ON p.id = b.publicationId
        UNION ALL
        SELECT
            'annotation' AS type,
            a.id AS id,
            a.publicationId AS publicationId,
            p.title AS publicationTitle,
            a.startResourceHref AS resourceHref,
            a.startChapterIndex AS chapterIndex,
            a.startParagraphIndex AS paragraphIndex,
            a.startCharOffset AS charOffset,
            a.endResourceHref AS endResourceHref,
            a.endChapterIndex AS endChapterIndex,
            a.endParagraphIndex AS endParagraphIndex,
            a.endCharOffset AS endCharOffset,
            a.color AS color,
            a.excerpt AS excerpt,
            a.content AS note,
            a.isPinned AS isPinned,
            a.createdAt AS createdAt
        FROM annotations a LEFT JOIN publications p ON p.id = a.publicationId
    """,
)
data class LibraryItemView(
    val type: String,
    val id: String,
    val publicationId: String,
    val publicationTitle: String?,
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int?,
    val charOffset: Int,
    val endResourceHref: String?,
    val endChapterIndex: Int?,
    val endParagraphIndex: Int?,
    val endCharOffset: Int?,
    val color: String?,
    val excerpt: String?,
    val note: String?,
    val isPinned: Boolean,
    val createdAt: Long,
)
