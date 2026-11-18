package com.inktone.feature.settings

import com.inktone.core.designsystem.InkToneSpacing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Règles de prononciation, ouvertes depuis le panneau Voix du Lecteur et la
 * section Narration des Réglages. Même
 * carte que les Réglages (audit UX B8) ; le titre est porté par la barre de
 * l'écran (InkToneNavHost), pas répété ici.
 */
@Composable
fun PronunciationRulesScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(horizontal = InkToneSpacing.lg).verticalScroll(rememberScrollState())) {
        PronunciationRulesSection(rules = state.pronunciationRules, onIntent = viewModel::onIntent)
    }
}
