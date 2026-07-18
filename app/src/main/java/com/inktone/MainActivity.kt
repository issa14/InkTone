package com.inktone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inktone.data.settings.AppTheme
import com.inktone.data.settings.SettingsRepository
import com.inktone.ui.navigation.InkToneNavGraph
import com.inktone.ui.theme.InkToneTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appTheme by settingsRepository.theme.collectAsStateWithLifecycle(initialValue = AppTheme.PAPIER_ART)
            InkToneTheme(theme = appTheme) {
                InkToneNavGraph()
            }
        }
    }
}
