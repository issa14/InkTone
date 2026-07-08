package com.readflow.data.mapper

import com.readflow.data.database.entity.BookEntity
import com.readflow.data.database.entity.ProgressEntity
import com.readflow.domain.model.Book
import com.readflow.domain.model.Progress

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    description = description,
    totalChapters = totalChapters,
    language = language,
    addedAt = addedAt
)

fun Book.toEntity(filePath: String, coverPath: String? = null) = BookEntity(
    id = id,
    title = title,
    author = author,
    description = description,
    filePath = filePath,
    coverPath = coverPath,
    totalChapters = totalChapters,
    language = language,
    addedAt = addedAt
)

fun ProgressEntity.toDomain() = Progress(
    bookId = bookId,
    currentChapterIndex = currentChapterIndex,
    currentSentenceIndex = currentSentenceIndex,
    totalProgressFraction = totalProgressFraction
)

fun Progress.toEntity() = ProgressEntity(
    bookId = bookId,
    currentChapterIndex = currentChapterIndex,
    currentSentenceIndex = currentSentenceIndex,
    currentWordOffset = 0,
    totalProgressFraction = totalProgressFraction,
    updatedAt = System.currentTimeMillis()
)
