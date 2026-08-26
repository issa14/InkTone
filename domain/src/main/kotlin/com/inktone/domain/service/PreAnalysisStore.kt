package com.inktone.domain.service

import com.inktone.domain.model.Chapter

/**
 * Stockage persistant de la pré-analyse des chapitres d'une publication
 * (Lot 22, Palier A) — le travail lourd du parsing HTML (`Jsoup`) et du
 * découpage de phrases (`FrenchSentenceSplitter`) est fait **une fois** à
 * l'import, puis relu à chaque ouverture.
 *
 * Décision 1 : fichier sérialisé par publication, **pas Room**. Les blocs
 * de chapitre sont des blobs lus séquentiellement, jamais interrogés par
 * requête : Room n'apporterait rien et ferait payer une migration à chaque
 * évolution du parseur, là où un fichier se régénère en incrémentant un
 * numéro de version.
 *
 * Le cache est **versionné** (version du format + version du parseur) et
 * **clé par le `fileHash`** déjà calculé à l'import : il n'est jamais
 * servi pour une source différente, ni après une évolution du format ou du
 * parseur. L'implémentation se charge de comparer les versions et le hash
 * — l'appelant ne passe que le `fileHash` courant.
 *
 * ## Purge (décision 1, prix assumé)
 *
 * Le fichier n'étant pas une table Room, il ne bénéficie pas du
 * `ON DELETE CASCADE`. La purge à la suppression d'une publication est
 * donc du code explicite ([delete]) — jamais implicite. C'est un critère
 * de sortie du Lot, testé.
 */
interface PreAnalysisStore {

    /**
     * Persiste la pré-analyse complète d'une publication.
     *
     * @param publicationId Identifiant de la publication (clé de fichier).
     * @param fileHash Hash SHA-256 de la source — sert à invalider le
     *   cache si le fichier change.
     * @param chapters Chapitres déjà parsés (blocs + phrases), dans
     *   l'ordre du spine.
     */
    suspend fun save(publicationId: String, fileHash: String, chapters: List<Chapter>)

    /**
     * Relit la pré-analyse persistée d'une publication.
     *
     * @return Les chapitres persistés, ou `null` si le cache est absent,
     *   corrompu, ou périmé (version du format/parseur différente, ou
     *   [fileHash] divergent de celui enregistré à l'écriture).
     */
    suspend fun load(publicationId: String, fileHash: String): List<Chapter>?

    /** Supprime la pré-analyse d'une publication (purge à sa suppression). */
    suspend fun delete(publicationId: String)
}
