package com.inktone.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.data.backup.BackupManager
import com.inktone.data.backup.ImportBackupResult
import com.inktone.feature.settings.DataOperationResult
import com.inktone.feature.settings.ModelsFolderInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Lot 6, Palier B — pilote l'export/import de sauvegarde depuis la carte
 * Données des Réglages. `BackupManager` vit dans `:data`, invisible depuis
 * `feature/settings` (Blueprint §12.4 : feature ne dépend que de
 * domain/core) — ce ViewModel vit donc dans `app`, seul module autorisé à
 * dépendre à la fois de `:data` (pour `BackupManager`) et de
 * `feature/settings` (pour les types `DataOperationResult`/`ModelsFolderInfo`
 * qu'il traduit). `BuildConfig.VERSION_NAME` est lu ici pour la même
 * raison que dans `AboutRoute` (`InkToneNavHost.kt`) : seul `app` connaît
 * la version réelle de l'application.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _lastResult = MutableStateFlow<DataOperationResult?>(null)
    val lastResult: StateFlow<DataOperationResult?> = _lastResult.asStateFlow()

    /**
     * Dossier des modèles TTS — même convention que `VoiceModelDownloader`/
     * `SherpaOnnxModelPaths` (infrastructure/tts) : `filesDir/voices`.
     * Toujours en lecture seule : aucune capacité de déplacement n'existe
     * dans ce lot (signalé plutôt que masqué par un contrôle sans effet).
     */
    val modelsFolderInfo: ModelsFolderInfo
        get() = ModelsFolderInfo(
            path = File(appContext.filesDir, "voices").absolutePath,
            isEditable = false,
        )

    fun exportTo(destinationUri: String, password: String) {
        viewModelScope.launch {
            val success = backupManager.exportTo(destinationUri, BuildConfig.VERSION_NAME, password)
            _lastResult.value = if (success) {
                DataOperationResult.ExportSuccess
            } else {
                DataOperationResult.ExportFailed("Impossible d'écrire le fichier de sauvegarde.")
            }
        }
    }

    /** @param password ignoré si le fichier importé est un export antérieur en clair (compatibilité ascendante). */
    fun importFrom(sourceUri: String, password: String?) {
        viewModelScope.launch {
            _lastResult.value = when (val result = backupManager.importFrom(sourceUri, password)) {
                is ImportBackupResult.Success ->
                    DataOperationResult.ImportSuccess(result.restored, result.skippedOrphans)
                is ImportBackupResult.Failed -> DataOperationResult.ImportFailed(result.message)
            }
        }
    }

    fun dismissResult() {
        _lastResult.value = null
    }
}
