# ADR-014 : Crash reporting en opt-in explicite

**Status :** Accepted
**Date :** 2026-07-26

## Context

Crashlytics est nécessaire à la qualité du produit en production, mais
constitue un envoi réseau de données — en tension frontale avec le
principe « aucune donnée sans accord explicite » (§10.2). Le legacy
embarquait Crashlytics sans aucun flux de consentement utilisateur.

## Decision

Le rapport de crash est désactivé par défaut. Il est proposé en opt-in
explicite à l'onboarding, avec une explication honnête de son contenu ;
réversible à tout moment dans les réglages ; et fonctionne en no-op
gracieux lorsqu'aucun identifiant Firebase n'est commis au dépôt (K10).

## Rationale

Réconcilier les besoins de qualité du produit avec le principe de Privacy
by Design (ADR-010), sans hypocrisie documentaire — le legacy affirmait
respecter le consentement sans l'implémenter.

## Consequences

Taux de remontée de crashs partiel puisque limité aux utilisateurs ayant
consenti — accepté comme compromis assumé. Les testeurs volontaires
activent la fonctionnalité en connaissance de cause.

## Alternatives Considered

- **Opt-out** (actif par défaut, désactivable) : rejeté, contraire au
  §10.2.
- **Aucun crash reporting** : rejeté, reviendrait à développer en
  aveugle sur la stabilité en production.
