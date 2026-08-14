package com.inktone.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UserAgentInterceptorTest {

    private val server = MockWebServer()

    @Before fun setUp() { server.start() }

    @After fun tearDown() { server.shutdown() }

    @Test
    fun ajoute_un_user_agent_explicite_a_toute_requete() {
        val client = OkHttpClient.Builder().addInterceptor(UserAgentInterceptor()).build()
        server.enqueue(MockResponse().setBody("ok"))

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
            it.body?.string()
        }

        val recorded = server.takeRequest()
        assertEquals("InkTone-Reader/1.0 (Android)", recorded.getHeader("User-Agent"))
    }
}
