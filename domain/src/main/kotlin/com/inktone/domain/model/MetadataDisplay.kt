package com.inktone.domain.model

/**
 * Nettoie un champ de métadonnées EPUB pour l'AFFICHAGE.
 *
 * Les métadonnées d'un EPUB sont saisies à la main par des éditeurs et des
 * convertisseurs très divers : elles arrivent régulièrement préfixées d'un
 * tiret ou noyées d'espaces (« -La première loi - Tome 1 »). Le tiret
 * INTERNE, lui, est signifiant — il sépare le titre du tome — et doit être
 * conservé : seules les bornes sont rognées.
 *
 * Nettoyage à l'affichage et non à l'import (décision actée) : la valeur
 * d'origine reste intacte en base, ce qui rend la règle réversible et
 * applicable aux livres DÉJÀ importés, sans migration ni re-scan.
 *
 * Volontairement conservateur. Une règle plus agressive (retirer les
 * guillemets, réordonner « Nom, Prénom ») mutilerait des titres légitimes,
 * et le coût d'un faux positif est plus élevé ici que celui d'un artefact
 * résiduel : l'utilisateur ne peut pas corriger ce qu'il ne voit plus.
 * Seule exception, pour les auteurs : [authorForDisplay].
 */
fun String.cleanedForDisplay(): String =
    trim().trim('-', '–', '—').trim()

/** Auteurs nettoyés puis assemblés ; les entrées devenues vides disparaissent. */
fun List<String>.cleanedAuthorsForDisplay(): String =
    map { it.authorForDisplay() }.filter { it.isNotBlank() }.joinToString(", ")

/**
 * Auteur nettoyé, et remis dans l'ordre « Prénom Nom » quand le `dc:creator`
 * porte une forme de tri collée (« Aldiss,Brian »). Seulement sans espace
 * après l'unique virgule : « Dupont, Durand » peut être deux co-auteurs.
 * La valeur stockée, qui sert au tri par auteur, reste intacte.
 */
fun String.authorForDisplay(): String {
    val cleaned = cleanedForDisplay()
    val match = SORT_FORM.matchEntire(cleaned) ?: return cleaned
    val (lastName, firstName) = match.destructured
    return "$firstName $lastName"
}

private val SORT_FORM = Regex("""([^,]*[^,\s]),([^,\s][^,]*)""")
