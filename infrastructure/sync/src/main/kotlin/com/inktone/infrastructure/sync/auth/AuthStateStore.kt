package com.inktone.infrastructure.sync.auth

import net.openid.appauth.AuthState

/** Extrait de [SecureAuthStateStore] (tâche 11.4/11.7) — permet de tester [AppAuthGoogleAuthRepository] sans Keystore Android (JVM pur, via un faux en mémoire). */
interface AuthStateStore {
    fun read(): AuthState?
    fun write(state: AuthState)
    fun clear()
}
