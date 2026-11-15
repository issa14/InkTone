package com.inktone.data.backup

import com.inktone.domain.repository.PublicationRepository

/**
 * Traduit l'identifiant d'un livre dans une sauvegarde ou un instantané
 * distant vers le livre local qui a le même fichier (audit UX D57, D58).
 * L'identifiant d'un livre est propre à chaque installation ; l'empreinte de
 * son fichier est la seule donnée commune.
 *
 * Sans empreinte connue (sauvegarde antérieure), l'identifiant est cherché
 * tel quel : c'est le cas d'une restauration sur l'installation d'origine.
 */
internal class PublicationMatcher(
    payload: BackupPayload,
    private val publicationRepository: PublicationRepository,
) {
    private val hashById = payload.publications.associate { it.id to it.fileHash }
    private val resolved = mutableMapOf<String, String?>()

    /** Identifiant local du livre désigné par [remoteId], `null` s'il n'est pas dans la bibliothèque. */
    suspend fun localIdOf(remoteId: String): String? = resolved.getOrPut(remoteId) {
        val hash = hashById[remoteId]
        val byHash = hash?.let { publicationRepository.getByFileHash(it) }
        byHash?.id ?: publicationRepository.getById(remoteId)?.id
    }
}
