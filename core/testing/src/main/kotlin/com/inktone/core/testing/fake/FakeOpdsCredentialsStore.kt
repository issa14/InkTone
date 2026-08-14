package com.inktone.core.testing.fake

import com.inktone.domain.service.OpdsCredentials
import com.inktone.domain.service.OpdsCredentialsStore

class FakeOpdsCredentialsStore : OpdsCredentialsStore {
    private val store = mutableMapOf<String, OpdsCredentials>()

    override fun hasCredentials(catalogId: String): Boolean = store.containsKey(catalogId)

    override fun getCredentials(catalogId: String): OpdsCredentials? = store[catalogId]

    override fun setCredentials(catalogId: String, username: String, password: String) {
        store[catalogId] = OpdsCredentials(username, password)
    }

    override fun clearCredentials(catalogId: String) {
        store.remove(catalogId)
    }
}
