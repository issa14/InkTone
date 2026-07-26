# ADR-009 : Performance by Design, with Budgets

**Status :** Accepted
**Date :** 2026-07-26

## Context

« La performance est une fonctionnalité » reste un slogan sans chiffres
vérifiables. Le legacy a montré des dégradations découvertes tardivement,
notamment un import par lot dépassant 15 minutes pour 500 EPUB avant
correction.

## Decision

Des budgets de performance chiffrés (§11.2) sont définis sur une baseline
matérielle de référence (Snapdragon 680), vérifiés par des benchmarks
automatisés, et intégrés à la Definition of Done (§14.9) de chaque
module concerné.

## Rationale

Mesurer avant d'optimiser exige des cibles explicites ; sans chiffre, un
budget de performance n'est ni vérifiable ni actionnable.

## Consequences

Un dépassement de budget bloque la release, ou déclenche un ADR de
révision explicite du budget concerné — jamais une tolérance silencieuse.

## Alternatives Considered

- **Objectifs de performance qualitatifs** (« ça doit être rapide ») :
  rejetés, invérifiables et donc inapplicables en CI.
