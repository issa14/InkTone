# ADR-016 : WAL comme journal mode

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le legacy utilisait le journal mode TRUNCATE, dont le coût de commit
croît avec la taille du fichier de base de données — une dégradation
mesurée concrètement sur l'import par lot (15+ minutes pour 500 EPUB
avant correction).

## Decision

Le journal mode WAL (Write-Ahead Logging) est obligatoire pour la base de
données InkTone (§6.5).

## Rationale

Coût de commit constant indépendamment de la taille de la base, et
lectures concurrentes aux écritures — pertinent pour un import par lot
qui écrit pendant que l'UI lit la bibliothèque.

## Consequences

Des fichiers `-wal` et `-shm` accompagnent le fichier de base principal.
Sans conséquence pratique ici : un seul processus accède à la base,
aucune contrainte multi-process ne s'applique.

## Alternatives Considered

- **TRUNCATE** : rejeté, directement invalidé par les mesures de
  performance du legacy sur l'import par lot.
