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

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
