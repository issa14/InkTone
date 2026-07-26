package com.inktone.domain.usecase

import com.inktone.domain.model.Bookmark
import com.inktone.domain.repository.BookmarkRepository

class CreateBookmarkUseCase(
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend operator fun invoke(bookmark: Bookmark) {
        bookmarkRepository.insert(bookmark)
    }
}
