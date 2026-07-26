package com.inktone.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.inktone.feature.reader.ReaderIntent
import com.inktone.feature.reader.ReaderScreen
import com.inktone.feature.reader.ReaderViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Point d'entree minimal pour le test de bout en bout manuel de la
 * marche a blanc (Tache 3.7) : heberge ReaderScreen et charge une phrase
 * fixe du fixture EPUB (Tache 3.2). Pas de navigation reelle — la
 * Phase 4 remplacera ceci par le graphe de navigation complet.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ReaderViewModel = hiltViewModel()
                    LaunchedEffect(Unit) {
                        viewModel.onIntent(ReaderIntent.LoadSentence(WALKING_SKELETON_FIXTURE_SENTENCE))
                    }
                    ReaderScreen(viewModel = viewModel)
                }
            }
        }
    }

    private companion object {
        const val WALKING_SKELETON_FIXTURE_SENTENCE =
            "Bonjour, ceci est un test de synchronisation."
    }
}
