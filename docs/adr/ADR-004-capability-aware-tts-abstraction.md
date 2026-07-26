# ADR-004 : Capability-Aware TTS Abstraction

**Status :** Accepted
**Date :** 2026-07-26

## Context

Plusieurs moteurs TTS aux capacités inégales doivent coexister derrière
une même interface (Sherpa-ONNX, Piper, Edge TTS, moteurs futurs). Le
risque d'une abstraction naïve — une interface minimale commune à tous
les moteurs — est de niveler par le bas et de perdre la fonctionnalité
signature du produit : le surlignage mot-à-mot sur timestamps réels, que
seuls certains moteurs fournissent nativement.

## Decision

InkTone définit une interface commune `TtsEngine` accompagnée d'un
contrat `TtsCapabilities` déclaratif (§8.4) : chaque moteur annonce
explicitement ce qu'il sait faire (timestamps mot, langues supportées,
fonctionnement hors ligne, etc.), plutôt que d'exposer une surface
réduite au plus petit dénominateur commun.

## Rationale

- Interchangeabilité des moteurs sans sacrifier les capacités avancées.
- L'UI peut adapter honnêtement ses fonctionnalités selon le moteur actif
  au lieu de mentir sur une capacité absente ou de la simuler.

## Consequences

L'UI porte une logique de branchement conditionnel sur les capacités
déclarées (surlignage mot si `wordTimestamps`, sinon surlignage phrase).
C'est un coût assumé en échange de l'honnêteté fonctionnelle.

## Alternatives Considered

- **Interface minimale commune à tous les moteurs** : rejetée, elle
  effacerait l'avantage compétitif du surlignage mot-à-mot.
- **Sherpa-ONNX codé en dur, sans abstraction** : rejetée, elle fermerait
  la porte à Edge TTS et aux moteurs futurs.
