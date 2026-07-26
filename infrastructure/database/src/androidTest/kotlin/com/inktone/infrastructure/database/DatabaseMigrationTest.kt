package com.inktone.infrastructure.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Harnais de migration (Blueprint §14.5, acquis K4). Ne valide aucune
 * migration réelle pour l'instant — le schéma est en version 1, il n'y a
 * rien à migrer. Sert de GABARIT : copier cette structure pour CHAQUE
 * migration future (ajouter `helper.runMigrationsAndValidate(dbName, N,
 * true, MIGRATION_N_MINUS_1_TO_N)` après avoir créé la base à N-1 et
 * inséré des données représentatives). Ne jamais ajouter une version de
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

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
