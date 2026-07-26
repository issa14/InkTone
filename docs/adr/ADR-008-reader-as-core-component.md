# ADR-008 : Reader as Core Component

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le produit a besoin d'un critère d'arbitrage clair lorsque des priorités
concurrentes se présentent entre la lecture visuelle, le TTS, les
annotations, la recherche et les statistiques.

## Decision

Le Reader est le composant central du produit ; le TTS, les annotations,
la recherche et les statistiques gravitent autour de lui, jamais
l'inverse.

## Rationale

Cohérent avec la philosophie « Reading First » du Blueprint (§1.4) :
InkTone est d'abord un lecteur, la narration TTS en est une extension de
premier plan, pas le produit lui-même.

## Consequences

Aucun développement périphérique (statistiques, recherche avancée,
synchronisation) ne doit dégrader l'expérience de lecture principale. En
cas d'arbitrage produit, le Reader l'emporte.

## Alternatives Considered

- **Produit audio-first**, où la lecture visuelle serait secondaire :
  rejeté — InkTone est un lecteur qui parle, pas un lecteur audio qui
  affiche accessoirement du texte.
