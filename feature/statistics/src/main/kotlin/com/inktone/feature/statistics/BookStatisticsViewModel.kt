package com.inktone.feature.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.ReadingSession
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.repository.ReadingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Item d'historique temporel pour l'écran de détail par ouvrage
 * (Lot Statistiques Palier 4).
 *
 * **Décision de conception — pourquoi l'historique est purement
 * temporel et ignore les chapitres :**
 *
 * 1. **Instabilité structurelle des chapitres.** Les fichiers EPUB
 *    mal formés ou les PDF ne garantissent pas une table des matières
 *    fiable. Un index de chapitre basé sur `chapterIndex` peut pointer
 *    vers une ressource inexistante ou un découpage arbitraire selon
 *    le parseur — afficher "Chapitre 3" alors que le livre n'en a que
 *    2 est pire que de ne rien afficher.
 *
 * 2. **Incohérence des micro-sessions.** Une session TTS de 4 minutes
 *    peut ne pas faire avancer l'index de chapitre. Afficher le même
 *    chapitre pour 15 sessions consécutives donne l'illusion que
 *    l'utilisateur n'avance pas, alors qu'il progresse réellement dans
 *    le texte. L'information temporelle (dates, durées) est plus
 *    honnête et plus stable.
 *
 * 3. **Séparation des responsabilités.** L'index de chapitre appartient
 *    au domaine de la reprise de lecture (`ReadingState`), pas à celui
 *    de l'activité (`ReadingSession`). Mélanger les deux dans
 *    l'historique viole la single responsibility : `ReadingState`
 *    répond à "où reprendre ?", `ReadingSession` répond à "quand et
 *    combien de temps a-t-on lu ?". L'écran de détail affiche
 *    l'historique d'activité, pas l'historique de progression.
 */
data class SessionHistoryItem(
    val dateFormatted: String,
    val timeRange: String,
    val durationFormatted: String,
    val isVisual: Boolean,
    val isTts: Boolean,
)

/**
 * État de l'écran de détail par ouvrage (Palier 4).
 */
sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState
    data class Ready(
        val bookTitle: String,
        val bookAuthor: String,
        val availableBooks: List<BookSelectorItem>,
        val wpmFormatted: String,
        val remainingTimeFormatted: String,
        val history: List<SessionHistoryItem>,
    ) : BookDetailUiState
}

data class BookSelectorItem(
    val id: String,
    val title: String,
)

@HiltViewModel
class BookStatisticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val readingSessionRepository: ReadingSessionRepository,
    private val publicationRepository: PublicationRepository,
) : ViewModel() {

    private val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    val state: StateFlow<BookDetailUiState> = flow {
        val publication = publicationRepository.getById(bookId)
        val sessions = readingSessionRepository.getByPublicationId(bookId)

        // Sélecteur : DISTINCT publicationId (SQL, pas getAll)
        val distinctIds = readingSessionRepository.getDistinctPublicationIds()
        val availableBooks = distinctIds.mapNotNull { id ->
            publicationRepository.getById(id)?.let { BookSelectorItem(it.id, it.title) }
        }.sortedBy { it.title }

        // KPIs
        val wpm = computeWpm(sessions)
        val remainingFormatted = if (wpm > 0 && (publication?.chapterCount ?: 0) > 0) {
            val estimatedWordsRemaining = publication!!.chapterCount * 3000L
            val minutesRemaining = estimatedWordsRemaining / wpm
            formatDuration(TimeUnit.MINUTES.toMillis(minutesRemaining))
        } else "—"

        // Historique — DateTimeFormatter (thread-safe, API 26+)
        val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        val timeFmt = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH)
        val zone = ZoneId.systemDefault()
        val history = sessions.sortedByDescending { it.startedAt }.map { session ->
            val start = Instant.ofEpochMilli(session.startedAt).atZone(zone)
            val end = session.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
            SessionHistoryItem(
                dateFormatted = dateFmt.format(start),
                timeRange = if (end != null) "${timeFmt.format(start)} - ${timeFmt.format(end)}"
                else timeFmt.format(start),
                durationFormatted = formatDuration(session.durationMs),
                isVisual = session.visualDurationMs > 0,
                isTts = session.ttsDurationMs > 0,
            )
        }

        emit(
            BookDetailUiState.Ready(
                bookTitle = publication?.title ?: bookId,
                bookAuthor = publication?.authors?.firstOrNull() ?: "",
                availableBooks = availableBooks,
                wpmFormatted = "$wpm WPM",
                remainingTimeFormatted = remainingFormatted,
                history = history,
            )
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailUiState.Loading)

    private fun computeWpm(sessions: List<ReadingSession>): Int {
        val withWords = sessions.filter { it.wordsRead > 0 && it.durationMs > 0 }
        if (withWords.isEmpty()) return 0
        val totalWords = withWords.sumOf { it.wordsRead }
        val totalMinutes = withWords.sumOf { it.durationMs } / 60_000.0
        return if (totalMinutes > 0) (totalWords / totalMinutes).toInt() else 0
    }

    private fun formatDuration(ms: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
    }
}
