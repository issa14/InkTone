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

/** D.3 : gain audio et respect du fontScale système. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN audioGain REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN useSystemFontScale INTEGER NOT NULL DEFAULT 0")
    }
}

/** Lot 2b.1 : épinglage d'une publication (remontée en tête de la bibliothèque). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE publications ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

/** 3d.2/3d.3/3d.5 : interligne, luminosité du lecteur, rappel de repos oculaire. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN lineHeightMultiplier REAL NOT NULL DEFAULT 1.4")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN readerBrightness REAL DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN eyeRestReminderEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN eyeRestReminderIntervalMinutes INTEGER NOT NULL DEFAULT 60")
    }
}

/**
 * Lot 4.2/4.3/4.4 : extrait de texte persisté et épinglage sur les
 * marque-pages et annotations, plus la vue UNION `library_items` qui
 * fusionne les deux sources pour la vue globale « Marque-pages et
 * notes » (recherche/tri entièrement SQL, tâche 4.4).
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN excerpt TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE bookmarks ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE annotations ADD COLUMN excerpt TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE annotations ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP VIEW IF EXISTS `library_items`")
        // Le texte SQL ci-dessous doit être byte pour byte celui que Room
        // génère depuis LibraryItemView (@DatabaseView) : la validation de
        // migration (MigrationTestHelper) compare le SQL normalisé de la
        // vue trouvée en base à celui attendu par le schéma exporté — un
        // simple écart d'indentation ou de saut de ligne fait échouer
        // runMigrationsAndValidate. Ne pas reformater sans reexporter le
        // schéma et comparer.
        db.execSQL(
            "CREATE VIEW `library_items` AS SELECT\n" +
                "            'bookmark' AS type,\n" +
                "            b.id AS id,\n" +
                "            b.publicationId AS publicationId,\n" +
                "            p.title AS publicationTitle,\n" +
                "            b.resourceHref AS resourceHref,\n" +
                "            b.chapterIndex AS chapterIndex,\n" +
                "            b.paragraphIndex AS paragraphIndex,\n" +
                "            b.charOffset AS charOffset,\n" +
                "            NULL AS endResourceHref,\n" +
                "            NULL AS endChapterIndex,\n" +
                "            NULL AS endParagraphIndex,\n" +
                "            NULL AS endCharOffset,\n" +
                "            NULL AS color,\n" +
                "            b.excerpt AS excerpt,\n" +
                "            b.note AS note,\n" +
                "            b.isPinned AS isPinned,\n" +
                "            b.createdAt AS createdAt\n" +
                "        FROM bookmarks b LEFT JOIN publications p ON p.id = b.publicationId\n" +
                "        UNION ALL\n" +
                "        SELECT\n" +
                "            'annotation' AS type,\n" +
                "            a.id AS id,\n" +
                "            a.publicationId AS publicationId,\n" +
                "            p.title AS publicationTitle,\n" +
                "            a.startResourceHref AS resourceHref,\n" +
                "            a.startChapterIndex AS chapterIndex,\n" +
                "            a.startParagraphIndex AS paragraphIndex,\n" +
                "            a.startCharOffset AS charOffset,\n" +
                "            a.endResourceHref AS endResourceHref,\n" +
                "            a.endChapterIndex AS endChapterIndex,\n" +
                "            a.endParagraphIndex AS endParagraphIndex,\n" +
                "            a.endCharOffset AS endCharOffset,\n" +
                "            a.color AS color,\n" +
                "            a.excerpt AS excerpt,\n" +
                "            a.content AS note,\n" +
                "            a.isPinned AS isPinned,\n" +
                "            a.createdAt AS createdAt\n" +
                "        FROM annotations a LEFT JOIN publications p ON p.id = a.publicationId",
        )
    }
}

/** Lot 5 : table des résultats d'import par session. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS import_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                session_id TEXT NOT NULL,
                file_name TEXT NOT NULL,
                result_type TEXT NOT NULL,
                message TEXT,
                existing_publication_id TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_import_results_session_id ON import_results (session_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_import_results_session_id_file_name ON import_results (session_id, file_name)")
    }
}

/** Lot 6 : thème système de l'application (SYSTEM/LIGHT/DARK). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN appTheme TEXT NOT NULL DEFAULT 'SYSTEM'")
    }
}

/** Lot 6 : disposition de la bibliothèque (LIST/GRID_COVERS), pilotée par le préréglage d'accessibilité. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN libraryLayoutMode TEXT NOT NULL DEFAULT 'GRID_COVERS'")
    }
}

/**
 * Lot Statistiques Palier 1 : séparation des métriques de durée
 * (visuelle / TTS) dans `reading_sessions`. L'ancien champ
 * `durationMs` est conservé pour compatibilité ascendante mais
 * n'est plus alimenté par le nouveau code — les projections SQL
 * calculent le total à la volée (`visualDurationMs + ttsDurationMs`).
 * Les sessions existantes migrent leur `durationMs` vers
 * `visualDurationMs` (valeur par défaut raisonnable pour des données
 * préexistantes qui ne distinguaient pas les deux modes).
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_sessions ADD COLUMN visualDurationMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reading_sessions ADD COLUMN ttsDurationMs INTEGER NOT NULL DEFAULT 0")
        // Peupler les nouvelles colonnes depuis l'ancien champ pour les
        // sessions préexistantes (valeur par défaut = mode visuel).
        db.execSQL("UPDATE reading_sessions SET visualDurationMs = durationMs WHERE mode != 'AUDIO' AND durationMs > 0")
        db.execSQL("UPDATE reading_sessions SET ttsDurationMs = durationMs WHERE mode = 'AUDIO' AND durationMs > 0")
    }
}

/**
 * Audit Lot Statistiques : index sur `startedAt` pour les agrégations
 * temporelles (daily stats, heatmap) + correction des sessions AUDIO
 * qui avaient été migrées vers `visualDurationMs` par erreur (16→17
 * ne tenait pas compte de la colonne `mode`).
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_sessions_startedAt ON reading_sessions (startedAt)")
        db.execSQL("UPDATE reading_sessions SET ttsDurationMs = visualDurationMs, visualDurationMs = 0 WHERE mode = 'AUDIO' AND visualDurationMs > 0 AND ttsDurationMs = 0")
    }
}

/**
 * Lot 9 : ouvre `ReadingTheme` (enum fermé LIGHT/DARK/SEPIA/SYSTEM → modèle
 * à thèmes personnalisés). Deux responsabilités dans la même migration,
 * jamais séparées :
 *
 * 1. Table `custom_themes` — les thèmes créés depuis le Studio.
 * 2. Réécriture des valeurs héritées de `user_preferences.theme` et
 *    `reading_states.overrideTheme` : les anciens noms d'enum
 *    (`LIGHT`/`DARK`/`SEPIA`/`SYSTEM`) ne correspondent plus à aucun id
 *    du nouveau catalogue. Sans cette réécriture, `UserPreferencesMapper`
 *    planterait au premier lancement post-mise à jour (K4 — aucune
 *    migration manquante silencieuse). `SYSTEM` n'a jamais été un choix
 *    réel de thème de lecture (AppTheme le porte séparément depuis le lot
 *    6) : replié sur Papier Clair comme les autres valeurs orphelines,
 *    par défensive plutôt que par correspondance sémantique.
 *    `overrideTheme` reste NULL quand il l'était déjà (aucune surcharge).
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_themes (
                id TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                backgroundColorHex TEXT NOT NULL,
                textColorHex TEXT NOT NULL,
                accentColorHex TEXT NOT NULL,
                highlightColorHex TEXT NOT NULL,
                fontFamily TEXT NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL("UPDATE user_preferences SET theme = 'papier_clair' WHERE theme = 'LIGHT'")
        db.execSQL("UPDATE user_preferences SET theme = 'obsidienne' WHERE theme = 'DARK'")
        db.execSQL("UPDATE user_preferences SET theme = 'sepia_vintage' WHERE theme = 'SEPIA'")
        db.execSQL("UPDATE user_preferences SET theme = 'papier_clair' WHERE theme = 'SYSTEM'")

        db.execSQL("UPDATE reading_states SET overrideTheme = 'papier_clair' WHERE overrideTheme = 'LIGHT'")
        db.execSQL("UPDATE reading_states SET overrideTheme = 'obsidienne' WHERE overrideTheme = 'DARK'")
        db.execSQL("UPDATE reading_states SET overrideTheme = 'sepia_vintage' WHERE overrideTheme = 'SEPIA'")
        db.execSQL("UPDATE reading_states SET overrideTheme = 'papier_clair' WHERE overrideTheme = 'SYSTEM'")
    }
}

/**
 * Lot 10 : indicateur "onboarding vu", pilote le `startDestination`
 * (Onboarding au premier lancement, Bibliothèque ensuite).
 *
 * `DEFAULT 1` ici est délibérément l'inverse du défaut Kotlin de
 * `UserPreferencesEntity.hasSeenOnboarding` (`false`) : cette migration
 * ne s'applique qu'à des lignes déjà existantes en base, c'est-à-dire des
 * utilisateurs qui utilisaient déjà l'app avant ce lot — leur faire
 * revoir l'onboarding après une simple mise à jour serait un défaut, pas
 * une fonctionnalité. Une installation neuve n'a AUCUNE ligne
 * `user_preferences` avant le premier `update()` (voir
 * `RoomPreferencesRepository.get()`) : elle ne passe jamais par cette
 * migration, et reçoit le défaut Kotlin `false` en mémoire tant qu'aucune
 * ligne n'est écrite — c'est ce qui déclenche l'onboarding au tout
 * premier lancement.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN hasSeenOnboarding INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * Lot 10 — retour Issa (vérification device) : proposition proactive de
 * la voix neuronale au premier usage réel du TTS (`ReaderViewModel
 * .playCurrentSentence`). `DEFAULT 0` ici (contrairement à
 * `hasSeenOnboarding`) : contrairement à l'onboarding, il n'y a aucune
 * raison de priver les utilisateurs déjà en base de cette proposition —
 * elle ne leur a jamais été faite non plus.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN hasPromptedVoiceDownload INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Lot 11, tâche 11.2 — identité d'appareil (utilisée par la flotte,
 * palier C, et la détection de conflits, palier D) et état du compte de
 * synchronisation unique (exclusivité mutuelle Drive/WebDAV). Toutes les
 * colonnes sont nullables ou à défaut neutre : une ligne existante se
 * retrouve simplement non configurée (`syncProvider IS NULL`), jamais en
 * échec.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN deviceId TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN deviceDisplayName TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncProvider TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncAccountLabel TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncLinkedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncLastSyncAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncLastAutoSyncFailed INTEGER NOT NULL DEFAULT 0")
    }
}

/** Lot 11, tâche 11.8 — synchro automatique en arrière-plan et sa contrainte Wi-Fi uniquement, toutes deux désactivées par défaut. */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncAutoEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN syncWifiOnly INTEGER NOT NULL DEFAULT 0")
    }
}

/** Lot 11, tâche 11.10 — file des conflits de position en attente d'arbitrage, au plus un par publication. */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_conflicts` (
                `publicationId` TEXT NOT NULL,
                `bookTitle` TEXT NOT NULL,
                `localResourceHref` TEXT NOT NULL,
                `localChapterIndex` INTEGER NOT NULL,
                `localParagraphIndex` INTEGER,
                `localCharOffset` INTEGER NOT NULL,
                `localDeviceLabel` TEXT NOT NULL,
                `localAt` INTEGER NOT NULL,
                `localChapterCount` INTEGER NOT NULL,
                `remoteResourceHref` TEXT NOT NULL,
                `remoteChapterIndex` INTEGER NOT NULL,
                `remoteParagraphIndex` INTEGER,
                `remoteCharOffset` INTEGER NOT NULL,
                `remoteDeviceLabel` TEXT NOT NULL,
                `remoteAt` INTEGER NOT NULL,
                `remoteChapterCount` INTEGER NOT NULL,
                PRIMARY KEY(`publicationId`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * Lot 12, tâche 12.5 — comptage de pages pour un format paginé (PDF).
 * Colonne nullable à défaut neutre : une ligne existante (EPUB/TXT) se
 * retrouve simplement à `pageCount IS NULL`, jamais en échec.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE publications ADD COLUMN pageCount INTEGER DEFAULT NULL")
    }
}

/**
 * Lot 13, tâche 13.1 — table des catalogues OPDS (Volet 1, ADR-023).
 * Aucune colonne d'identifiants ici : les credentials Basic Auth vivent
 * chiffrés dans `SecureOpdsCredentialsStore`, jamais en clair en base.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `opds_catalogs` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `rootUrl` TEXT NOT NULL,
                `searchTemplateUrl` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

/**
 * P4 (plan polissage Pareto) — réglages de confort de lecture visuelle : marges
 * latérales, espacement de paragraphe, justification, écran maintenu allumé.
 *
 * Toutes les colonnes ont un défaut neutre reproduisant le rendu d'avant la
 * migration (marge et espacement au cran médian, pas de justification, écran
 * non forcé) : une bibliothèque existante s'ouvre exactement comme avant, sans
 * que l'utilisateur constate un changement qu'il n'a pas demandé.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN readerMarginStep INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN paragraphSpacingStep INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN textJustified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN keepScreenOn INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Lot 21, tâche 9 — auto-scroll visuel en mode SCROLL (vitesse réglable).
 * Colonne additive avec défaut neutre `0` = désactivé : une bibliothèque
 * existante ne défile jamais d'elle-même tant que l'utilisateur n'a pas
 * réglé une vitesse — aucun changement de comportement après la mise à
 * jour (même discipline que MIGRATION_26_27).
 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE user_preferences ADD COLUMN autoScrollSpeed INTEGER NOT NULL DEFAULT 0")
    }
}
