package com.inktone.data.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Assainit les réponses XML/Atom (Lot 13, retour device) : certains
 * serveurs OPDS émettent des `&` non échappés dans le texte, ce que
 * `XmlPullParser` refuse (« Undetermined entity ref »). On remplace les
 * `&` qui ne font pas partie d'une entité XML valide (`amp`/`lt`/`gt`/
 * `quot`/`apos`) ni d'une référence numérique (`&#…;`) par `&amp;`.
 *
 * Sûr pour les corps volumineux : on n'assainit que si le Content-Type
 * contient « xml » ou « atom » (les flux sont petits), jamais les
 * téléchargements binaires (`application/epub+zip`…).
 */
class XmlSanitizerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        val contentType = response.header("Content-Type").orEmpty()
        val isXmlLike = contentType.contains("xml", ignoreCase = true) ||
            contentType.contains("atom", ignoreCase = true)
        if (!isXmlLike) return response

        val body = response.body ?: return response
        val sanitized = UNESCAPED_AMPERSAND.replace(body.string(), "&amp;")
        val newBody = sanitized.toResponseBody(body.contentType())

        // La longueur change après assainissement : on retire l'en-tête
        // Content-Length devenu inexact plutôt que de laisser un corps tronqué.
        return response.newBuilder()
            .body(newBody)
            .removeHeader("Content-Length")
            .build()
    }

    private companion object {
        val UNESCAPED_AMPERSAND = Regex("&(?!amp;|lt;|gt;|quot;|apos;|#x?[0-9a-fA-F]+;)")
    }
}
