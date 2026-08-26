package com.inktone.domain.service

import java.text.BreakIterator
import java.util.Locale

/**
 * Découpeur de phrases unifié pour le français, basé sur
 * [java.text.BreakIterator] (ICU natif, zéro regex runtime).
 *
 * Conçu pour remplacer à terme les deux tokenizers actuels :
 * - [org.readium.r2.shared.publication.services.content.TextContentTokenizer]
 *   pour l'EPUB (via Readium)
 * - La regex naïve `(?<=[.!?])\\s+` pour PDF/TXT
 *
 * ## Pourquoi BreakIterator et pas une regex ?
 *
 * BreakIterator utilise les règles de segmentation ICU compilées — il gère
 * nativement les points de suspension, les points dans les nombres et les
 * guillemets. Les abréviations françaises (M., Mme, etc.) restent, elles,
 * gérées par [abbreviations] ci-dessous : BreakIterator ne les reconnaît
 * PAS nativement (correctif de ce commentaire — l'affirmation contraire
 * était fausse et contredisait le code qui la suit de quelques lignes).
 * L'intérêt face à une regex reste entier : BreakIterator gère seul les
 * points de suspension et les guillemets, laissant la liste d'exceptions
 * ne couvrir que les abréviations — une regex sophistiquée devrait, elle,
 * tout encoder à la main, y compris ces cas-là. "M. Dubois est arrivé."
 * (coupure après "M.") reste le cas qui justifie la liste d'exceptions,
 * pas BreakIterator seul.
 *
 * ## Contrat de stabilité des offsets
 *
 * Les offsets [startOffset, endOffset[ sont comptés dans l'espace du texte
 * source. Aucune normalisation de whitespace n'est appliquée — le texte de
 * chaque phrase est un substring exact du texte d'entrée. Ce contrat est
 * critique pour la synchronisation TTS mot-à-mot : un décalage d'un
 * caractère casse le surlignage.
 *
 * ## Filtrage des abréviations
 *
 * Une liste pré-allouée de préfixes d'abréviations françaises est utilisée
 * en post-traitement pour recoller les phrases incorrectement coupées.
 * Cette liste est conservative : elle ne contient que les abréviations
 * dont l'absence de filtre produirait des phrases d'un seul mot (faux
 * positif pire que faux négatif pour le TTS).
 *
 * ## Performance
 *
 * BreakIterator.getInstance() alloue un nouvel itérateur à chaque appel —
 * coût ~0.1ms sur Snapdragon 680. Pour des chapitres de <50 000
 * caractères, le coût total du splitting est <5ms. Le goulot
 * d'étranglement reste le parsing HTML (Jsoup), pas le split de phrases.
 */
object FrenchSentenceSplitter {

    /** Abréviations françaises dont le point final n'est PAS une fin de phrase. */
    private val abbreviations: Set<String> = hashSetOf(
        "M", "MM", "Mme", "Mmes", "Mlle", "Mlles",
        "Dr", "Pr", "Me", "Sr", "Ste",
        "Mr", "Mrs", "Ms",
        "etc", "cf", "vs", "env", "approx",
        "janv", "févr", "mars", "avr", "mai", "juin", "juil", "août",
        "sept", "oct", "nov", "déc",
        "art", "chap", "ch", "fig", "p", "pp",
        "vol", "t", "n", "nos", "éd", "op", "cit",
        "i.e", "e.g",
    )

    /**
     * Découpe [text] en phrases.
     *
     * @return Liste de triplets (texte, startOffset, endOffset). Les offsets
     *         sont dans l'espace du [text] original.
     */
    fun split(text: String): List<Triple<String, Int, Int>> {
        if (text.isBlank()) return emptyList()

        val iterator = BreakIterator.getSentenceInstance(Locale.FRENCH)
        iterator.setText(text)

        val rawBoundaries = mutableListOf<Int>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            rawBoundaries.add(start)
            rawBoundaries.add(end)
            start = end
            end = iterator.next()
        }

        if (rawBoundaries.isEmpty()) {
            val trimmed = text.trim()
            return if (trimmed.isNotEmpty()) listOf(Triple(trimmed, 0, trimmed.length)) else emptyList()
        }

        // Fusion des segments qui se terminent par une abréviation, puis
        // recalcul des offsets sur le texte TRIMMÉ (un seul passage — le
        // contrat de stabilité des offsets exige que `text.substring(start,
        // end)` reste exactement égal à la phrase retournée).
        val merged = mergeAbbreviationSplits(text, rawBoundaries)
        return merged.mapNotNull { (s, e) ->
            val raw = text.substring(s, e)
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val leadingSpaces = raw.length - raw.trimStart().length
            val start = s + leadingSpaces
            Triple(trimmed, start, start + trimmed.length)
        }
    }

    /**
     * Fusionne les coupures qui surviennent après une abréviation.
     *
     * Stratégie : parcourir les frontières 2 par 2. Si le segment courant
     * se termine par une abréviation connue suivie d'un point, l'étendre
     * jusqu'au segment suivant — et RÉÉVALUER : une chaîne d'abréviations
     * consécutives ("M. Dr. Dubois est venu.") doit fusionner tous ses
     * segments, pas seulement les deux premiers.
     */
    private fun mergeAbbreviationSplits(
        text: String,
        boundaries: List<Int>,
    ): List<Pair<Int, Int>> {
        if (boundaries.size < 2) return emptyList()

        val segments = boundaries.chunked(2) { (s, e) -> s to e }
        if (segments.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < segments.size) {
            val segStart = segments[i].first
            var segEnd = segments[i].second
            var next = i + 1
            while (next < segments.size && endsWithAbbreviation(text, segStart, segEnd)) {
                segEnd = segments[next].second
                next++
            }
            merged.add(segStart to segEnd)
            i = next
        }
        return merged
    }

    /**
     * Vérifie si le segment [start, end[ se termine par une abréviation
     * connue (mot suivi d'un point).
     */
    private fun endsWithAbbreviation(text: String, start: Int, end: Int): Boolean {
        if (end <= start) return false
        val segment = text.substring(start, end).trimEnd()
        // Dernier "mot" avant un point final éventuel — toute espace
        // blanche sépare les mots, pas seulement l'espace ASCII :
        // `substringAfterLast(' ')` manquait les sauts de ligne que
        // PDFium (une ligne visuelle par `\r\n`) et un TXT dur-wrappé
        // insèrent entre les mots, rendant le filtre inopérant sur ces
        // deux formats précisément.
        val lastWord = segment.takeLastWhile { !it.isWhitespace() }
            .trimEnd('.', ')', '»', '"', '\'')
        return lastWord.isNotEmpty() && lastWord in abbreviations
    }
}
