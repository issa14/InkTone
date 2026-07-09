package com.readflow.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.readflow.data.database.entity.BookEntity
import com.readflow.data.database.entity.BookmarkEntity
import com.readflow.data.database.entity.ProgressEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bookId TEXT NOT NULL,
                chapterIndex INTEGER NOT NULL,
                sentenceIndex INTEGER NOT NULL,
                text TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks (bookId)")
    }
}

@Database(
    entities = [BookEntity::class, ProgressEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ReadFlowDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
}
