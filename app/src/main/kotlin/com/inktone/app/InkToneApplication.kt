package com.inktone.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée Hilt minimal — permet au graphe de DI de s'assembler et
 * de valider les modules `data` et `infrastructure` (Tâche 2.6). Navigation
 * et initialisation applicative réelles : Phase 3+ (Blueprint §12.3).
 */
@HiltAndroidApp
class InkToneApplication : Application()
