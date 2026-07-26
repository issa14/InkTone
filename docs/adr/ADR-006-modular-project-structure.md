# ADR-006 : Modular Project Structure

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le legacy était mono-module : temps de build croissants avec la taille du
projet, frontières entre responsabilités purement conventionnelles, et
violations de couches invisibles jusqu'à ce qu'elles causent un incident.

## Decision

InkTone adopte une structure Gradle multi-modules (§12), avec les règles
de dépendance encodées dans `build-logic/` plutôt que documentées
séparément.

## Rationale

- Frontières vérifiées par le build, pas par la relecture humaine.
- Compilation incrémentale : modifier `feature/reader` ne recompile pas
  `feature/settings`.
- Développement parallèle facilité par des périmètres de module clairs.

## Consequences

Coût initial de mise en place des convention plugins et du catalogue de
versions partagé (§0.5). Ce coût est payé une fois, en Phase 0.

## Alternatives Considered

- **Mono-module à packages disciplinés** : rejeté — la discipline non
  outillée par le build s'érode avec le temps, ce que le legacy a
  démontré concrètement.
