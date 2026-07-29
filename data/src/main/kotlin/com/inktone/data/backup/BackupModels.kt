package com.inktone.data.backup

import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingOverrides
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.model.ReadingState
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.valueobject.Locator
import kotlinx.serialization.Serializable

/**
 * DTOs de serialisation (Tache 8.5) — separes des modeles de domaine
 * (qui restent purs Kotlin sans annotation de framework, Blueprint
 * §4.6/§12.5) plutot que d'annoter `Bookmark`/`ReadingState` etc.
 * directement avec `@Serializable`.
 */
@Serializable
data class BackupPayload(
    val appVersion: String,
    val createdAt: Long,
    val bookmarks: List<BookmarkBackup>,
    val pronunciationRules: List<PronunciationRuleBackup>,
    val readingStates: List<ReadingStateBackup>,
    val readingSessions: List<ReadingSessionBackup>,
)

@Serializable
data class LocatorBackup(
    val resourceHref: String,
    val chapterIndex: Int,
    val paragraphIndex: Int? = null,
    val charOffset: Int,
)

@Serializable
data class BookmarkBackup(
    val id: String,
    val publicationId: String,
    val locator: LocatorBackup,
    val title: String? = null,
    val note: String? = null,
    val createdAt: Long,
)

@Serializable
data class PronunciationRuleBackup(
    val id: String,
    val originalText: String,
    val replacementText: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true,
)

@Serializable
data class ReadingStateBackup(
    val publicationId: String,
    val locator: LocatorBackup,
    val lastReadAt: Long,
    val voiceProfileId: String? = null,
    val overrideTheme: String? = null,
    val overrideFontSize: Int? = null,
)

@Serializable
data class ReadingSessionBackup(
    val id: String,
    val publicationId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: String,
    val sentencesRead: Int = 0,
    val durationMs: Long = 0,
)

fun Locator.toBackup(): LocatorBackup = LocatorBackup(resourceHref, chapterIndex, paragraphIndex, charOffset)
fun LocatorBackup.toDomain(): Locator = Locator(resourceHref, chapterIndex, paragraphIndex, charOffset)

fun Bookmark.toBackup(): BookmarkBackup = BookmarkBackup(id, publicationId, locator.toBackup(), title, note, createdAt)
fun BookmarkBackup.toDomain(): Bookmark = Bookmark(id, publicationId, locator.toDomain(), title, note, createdAt)

fun PronunciationRule.toBackup(): PronunciationRuleBackup =
    PronunciationRuleBackup(id, originalText, replacementText, isRegex, isEnabled)
fun PronunciationRuleBackup.toDomain(): PronunciationRule =
    PronunciationRule(id, originalText, replacementText, isRegex, isEnabled)

fun ReadingState.toBackup(): ReadingStateBackup = ReadingStateBackup(
    publicationId, locator.toBackup(), lastReadAt, voiceProfileId,
    overrides?.theme?.name, overrides?.fontSize,
)
fun ReadingStateBackup.toDomain(): ReadingState = ReadingState(
    publicationId, locator.toDomain(), lastReadAt, voiceProfileId,
    if (overrideTheme != null || overrideFontSize != null) {
        ReadingOverrides(overrideTheme?.let { ReadingTheme.valueOf(it) }, overrideFontSize)
    } else {
        null
    },
)

fun ReadingSession.toBackup(): ReadingSessionBackup =
    ReadingSessionBackup(id, publicationId, startedAt, endedAt, mode.name, sentencesRead, durationMs)
fun ReadingSessionBackup.toDomain(): ReadingSession =
    ReadingSession(id, publicationId, startedAt, endedAt, ReadingMode.valueOf(mode), sentencesRead, durationMs)
