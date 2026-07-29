package com.inktone.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inktone.domain.model.PronunciationRule
import com.inktone.domain.repository.PronunciationRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PronunciationRulesViewModel @Inject constructor(
    private val ruleRepository: PronunciationRuleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PronunciationRulesUiState())
    val state: StateFlow<PronunciationRulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ruleRepository.observeAll().collect { rules ->
                _state.value = _state.value.copy(rules = rules)
            }
        }
    }

    fun onIntent(intent: PronunciationRulesIntent) {
        when (intent) {
            is PronunciationRulesIntent.SetEditingOriginalText ->
                _state.value = _state.value.copy(editingOriginalText = intent.text)
            is PronunciationRulesIntent.SetEditingReplacementText ->
                _state.value = _state.value.copy(editingReplacementText = intent.text)
            is PronunciationRulesIntent.SetEditingIsRegex ->
                _state.value = _state.value.copy(editingIsRegex = intent.isRegex)
            is PronunciationRulesIntent.Save -> {
                val current = _state.value
                if (current.editingOriginalText.isBlank()) return
                viewModelScope.launch {
                    ruleRepository.save(
                        PronunciationRule(
                            id = UUID.randomUUID().toString(),
                            originalText = current.editingOriginalText,
                            replacementText = current.editingReplacementText,
                            isRegex = current.editingIsRegex,
                        ),
                    )
                    _state.value = current.copy(
                        editingOriginalText = "", editingReplacementText = "", editingIsRegex = false,
                    )
                }
            }
            is PronunciationRulesIntent.ToggleEnabled -> viewModelScope.launch {
                ruleRepository.save(intent.rule.copy(isEnabled = !intent.rule.isEnabled))
            }
            is PronunciationRulesIntent.Delete -> viewModelScope.launch { ruleRepository.delete(intent.id) }
        }
    }
}
