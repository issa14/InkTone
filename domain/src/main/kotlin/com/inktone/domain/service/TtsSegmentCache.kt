package com.inktone.domain.service

import java.security.MessageDigest

/**
 * Cache persistant des segments de synthèse TTS (Lot 22, Palier B) — le
 * principe directeur du Lot : **faire la synthèse lourde une fois, puis
 * relire vite** (démarrage à froid, reprise, retour en arrière).
 *
 * ## Indivisibilité audio / timestamps (correction 1, ADR-021)
 *
 * La synthèse VITS n'est **pas** déterministe (`SherpaOnnxTtsEngine`
 * passe `noiseScale`/`noiseScaleW`, sans graine) : deux synthèses de la
 * même phrase produisent un audio **et des durées** différents. Un
 * timestamp rejoué sur une synthèse fraîche produirait un surlignage faux.
 * La valeur de cache est donc **l'audio et ses `wordTimestamps`, écrits et
 * lus ensemble** — aucun chemin ne peut servir des timestamps issus d'une
 * autre synthèse. Le surlignage mot-à-mot ne reste actif que si le moteur
 * le déclare (`TtsCapabilities.wordTimestamps`).
 */
interface TtsSegmentCache {

    /**
     * Retourne le segment caché pour [key], ou `null` (absent ou périmé).
     * [publicationId] est porté explicitement (et non dérivé de la clé,
     * irréversible par nature) : il structure le stockage par livre et
     * rend la purge ([deletePublication]) triviale.
     */
    suspend fun get(publicationId: String, key: TtsCacheKey): AudioSegment?

    /** Stocke [segment] sous [key] pour [publicationId]. */
    suspend fun put(publicationId: String, key: TtsCacheKey, segment: AudioSegment)

    /**
     * Épingle le segment de reprise d'une publication (décision 4) : il
     * survit à la purge LRU, pour que le tap sur « reprendre » démarre
     * sans synthèse. Un seul segment épinglé par publication — un nouvel
     * épinglage remplace le précédent.
     */
    suspend fun pinResumePoint(publicationId: String, key: TtsCacheKey)

    /** Purge le cache TTS d'une publication (suppression du livre). */
    suspend fun deletePublication(publicationId: String)
}

/** Clé de cache TTS pré-calculée (hash hexadécimal, sûr pour un nom de fichier). */
@JvmInline
value class TtsCacheKey(val value: String) {
    init {
        require(value.isNotBlank()) { "TtsCacheKey ne peut pas être vide" }
    }
}

/**
 * Calcule la clé de cache d'un segment (Lot 22, Palier B, tâche 4) —
 * hash SHA-256 d'un tuple canonique :
 * `(publicationId, chapterIndex, sentenceOffset, voiceProfileId, hash des
 * règles de prononciation, version du moteur)`.
 *
 * Chaque composant est une dimension d'invalidation : changer de voix, de
 * règle de prononciation, de moteur ou de texte source (via le
 * `sentenceOffset`, unique par phrase dans un chapitre) produit une clé
 * différente — jamais un segment périmé servi.
 */
fun ttsCacheKey(
    publicationId: String,
    chapterIndex: Int,
    sentenceOffset: Int,
    voiceProfileId: String,
    pronunciationRulesHash: String,
    engineVersion: Int,
): TtsCacheKey {
    val canonical = listOf(
        publicationId, chapterIndex.toString(), sentenceOffset.toString(),
        voiceProfileId, pronunciationRulesHash, engineVersion.toString(),
    ).joinToString("|")
    return TtsCacheKey(sha256Hex(canonical))
}

/** Hash SHA-256 hexadécimal d'une chaîne — pur JVM, stable et sans collision utile. */
fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
