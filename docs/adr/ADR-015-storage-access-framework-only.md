# ADR-015 : Storage Access Framework exclusivement

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le legacy utilisait `MANAGE_EXTERNAL_STORAGE`, une permission qui bloque
la publication sur le Play Store. Ce problème avait été marqué résolu
dans la documentation de statut du projet alors que la permission restait
active dans le code — un cas concret ayant motivé la gouvernance
documentaire du §17.2.

## Decision

Le Storage Access Framework (sélecteur de documents, URI persistées) est
l'unique voie d'accès aux fichiers de l'utilisateur. `MANAGE_EXTERNAL_
STORAGE` est interdit et sa présence est vérifiée automatiquement en CI
(§14.8).

## Rationale

Conformité aux règles de publication du Play Store, et respect du
principe de moindre privilège.

## Consequences

L'import « scanner un dossier entier » passe par `OpenDocumentTree` plutôt
que par un accès direct au système de fichiers. L'ergonomie est
légèrement différente d'un accès brut — un compromis assumé.

## Alternatives Considered

- **`MANAGE_EXTERNAL_STORAGE` avec justification Play Store** : rejetée,
  refusée en pratique pour un lecteur d'ebooks qui n'a pas de besoin de
  gestion de fichiers à l'échelle du système.
