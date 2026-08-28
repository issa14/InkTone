package com.inktone.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.domain.model.PronunciationRule

/**
 * Liste + formulaire ajout/edition (Tache 8.3) — pattern MVI standard,
 * meme gabarit que [SettingsScreen].
 */
@Composable
fun PronunciationRulesScreen(viewModel: PronunciationRulesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Regles de prononciation")

        OutlinedTextField(
            value = state.editingOriginalText,
            onValueChange = { viewModel.onIntent(PronunciationRulesIntent.SetEditingOriginalText(it)) },
            label = { Text("Texte original (ou motif regex)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.editingReplacementText,
            onValueChange = { viewModel.onIntent(PronunciationRulesIntent.SetEditingReplacementText(it)) },
            label = { Text("Remplacement") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Regex")
            Switch(
                checked = state.editingIsRegex,
                onCheckedChange = { viewModel.onIntent(PronunciationRulesIntent.SetEditingIsRegex(it)) },
            )
        }
        Button(onClick = { viewModel.onIntent(PronunciationRulesIntent.Save) }) {
            Text("Ajouter")
        }

        LazyColumn {
            items(state.rules, key = { it.id }) { rule ->
                PronunciationRuleRow(
                    rule = rule,
                    onToggle = { viewModel.onIntent(PronunciationRulesIntent.ToggleEnabled(rule)) },
                    onDelete = { viewModel.onIntent(PronunciationRulesIntent.Delete(rule.id)) },
                )
            }
        }
    }
}

@Composable
private fun PronunciationRuleRow(rule: PronunciationRule, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${rule.originalText} -> ${rule.replacementText}${if (rule.isRegex) " (regex)" else ""}")
        Switch(checked = rule.isEnabled, onCheckedChange = { onToggle() })
        Button(onClick = onDelete) { Text("Supprimer") }
    }
}
