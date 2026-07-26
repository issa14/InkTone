# ADR-002 : Domain-Driven Design

**Status :** Accepted
**Date :** 2026-07-26

## Context

Les concepts du produit (Publication, ReadingState, VoiceProfile, Locator)
doivent survivre aux choix techniques. Un modèle métier qui suit les
évolutions du schéma de base ou de l'UI plutôt que le vocabulaire du
produit s'érode à chaque changement de technologie, ce que le legacy a
illustré à plusieurs reprises.

## Decision

Le domaine métier est le cœur du système ; les technologies (persistance,
UI, moteurs TTS, parseurs) l'implémentent, jamais l'inverse. Les entités,
value objects et règles métier sont définis une seule fois dans `domain/`
et ne varient pas selon la couche qui les consomme.

## Rationale

- Un langage commun stable entre le produit, le code et la documentation.
- Indépendance vis-à-vis des choix technologiques : le domaine ne change
  pas quand la base de données ou le moteur TTS change.

## Consequences

Exige une discipline de modélisation avant l'implémentation technique :
chaque nouvelle capacité commence par une question de vocabulaire métier,
pas par une table ou un écran.

## Alternatives Considered

- **Modèle guidé par le schéma de base de données** : plus rapide à
  démarrer, mais couple le métier à Room et à ses contraintes techniques
  (types nullable, clés étrangères) — rejeté.
