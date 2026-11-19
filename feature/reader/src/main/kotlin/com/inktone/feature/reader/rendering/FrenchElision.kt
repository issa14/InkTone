package com.inktone.feature.reader.rendering

/**
 * Texte AFFICHÉ avec des élisions insécables : « l’existence » ne doit jamais
 * se couper après l’apostrophe (audit UX P0-3). En justifié, la césure
 * française d’Android y autorise une coupure sans tiret.
 *
 * U+2019 (ponctuation) devient U+02BC, apostrophe-lettre au même glyphe, que
 * l’algorithme de coupure traite comme une lettre. Substitution caractère pour
 * caractère : les offsets (`Locator.charOffset`, mots horodatés) sont inchangés.
 * À n’appliquer qu’au rendu et à la mesure, jamais au texte lu par le TTS.
 */
internal fun String.withUnbreakableElisions(): String {
    if (indexOf(TYPOGRAPHIC_APOSTROPHE) < 0) return this
    val chars = toCharArray()
    for (i in 1 until chars.size - 1) {
        if (chars[i] == TYPOGRAPHIC_APOSTROPHE && chars[i - 1].isLetter() && chars[i + 1].isLetter()) {
            chars[i] = LETTER_APOSTROPHE
        }
    }
    return String(chars)
}

private const val TYPOGRAPHIC_APOSTROPHE = '’'
private const val LETTER_APOSTROPHE = 'ʼ'
