package com.readflow.data.repository

import android.content.Context
import com.readflow.data.database.BookDao
import com.readflow.data.database.ProgressDao
import com.readflow.data.mapper.toDomain
import com.readflow.data.mapper.toEntity
import com.readflow.domain.model.Book
import com.readflow.domain.model.Chapter
import com.readflow.domain.model.Progress
import com.readflow.domain.model.Sentence
import com.readflow.domain.repository.BookRepository
import com.readflow.domain.usecase.ChunkTextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val chunkText: ChunkTextUseCase
) : BookRepository {

    override suspend fun importEpub(inputStream: InputStream, fileName: String): Book {
        val bookId = UUID.randomUUID().toString()
        val epubDir = File(context.filesDir, "epubs/$bookId")
        epubDir.mkdirs()

        val epubFile = File(epubDir, fileName)
        epubFile.outputStream().use { inputStream.copyTo(it) }

        // TODO Phase 2.1-2.2 : remplacer par Readium Toolkit pour extraire
        // titre, auteur, chapitres, et contenu texte réel.
        val book = Book(
            id = bookId,
            title = fileName.removeSuffix(".epub"),
            author = "Auteur inconnu",
            description = null,
            totalChapters = 1,
            language = "fr",
            addedAt = System.currentTimeMillis()
        )

        bookDao.insert(book.toEntity(epubFile.absolutePath))
        return book
    }

    override suspend fun getChapter(bookId: String, chapterIndex: Int): Chapter {
        // TODO Phase 2.1 : extraire le texte du chapitre via Readium
        // Pour l'instant, retourne un chapitre vide
        return Chapter(
            index = chapterIndex,
            title = "Chapitre $chapterIndex",
            sentences = emptyList()
        )
    }

    override suspend fun getAllBooks(): List<Book> {
        return bookDao.getAll().first().map { it.toDomain() }
    }

    override suspend fun saveProgress(progress: Progress) {
        progressDao.upsert(progress.toEntity())
    }

    override suspend fun getProgress(bookId: String): Progress? {
        return progressDao.getByBookId(bookId)?.toDomain()
    }
}
