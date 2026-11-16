package com.inktone.data.sync

import com.inktone.data.backup.BackupPayload
import com.inktone.data.backup.PublicationMatcher
import com.inktone.data.backup.toDomain
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.Bookmark
import com.inktone.domain.model.SyncTombstone
import com.inktone.domain.model.TombstoneKind
import com.inktone.domain.repository.AnnotationRepository
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.SyncTombstoneRepository
import kotlinx.coroutines.flow.first

/**
 * Fusion des signets et des annotations d'un instantané ou d'une sauvegarde dans la base locale
 * (audit UX D61) — une seule règle pour la synchronisation et pour l'import manuel :
 * - une tombe supprime l'élément local, sauf s'il a été modifié après la suppression ;
 * - un élément distant n'est pas recréé si une tombe locale est plus récente que sa dernière modification ;
 * - un élément déjà connu prend la version la plus récente (`updatedAt`) ;
 * - un élément inconnu dont le livre est présent est inséré.
 * Les tombes reçues sont gardées pour être relayées.
 */
internal class BookmarkAnnotationMerger(
    private val bookmarkRepository: BookmarkRepository,
    private val annotationRepository: AnnotationRepository,
    private val syncTombstoneRepository: SyncTombstoneRepository,
) {
    /** Contenu local, tenu à jour pendant la fusion de plusieurs instantanés de suite. */
    class State(
        val bookmarks: MutableMap<String, Bookmark>,
        val annotations: MutableMap<String, Annotation>,
        val tombstones: MutableMap<String, SyncTombstone>,
    )

    /** Résultat d'une fusion : éléments créés ou mis à jour, et éléments d'un livre absent localement. */
    data class Outcome(val changed: Int, val skippedOrphans: Int)

    suspend fun load(): State = State(
        bookmarks = bookmarkRepository.observeAll().first().associateBy { it.id }.toMutableMap(),
        annotations = annotationRepository.getAll().associateBy { it.id }.toMutableMap(),
        tombstones = syncTombstoneRepository.getAll().associateBy { it.id }.toMutableMap(),
    )

    suspend fun merge(state: State, payload: BackupPayload, matcher: PublicationMatcher): Outcome {
        var changed = 0
        var skippedOrphans = 0

        // Suppressions d'abord : elles s'appliquent avant que les éléments soient recréés.
        payload.deletions.mapNotNull { it.toDomain() }.forEach { tombstone ->
            val known = state.tombstones[tombstone.id]
            if (known == null || known.deletedAt < tombstone.deletedAt) {
                syncTombstoneRepository.record(tombstone)
                state.tombstones[tombstone.id] = tombstone
            }
            when (tombstone.kind) {
                TombstoneKind.BOOKMARK -> state.bookmarks[tombstone.id]?.let { local ->
                    // Modifié après sa suppression ailleurs : la modification l'emporte.
                    if (local.updatedAt <= tombstone.deletedAt) {
                        bookmarkRepository.delete(tombstone.id)
                        state.bookmarks.remove(tombstone.id)
                        changed++
                    }
                }
                TombstoneKind.ANNOTATION -> state.annotations[tombstone.id]?.let { local ->
                    if (local.updatedAt <= tombstone.deletedAt) {
                        annotationRepository.delete(tombstone.id)
                        state.annotations.remove(tombstone.id)
                        changed++
                    }
                }
            }
        }

        payload.bookmarks.forEach { backup ->
            val remote = backup.toDomain()
            if (isDeletedSince(state.tombstones[backup.id], remote.updatedAt)) return@forEach
            val local = state.bookmarks[backup.id]
            if (local != null) {
                if (remote.updatedAt > local.updatedAt) {
                    val merged = local.copy(title = remote.title, note = remote.note, updatedAt = remote.updatedAt)
                    bookmarkRepository.update(merged)
                    state.bookmarks[backup.id] = merged
                    changed++
                }
                return@forEach
            }
            val publicationId = matcher.localIdOf(backup.publicationId)
            if (publicationId == null) {
                skippedOrphans++
                return@forEach
            }
            forgetTombstone(state, backup.id)
            val inserted = remote.copy(publicationId = publicationId)
            bookmarkRepository.insert(inserted)
            state.bookmarks[backup.id] = inserted
            changed++
        }

        payload.annotations.forEach { backup ->
            val remote = backup.toDomain()
            if (isDeletedSince(state.tombstones[backup.id], remote.updatedAt)) return@forEach
            val local = state.annotations[backup.id]
            if (local != null) {
                if (remote.updatedAt > local.updatedAt) {
                    val merged = local.copy(
                        color = remote.color, kind = remote.kind, content = remote.content,
                        excerpt = remote.excerpt, updatedAt = remote.updatedAt,
                    )
                    annotationRepository.update(merged)
                    state.annotations[backup.id] = merged
                    changed++
                }
                return@forEach
            }
            val publicationId = matcher.localIdOf(backup.publicationId)
            if (publicationId == null) {
                skippedOrphans++
                return@forEach
            }
            forgetTombstone(state, backup.id)
            val inserted = remote.copy(publicationId = publicationId)
            annotationRepository.insert(inserted)
            state.annotations[backup.id] = inserted
            changed++
        }
        return Outcome(changed, skippedOrphans)
    }

    /** Vrai si [tombstone] supprime un élément dont la dernière modification ([updatedAt]) ne la dépasse pas. */
    private fun isDeletedSince(tombstone: SyncTombstone?, updatedAt: Long): Boolean =
        tombstone != null && tombstone.deletedAt >= updatedAt

    /** Un élément modifié après sa suppression ailleurs est recréé : sa tombe n'a plus lieu d'être. */
    private suspend fun forgetTombstone(state: State, id: String) {
        if (state.tombstones.remove(id) != null) syncTombstoneRepository.remove(id)
    }
}
