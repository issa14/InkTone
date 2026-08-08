package com.inktone.feature.statistics

import com.inktone.core.testing.fake.FakePreferencesRepository
import com.inktone.core.testing.fake.FakePublicationRepository
import com.inktone.core.testing.fake.FakeReadingSessionRepository
import com.inktone.core.testing.fake.FakeReadingStateRepository
import com.inktone.core.testing.fake.FakeStatisticsExportService
import com.inktone.domain.model.Publication
import com.inktone.domain.model.PublicationFormat
import com.inktone.domain.model.ReadingMode
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.usecase.GetCurrentBookUseCase
import com.inktone.domain.usecase.GetStatisticsUseCase
import com.inktone.domain.usecase.StatsPeriod
import com.inktone.domain.usecase.StatisticsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun publication(id: String, chapterCount: Int = 10) = Publication(
        id = id, title = "Titre $id", format = PublicationFormat.EPUB,
        fileUri = "content://$id", fileHash = "hash-$id", fileSize = 100L,
        chapterCount = chapterCount, importDate = 0L,
    )

    private fun session(id: String, publicationId: String, startedAt: Long, wordsRead: Int = 0, visualMs: Long = 0, ttsMs: Long = 0) =
        ReadingSession(
            id = id, publicationId = publicationId, startedAt = startedAt, endedAt = startedAt + visualMs + ttsMs,
            mode = if (ttsMs > 0) ReadingMode.AUDIO else ReadingMode.VISUAL,
            wordsRead = wordsRead, visualDurationMs = visualMs, ttsDurationMs = ttsMs,
        )

    @Test
    fun etat_initial_est_Loading() = runTest {
        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(FakeReadingSessionRepository(), FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        // Tant que le combine n'a pas émis, l'état initial (seed) de stateIn est Loading
        assertTrue(vm.state.value is StatisticsUiState.Loading)
    }

    @Test
    fun passe_a_Ready_avec_des_KPIs_formattes() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()

        // Une session pour avoir des stats non nulles
        readingSessionRepo.insert(
            ReadingSession("s1", "pub-1", System.currentTimeMillis(), System.currentTimeMillis() + 60_000,
                ReadingMode.VISUAL, visualDurationMs = 30_000, ttsDurationMs = 0),
        )

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready

        // Les durées brutes sont transformées en Strings formatées
        assertEquals("0h 0m", ready.kpi.totalVisualTimeFormatted)
        assertEquals("0h 0m", ready.kpi.totalTtsTimeFormatted)
        assertTrue(ready.kpi.currentStreakDays >= 0)
        assertTrue(ready.kpi.averageWpm >= 0)
    }

    @Test
    fun livre_en_cours_est_null_sans_session() = runTest {
        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(FakeReadingSessionRepository(), FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready
        assertEquals(null, ready.currentBook)
    }

    @Test
    fun livre_en_cours_affiche_titre_et_progression() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        val publicationRepo = FakePublicationRepository()
        publicationRepo.insert(publication("pub-1", chapterCount = 5))

        // Une session récente pour désigner pub-1 comme livre en cours
        readingSessionRepo.insert(
            ReadingSession("s1", "pub-1", System.currentTimeMillis(), System.currentTimeMillis() + 60_000,
                ReadingMode.VISUAL, visualDurationMs = 30_000),
        )

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), publicationRepo),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, publicationRepo, FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready
        val book = ready.currentBook

        assertNotNull(book)
        assertEquals("pub-1", book!!.id)
        assertEquals("Titre pub-1", book.title)
    }

    @Test
    fun formatage_des_durees_est_en_heures_et_minutes() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        readingSessionRepo.insert(
            ReadingSession("s1", "pub-1", 0L, 3_600_000L + 1_800_000L, // 1h30
                ReadingMode.VISUAL, visualDurationMs = 3_600_000L, ttsDurationMs = 1_800_000L),
        )

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready

        assertEquals("1h 0m", ready.kpi.totalVisualTimeFormatted)
        assertEquals("0h 30m", ready.kpi.totalTtsTimeFormatted)
    }

    // ───── Tache 7.1 — objectif quotidien branché sur les préférences ─────

    @Test
    fun objectif_quotidien_utilise_la_preference_et_pas_la_valeur_en_dur() = runTest {
        val prefsRepo = FakePreferencesRepository()
        prefsRepo.update(prefsRepo.get().copy(dailyGoalMinutes = 45))

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(FakeReadingSessionRepository(), FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = prefsRepo,
            exportService = FakeStatisticsExportService(),
        )

        val state = vm.state.first { it is StatisticsUiState.Ready }
        val ready = state as StatisticsUiState.Ready

        assertEquals(45, ready.kpi.dailyGoalMinutes)
    }

    @Test
    fun objectif_quotidien_se_recalibre_sans_redemarrage_quand_la_preference_change() = runTest {
        val prefsRepo = FakePreferencesRepository()

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(FakeReadingSessionRepository(), FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(FakeReadingSessionRepository(), FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = prefsRepo,
            exportService = FakeStatisticsExportService(),
        )

        val initial = vm.state.first { it is StatisticsUiState.Ready } as StatisticsUiState.Ready
        assertEquals(30, initial.kpi.dailyGoalMinutes)

        // L'utilisateur règle 45 min dans les Réglages, sans redémarrer l'app :
        // le même ViewModel doit refléter le changement.
        prefsRepo.update(prefsRepo.get().copy(dailyGoalMinutes = 45))

        val updated = vm.state.first { (it as? StatisticsUiState.Ready)?.kpi?.dailyGoalMinutes == 45 } as StatisticsUiState.Ready
        assertEquals(45, updated.kpi.dailyGoalMinutes)
    }

    // ───── Tache 7.2 — volumes parcourus, format abrégé, régularité ─────

    @Test
    fun mots_parcourus_sont_formates_en_abrege_pour_les_grands_nombres() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        readingSessionRepo.insert(session("s1", "pub-1", 0L, wordsRead = 1_250_000, visualMs = 1_000L))

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        val ready = vm.state.first { it is StatisticsUiState.Ready } as StatisticsUiState.Ready

        // Jamais la forme brute "1250000"
        assertTrue(ready.kpi.totalWordsReadFormatted.endsWith("M"))
        assertTrue(!ready.kpi.totalWordsReadFormatted.contains("1250000"))
    }

    @Test
    fun libelle_de_regularite_reflete_l_assiduite_reelle() = runTest {
        val noStreak = FakeReadingSessionRepository()
        val vmNoStreak = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(noStreak, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(noStreak, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )
        val readyNoStreak = vmNoStreak.state.first { it is StatisticsUiState.Ready } as StatisticsUiState.Ready

        val longStreak = FakeReadingSessionRepository()
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)
        for (i in 0 until 8) {
            longStreak.insert(session("streak-$i", "pub-1", now - i * oneDayMs, visualMs = 1_000L))
        }
        val vmLongStreak = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(longStreak, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(longStreak, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )
        val readyLongStreak = vmLongStreak.state.first { it is StatisticsUiState.Ready } as StatisticsUiState.Ready

        // Le libellé n'est pas constant : il varie avec la série réelle.
        assertNotEquals(readyNoStreak.kpi.regularityLabel, readyLongStreak.kpi.regularityLabel)
    }

    // ───── Tache 7.4 — sélecteur Semaine/Mois ─────

    @Test
    fun changer_de_periode_change_le_total_et_potentiellement_la_variation() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)
        // 20 jours d'activité récente, au-delà de la semaine mais dans le mois.
        for (i in 0 until 20) {
            readingSessionRepo.insert(session("d-$i", "pub-1", now - i * oneDayMs, visualMs = 60_000L))
        }

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )

        val monthState = vm.state.first { it is StatisticsUiState.Ready } as StatisticsUiState.Ready
        assertEquals(StatsPeriod.MONTH, monthState.activity.period)

        vm.onPeriodSelected(StatsPeriod.WEEK)
        val weekState = vm.state.first { (it as? StatisticsUiState.Ready)?.activity?.period == StatsPeriod.WEEK } as StatisticsUiState.Ready

        // Le mois (20 jours d'activité) cumule strictement plus que la semaine (7 jours).
        assertNotEquals(monthState.activity.periodTotalFormatted, weekState.activity.periodTotalFormatted)
    }

    // ───── Densification des jours manquants (carte Activité) ─────

    @Test
    fun activite_contient_toujours_exactement_7_jours_en_vue_semaine_meme_avec_des_trous() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)
        // Deux sessions non consecutives : aujourd'hui et il y a 3 jours.
        // Cote SQL, les jours sans session n'ont aucune ligne (GROUP BY date) :
        // sans densification, la liste ne contiendrait que 2 entrees.
        readingSessionRepo.insert(session("today", "pub-1", now, visualMs = 60_000L))
        readingSessionRepo.insert(session("j-3", "pub-1", now - 3 * oneDayMs, visualMs = 30_000L))

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )
        vm.onPeriodSelected(StatsPeriod.WEEK)

        val ready = vm.state.first { (it as? StatisticsUiState.Ready)?.activity?.period == StatsPeriod.WEEK } as StatisticsUiState.Ready

        // 7 jours calendaires consecutifs, pas seulement les 2 jours actifs.
        assertEquals(7, ready.activity.dailyStats.size)
        // La derniere entree est bien aujourd'hui (avec la session inseree).
        assertEquals(60_000L, ready.activity.dailyStats.last().visualMs)
        // Un jour sans session est present, a zero — pas absent de la liste.
        assertTrue(ready.activity.dailyStats.any { it.visualMs == 0L && it.ttsMs == 0L })
    }

    // ───── Correction du calcul de variation (fenetres calendaires, pas les N derniers jours actifs) ─────

    @Test
    fun variation_compare_les_bonnes_fenetres_calendaires_meme_avec_un_trou() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)

        // Periode actuelle : J-0 a J-6, activite chaque jour.
        for (i in 0..6) {
            readingSessionRepo.insert(session("cur-$i", "pub-1", now - i * oneDayMs, visualMs = 60_000L))
        }
        // Trou calendaire de J-7 a J-20 : aucune session (la vraie semaine
        // precedente, J-7..J-13, est donc entierement vide).
        // Activite plus ancienne, J-21 a J-27 : avec l'ancien code (takeLast/
        // dropLast sur la liste creuse), ces 7 entrees etaient prises a tort
        // comme "periode precedente" puisque ce sont les 7 entrees actives
        // juste avant les 7 dernieres, quel que soit leur veritable ecart
        // calendaire.
        for (i in 21..27) {
            readingSessionRepo.insert(session("old-$i", "pub-1", now - i * oneDayMs, visualMs = 90_000L))
        }

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )
        vm.onPeriodSelected(StatsPeriod.WEEK)

        val ready = vm.state.first { (it as? StatisticsUiState.Ready)?.activity?.period == StatsPeriod.WEEK } as StatisticsUiState.Ready

        // La vraie semaine precedente (J-7..J-13) est vide : pas de comparaison
        // possible, et surtout pas un pourcentage calcule a partir de
        // l'activite de J-21..J-27.
        assertEquals("—", ready.activity.variationPercent)
    }

    @Test
    fun variation_est_un_tiret_quand_la_periode_precedente_est_entierement_vide() = runTest {
        val readingSessionRepo = FakeReadingSessionRepository()
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)

        // Seule la semaine en cours a de l'activite, rien avant.
        for (i in 0..6) {
            readingSessionRepo.insert(session("cur-$i", "pub-1", now - i * oneDayMs, visualMs = 45_000L))
        }

        val vm = StatisticsViewModel(
            getStatistics = GetStatisticsUseCase(readingSessionRepo, FakePublicationRepository()),
            getCurrentBook = GetCurrentBookUseCase(readingSessionRepo, FakePublicationRepository(), FakeReadingStateRepository()),
            preferencesRepository = FakePreferencesRepository(),
            exportService = FakeStatisticsExportService(),
        )
        vm.onPeriodSelected(StatsPeriod.WEEK)

        val ready = vm.state.first { (it as? StatisticsUiState.Ready)?.activity?.period == StatsPeriod.WEEK } as StatisticsUiState.Ready

        assertEquals("—", ready.activity.variationPercent)
    }
}
