package com.readflow.ui.screen.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.data.repository.RecentBooksRepository
import com.readflow.data.database.entity.RecentBookEntity
import com.readflow.domain.model.Book
import com.readflow.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class LibraryUiState(
    val allBooks: List<Book> = emptyList(),
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val filterMode: FilterMode = FilterMode.ALL,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val filterType: FilterType = FilterType.ALL,
    val layoutMode: LayoutMode = LayoutMode.GRID_COVERS,
    val isFilterDialogVisible: Boolean = false,
    val currentDestination: NavigationDestination = NavigationDestination.LIBRARY,
    val isDarkTheme: Boolean = true,
    val recentBooks: List<RecentBookEntity> = emptyList()
)

enum class FilterMode { ALL, BY_AUTHOR, BY_TITLE, IN_PROGRESS, READ, UNREAD }
enum class SortOrder(val label: String) { TITLE("Nom de livre"), AUTHOR("Auteur"), DATE("Date d'import"), FOLDERS("Dossiers"), RECENT("Liste des récents") }
enum class FilterType(val label: String) { ALL("Tous"), UNREAD("Non lu"), IN_PROGRESS("En cours"), READ("Lu") }
enum class LayoutMode { LIST, GRID, GRID_COVERS }

enum class NavigationDestination(
    val label: String,
    val icon: ImageVector
) {
    RECENTS("Liste des récents", Icons.Default.Schedule),
    LIBRARY("Bibliothèque", Icons.Default.Book),
    FILES("Fichiers", Icons.Default.Folder),
    OPDS("Catalogues OPDS", Icons.Default.Language),
    BOOKMARKS("Marque-pages et notes", Icons.Default.Bookmark),
    STATS("Statistiques de lecture", Icons.Default.BarChart),
    SYNC("Synchronisation & Sauvegarde", Icons.Default.Sync),
    SETTINGS("Options", Icons.Default.Settings),
    ABOUT("À propos", Icons.Default.Info)
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val recentBooksRepo: RecentBooksRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
        // Observer les livres récents
        viewModelScope.launch {
            recentBooksRepo.recentBooks.collect { recents ->
                _uiState.update { it.copy(recentBooks = recents) }
            }
        }
    }

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

    fun showFilterDialog() { _uiState.update { it.copy(isFilterDialogVisible = true) } }
    fun hideFilterDialog() { _uiState.update { it.copy(isFilterDialogVisible = false) } }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        applyFilters()
    }

    fun setFilterType(type: FilterType) {
        _uiState.update { it.copy(filterType = type) }
        applyFilters()
    }

    fun setLayoutMode(mode: LayoutMode) {
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun navigateTo(dest: NavigationDestination) {
        _uiState.update { it.copy(currentDestination = dest) }
    }

    /**
     * Enregistre l'ouverture d'un livre dans l'historique des récents.
     * Appelé depuis [LibraryScreen] quand l'utilisateur ouvre un livre.
     */
    fun recordBookOpen(book: Book) {
        viewModelScope.launch {
            try {
                recentBooksRepo.openBook(
                    RecentBookEntity(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        coverPath = book.coverPath ?: ""
                    )
                )
            } catch (e: Exception) {
                // Non-bloquant : un échec d'enregistrement n'empêche pas la lecture
            }
        }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
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

        // Tri
        filtered = when (s.sortOrder) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.AUTHOR -> filtered.sortedBy { it.author.lowercase() }
            SortOrder.DATE -> filtered.sortedByDescending { it.addedAt }
            SortOrder.FOLDERS -> filtered  // TODO
            SortOrder.RECENT -> filtered.sortedByDescending { it.addedAt }
        }

        // Filtre type (TODO: progression réelle depuis Room)
        filtered = when (s.filterType) {
            FilterType.ALL -> filtered
            FilterType.UNREAD -> filtered
            FilterType.IN_PROGRESS -> filtered
            FilterType.READ -> emptyList()
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

    /** Import depuis un fichier local (explorateur FilesScreen). */
    fun importFile(inputStream: java.io.InputStream, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                bookRepository.importEpub(inputStream, fileName)
                loadBooks()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    /** Import par lot depuis des URIs (multi-sélection SAF). */
    fun importBooks(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                uris.forEach { uri ->
                    val fileName = resolveFileName(uri) ?: "inconnu.epub"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bookRepository.importEpub(stream, fileName)
                    }
                }
                withContext(Dispatchers.Main) { loadBooks() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
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
