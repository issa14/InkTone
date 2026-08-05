package com.inktone.feature.reader

import androidx.compose.ui.text.AnnotatedString
import com.inktone.domain.model.Sentence
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Régression — crash réel trouvé sur appareil pendant la vérification du
 * lot 3c (plusieurs occurrences en logcat, la première antérieure à ce
 * lot) : `IllegalArgumentException: start (...) should be less or equal
 * to end (...)` dans `AnnotatedString.subSequence`. `pageOffsetRange`
 * (versionné via `VirtualPaginationEngine`) et `full`
 * (`currentMeasurement.annotatedString`, `State` distinct sur
 * `ChapterPaginationState`) sont écrits séparément par
 * `rememberChapterPaginationState` pendant une mesure progressive
 * (changement de chapitre, de taille de police) — une recomposition
 * transitoire peut lire un `pageOffsetRange` calculé pour une mesure plus
 * longue que le `full` déjà retombé sur la mesure partielle du nouveau
 * style. Ce test reproduit directement ce décalage (sans passer par
 * Compose) : `pageOffsetRange.first` dépassant `full.length`.
 */
class PagedChapterContentBuildPageAnnotatedStringTest {

    @Test
    fun pageOffsetRange_depassant_full_length_ne_leve_pas_d_exception() {
        val full = AnnotatedString("Court texte.") // full.length = 12, mesure partielle post-changement de style
        val staleRange = 8298..8310 // pageOffsetRange calculé pour l'ancienne mesure, bien plus longue

        val result = buildPageAnnotatedString(
            full = full,
            pageOffsetRange = staleRange,
            sentences = emptyList(),
            sentenceStartOffsets = emptyList(),
            pageSentenceRange = IntRange.EMPTY,
            chapterIndex = 0,
            annotations = emptyList(),
        )

        // Page transitoirement vide plutôt qu'un crash — la frame suivante
        // (une fois `pagination`/`full` resynchronisés) affiche le contenu
        // correct.
        assertEquals("", result.text)
    }

    @Test
    fun pageOffsetRange_partiellement_hors_bornes_tronque_sans_exception() {
        val full = AnnotatedString("0123456789") // full.length = 10
        val partiallyStaleRange = 5..19 // fin bien au-delà de full.length

        val result = buildPageAnnotatedString(
            full = full,
            pageOffsetRange = partiallyStaleRange,
            sentences = emptyList(),
            sentenceStartOffsets = emptyList(),
            pageSentenceRange = IntRange.EMPTY,
            chapterIndex = 0,
            annotations = emptyList(),
        )

        assertEquals("56789", result.text)
    }

    @Test
    fun pageOffsetRange_coherent_avec_full_reste_inchange() {
        val full = AnnotatedString("Phrase un. Phrase deux.")
        val sentence = Sentence(index = 0, text = "Phrase un.", startOffset = 0, endOffset = 10)

        val result = buildPageAnnotatedString(
            full = full,
            pageOffsetRange = 0..22,
            sentences = listOf(sentence),
            sentenceStartOffsets = listOf(0),
            pageSentenceRange = 0..0,
            chapterIndex = 0,
            annotations = emptyList(),
        )

        assertEquals(full.text, result.text)
    }
}
