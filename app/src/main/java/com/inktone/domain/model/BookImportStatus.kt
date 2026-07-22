package com.inktone.domain.model

/**
 * État d'import d'un [Book]. `IMPORTING` couvre la fenêtre entre le premier enregistrement en
 * base (voir `BookRepositoryImpl.importEpub`) et la fin du traitement de tous ses chapitres —
 * un livre dans cet état peut déjà être partiellement lisible mais son contenu n'est pas
 * définitif. `FAILED` marque un import qui n'a jamais atteint `READY` (exception pendant
 * l'import, ou processus arrêté en cours de route — voir PLAN import EPUB §3).
 */
enum class BookImportStatus { IMPORTING, READY, FAILED }
