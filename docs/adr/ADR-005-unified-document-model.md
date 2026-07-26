# ADR-005 : Unified Document Model

**Status :** Accepted
**Date :** 2026-07-26

## Context

InkTone gère plusieurs formats de livres (EPUB, TXT, PDF à terme). Le
Reader, le moteur TTS et la recherche ne doivent pas avoir à connaître le
format d'origine d'une publication pour fonctionner.

## Decision

Tout parser de format produit la même représentation interne : le
Document Model (§7.5), indépendante du format source.

## Rationale

- Un seul moteur de lecture, de navigation et de synchronisation TTS sert
  tous les formats.
- Les couches supérieures (Reader, TTS, recherche) sont écrites une seule
  fois, contre le Document Model.

## Consequences

Chaque nouveau format supporté se limite à l'écriture d'un nouveau parser
qui produit le Document Model existant — aucune modification du Reader,
du TTS ou de la recherche.

## Alternatives Considered

- **Chemins de rendu séparés par format** : dupliquerait l'intégralité du
  Reader, du TTS et de la recherche pour chaque format — rejeté.
