# ADR-017 : Périmètre PDF différé et borné

**Status :** Accepted
**Date :** 2026-07-26

## Context

La version 1.0.0 du Blueprint plaçait le support PDF en v1, aux côtés
d'EPUB. Or un support PDF top-tier (rendu paginé sans reflow, extraction
fiable de l'ordre de lecture pour alimenter le TTS) est un chantier à
part entière. Le mener en v1 aurait dilué la qualité du support EPUB,
contraire au standard « production-grade, no half-measures » du produit.

## Decision

La v1 se limite à EPUB et TXT, complets. Le PDF est repoussé en v1.x,
d'abord en **affichage seul** ; le support TTS sur PDF est conditionné à
la validation préalable d'une extraction fiable de l'ordre de lecture.

## Rationale

Mieux vaut exceller sur un périmètre maîtrisé que de moyenner la qualité
sur deux formats simultanément.

## Consequences

Nécessite une communication produit claire sur le périmètre exact de la
v1 : EPUB et TXT complets, PDF absent jusqu'à v1.x.

## Alternatives Considered

- **Support PDF complet dès la v1** : rejeté, risque de dégrader la
  qualité des deux formats simultanément.
- **Abandon complet du PDF** : rejeté, il existe une demande réelle et
  l'architecture (Document Model unifié, ADR-005) le permet sans coût
  structurel supplémentaire.
