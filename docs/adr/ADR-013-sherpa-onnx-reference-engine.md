# ADR-013 : Sherpa-ONNX moteur de référence, timestamps mot exigence de première classe

**Status :** Superseded by ADR-021 (2026-07-26)
**Date :** 2026-07-26

> **Note de révision :** la prémisse de cet ADR — « Sherpa-ONNX fournit
> des timestamps natifs » — s'est révélée fausse à la vérification
> empirique en Phase 3 (inspection directe des bindings Kotlin et
> Python). `GeneratedAudio` n'expose que `samples`/`sample_rate`, pour
> tous les modèles supportés. Sherpa-ONNX reste le moteur de synthèse de
> référence pour la **qualité vocale** ; la question des timestamps mot
> est traitée séparément par [ADR-021](ADR-021-tts-word-timing-tiered-architecture.md).
> Contenu original conservé ci-dessous pour l'historique — ne pas le
> lire comme la décision en vigueur.

## Context (original)

Le surlignage mot-à-mot avec de vrais timestamps est l'écart compétitif
n°1 identifié face aux lecteurs top-tier du marché. Sherpa-ONNX fournit
des timestamps natifs par mot ; Piper n'en fournit pas — un pivot non
documenté vers Piper a déjà été flagué et corrigé dans l'historique du
projet. Le legacy simulait le surlignage par interpolation proportionnelle
au nombre de caractères, une approche jugée malhonnête envers
l'utilisateur.

## Decision

Sherpa-ONNX est le moteur TTS de référence d'InkTone. `wordTimestamps`
est une capability de premier rang du contrat `TtsCapabilities` (§8.4) ;
le surlignage mot-à-mot n'est jamais simulé ou interpolé (§8.9).

## Rationale

La fonctionnalité signature du produit doit reposer sur des données
réelles produites par le moteur TTS, jamais sur une illusion visuelle qui
donnerait l'impression d'une précision qui n'existe pas.

## Consequences

Les moteurs qui ne fournissent pas de timestamps mot offrent un
surlignage au niveau de la phrase, honnêtement annoncé comme tel dans
l'UI. Le critère de précision ±120 ms entre dans les benchmarks de
référence (§14.7).

## Alternatives Considered

- **Interpolation par nombre de caractères** : rejetée, trompeuse envers
  l'utilisateur car elle simule une précision inexistante.
- **Exiger des timestamps mot de tous les moteurs** : rejeté, cela
  exclurait inutilement Piper et Edge TTS du produit alors qu'ils restent
  utiles avec un surlignage phrase honnête.
