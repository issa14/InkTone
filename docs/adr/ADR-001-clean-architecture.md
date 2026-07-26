# ADR-001 : Adoption de la Clean Architecture

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le projet vise plusieurs années d'évolution avec des technologies (UI, base
de données, moteurs TTS) susceptibles de changer. L'implémentation legacy
mono-couche rendait les responsabilités poreuses : logique métier mêlée à
la persistance et à l'UI, ce qui a rendu chaque audit du projet plus long
et chaque correction plus risquée.

## Decision

InkTone adopte une architecture en couches (Presentation → Application →
Domain → Data → Infrastructure, cf. Blueprint §4.3) avec une règle de
dépendance stricte : toutes les dépendances pointent vers le domaine.

## Rationale

- Testabilité du métier indépendamment d'Android.
- Remplacement des technologies (base de données, moteur TTS, parseur)
  sans modifier le cœur métier.
- Clarté des responsabilités par couche.

## Consequences

Plus de code de liaison (interfaces, mappers) qu'une architecture plate.
En contrepartie, chaque couche évolue et se teste indépendamment.

## Alternatives Considered

- **Architecture par écrans sans couches** : rapide à démarrer, mais c'est
  exactement le schéma qui a produit la dette technique du legacy.
- **Architecture hexagonale stricte** : bénéfices équivalents, vocabulaire
  moins répandu dans l'écosystème Android — Clean Architecture retenue
  pour la familiarité de l'outillage et de la documentation disponibles.
