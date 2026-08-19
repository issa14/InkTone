package com.inktone.infrastructure.sync.webdav

/** Extrait de [WebDavCredentialsStore] — permet de tester [WebDavSyncProvider] sans Keystore Android (JVM pur, faux en mémoire). */
interface WebDavCredentialsStoreContract {
    fun read(): WebDavCredentials?
    fun write(credentials: WebDavCredentials)
    fun clear()
}
