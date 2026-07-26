# ADR-020 : Rupture du schéma de base de données

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le schéma legacy (version 17) porte l'histoire de ses migrations
successives, dont un trou documenté entre les versions 6 et 13. Le
nouveau Data Model (§6) en diffère structurellement : Locator stocké à
plat plutôt qu'imbriqué, scission explicite entre ReadingState et
ReadingSession là où le legacy les conflait.

## Decision

Le nouveau schéma démarre en version 1. Aucune migration n'est fournie
depuis le schéma legacy ; les installations de test existantes sont
ré-importées plutôt que migrées.

## Rationale

Le projet est en fenêtre pré-release, sans installation publique à
préserver — la seule fenêtre temporelle où cette rupture de schéma est
gratuite, sans coût utilisateur.

## Consequences

Dès la première publication publique, plus aucune rupture de schéma ne
sera possible : les règles de migration non négociables du §6.4
s'appliquent sans exception à partir de la version 1.

## Alternatives Considered

- **Migration v17 → v1** : rejetée, coût de développement élevé pour un
  bénéfice nul (zéro utilisateur public à préserver).
- **Reprise du schéma legacy tel quel** : rejetée, aurait reconduit la
  conflation ReadingState/ReadingSession et l'adressage triple que le
  nouveau Data Model corrige explicitement.
