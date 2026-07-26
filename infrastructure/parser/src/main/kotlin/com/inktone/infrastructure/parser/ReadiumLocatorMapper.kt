package com.inktone.infrastructure.parser

import com.inktone.domain.valueobject.Locator
import org.readium.r2.shared.publication.Locator as ReadiumLocator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Construit un Locator Readium MINIMAL à partir de notre Locator domaine
 * — suffisant pour demander à Readium de pointer vers la bonne ressource
 * (href). Ne tente PAS de reconstruire une progression ou des fragments
 * Readium-natifs : on n'utilise pas le navigateur Readium pour le rendu
 * (décision de ne pas adopter le navigateur visuel maintenant), donc ces
 * champs ne servent à rien ici.
 *
 * Aucune fonction inverse (ReadiumLocator -> Locator) : on ne dérive
 * jamais notre position depuis la progression Readium (Tâche 3.4 — les
 * offsets sont comptés par nous à l'extraction).
 *
 * Vit dans infrastructure/parser, PAS dans data (écart volontaire par
 * rapport au plan d'origine qui plaçait ce fichier dans data/) : Readium
 * est confiné à ce module (ADR-011) et data/ n'a — et ne doit pas avoir
 * — de dépendance vers Readium.
 */
fun Locator.toMinimalReadiumLocator(mediaType: MediaType): ReadiumLocator =
    ReadiumLocator(
        href = Url(resourceHref) ?: error("resourceHref invalide: $resourceHref"),
        mediaType = mediaType,
    )
