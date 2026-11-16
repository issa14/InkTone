package com.inktone.infrastructure.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Supprime l'ancienne table d'index plein texte `sentence_fts` (tokeniseur par défaut, audit UX D8) sans
 * jamais la libérer d'un bloc : un `DROP TABLE` de 2 Go tient toutes les pages en mémoire et fait
 * planter SQLite. Les tables auxiliaires de l'index sont vidées par petits lots, chacun dans sa propre
 * transaction, puis la table, devenue vide, est supprimée instantanément.
 *
 * Idempotent et reprenable : interrompu, il reprend où il s'est arrêté au prochain démarrage ; sans
 * ancienne table, il ne fait rien.
 */
object LegacySearchIndexPurger {
    private const val TABLE = "sentence_fts"

    /** Tables auxiliaires de l'index FTS4 et leur clé de ligne, de la plus volumineuse à la plus petite. */
    private val SHADOW_TABLES = listOf(
        "${TABLE}_segments" to "blockid",
        "${TABLE}_docsize" to "docid",
        "${TABLE}_content" to "docid",
        "${TABLE}_segdir" to "rowid",
        "${TABLE}_stat" to "rowid",
    )

    private const val BATCH_ROWS = 300

    /** @return vrai si l'ancienne table a été supprimée (ou n'existait pas), faux si le travail reste à finir. */
    fun purge(db: SupportSQLiteDatabase, shouldContinue: () -> Boolean = { true }): Boolean {
        if (!tableExists(db, TABLE)) return true
        for ((table, key) in SHADOW_TABLES) {
            if (!tableExists(db, table)) continue
            while (shouldContinue()) {
                db.execSQL("DELETE FROM `$table` WHERE $key IN (SELECT $key FROM `$table` LIMIT $BATCH_ROWS)")
                if (!hasRows(db, table)) break
            }
            if (hasRows(db, table)) return false
        }
        db.execSQL("DROP TABLE IF EXISTS `$TABLE`")
        return true
    }

    private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean =
        db.query("SELECT 1 FROM sqlite_master WHERE name = ?", arrayOf(name)).use { it.moveToFirst() }

    private fun hasRows(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT 1 FROM `$table` LIMIT 1").use { it.moveToFirst() }
}
