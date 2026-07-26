# ADR-011 : Readium comme fondation EPUB, encapsulée

**Status :** Accepted
**Date :** 2026-07-26

## Context

Le Reading Engine exige le parsing EPUB, la navigation et la gestion de
locators. Construire un Document Model custom from scratch signifierait
réimplémenter une conformité EPUB déjà éprouvée ; à l'inverse, dépendre
nûment de Readium dans le domaine couplerait le métier à une
bibliothèque externe. Le legacy utilisait déjà Readium avec succès
(parsing, `belongsTo`, `subjects`).

## Decision

Readium est le parser EPUB officiel d'InkTone, confiné entièrement à
`infrastructure/parser`. Le Document Model et le Locator du domaine
encapsulent les modèles Readium via un mapping sans perte — aucun type
Readium ne franchit la frontière du domaine.

## Rationale

Hériter d'années de travail sur la conformité EPUB (encodage, structure,
métadonnées) tout en gardant le domaine indépendant de toute bibliothèque
externe, conformément à la règle de dépendance (ADR-001).

## Consequences

Une couche de mapping Readium → Document Model doit être maintenue et
testée. En contrepartie, les autres formats (TXT, PDF) produisent le même
Document Model par leurs propres parsers, sans jamais exposer Readium.

## Alternatives Considered

- **Document Model 100 % custom** : rejeté, réimplémentation massive de
  la conformité EPUB sans valeur ajoutée pour le produit.
- **Exposition directe des types Readium dans le domaine** : rejetée,
  violerait la règle de dépendance (§4.8) et couplerait irrémédiablement
  le métier à Readium.
