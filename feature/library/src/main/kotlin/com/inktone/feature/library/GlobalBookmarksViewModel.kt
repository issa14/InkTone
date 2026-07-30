package com.inktone.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository
import com.inktone.domain.repository.PublicationRepository
import com.inktone.domain.usecase.DeleteBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalBookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val publicationRepository: PublicationRepository,
    private val deleteBookmark: DeleteBookmarkUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalBookmarksUiState())
    val state: StateFlow<GlobalBookmarksUiState> = _state.asStateFlow()

    private val _effects = Channel<GlobalBookmarksEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Cache titre par publicationId - evite un aller-retour DAO par
    // signet a chaque emission (K8, meme discipline que la progression
    // de bibliotheque : une requete groupee, jamais de N+1).
    private val titleCache = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            bookmarkRepository.observeAll().collect { bookmarks ->
                _state.value = _state.value.copy(
                    bookmarks = bookmarks.map { bookmark -> BookmarkWithPublicationTitle(bookmark, titleFor(bookmark.publicationId)) },
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun titleFor(publicationId: String): String =
        titleCache.getOrPut(publicationId) { publicationRepository.getById(publicationId)?.title ?: publicationId }

    fun onIntent(intent: GlobalBookmarksIntent) {
        when (intent) {
            is GlobalBookmarksIntent.SetSearchQuery -> _state.value = _state.value.copy(searchQuery = intent.query)
            is GlobalBookmarksIntent.OpenBookmark -> viewModelScope.launch { emitNavigateToReader(intent.bookmark) }
            is GlobalBookmarksIntent.DeleteBookmark -> viewModelScope.launch { deleteBookmark(intent.id) }
        }
    }

    private suspend fun emitNavigateToReader(bookmark: Bookmark) {
        _effects.send(
            GlobalBookmarksEffect.NavigateToReader(
                publicationId = bookmark.publicationId,
                resourceHref = bookmark.locator.resourceHref,
                chapterIndex = bookmark.locator.chapterIndex,
                charOffset = bookmark.locator.charOffset,
            ),
        )
    }
}
