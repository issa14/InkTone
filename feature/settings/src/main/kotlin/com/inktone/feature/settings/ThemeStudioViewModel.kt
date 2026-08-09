package com.inktone.feature.settings

import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.core.designsystem.toColor
import com.inktone.domain.model.FontFamily
import com.inktone.domain.model.ReadingTheme
import com.inktone.domain.repository.ThemeRepository
import com.inktone.domain.usecase.DeleteCustomThemeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ThemeStudioViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val deleteCustomTheme: DeleteCustomThemeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ThemeStudioUiState())
    val state: StateFlow<ThemeStudioUiState> = _state.asStateFlow()

    private val _effects = Channel<ThemeStudioEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onIntent(intent: ThemeStudioIntent) {
        when (intent) {
            is ThemeStudioIntent.Load -> load(intent.themeId)
            is ThemeStudioIntent.SetName -> _state.value = _state.value.copy(name = intent.name)
            is ThemeStudioIntent.SetBackgroundColor -> _state.value = _state.value.copy(backgroundColorHex = intent.hex)
            is ThemeStudioIntent.SetTextColor -> _state.value = _state.value.copy(textColorHex = intent.hex)
            is ThemeStudioIntent.SetAccentColor -> _state.value = _state.value.copy(accentColorHex = intent.hex)
            is ThemeStudioIntent.SetHighlightColor -> _state.value = _state.value.copy(highlightColorHex = intent.hex)
            is ThemeStudioIntent.ApplyStarterPalette -> applyStarterPalette(intent.palette)
            is ThemeStudioIntent.Save -> save()
            is ThemeStudioIntent.ConfirmDelete -> delete()
        }
    }

    private fun load(themeId: String?) {
        if (themeId == null) {
            _state.value = ThemeStudioUiState()
            return
        }
        viewModelScope.launch {
            val theme = themeRepository.getById(themeId) ?: return@launch
            _state.value = ThemeStudioUiState(
                editingThemeId = theme.id,
                name = theme.displayName,
                backgroundColorHex = theme.backgroundColorHex,
                textColorHex = theme.textColorHex,
                accentColorHex = theme.accentColorHex,
                highlightColorHex = theme.highlightColorHex,
            )
        }
    }

    private fun applyStarterPalette(palette: StarterPalette) {
        val preset = STARTER_PALETTES.getValue(palette)
        _state.value = _state.value.copy(
            backgroundColorHex = preset.backgroundColorHex,
            textColorHex = preset.textColorHex,
            accentColorHex = preset.accentColorHex,
            highlightColorHex = preset.highlightColorHex,
        )
    }

    /**
     * Tâche 9.6, point 7 — un thème sans nom ne peut pas être sauvegardé
     * sous une entrée anonyme : reçoit un nom par défaut plutôt que de
     * bloquer (Sauvegarder reste toujours actionnable, cohérent avec le
     * badge WCAG jamais bloquant non plus).
     */
    private fun save() {
        val s = _state.value
        val name = s.name.ifBlank { "Thème personnalisé" }
        viewModelScope.launch {
            val id = s.editingThemeId ?: "custom_${UUID.randomUUID()}"
            themeRepository.saveCustom(
                ReadingTheme(
                    id = id, displayName = name, isBuiltIn = false,
                    backgroundColorHex = s.backgroundColorHex, textColorHex = s.textColorHex,
                    accentColorHex = s.accentColorHex, highlightColorHex = s.highlightColorHex,
                    fontFamily = inferFontFamily(s.backgroundColorHex),
                ),
            )
            _effects.send(ThemeStudioEffect.SavedAndClose)
        }
    }

    private fun delete() {
        val id = _state.value.editingThemeId ?: return
        viewModelScope.launch {
            deleteCustomTheme(id)
            _effects.send(ThemeStudioEffect.DeletedAndClose)
        }
    }

    companion object {
        /**
         * Famille de police inférée depuis la luminance du fond (Tâche 9.1 :
         * « serif pour les ambiances claires, sans-serif pour les sombres »).
         * Le Studio n'expose pas de sélecteur de police dédié (hors scope de
         * la cible, qui ne liste que 4 sélecteurs de couleur) : dériver
         * plutôt qu'inventer un cinquième contrôle non spécifié.
         */
        internal fun inferFontFamily(backgroundHex: String): FontFamily =
            if (backgroundHex.toColor().luminance() > 0.5f) FontFamily.SERIF else FontFamily.SANS_SERIF

        private val STARTER_PALETTES = mapOf(
            StarterPalette.SOMBRE to ThemeStudioUiState(backgroundColorHex = "#121212", textColorHex = "#E0E0E0", accentColorHex = "#82B1FF", highlightColorHex = "#FFD54F"),
            StarterPalette.CLAIR to ThemeStudioUiState(backgroundColorHex = "#FFFFFF", textColorHex = "#212121", accentColorHex = "#1976D2", highlightColorHex = "#FFEB3B"),
            StarterPalette.CHAUD to ThemeStudioUiState(backgroundColorHex = "#FFF3E0", textColorHex = "#4E342E", accentColorHex = "#E64A19", highlightColorHex = "#FFAB40"),
            StarterPalette.NEON to ThemeStudioUiState(backgroundColorHex = "#0D0221", textColorHex = "#F5F5F5", accentColorHex = "#00E5FF", highlightColorHex = "#FF00E5"),
        )
    }
}
