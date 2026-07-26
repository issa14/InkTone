# ADR-007 : Local Data Ownership

**Status :** Accepted
**Date :** 2026-07-26

## Context

Les livres importés et les données de lecture qui en découlent (position,
annotations, statistiques) appartiennent à l'utilisateur, pas à InkTone.

## Decision

Le stockage est local par défaut. Toute synchronisation est optionnelle
et activée explicitement par l'utilisateur ; elle ne conditionne jamais
l'usage du produit.

## Rationale

- Confiance : l'utilisateur garde le contrôle de ses données de lecture.
- Cohérence avec Offline First (ADR-003) : aucune fonction essentielle ne
  dépend d'un compte ou d'un serveur.

## Consequences

La valeur du produit ne dépend d'aucun compte ni serveur ; la
synchronisation (§9, v2.x) s'ajoute sans jamais devenir un prérequis.

## Alternatives Considered

- **Compte utilisateur obligatoire** : rejeté — contraire à la propriété
  locale des données et à Offline First.
