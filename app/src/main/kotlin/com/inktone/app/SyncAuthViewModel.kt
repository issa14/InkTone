package com.inktone.app

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.data.sync.GoogleSyncLinker
import com.inktone.data.sync.WebDavSyncLinker
import com.inktone.data.sync.WebDavSyncLinkResult
import com.inktone.infrastructure.sync.auth.GoogleAuthConfig
import com.inktone.infrastructure.sync.auth.GoogleAuthRepository
import com.inktone.infrastructure.sync.auth.GoogleAuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pont entre `infrastructure/sync` (`GoogleAuthRepository`, types
 * Android/AppAuth) et `data` (`GoogleSyncLinker`, écrit le compte
 * persisté) — vit dans `app` pour la même raison que `BackupViewModel` :
 * ni `feature/sync` (jamais de réseau/identifiants, même discipline que
 * `ImportScheduler`) ni `domain` (`app` n'a pas le droit d'en dépendre)
 * ne peuvent héberger ce pont (Lot 11, tâche 11.6).
 *
 * Lot 19 — étend le même pont à WebDAV ([WebDavSyncLinker], data) : le
 * réseau et le stockage chiffré des identifiants restent dans
 * `infrastructure/sync`, jamais dans `app`.
 */
@HiltViewModel
class SyncAuthViewModel @Inject constructor(
    private val googleAuthRepository: GoogleAuthRepository,
    private val googleSyncLinker: GoogleSyncLinker,
    private val webDavSyncLinker: WebDavSyncLinker,
) : ViewModel() {

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isWebDavConnecting = MutableStateFlow(false)
    val isWebDavConnecting: StateFlow<Boolean> = _isWebDavConnecting.asStateFlow()

    private val _webDavError = MutableStateFlow<String?>(null)
    val webDavError: StateFlow<String?> = _webDavError.asStateFlow()

    val isGoogleConfigured: Boolean get() = GoogleAuthConfig.isConfigured

    fun buildAuthorizationIntent(): Intent = googleAuthRepository.buildAuthorizationIntent()

    fun onAuthorizationResult(intent: Intent?) {
        if (intent == null) {
            _isAuthenticating.value = false
            return
        }
        _isAuthenticating.value = true
        viewModelScope.launch {
            when (val result = googleAuthRepository.handleAuthorizationResponse(intent)) {
                is GoogleAuthResult.Success -> googleSyncLinker.link(accountLabel = "Compte Google connecté")
                is GoogleAuthResult.Failed -> _authError.value = result.message
            }
            _isAuthenticating.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            googleAuthRepository.disconnect()
            googleSyncLinker.unlink()
        }
    }

    fun connectWebDav(url: String, username: String, password: String) {
        if (_isWebDavConnecting.value) return
        _isWebDavConnecting.value = true
        viewModelScope.launch {
            when (val result = webDavSyncLinker.connect(url, username, password)) {
                is WebDavSyncLinkResult.Success -> _webDavError.value = null
                is WebDavSyncLinkResult.Failed -> _webDavError.value = result.message
            }
            _isWebDavConnecting.value = false
        }
    }

    fun disconnectWebDav() {
        viewModelScope.launch { webDavSyncLinker.disconnect() }
    }

    fun dismissError() {
        _authError.value = null
    }

    fun dismissWebDavError() {
        _webDavError.value = null
    }
}
