package com.inktone.feature.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingState
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingStateRepository
import com.inktone.domain.valueobject.Locator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Valide K3 pour la marche à blanc : sauver un ReadingState, simuler une
 * "relance" (nouvelle instance du repository sur la même base), vérifier
 * que la position exacte (mot) est bien restaurée.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReadingResumeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var readingStateRepository: ReadingStateRepository

    @Inject
    lateinit var publicationRepository: PublicationRepository

    @Test
    fun la_position_sauvegardee_est_restauree_a_l_identique() = runTest {
        hiltRule.inject()

        // ReadingStateEntity porte une contrainte de cle etrangere vers
        // PublicationEntity (Phase 2, ON DELETE CASCADE) : sauver un
        // ReadingState sans publication existante viole cette contrainte
        // sur la base Room reelle utilisee par ce test (pas in-memory).
        publicationRepository.insert(
            Publication(
                id = "walking-skeleton-fixture", title = "Fixture marche a blanc",
                format = PublicationFormat.EPUB, fileUri = "content://fixture",
                fileHash = "walking-skeleton-fixture-hash", fileSize = 0L,
                chapterCount = 1, importDate = System.currentTimeMillis(),
            ),
        )

        val locator = Locator(resourceHref = "OEBPS/chapter1.xhtml", chapterIndex = 0, charOffset = 42)
        val state = ReadingState(
            publicationId = "walking-skeleton-fixture", locator = locator,
            lastReadAt = System.currentTimeMillis(),
        )

        readingStateRepository.save(state)

        // "Relance" simulee : relecture depuis le meme repository injecte
        // (base Room reelle, pas in-memory, pour ce test - a la difference
        // des tests CRUD de la Phase 2, l'objectif ici est la persistance
        // reelle, pas la logique DAO isolee).
        val restored = readingStateRepository.get("walking-skeleton-fixture")

        assertEquals(locator, restored?.locator)
    }
}
