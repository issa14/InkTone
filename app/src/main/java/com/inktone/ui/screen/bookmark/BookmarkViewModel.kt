package com.inktone.ui.screen.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.data.database.BookmarkDao
import com.inktone.data.database.HighlightDao
import com.inktone.data.database.entity.BookmarkEntity
import com.inktone.data.database.entity.HighlightEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao
) : ViewModel() {

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks

    private val _highlights = MutableStateFlow<List<HighlightEntity>>(emptyList())
    val highlights: StateFlow<List<HighlightEntity>> = _highlights

    private var currentBookId: String = ""

    fun load(bookId: String) {
        if (currentBookId == bookId) return
        currentBookId = bookId
        viewModelScope.launch {
            bookmarkDao.getBookmarks(bookId).collect { _bookmarks.value = it }
        }
        viewModelScope.launch {
            highlightDao.getHighlightsForBook(bookId).collect { _highlights.value = it }
        }
    }

    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch { highlightDao.deleteHighlight(highlight) }
    }

    fun add(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkDao.insert(bookmark) }
    }

    fun delete(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkDao.delete(bookmark) }
    }

    fun toggle(bookId: String, chapterIndex: Int, sentenceIndex: Int, text: String) {
        viewModelScope.launch {
            val existing = bookmarkDao.findByPosition(bookId, chapterIndex, sentenceIndex)
            if (existing != null) {
                bookmarkDao.delete(existing)
            } else {
                bookmarkDao.insert(BookmarkEntity(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    sentenceIndex = sentenceIndex,
                    text = text.take(120)
                ))
            }
        }
    }

    // ── Mode "Tous les livres" pour le drawer ──

    fun loadAll(searchQuery: String = "") {
        viewModelScope.launch {
            val flow = if (searchQuery.isBlank()) {
                bookmarkDao.getAllBookmarks()
            } else {
                bookmarkDao.searchBookmarks(searchQuery)
            }
            flow.collect { _bookmarks.value = it }
        }
    }
}
