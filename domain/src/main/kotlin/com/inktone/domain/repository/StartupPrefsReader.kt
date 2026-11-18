package com.inktone.domain.repository

import com.inktone.domain.model.AppTheme

/**
 * Copie synchrone des préférences qui conditionnent le premier frame
 * (thème, destination de départ), lisible avant que la base soit ouverte.
 * [PreferencesRepository] reste la source de vérité : cette copie n'en est
 * que le reflet de la dernière session, corrigé dès la première émission.
 */
interface StartupPrefsReader {
    /** `null` tant que la copie n'a jamais été écrite (première installation). */
    fun readHasSeenOnboarding(): Boolean?

    fun readDynamicColorEnabled(): Boolean

    fun readAppTheme(): AppTheme
}
