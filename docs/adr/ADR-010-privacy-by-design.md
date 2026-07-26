# ADR-010 : Privacy by Design

**Status :** Accepted
**Date :** 2026-07-26

## Context

InkTone manipule des habitudes de lecture — quels livres, à quel rythme,
à quelles heures — des données sensibles par nature, révélatrices des
centres d'intérêt et des habitudes de vie de l'utilisateur.

## Decision

Le produit applique une minimisation de la collecte, un traitement local
par défaut, une transparence sur toute donnée traitée, et un consentement
explicite requis avant tout envoi de données (§10).

## Rationale

La confiance des utilisateurs et l'éthique du produit dépendent d'un
traitement des données de lecture qui ne surprend jamais l'utilisateur.

## Consequences

Contraint la télémétrie et le crash reporting, qui ne peuvent pas être
actifs par défaut — tension résolue explicitement par l'ADR-014.

## Alternatives Considered

- **Télémétrie active par défaut avec opt-out** : rejetée, contraire au
  principe de consentement explicite avant tout envoi de données (§10.2).
