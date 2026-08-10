package com.inktone.domain.service

/**
 * Effectue une synchronisation réelle (tâche 11.8) — implémentation dans
 * `data` (a besoin de la sérialisation de charge utile, `com.inktone
 * .data.backup.BackupPayload`, indisponible depuis `domain`). Portée de
 * ce palier : téléverse un instantané propre à l'appareil courant
 * (bookmarks, annotations, sessions, thèmes, etc. — même charge utile
 * que la sauvegarde locale, tâche 11.1), met à jour la flotte et le
 * journal. Aucune fusion entre appareils ni résolution de conflit —
 * palier D.
 *
 * Se protège elle-même contre un second déclenchement concurrent (via
 * [SyncOperationTracker]) : l'appelant n'a pas à dupliquer cette garde.
 */
interface SyncNowService {
    suspend fun synchronizeNow(): SyncOperationResult
}
