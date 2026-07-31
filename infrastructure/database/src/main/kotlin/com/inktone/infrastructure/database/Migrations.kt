package com.inktone.infrastructure.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Ajoute la table virtuelle FTS4 `sentence_fts` (Tâche 7.3.1, Blueprint
 * §6.9) — première migration réelle de ce projet (K4, Blueprint §14.5) :
 * aucun `fallbackToDestructiveMigration`, cette migration doit accompagner
 * `InkToneDatabase.version = 2` dans le même commit, avec son test
 * (`DatabaseMigrationTest`).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS sentence_fts USING fts4(
                publicationId, chapterIndex, resourceHref, charOffset, text
            )
            """.trimIndent(),
        )
    }
}

/** Tache 8.0 : UserPreferences etendu (FontFamily, reduceMotion). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN fontFamily TEXT NOT NULL DEFAULT 'DEFAULT'")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN reduceMotion INTEGER NOT NULL DEFAULT 0")
    }
}

/** Tache 8.3 : regles de prononciation personnalisees. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pronunciation_rules (
                id TEXT NOT NULL PRIMARY KEY,
                originalText TEXT NOT NULL,
                replacementText TEXT NOT NULL,
                isRegex INTEGER NOT NULL,
                isEnabled INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/** Tache 9bis.5 : couleur dynamique et reglette de lecture (reglages). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN dynamicColorEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN readingRulerEnabled INTEGER NOT NULL DEFAULT 0")
    }
}

/** Tache 1.4 (Partie 1) : objectif de lecture quotidien. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN dailyGoalMinutes INTEGER NOT NULL DEFAULT 20")
    }
}

/** A.5 : profil vocal actif. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN activeVoiceProfileId TEXT DEFAULT NULL")
    }
}

/** D.4 : compteur de mots lus pour les statistiques WPM. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_sessions ADD COLUMN wordsRead INTEGER NOT NULL DEFAULT 0")
    }
}

/** B.1 : persistance du mode de lecture (SCROLL / PAGED). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN readingMode TEXT NOT NULL DEFAULT 'SCROLL'")
    }
}
