# ADR-003 : Offline First

**Status :** Accepted
**Date :** 2026-07-26

## Context

InkTone est un produit de lecture destiné à un usage quotidien, y compris
sans réseau — trajets, transports souterrains, avion, zones mal couvertes.
Le marché cible ne garantit pas une connectivité permanente.

## Decision

Toutes les fonctions essentielles (lecture, navigation, TTS avec voix déjà
téléchargée, annotations, bibliothèque) fonctionnent intégralement hors
ligne. Le réseau reste optionnel : synchronisation, voix cloud éventuelles,
téléchargement ponctuel de modèles de voix.

## Rationale

- Disponibilité : le produit fonctionne partout, indépendamment du réseau.
- Vie privée : moins de dépendance à des services distants signifie moins
  de surface d'exposition des données de lecture.
- Expérience utilisateur cohérente, sans dégradation silencieuse en
  l'absence de réseau.

## Consequences

La synchronisation (§9) est conçue comme une réconciliation différée,
jamais comme une dépendance bloquante. Les modèles de voix se téléchargent
une fois puis vivent localement, intégralement utilisables hors ligne
ensuite.

## Alternatives Considered

- **Cloud-first** : simplifie certains aspects de synchronisation, mais
  contraire à la propriété locale des données (ADR-007) et inadapté au
  contexte d'usage cible — rejeté.
