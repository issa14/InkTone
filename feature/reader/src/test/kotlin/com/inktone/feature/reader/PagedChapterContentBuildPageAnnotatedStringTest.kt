package com.inktone.feature.reader

import androidx.compose.ui.text.AnnotatedString
import com.inktone.domain.model.Annotation
import com.inktone.domain.model.AnnotationColor
import com.inktone.domain.valueobject.Locator
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
            chapterIndex = 0,
            annotations = emptyList(),
        )

        assertEquals("56789", result.text)
    }

    @Test
    fun pageOffsetRange_coherent_avec_full_reste_inchange() {
        val full = AnnotatedString("Phrase un. Phrase deux.")

        val result = buildPageAnnotatedString(
            full = full,
            pageOffsetRange = 0..22,
            chapterIndex = 0,
            annotations = emptyList(),
        )

        assertEquals(full.text, result.text)
    }

    /**
     * Régression — diagnostic palier 3f.2 : une annotation au MOT (offsets
     * de caractère exacts, sélection libre 3f.1) se retrouvait peinte sur
     * la PHRASE entière qui la contient, l'ancienne version bouclant sur
     * les phrases de la page et appliquant le `SpanStyle` sur leurs bornes
     * complètes dès qu'une annotation les touchait (simple test de
     * chevauchement, pas d'intersection réelle). Ici, l'annotation ne
     * couvre que le mot « deux » (offsets 18..21 dans « Phrase un. Phrase
     * deux. ») — le `SpanStyle` posé doit s'arrêter exactement là, pas
     * couvrir toute la deuxième phrase (offsets 11..22).
     */
    @Test
    fun annotation_au_mot_ne_peint_que_ses_propres_offsets_pas_toute_la_phrase() {
        val full = AnnotatedString("Phrase un. Phrase deux.")
        val annotation = Annotation(
            id = "a1",
            publicationId = "pub",
            startLocator = Locator(resourceHref = "c.xhtml", chapterIndex = 0, charOffset = 18),
            endLocator = Locator(resourceHref = "c.xhtml", chapterIndex = 0, charOffset = 22),
            color = AnnotationColor.YELLOW,
            createdAt = 0,
            updatedAt = 0,
        )

        val result = buildPageAnnotatedString(
            full = full,
            pageOffsetRange = 0..22,
            chapterIndex = 0,
            annotations = listOf(annotation),
        )

        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles.first()
        assertEquals(18, span.start)
        assertEquals(22, span.end)
        assertEquals(AnnotationColor.YELLOW.toComposeColor(), span.item.background)
    }
}
