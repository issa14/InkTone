package com.inktone.infrastructure.tts.di

import javax.inject.Qualifier

/** Adaptateur Palier 2 (Sherpa-ONNX, Tache 5.1) - qualifie pour permettre a FallbackTtsEngine (Tache 5.8) de distinguer les deux moteurs TtsEngine du graphe Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Palier2

/** Adaptateur Palier 1 (Android natif, Tache 3.1) - voir Palier2. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Palier1
