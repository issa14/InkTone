# ADR-019 : Réécriture complète sur branche orpheline

**Status :** Accepted
**Date :** 2026-07-26

## Context

Voir Blueprint §13.1–13.3 : l'architecture cible d'InkTone diffère
structurellement de l'implémentation legacy (couches, modèle de données,
structure de modules). Le produit est en fenêtre pré-release, sans
installation publique à préserver, et le coût d'une réécriture assistée
est jugé faible dès lors que les leçons du legacy sont explicitement
spécifiées plutôt qu'implicitement supposées.

## Decision

Une nouvelle `main` orpheline démarre l'historique du projet depuis le
Blueprint et les ADR fondateurs. Le code legacy est archivé intégralement
sur la branche `legacy/monolith`, taguée `legacy-final-v0`, protégée en
lecture seule, et jamais fusionnée dans la nouvelle `main`.

## Rationale

L'architecture cible est structurellement différente du legacy ; la
fenêtre pré-release rend cette rupture gratuite ; le coût d'une
réécriture assistée est faible si les leçons capitalisées (K1–K12, §13.4)
sont spécifiées avant le premier commit de code.

## Consequences

Risque classique de réécriture (perte de fonctionnalités, régressions
silencieuses), encadré par les garde-fous bloquants du §13.4 (K1–K12) et
par la Definition of Done du §14.9. L'historique Git de la nouvelle
`main` commence par la documentation, jamais par du code.

## Alternatives Considered

- **Migration incrémentale (strangler fig)** : rejetée, coût supérieur
  pour cette ampleur de restructuration architecturale.
- **Nouveau dépôt Git** : rejeté, perdrait la co-localisation de
  l'historique legacy et des leçons capitalisées avec le nouveau code.
