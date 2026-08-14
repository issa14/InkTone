package com.inktone.infrastructure.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lot 13, tâche 13.1 — catalogue OPDS persistant (Volet 1, ADR-023).
 *
 * Ne porte **jamais** les identifiants Basic Auth : ils vivent chiffrés
 * dans `SecureOpdsCredentialsStore` (`infrastructure:opds`, keyés par
 * `id`), même discipline que `SecureAuthStateStore` (Lot 11) — la base
 * Room ne contient aucun mot de passe en clair.
 */
@Entity(tableName = "opds_catalogs")
data class CatalogEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rootUrl: String,
    val searchTemplateUrl: String?,
    val createdAt: Long,
)
