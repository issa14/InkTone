package com.inktone.domain.service

import java.io.InputStream

/**
 * Résout les ressources d'un EPUB (images, polices, etc.) à partir d'un
 * `publicationId` et d'un `resourceHref`.
 *
 * Le domaine ne connaît ni Readium, ni Coil, ni le format EPUB — seule
 * cette interface est visible. L'implémentation ([ReadiumResourceResolver])
 * est dans `infrastructure/parser` et utilise Readium pour ouvrir les
 * flux depuis l'archive ZIP.
 *
 * ## Cycle de vie
 *
 * L'implémentation est scopée au [ReaderViewModel] : Hilt injecte une
 * instance fraîche (`@ViewModelScoped`), le ViewModel la transmet à
 * `ReaderScreen` en paramètre de composable, et `ReaderScreen` appelle
 * [close] dans un `DisposableEffect` quand l'écran quitte la composition.
 *
 * Aucune instance Readium ne survit au `ReaderScreen` — pas de fuite
 * globale, pas de violation Clean Architecture.
 */
interface EpubResourceResolver {
    /**
     * Ouvre un [InputStream] vers la ressource [resourceHref] dans
     * l'EPUB identifié par [publicationId].
     *
     * @return Le flux, ou `null` si la ressource n'existe pas.
     */
    suspend fun openStream(publicationId: String, resourceHref: String): InputStream?

    /**
     * Libère les ressources (Publication Readium, fichiers ouverts).
     * Appelé quand le lecteur quitte la composition.
     */
    fun close()
}
