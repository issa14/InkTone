package com.readflow.ui.screen.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.domain.model.Book
import com.readflow.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val allBooks: List<Book> = emptyList(),
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val filterMode: FilterMode = FilterMode.ALL
)

enum class FilterMode { ALL, BY_AUTHOR, BY_TITLE, IN_PROGRESS, READ, UNREAD }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init { loadBooks() }

    private fun loadBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val books = bookRepository.getAllBooks()
                _uiState.update { it.copy(allBooks = books, isLoading = false) }
                applyFilters()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setFilterMode(mode: FilterMode) {
        _uiState.update { it.copy(filterMode = mode) }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _uiState.value
        var filtered = s.allBooks

        // Filtre texte
        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) || it.author.lowercase().contains(q)
            }
        }

        // Filtre mode
        filtered = when (s.filterMode) {
            FilterMode.ALL -> filtered
            FilterMode.BY_AUTHOR -> filtered.sortedBy { it.author.lowercase() }
            FilterMode.BY_TITLE -> filtered.sortedBy { it.title.lowercase() }
            FilterMode.IN_PROGRESS -> filtered  // TODO: progression réelle
            FilterMode.READ -> emptyList()       // TODO
            FilterMode.UNREAD -> filtered        // TODO
        }

        _uiState.update { it.copy(books = filtered) }
    }

    fun importEpub(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val fileName = resolveFileName(uri) ?: "inconnu.epub"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bookRepository.importEpub(stream, fileName)
                } ?: throw IllegalStateException("Impossible de lire le fichier")
                loadBooks()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun refresh() = loadBooks()
    fun clearError() { _uiState.update { it.copy(error = null) } }

    private fun resolveFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        }
    }
}
