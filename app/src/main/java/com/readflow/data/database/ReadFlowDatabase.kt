package com.readflow.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.readflow.data.database.entity.BookEntity
import com.readflow.data.database.entity.ProgressEntity

@Database(
    entities = [BookEntity::class, ProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReadFlowDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
}
