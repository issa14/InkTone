package com.inktone.data.backup

import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.FontFamily
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
    // Lot 9 — thèmes personnalisés, absents des exports antérieurs
    // (défaut vide : un import d'une sauvegarde pré-lot 9 ne perd rien
    // qu'elle ne contenait déjà pas).
    val customThemes: List<CustomThemeBackup> = emptyList(),
    // Lot 11, tâche 11.1 — absentes des exports antérieurs à ce lot
    // (BackupManager n'appelait pas AnnotationRepository, défaut hérité
    // du lot 6). Défaut vide : un import d'une sauvegarde pré-lot 11 ne
    // perd rien qu'elle ne contenait déjà pas.
    val annotations: List<AnnotationBackup> = emptyList(),
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
data class CustomThemeBackup(
    val id: String,
    val displayName: String,
    val backgroundColorHex: String,
    val textColorHex: String,
    val accentColorHex: String,
    val highlightColorHex: String,
    val fontFamily: String,
)

@Serializable
data class AnnotationBackup(
    val id: String,
    val publicationId: String,
    val startLocator: LocatorBackup,
    val endLocator: LocatorBackup,
    val color: String,
    val content: String? = null,
    val excerpt: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
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
fun BookmarkBackup.toDomain(): Bookmark = Bookmark(id, publicationId, locator.toDomain(), title, note, createdAt = createdAt)

fun PronunciationRule.toBackup(): PronunciationRuleBackup =
    PronunciationRuleBackup(id, originalText, replacementText, isRegex, isEnabled)
fun PronunciationRuleBackup.toDomain(): PronunciationRule =
    PronunciationRule(id, originalText, replacementText, isRegex, isEnabled)

fun ReadingState.toBackup(): ReadingStateBackup = ReadingStateBackup(
    publicationId, locator.toBackup(), lastReadAt, voiceProfileId,
    // Lot 9 — id de thème (String), plus un enum.
    overrides?.theme, overrides?.fontSize,
)
fun ReadingStateBackup.toDomain(): ReadingState = ReadingState(
    publicationId, locator.toDomain(), lastReadAt, voiceProfileId,
    if (overrideTheme != null || overrideFontSize != null) {
        ReadingOverrides(overrideTheme, overrideFontSize)
    } else {
        null
    },
)

fun ReadingTheme.toBackup(): CustomThemeBackup = CustomThemeBackup(
    id, displayName, backgroundColorHex, textColorHex, accentColorHex, highlightColorHex, fontFamily.name,
)
fun CustomThemeBackup.toDomain(): ReadingTheme = ReadingTheme(
    id = id, displayName = displayName, isBuiltIn = false,
    backgroundColorHex = backgroundColorHex, textColorHex = textColorHex,
    accentColorHex = accentColorHex, highlightColorHex = highlightColorHex,
    fontFamily = FontFamily.valueOf(fontFamily),
)

fun Annotation.toBackup(): AnnotationBackup = AnnotationBackup(
    id, publicationId, startLocator.toBackup(), endLocator.toBackup(),
    color.name, content, excerpt, isPinned, createdAt, updatedAt,
)
fun AnnotationBackup.toDomain(): Annotation = Annotation(
    id = id, publicationId = publicationId,
    startLocator = startLocator.toDomain(), endLocator = endLocator.toDomain(),
    color = AnnotationColor.valueOf(color), content = content, excerpt = excerpt,
    isPinned = isPinned, createdAt = createdAt, updatedAt = updatedAt,
)

fun ReadingSession.toBackup(): ReadingSessionBackup =
    ReadingSessionBackup(id, publicationId, startedAt, endedAt, mode.name, sentencesRead, durationMs)
fun ReadingSessionBackup.toDomain(): ReadingSession =
    ReadingSession(
        id = id, publicationId = publicationId, startedAt = startedAt, endedAt = endedAt,
        mode = ReadingMode.valueOf(mode), sentencesRead = sentencesRead,
        visualDurationMs = durationMs, ttsDurationMs = 0,
    )
