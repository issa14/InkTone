package com.inktone.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ajoute un en-tête `User-Agent` explicite à toute requête sortante
 * (Lot 13, retour device) : certains catalogues OPDS (ex. Gutenberg)
 * bloquent les User-Agent génériques type `okhttp/4.x`. `.header()` (et
 * non `.addHeader()`) garantit qu'un seul User-Agent est posé, même si un
 * autre composant en posait un.
 */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val USER_AGENT = "InkTone-Reader/1.0 (Android)"
    }
}
