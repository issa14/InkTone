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
 * ## Régénération
 *
 * Une pré-analyse absente ou périmée (évolution du parseur, échec à
 * l'import) n'est PAS régénérée par l'import, qui n'a lieu qu'une fois :
 * c'est le rôle de `RefreshStalePreAnalysesUseCase`, planifié en tâche de
 * fond via [PreAnalysisScheduler]. Sans lui, incrémenter la version du
 * parseur rendait la pré-analyse inutilisable pour TOUS les livres déjà
 * importés, définitivement (cas réel du passage à la version 2).
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
     * Relit la pré-analyse persistée d'UN chapitre — jamais celle du livre
     * entier : la pré-analyse d'un gros livre atteint plusieurs dizaines de
     * Mo (25 Mo, 181 chapitres, pour L'Arcane des Épées), dont la lecture
     * complète prenait ~10 s sur V2206 pour n'afficher qu'un chapitre.
     *
     * @return Le chapitre persisté, ou `null` si la pré-analyse est absente,
     *   corrompue, périmée (version du format/parseur différente, ou
     *   [fileHash] divergent de celui enregistré à l'écriture), ou si elle
     *   ne contient pas [chapterHref].
     */
    suspend fun loadChapter(publicationId: String, fileHash: String, chapterHref: String): Chapter?

    /**
     * Vrai si une pré-analyse VALIDE existe pour [publicationId] — mêmes
     * critères que [loadChapter] (version du format, version du parseur,
     * [fileHash]) ; seul l'en-tête est lu. Sert à décider d'une
     * régénération.
     */
    suspend fun isFresh(publicationId: String, fileHash: String): Boolean

    /** Supprime la pré-analyse d'une publication (purge à sa suppression). */
    suspend fun delete(publicationId: String)
}
