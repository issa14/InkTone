package com.inktone.infrastructure.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Harnais de migration (Blueprint §14.5, acquis K4). `le_schema_v1_...`
 * couvre encore la version 1 (jamais retiré — une régression sur v1 doit
 * continuer à échouer même après l'ajout de v2). `MIGRATION_1_2` (Tâche
 * 7.3.1, ajout de `sentence_fts`) est la première migration réelle de ce
 * projet : gabarit à copier pour CHAQUE migration future (créer à N-1,
 * insérer des données représentatives, `runMigrationsAndValidate` vers N,
 * vérifier que les données survivent). Ne jamais ajouter une version de
 * schéma sans le test correspondant dans le même commit.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InkToneDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun le_schema_v1_exporte_se_cree_et_s_ouvre_sans_erreur() {
        // Valide que le schéma exporté (schemas/.../1.json, committé)
        // correspond exactement à InkToneDatabase — toute divergence fait
        // échouer ce test immédiatement.
        helper.createDatabase(TEST_DB_NAME, 1).apply { close() }
    }

    @Test
    fun migration_1_vers_2_conserve_les_donnees_et_ajoute_sentence_fts() {
        val v1 = helper.createDatabase(TEST_DB_NAME, 1)

        v1.execSQL(
            """
            INSERT INTO publications (
                id, title, subtitle, authors, publisher, language, description, coverUri,
                format, fileUri, fileHash, fileSize, chapterCount, seriesName, seriesIndex,
                isFavorite, subjects, isDrmProtected, importDate, lastOpened
            ) VALUES (
                'pub-1', 'Titre representatif', NULL, '', NULL, NULL, NULL, NULL,
                'EPUB', 'content://x/1', 'hash-1', 1000, 3, NULL, NULL,
                0, '', 0, 0, NULL
            )
            """.trimIndent(),
        )
        v1.execSQL(
            """
            INSERT INTO reading_states (
                publicationId, resourceHref, chapterIndex, paragraphIndex, charOffset,
                lastReadAt, voiceProfileId, overrideTheme, overrideFontSize
            ) VALUES ('pub-1', 'ch1.xhtml', 0, NULL, 42, 0, NULL, NULL, NULL)
            """.trimIndent(),
        )
        v1.close()

        val v2 = helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2)

        v2.query("SELECT title FROM publications WHERE id = 'pub-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Titre representatif", cursor.getString(0))
        }
        v2.query("SELECT charOffset FROM reading_states WHERE publicationId = 'pub-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(42, cursor.getInt(0))
        }

        // La table FTS existe et accepte une insertion — pas seulement
        // "la migration ne plante pas", la table doit etre reellement
        // utilisable derriere.
        v2.execSQL(
            "INSERT INTO sentence_fts (publicationId, chapterIndex, resourceHref, charOffset, text) " +
                "VALUES ('pub-1', 0, 'ch1.xhtml', 0, 'Phrase de test migree')",
        )
        v2.query("SELECT count(*) FROM sentence_fts").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        v2.close()
    }

    @Test
    fun migration_2_vers_3_conserve_les_donnees_et_ajoute_fontFamily_reduceMotion() {
        val v2 = helper.createDatabase(TEST_DB_NAME, 2)
        v2.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr')
            """.trimIndent(),
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, MIGRATION_2_3)

        v3.query("SELECT fontFamily, reduceMotion FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("DEFAULT", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }
        v3.close()
    }

    @Test
    fun migration_3_vers_4_cree_la_table_pronunciation_rules_utilisable() {
        val v3 = helper.createDatabase(TEST_DB_NAME, 3)
        v3.close()

        val v4 = helper.runMigrationsAndValidate(TEST_DB_NAME, 4, true, MIGRATION_2_3, MIGRATION_3_4)

        v4.execSQL(
            "INSERT INTO pronunciation_rules (id, originalText, replacementText, isRegex, isEnabled) " +
                "VALUES ('r1', 'Dr.', 'Docteur', 0, 1)",
        )
        v4.query("SELECT replacementText FROM pronunciation_rules WHERE id = 'r1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Docteur", cursor.getString(0))
        }
        v4.close()
    }

    @Test
    fun migration_4_vers_5_conserve_les_donnees_et_ajoute_dynamicColorEnabled_readingRulerEnabled() {
        val v4 = helper.createDatabase(TEST_DB_NAME, 4)
        v4.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0)
            """.trimIndent(),
        )
        v4.close()

        val v5 = helper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        v5.query("SELECT dynamicColorEnabled, readingRulerEnabled FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }
        v5.close()
    }

    @Test
    fun migration_5_vers_6_conserve_les_donnees_et_ajoute_dailyGoalMinutes() {
        val v5 = helper.createDatabase(TEST_DB_NAME, 5)
        v5.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0)
            """.trimIndent(),
        )
        v5.close()

        val v6 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 6, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        )

        v6.query("SELECT dailyGoalMinutes FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(20, cursor.getInt(0)) // valeur par defaut
        }
        v6.close()
    }

    @Test
    fun migration_6_vers_7_conserve_les_donnees_et_ajoute_activeVoiceProfileId() {
        val v6 = helper.createDatabase(TEST_DB_NAME, 6)
        v6.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20)
            """.trimIndent(),
        )
        v6.close()

        val v7 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 7, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        )

        v7.query("SELECT activeVoiceProfileId FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(null, cursor.getString(0)) // null par defaut
        }
        v7.close()
    }

    @Test
    fun migration_7_vers_8_ajoute_wordsRead_aux_reading_sessions() {
        val v7 = helper.createDatabase(TEST_DB_NAME, 7)
        v7.execSQL(
            """
            INSERT INTO publications (id, title, authors, format, fileUri, fileHash, fileSize, chapterCount, subjects, isFavorite, isDrmProtected, importDate)
            VALUES ('pub-d4', 'Test', '', 'EPUB', '/test.epub', 'hash-d4', 1024, 1, '', 0, 0, 0)
            """.trimIndent(),
        )
        v7.execSQL(
            """
            INSERT INTO reading_sessions (id, publicationId, startedAt, mode, sentencesRead, durationMs)
            VALUES ('s1', 'pub-d4', 0, 'SCROLL', 10, 60000)
            """.trimIndent(),
        )
        v7.close()

        val v8 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 8, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        )

        v8.query("SELECT wordsRead FROM reading_sessions WHERE id = 's1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0)) // valeur par defaut
        }
        v8.close()
    }

    @Test
    fun migration_8_vers_9_ajoute_readingMode_aux_user_preferences() {
        val v8 = helper.createDatabase(TEST_DB_NAME, 8)
        v8.execSQL(
            """
            INSERT INTO publications (id, title, authors, format, fileUri, fileHash, fileSize, chapterCount, subjects, isFavorite, isDrmProtected, importDate)
            VALUES ('pub-b1', 'Test B1', '', 'EPUB', '/test-b1.epub', 'hash-b1', 1024, 1, '', 0, 0, 0)
            """.trimIndent(),
        )
        v8.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL)
            """.trimIndent(),
        )
        v8.close()

        val v9 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 9, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        )

        v9.query("SELECT readingMode FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("SCROLL", cursor.getString(0)) // valeur par defaut
        }
        v9.close()
    }
    @Test
    fun migration_9_vers_10_ajoute_audioGain_et_useSystemFontScale() {
        val v9 = helper.createDatabase(TEST_DB_NAME, 9)
        v9.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL')
            """.trimIndent(),
        )
        v9.close()

        val v10 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 10, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
        )
        v10.query("SELECT audioGain, useSystemFontScale FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1.0f, cursor.getFloat(0))
            assertEquals(0, cursor.getInt(1))
        }
        v10.close()
    }
    @Test
    fun migration_10_vers_11_conserve_les_donnees_et_ajoute_isPinned() {
        val v10 = helper.createDatabase(TEST_DB_NAME, 10)
        v10.execSQL(
            """
            INSERT INTO publications (
                id, title, subtitle, authors, publisher, language, description, coverUri,
                format, fileUri, fileHash, fileSize, chapterCount, seriesName, seriesIndex,
                isFavorite, subjects, isDrmProtected, importDate, lastOpened
            ) VALUES (
                'pub-2b1', 'Titre epinglable', NULL, '', NULL, NULL, NULL, NULL,
                'EPUB', 'content://x/2b1', 'hash-2b1', 1000, 3, NULL, NULL,
                0, '', 0, 0, NULL
            )
            """.trimIndent(),
        )
        v10.close()

        val v11 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 11, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        )

        v11.query("SELECT title, isPinned FROM publications WHERE id = 'pub-2b1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Titre epinglable", cursor.getString(0))
            assertEquals(0, cursor.getInt(1)) // valeur par defaut : pas epingle
        }
        v11.execSQL("UPDATE publications SET isPinned = 1 WHERE id = 'pub-2b1'")
        v11.query("SELECT isPinned FROM publications WHERE id = 'pub-2b1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        v11.close()
    }

    @Test
    fun migration_11_vers_12_conserve_les_donnees_et_ajoute_interligne_luminosite_repos_oculaire() {
        val v11 = helper.createDatabase(TEST_DB_NAME, 11)
        v11.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0)
            """.trimIndent(),
        )
        v11.close()

        val v12 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 12, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        )

        v12.query(
            "SELECT lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes FROM user_preferences WHERE id = 0",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1.4f, cursor.getFloat(0))
            assertEquals(true, cursor.isNull(1)) // luminosité : valeur système par défaut
            assertEquals(1, cursor.getInt(2))
            assertEquals(60, cursor.getInt(3))
        }
        v12.execSQL("UPDATE user_preferences SET readerBrightness = 0.5 WHERE id = 0")
        v12.query("SELECT readerBrightness FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0.5f, cursor.getFloat(0))
        }
        v12.close()
    }

    @Test
    fun migration_12_vers_13_conserve_les_donnees_ajoute_extrait_epinglage_et_la_vue_library_items() {
        val v12 = helper.createDatabase(TEST_DB_NAME, 12)
        v12.execSQL(
            """
            INSERT INTO publications (
                id, title, subtitle, authors, publisher, language, description, coverUri,
                format, fileUri, fileHash, fileSize, chapterCount, seriesName, seriesIndex,
                isFavorite, isPinned, subjects, isDrmProtected, importDate, lastOpened
            ) VALUES (
                'pub-l4', 'Titre avant lot 4', NULL, '', NULL, NULL, NULL, NULL,
                'EPUB', 'content://x/l4', 'hash-l4', 1000, 3, NULL, NULL,
                0, 0, '', 0, 0, NULL
            )
            """.trimIndent(),
        )
        v12.execSQL(
            """
            INSERT INTO bookmarks (id, publicationId, resourceHref, chapterIndex, paragraphIndex, charOffset, title, note, createdAt)
            VALUES ('bm-l4', 'pub-l4', 'ch1.xhtml', 0, NULL, 10, NULL, NULL, 100)
            """.trimIndent(),
        )
        v12.execSQL(
            """
            INSERT INTO annotations (
                id, publicationId, startResourceHref, startChapterIndex, startParagraphIndex, startCharOffset,
                endResourceHref, endChapterIndex, endParagraphIndex, endCharOffset, color, content, createdAt, updatedAt
            ) VALUES (
                'an-l4', 'pub-l4', 'ch1.xhtml', 0, NULL, 20, 'ch1.xhtml', 0, NULL, 40, 'YELLOW', NULL, 200, 200
            )
            """.trimIndent(),
        )
        v12.close()

        val v13 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 13, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
        )

        // Les enregistrements pré-existants (avant ce lot) n'ont pas d'extrait,
        // et ne sont pas épinglés par défaut — tâche 4.2/4.3.
        v13.query("SELECT excerpt, isPinned FROM bookmarks WHERE id = 'bm-l4'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
            assertEquals(0, cursor.getInt(1))
        }
        v13.query("SELECT excerpt, isPinned FROM annotations WHERE id = 'an-l4'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
            assertEquals(0, cursor.getInt(1))
        }

        // La vue library_items existe, est utilisable, et résout le titre par jointure.
        v13.query("SELECT type, publicationTitle, note FROM library_items ORDER BY type").use { cursor ->
            assertEquals(2, cursor.count)
            assertEquals(true, cursor.moveToFirst())
            assertEquals("annotation", cursor.getString(0))
            assertEquals("Titre avant lot 4", cursor.getString(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals("bookmark", cursor.getString(0))
            assertEquals("Titre avant lot 4", cursor.getString(1))
        }

        v13.execSQL("UPDATE bookmarks SET isPinned = 1 WHERE id = 'bm-l4'")
        v13.query("SELECT isPinned FROM library_items WHERE id = 'bm-l4'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        v13.close()
    }

    @Test
    fun migration_12_vers_13_le_renommage_dune_publication_se_reflete_dans_library_items_sans_redemarrage() {
        val v12 = helper.createDatabase(TEST_DB_NAME, 12)
        v12.execSQL(
            """
            INSERT INTO publications (
                id, title, subtitle, authors, publisher, language, description, coverUri,
                format, fileUri, fileHash, fileSize, chapterCount, seriesName, seriesIndex,
                isFavorite, isPinned, subjects, isDrmProtected, importDate, lastOpened
            ) VALUES (
                'pub-rename', 'Ancien titre', NULL, '', NULL, NULL, NULL, NULL,
                'EPUB', 'content://x/rename', 'hash-rename', 1000, 3, NULL, NULL,
                0, 0, '', 0, 0, NULL
            )
            """.trimIndent(),
        )
        v12.execSQL(
            """
            INSERT INTO bookmarks (id, publicationId, resourceHref, chapterIndex, paragraphIndex, charOffset, title, note, createdAt)
            VALUES ('bm-rename', 'pub-rename', 'ch1.xhtml', 0, NULL, 10, NULL, NULL, 100)
            """.trimIndent(),
        )
        v12.close()

        val v13 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 13, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
        )

        v13.execSQL("UPDATE publications SET title = 'Nouveau titre' WHERE id = 'pub-rename'")
        v13.query("SELECT publicationTitle FROM library_items WHERE id = 'bm-rename'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Nouveau titre", cursor.getString(0))
        }
        v13.close()
    }

    @Test
    fun migration_13_vers_14_cree_la_table_import_results_utilisable() {
        val v13 = helper.createDatabase(TEST_DB_NAME, 13)
        v13.close()

        val v14 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 14, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14,
        )

        v14.execSQL(
            "INSERT INTO import_results (session_id, file_name, result_type, message, existing_publication_id) " +
                "VALUES ('s1', 'test.epub', 'success', NULL, NULL)",
        )
        v14.execSQL(
            "INSERT INTO import_results (session_id, file_name, result_type, message, existing_publication_id) " +
                "VALUES ('s1', 'corrompu.epub', 'corrupted', 'Fichier illisible', NULL)",
        )
        v14.execSQL(
            "INSERT INTO import_results (session_id, file_name, result_type, message, existing_publication_id) " +
                "VALUES ('s2', 'autre.epub', 'duplicate', NULL, 'pub-123')",
        )

        // Tri : échecs d'abord, puis doublons, puis succès
        v14.query(
            "SELECT file_name, result_type FROM import_results WHERE session_id = 's1' " +
                "ORDER BY CASE WHEN result_type IN ('corrupted','drm_protected','unsupported_format') THEN 0 WHEN result_type='duplicate' THEN 1 ELSE 2 END, file_name ASC",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            assertEquals(true, cursor.moveToFirst())
            assertEquals("corrompu.epub", cursor.getString(0))
            assertEquals("corrupted", cursor.getString(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals("test.epub", cursor.getString(0))
        }

        v14.query("SELECT existing_publication_id FROM import_results WHERE session_id = 's2'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("pub-123", cursor.getString(0))
        }

        // DELETE ALL purge toutes les sessions
        v14.execSQL("DELETE FROM import_results")
        v14.query("SELECT count(*) FROM import_results").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        v14.close()
    }

    @Test
    fun migration_14_vers_15_conserve_les_donnees_et_ajoute_appTheme() {
        val v14 = helper.createDatabase(TEST_DB_NAME, 14)
        v14.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 1.4, NULL, 1, 60)
            """.trimIndent(),
        )
        v14.close()

        val v15 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 15, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15,
        )

        v15.query("SELECT appTheme FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("SYSTEM", cursor.getString(0)) // valeur par defaut
        }
        v15.execSQL("UPDATE user_preferences SET appTheme = 'DARK' WHERE id = 0")
        v15.query("SELECT appTheme FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("DARK", cursor.getString(0))
        }
        v15.close()
    }

    @Test
    fun migration_15_vers_16_conserve_les_donnees_et_ajoute_libraryLayoutMode() {
        val v15 = helper.createDatabase(TEST_DB_NAME, 15)
        v15.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 1.4, NULL, 1, 60)
            """.trimIndent(),
        )
        v15.close()

        val v16 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 16, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
        )

        v16.query("SELECT libraryLayoutMode FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("GRID_COVERS", cursor.getString(0)) // valeur par defaut
        }
        v16.execSQL("UPDATE user_preferences SET libraryLayoutMode = 'LIST' WHERE id = 0")
        v16.query("SELECT libraryLayoutMode FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("LIST", cursor.getString(0))
        }
        v16.close()
    }

    @Test
    fun migration_16_vers_17_ajoute_visualDurationMs_et_ttsDurationMs_et_migre_les_donnees() {
        val v16 = helper.createDatabase(TEST_DB_NAME, 16)
        v16.execSQL(
            """
            INSERT INTO publications (id, title, authors, format, fileUri, fileHash, fileSize, chapterCount, subjects, isFavorite, isPinned, isDrmProtected, importDate)
            VALUES ('pub-stats', 'Stats Book', '', 'EPUB', '/stats.epub', 'hash-stats', 1024, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
        // Session sans mots lus (pre-D.4, durationMs seul)
        v16.execSQL(
            """
            INSERT INTO reading_sessions (id, publicationId, startedAt, endedAt, mode, sentencesRead, durationMs, wordsRead)
            VALUES ('s1', 'pub-stats', 1000000, 1005000, 'VISUAL', 0, 5000, 0)
            """.trimIndent(),
        )
        // Session avec mots lus (post-D.4)
        v16.execSQL(
            """
            INSERT INTO reading_sessions (id, publicationId, startedAt, endedAt, mode, sentencesRead, durationMs, wordsRead)
            VALUES ('s2', 'pub-stats', 2000000, 2003000, 'AUDIO', 10, 3000, 100)
            """.trimIndent(),
        )
        v16.close()

        val v17 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 17, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
        )

        // Les nouvelles colonnes existent et sont peuplées depuis durationMs
        v17.query(
            "SELECT id, durationMs, visualDurationMs, ttsDurationMs FROM reading_sessions ORDER BY id",
        ).use { cursor ->
            assertEquals(2, cursor.count)

            // s1 : durationMs = 5000, migré vers visualDurationMs
            assertEquals(true, cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
            assertEquals(5000, cursor.getInt(1))
            assertEquals(5000, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))

            // s2 : durationMs = 3000, migré vers ttsDurationMs (mode AUDIO)
            // — Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, R4) : ce test
            // attendait l'ANCIEN comportement (AUDIO → visualDurationMs),
            // contredit par la migration corrigée (AUDIO → ttsDurationMs,
            // complétée par MIGRATION_17_18). Mis en cohérence avec le
            // code : c'est ce que MIGRATION_16_17 exécute réellement.
            assertEquals(true, cursor.moveToNext())
            assertEquals("s2", cursor.getString(0))
            assertEquals(3000, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(3000, cursor.getInt(3))
        }

        // La table est encore utilisable : insertion avec les nouveaux champs
        v17.execSQL(
            "INSERT INTO reading_sessions (id, publicationId, startedAt, endedAt, mode, sentencesRead, durationMs, wordsRead, visualDurationMs, ttsDurationMs) " +
                "VALUES ('s3', 'pub-stats', 3000000, 3004000, 'AUDIO', 0, 4000, 0, 0, 4000)",
        )
        v17.query("SELECT visualDurationMs, ttsDurationMs FROM reading_sessions WHERE id = 's3'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(4000, cursor.getInt(1))
        }
        v17.close()
    }

    /**
     * Audit v1.0.0 (AUDIT_CONSOLIDATION_V1.md, R4) : cette migration
     * n'avait PAS de test dédié — seule transition de 16→18 non couverte
     * par un MigrationTestHelper (violation K4, Blueprint §14.5). Elle
     * corrige les sessions AUDIO que 16→17 avait migrées vers
     * `visualDurationMs` par erreur (le UPDATE de 16→17 ignorait la
     * colonne `mode`) et ajoute l'index d'agrégation temporelle.
     */
    @Test
    fun migration_17_vers_18_cree_l_index_startedAt_et_corrige_les_sessions_audio() {
        val v17 = helper.createDatabase(TEST_DB_NAME, 17)
        v17.execSQL(
            """
            INSERT INTO publications (id, title, authors, format, fileUri, fileHash, fileSize, chapterCount, subjects, isFavorite, isPinned, isDrmProtected, importDate)
            VALUES ('pub-stats', 'Stats Book', '', 'EPUB', '/stats.epub', 'hash-stats', 1024, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
        // Session AUDIO migrée par erreur vers visualDurationMs par 16→17
        // (le cas que 17→18 doit corriger) : visualDurationMs peuplé,
        // ttsDurationMs à 0, mode AUDIO.
        v17.execSQL(
            """
            INSERT INTO reading_sessions (id, publicationId, startedAt, endedAt, mode, sentencesRead, durationMs, wordsRead, visualDurationMs, ttsDurationMs)
            VALUES ('s-audio-errone', 'pub-stats', 2000000, 2003000, 'AUDIO', 10, 3000, 100, 3000, 0)
            """.trimIndent(),
        )
        // Session VISUAL normale : doit rester inchangée.
        v17.execSQL(
            """
            INSERT INTO reading_sessions (id, publicationId, startedAt, endedAt, mode, sentencesRead, durationMs, wordsRead, visualDurationMs, ttsDurationMs)
            VALUES ('s-visual', 'pub-stats', 1000000, 1005000, 'VISUAL', 0, 5000, 0, 5000, 0)
            """.trimIndent(),
        )
        // Session AUDIO déjà correcte (ttsDurationMs peuplé) : ne doit
        // PAS être touchée (garde WHERE ttsDurationMs = 0).
        v17.execSQL(
            """
            INSERT INTO reading_sessions (id, publicationId, startedAt, endedAt, mode, sentencesRead, durationMs, wordsRead, visualDurationMs, ttsDurationMs)
            VALUES ('s-audio-ok', 'pub-stats', 3000000, 3004000, 'AUDIO', 5, 2000, 50, 0, 2000)
            """.trimIndent(),
        )
        v17.close()

        val v18 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 18, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
        )

        // L'index d'agrégation temporelle existe réellement.
        v18.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_reading_sessions_startedAt'",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("index_reading_sessions_startedAt", cursor.getString(0))
        }

        // s-audio-errone : corrigée — les durées basculent vers ttsDurationMs.
        v18.query(
            "SELECT id, visualDurationMs, ttsDurationMs FROM reading_sessions ORDER BY id",
        ).use { cursor ->
            assertEquals(3, cursor.count)

            assertEquals(true, cursor.moveToFirst())
            assertEquals("s-audio-errone", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(3000, cursor.getInt(2))

            assertEquals(true, cursor.moveToNext())
            assertEquals("s-audio-ok", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(2000, cursor.getInt(2))

            // s-visual : intacte.
            assertEquals(true, cursor.moveToNext())
            assertEquals("s-visual", cursor.getString(0))
            assertEquals(5000, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        v18.close()
    }

    /**
     * Lot 9 — le point le plus a risque de la migration : une base reelle
     * peut porter n'importe laquelle des quatre valeurs historiques de
     * l'ancien enum (LIGHT/DARK/SEPIA/SYSTEM) sur `user_preferences.theme`
     * ET sur `reading_states.overrideTheme` (qui peut aussi etre NULL, cas
     * "pas de surcharge" a preserver tel quel). Sans cette migration,
     * UserPreferencesMapper/ReadingStateMapper planteraient au premier
     * lancement post-mise a jour (K4) : ce test le prouve pour les quatre
     * valeurs, pas seulement une.
     */
    @Test
    fun migration_18_vers_19_reecrit_les_valeurs_heritees_de_theme_et_cree_custom_themes() {
        val v18 = helper.createDatabase(TEST_DB_NAME, 18)
        v18.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes)
            VALUES (0, 'DARK', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.4, NULL, 1, 60)
            """.trimIndent(),
        )
        v18.execSQL(
            """
            INSERT INTO publications (id, title, authors, format, fileUri, fileHash, fileSize, chapterCount, subjects, isFavorite, isPinned, isDrmProtected, importDate)
            VALUES ('pub-1', 'Livre 1', '', 'EPUB', '/1.epub', 'hash-1', 1024, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
        v18.execSQL(
            """
            INSERT INTO publications (id, title, authors, format, fileUri, fileHash, fileSize, chapterCount, subjects, isFavorite, isPinned, isDrmProtected, importDate)
            VALUES ('pub-2', 'Livre 2', '', 'EPUB', '/2.epub', 'hash-2', 1024, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
        )
        // Les quatre valeurs historiques possibles + le cas NULL (aucune surcharge, doit le rester).
        v18.execSQL(
            "INSERT INTO reading_states (publicationId, resourceHref, chapterIndex, paragraphIndex, charOffset, lastReadAt, voiceProfileId, overrideTheme, overrideFontSize) " +
                "VALUES ('pub-1', 'ch1.xhtml', 0, NULL, 0, 0, NULL, 'SEPIA', NULL)",
        )
        v18.execSQL(
            "INSERT INTO reading_states (publicationId, resourceHref, chapterIndex, paragraphIndex, charOffset, lastReadAt, voiceProfileId, overrideTheme, overrideFontSize) " +
                "VALUES ('pub-2', 'ch1.xhtml', 0, NULL, 0, 0, NULL, NULL, 20)",
        )
        v18.close()

        val v19 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 19, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
        )

        // DARK -> obsidienne, sans perte du reste de la ligne.
        v19.query("SELECT theme, fontSize FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("obsidienne", cursor.getString(0))
            assertEquals(18, cursor.getInt(1))
        }

        // SEPIA -> sepia_vintage sur la surcharge par publication.
        v19.query("SELECT overrideTheme FROM reading_states WHERE publicationId = 'pub-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("sepia_vintage", cursor.getString(0))
        }

        // NULL reste NULL : "pas de surcharge" n'est jamais transformé en une valeur réelle.
        v19.query("SELECT overrideTheme, overrideFontSize FROM reading_states WHERE publicationId = 'pub-2'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(null, cursor.getString(0))
            assertEquals(20, cursor.getInt(1))
        }

        // La table custom_themes existe et est utilisable.
        v19.execSQL(
            "INSERT INTO custom_themes (id, displayName, backgroundColorHex, textColorHex, accentColorHex, highlightColorHex, fontFamily) " +
                "VALUES ('mon-theme', 'Mon thème', '#112233', '#FFFFFF', '#AABBCC', '#DDEEFF', 'DEFAULT')",
        )
        v19.query("SELECT displayName FROM custom_themes WHERE id = 'mon-theme'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Mon thème", cursor.getString(0))
        }
        v19.close()
    }

    @Test
    fun migration_18_vers_19_reecrit_LIGHT_et_SYSTEM_vers_papier_clair() {
        val v18 = helper.createDatabase(TEST_DB_NAME, 18)
        v18.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes)
            VALUES (0, 'SYSTEM', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.4, NULL, 1, 60)
            """.trimIndent(),
        )
        v18.close()

        val v19 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 19, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
        )

        // SYSTEM n'a jamais ete un vrai choix de theme de lecture (AppTheme
        // le porte separement depuis le lot 6) : replie sur Papier Clair,
        // comme LIGHT.
        v19.query("SELECT theme FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("papier_clair", cursor.getString(0))
        }
        v19.close()
    }

    /**
     * Lot 10 — les utilisateurs déjà en base avant ce lot ne doivent
     * jamais revoir l'onboarding après une simple mise à jour
     * (`DEFAULT 1` côté SQL, inverse du défaut Kotlin `false`).
     */
    @Test
    fun migration_19_vers_20_replie_les_lignes_existantes_sur_onboarding_deja_vu() {
        val v19 = helper.createDatabase(TEST_DB_NAME, 19)
        v19.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes)
            VALUES (0, 'papier_clair', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.4, NULL, 1, 60)
            """.trimIndent(),
        )
        v19.close()

        val v20 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 20, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20,
        )

        v20.query("SELECT hasSeenOnboarding, theme FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0)) // deja vu, pas un nouvel utilisateur
            assertEquals("papier_clair", cursor.getString(1)) // aucune perte de donnees
        }
        v20.close()
    }

    @Test
    fun migration_20_vers_21_ajoute_hasPromptedVoiceDownload() {
        val v20 = helper.createDatabase(TEST_DB_NAME, 20)
        v20.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes, hasSeenOnboarding)
            VALUES (0, 'papier_clair', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.4, NULL, 1, 60, 1)
            """.trimIndent(),
        )
        v20.close()

        val v21 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 21, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21,
        )

        v21.query("SELECT hasPromptedVoiceDownload FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        v21.close()
    }

    @Test
    fun migration_21_vers_22_conserve_les_donnees_et_ajoute_l_identite_d_appareil_et_l_etat_de_synchronisation() {
        val v21 = helper.createDatabase(TEST_DB_NAME, 21)
        v21.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes, hasSeenOnboarding, hasPromptedVoiceDownload)
            VALUES (0, 'papier_clair', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.4, NULL, 1, 60, 1, 1)
            """.trimIndent(),
        )
        v21.close()

        val v22 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 22, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
        )

        v22.query(
            "SELECT theme, deviceId, syncProvider, syncLastAutoSyncFailed FROM user_preferences WHERE id = 0",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("papier_clair", cursor.getString(0)) // aucune perte de donnees
            assertEquals(true, cursor.isNull(1)) // deviceId : pas encore genere
            assertEquals(true, cursor.isNull(2)) // syncProvider NULL == Unconfigured
            assertEquals(0, cursor.getInt(3)) // pas d echec par defaut
        }
        v22.close()
    }

    @Test
    fun migration_22_vers_23_conserve_les_donnees_et_ajoute_syncAutoEnabled_syncWifiOnly() {
        val v22 = helper.createDatabase(TEST_DB_NAME, 22)
        v22.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes, hasSeenOnboarding, hasPromptedVoiceDownload, syncLastAutoSyncFailed)
            VALUES (0, 'papier_clair', 18, 'SHERPA_ONNX', 0, 'fr', 'DEFAULT', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.4, NULL, 1, 60, 1, 1, 0)
            """.trimIndent(),
        )
        v22.close()

        val v23 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 23, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
        )

        v23.query("SELECT theme, syncAutoEnabled, syncWifiOnly FROM user_preferences WHERE id = 0").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("papier_clair", cursor.getString(0)) // aucune perte de donnees
            assertEquals(0, cursor.getInt(1)) // desactive par defaut
            assertEquals(0, cursor.getInt(2)) // desactive par defaut
        }
        v23.close()
    }

    @Test
    fun migration_23_vers_24_cree_la_table_pending_conflicts_utilisable() {
        helper.createDatabase(TEST_DB_NAME, 23).close()

        val v24 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 24, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
        )

        v24.execSQL(
            """
            INSERT INTO pending_conflicts (publicationId, bookTitle, localResourceHref, localChapterIndex, localParagraphIndex, localCharOffset, localDeviceLabel, localAt, localChapterCount, remoteResourceHref, remoteChapterIndex, remoteParagraphIndex, remoteCharOffset, remoteDeviceLabel, remoteAt, remoteChapterCount)
            VALUES ('pub-1', 'Le Grand Livre', 'ch1.xhtml', 1, NULL, 0, 'Cet appareil', 50, 20, 'ch8.xhtml', 8, NULL, 0, 'Tablette B', 200, 20)
            """.trimIndent(),
        )
        v24.query("SELECT bookTitle, localChapterIndex, remoteChapterIndex FROM pending_conflicts WHERE publicationId = 'pub-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Le Grand Livre", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(8, cursor.getInt(2))
        }
        v24.close()
    }

    @Test
    fun migration_24_vers_25_conserve_les_donnees_et_ajoute_pageCount() {
        val v24 = helper.createDatabase(TEST_DB_NAME, 24)
        v24.execSQL(
            """
            INSERT INTO publications (id, title, subtitle, authors, publisher, language, description, coverUri, format, fileUri, fileHash, fileSize, chapterCount, seriesName, seriesIndex, isFavorite, isPinned, subjects, isDrmProtected, importDate, lastOpened)
            VALUES ('pub-1', 'Les Miserables', NULL, '', NULL, 'fr', NULL, NULL, 'EPUB', 'content://pub-1', 'hash1', 1000, 20, NULL, NULL, 0, 0, '', 0, 500, NULL)
            """.trimIndent(),
        )
        v24.close()

        val v25 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 25, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
        )

        v25.query("SELECT title, chapterCount, pageCount FROM publications WHERE id = 'pub-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Les Miserables", cursor.getString(0)) // aucune perte de donnees
            assertEquals(20, cursor.getInt(1))
            assertEquals(true, cursor.isNull(2)) // pageCount absent avant, NULL apres
        }
        v25.close()
    }

    @Test
    fun migration_25_vers_26_cree_la_table_opds_catalogs_utilisable() {
        val v25 = helper.createDatabase(TEST_DB_NAME, 25)
        v25.close()

        val v26 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 26, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
            MIGRATION_25_26,
        )

        v26.execSQL(
            """
            INSERT INTO opds_catalogs (id, name, rootUrl, searchTemplateUrl, createdAt)
            VALUES ('cat-1', 'Gutenberg', 'https://www.gutenberg.org/ebooks.opds', NULL, 100)
            """.trimIndent(),
        )
        v26.query("SELECT name, rootUrl, searchTemplateUrl FROM opds_catalogs WHERE id = 'cat-1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Gutenberg", cursor.getString(0))
            assertEquals("https://www.gutenberg.org/ebooks.opds", cursor.getString(1))
            assertEquals(true, cursor.isNull(2)) // searchTemplateUrl absent par défaut
        }
        v26.close()
    }

    @Test
    fun migration_26_vers_27_ajoute_les_reglages_de_confort_sans_toucher_aux_preferences_existantes() {
        val v26 = helper.createDatabase(TEST_DB_NAME, 26)
        // Préférences déjà personnalisées par l'utilisateur : la migration ne
        // doit en modifier aucune, et les nouveaux réglages doivent arriver
        // sur un défaut qui reproduit le rendu d'avant.
        v26.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes, hasSeenOnboarding, hasPromptedVoiceDownload, syncLastAutoSyncFailed, syncAutoEnabled, syncWifiOnly)
            VALUES (0, 'obsidienne', 22, 'SHERPA_ONNX', 0, 'fr', 'SERIF', 0, 1, 0, 20, NULL, 'PAGED', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.6, NULL, 1, 60, 1, 1, 0, 0, 0)
            """.trimIndent(),
        )
        v26.close()

        val v27 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 27, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
            MIGRATION_25_26, MIGRATION_26_27,
        )

        v27.query(
            "SELECT theme, fontSize, lineHeightMultiplier, readerMarginStep, paragraphSpacingStep, textJustified, keepScreenOn FROM user_preferences WHERE id = 0",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            // Aucune perte : les réglages existants traversent la migration.
            assertEquals("obsidienne", cursor.getString(0))
            assertEquals(22, cursor.getInt(1))
            assertEquals(1.6f, cursor.getFloat(2), 0.001f)
            // Défauts neutres : le rendu reste identique tant que l'utilisateur
            // n'a rien changé.
            assertEquals(1, cursor.getInt(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals(0, cursor.getInt(5))
            assertEquals(0, cursor.getInt(6))
        }
        v27.close()
    }

    // Lot 21, tâche 9 — auto-scroll visuel (MIGRATION_27_28), même gabarit
    // que migration_26_vers_27 : les préférences existantes survivent, la
    // nouvelle colonne arrive sur un défaut neutre (0 = désactivé).
    @Test
    fun migration_27_vers_28_ajoute_l_auto_scroll_desactive_par_defaut() {
        val v27 = helper.createDatabase(TEST_DB_NAME, 27)
        v27.execSQL(
            """
            INSERT INTO user_preferences (id, theme, fontSize, defaultTtsEngine, crashReportingEnabled, language, fontFamily, reduceMotion, dynamicColorEnabled, readingRulerEnabled, dailyGoalMinutes, activeVoiceProfileId, readingMode, audioGain, useSystemFontScale, appTheme, libraryLayoutMode, lineHeightMultiplier, readerBrightness, eyeRestReminderEnabled, eyeRestReminderIntervalMinutes, hasSeenOnboarding, hasPromptedVoiceDownload, syncLastAutoSyncFailed, syncAutoEnabled, syncWifiOnly, readerMarginStep, paragraphSpacingStep, textJustified, keepScreenOn)
            VALUES (0, 'obsidienne', 22, 'SHERPA_ONNX', 0, 'fr', 'SERIF', 0, 1, 0, 20, NULL, 'SCROLL', 1.0, 0, 'SYSTEM', 'GRID_COVERS', 1.6, NULL, 1, 60, 1, 1, 0, 0, 0, 1, 1, 0, 0)
            """.trimIndent(),
        )
        v27.close()

        val v28 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 28, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
            MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
        )

        v28.query(
            "SELECT theme, fontSize, lineHeightMultiplier, autoScrollSpeed FROM user_preferences WHERE id = 0",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            // Aucune perte : les réglages existants traversent la migration.
            assertEquals("obsidienne", cursor.getString(0))
            assertEquals(22, cursor.getInt(1))
            assertEquals(1.6f, cursor.getFloat(2), 0.001f)
            // Auto-scroll désactivé par défaut : aucun changement de comportement.
            assertEquals(0, cursor.getInt(3))
        }
        v28.close()
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
