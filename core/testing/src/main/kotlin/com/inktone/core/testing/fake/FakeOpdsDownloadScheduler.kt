package com.inktone.core.testing.fake

import com.inktone.domain.service.OpdsDownloadScheduler

/** Fake du scheduler de téléchargement OPDS — enregistre les enqueues, jamais de vrai WorkManager. */
class FakeOpdsDownloadScheduler : OpdsDownloadScheduler {
    val enqueued = mutableListOf<Triple<String, String?, String>>()

    override fun enqueue(acquisitionHref: String, catalogId: String?, bookTitle: String): String {
        enqueued += Triple(acquisitionHref, catalogId, bookTitle)
        return "work-${enqueued.size}"
    }
}
