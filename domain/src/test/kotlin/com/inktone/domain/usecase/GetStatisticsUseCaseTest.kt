package com.inktone.domain.usecase

import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.domain.model.HeatmapPoint
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GetStatisticsUseCaseTest {

    @Test
    fun livre_termine_reutilise_exactement_la_definition_de_FilterMode_READ() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingSessionRepository = FakeReadingSessionRepository()

        publicationRepository.insert(
            Publication(
                id = "pub-1", title = "Termine", format = PublicationFormat.EPUB,
                fileUri = "content://x", fileHash = "h1", fileSize = 10, chapterCount = 3, importDate = 0L,
            ),
        )
        publicationRepository.insert(
            Publication(
                id = "pub-2", title = "En cours", format = PublicationFormat.EPUB,
                fileUri = "content://y", fileHash = "h2", fileSize = 10, chapterCount = 3, importDate = 0L,
            ),
        )
        // pub-1 : dernier chapitre atteint (chapterIndex 2 == chapterCount-1) -> termine.
        publicationRepository.setChapterProgress("pub-1", 2)
        // pub-2 : pas encore au dernier chapitre -> pas termine.
        publicationRepository.setChapterProgress("pub-2", 0)

        readingSessionRepository.insert(
            ReadingSession(
                id = "s1", publicationId = "pub-1", startedAt = 0L, endedAt = 1000L,
                mode = ReadingMode.VISUAL, visualDurationMs = 1000L,
            ),
        )

        val useCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository)
        val result = useCase().first()

        assertEquals(1, result.booksFinished)
        assertEquals(1000L, result.totalVisualMs)
    }

    @Test
    fun serie_de_jours_consecutifs_incluant_aujourd_hui() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingSessionRepository = FakeReadingSessionRepository()

        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)

        readingSessionRepository.insert(
            ReadingSession(
                id = "s1", publicationId = "p", startedAt = now, endedAt = now,
                mode = ReadingMode.AUDIO, ttsDurationMs = 0L,
            ),
        )
        readingSessionRepository.insert(
            ReadingSession(
                id = "s2", publicationId = "p", startedAt = now - oneDayMs, endedAt = now - oneDayMs,
                mode = ReadingMode.AUDIO, ttsDurationMs = 0L,
            ),
        )

        val useCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository)
        val result = useCase().first()

        assertEquals(2, result.currentStreakDays)
        assertEquals(2, result.maxStreakDays) // pas plus de 2 jours dans le fake
    }

    @Test
    fun totalVisualMs_et_totalTtsMs_sont_separes() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingSessionRepository = FakeReadingSessionRepository()

        readingSessionRepository.insert(
            ReadingSession(
                id = "s1", publicationId = "p", startedAt = 0L, endedAt = 1000L,
                mode = ReadingMode.VISUAL, visualDurationMs = 500L, ttsDurationMs = 0L,
            ),
        )
        readingSessionRepository.insert(
            ReadingSession(
                id = "s2", publicationId = "p", startedAt = 0L, endedAt = 1000L,
                mode = ReadingMode.AUDIO, visualDurationMs = 0L, ttsDurationMs = 300L,
            ),
        )

        val result = GetStatisticsUseCase(readingSessionRepository, publicationRepository)().first()

        assertEquals(500L, result.totalVisualMs)
        assertEquals(300L, result.totalTtsMs)
        assertEquals(800L, result.totalVisualMs + result.totalTtsMs)
    }

    @Test
    fun heatmap_slot_regroupe_les_heures_brutes_en_5_creneaux() = runTest {
        val publicationRepository = FakePublicationRepository()
        val readingSessionRepository = FakeReadingSessionRepository()

        val useCase = GetStatisticsUseCase(readingSessionRepository, publicationRepository)

        // 4 points sur des heures non ambiguës
        val raw = listOf(
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 7, interactionCount = 2),  // → 6h
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 13, interactionCount = 5), // → 14h
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 21, interactionCount = 3), // → 22h
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 9, interactionCount = 1),  // → 10h
        )
        val slots = useCase.computeHeatmapSlots(raw)

        // 4 créneaux distincts
        assertEquals(4, slots.size)

        // Le créneau 13h (slotIndex 2) a l'intensité max (1.0)
        val slot14h = slots.first { it.slotIndex == 2 }
        assertEquals(1f, slot14h.intensity)
        assertTrue(slot14h.dayOfWeek == 1)

        // 5h → slot 6h (slotIndex 0)
        val slot6h = slots.first { it.slotIndex == 0 }
        assertTrue(slot6h.intensity <= 1f)
    }

    @Test
    fun heatmap_midnight_0_a_3_remonte_sur_22h_jour_precedent() = runTest {
        val useCase = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository())

        // Mardi (dayOfWeek=2) à 2h du matin → lundi (1) soir, slot 22h
        val raw = listOf(HeatmapPoint(dayOfWeek = 2, hourOfDay = 2, interactionCount = 1))
        val slots = useCase.computeHeatmapSlots(raw)

        assertEquals(1, slots.size)
        assertEquals(4, slots.first().slotIndex) // 22h
        assertEquals(1, slots.first().dayOfWeek) // lundi
        assertEquals(1f, slots.first().intensity)
    }

    @Test
    fun pic_ignore_l_intensite_normalisee_et_compare_les_comptes_bruts() = runTest {
        val useCase = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository())

        // Meme jeu de donnees que heatmap_slot_regroupe_les_heures_brutes_en_5_creneaux :
        // un seul jour de donnees par creneau, donc les 4 creneaux ont chacun une
        // intensite normalisee de 1.0 (chacun est son propre max). Avant le fix,
        // maxByOrNull sur l'intensite ne pouvait pas departager cette egalite de
        // maniere fiable. Ici, 14h (5 interactions) doit gagner sur les comptes bruts.
        val raw = listOf(
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 7, interactionCount = 2),  // → 6h
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 13, interactionCount = 5), // → 14h (le plus actif)
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 21, interactionCount = 3), // → 22h
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 9, interactionCount = 1),  // → 10h
        )

        assertEquals(2, useCase.computePeakSlotIndex(raw)) // slotIndex 2 = 14h
    }

    @Test
    fun pic_departage_une_egalite_par_le_creneau_le_plus_tot() = runTest {
        val useCase = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository())

        // 6h (slot 0) et 18h (slot 3) ont exactement le meme total agrege (3) :
        // le departage doit etre deterministe, pas dependant d'un ordre SQL non garanti.
        val raw = listOf(
            HeatmapPoint(dayOfWeek = 1, hourOfDay = 6, interactionCount = 3),  // → 6h
            HeatmapPoint(dayOfWeek = 2, hourOfDay = 18, interactionCount = 3), // → 18h
        )

        assertEquals(0, useCase.computePeakSlotIndex(raw)) // 6h retenu, le plus tot
    }

    @Test
    fun pic_absent_sans_donnees() = runTest {
        val useCase = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository())
        assertEquals(null, useCase.computePeakSlotIndex(emptyList()))
    }
}
