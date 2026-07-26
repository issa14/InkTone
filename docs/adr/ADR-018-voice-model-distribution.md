# ADR-018 : Distribution des modèles de voix à la demande

**Status :** Accepted
**Date :** 2026-07-26

## Context

Un modèle Sherpa-ONNX pèse plusieurs dizaines de mégaoctets. L'embarquer
dans l'APK ferait exploser le budget de taille d'installation (≤ 60 Mo,
§11.2). Ne fournir aucun modèle casserait en revanche le TTS dès le
premier lancement.

## Decision

L'APK est distribué sans modèle de voix embarqué. Le téléchargement de la
voix française de référence est proposé à l'onboarding ; une fois
téléchargé, le modèle vit localement et son usage est intégralement hors
ligne. L'empreinte du téléchargement est vérifiée, et la gestion des
modèles est disponible dans les réglages (§8.11).

## Rationale

Concilier un APK léger avec le respect d'Offline by Default : après un
téléchargement initial unique, l'usage redevient entièrement local.

## Consequences

Le premier lancement comporte une étape réseau optionnelle pour activer
le TTS. La lecture visuelle silencieuse reste pleinement fonctionnelle
sans ce téléchargement.

## Alternatives Considered

- **Modèles embarqués dans l'APK** : rejeté, dépasse le budget de taille
  d'installation.
- **Play Feature Delivery** : non retenu en v1, mais noté comme piste à
  réévaluer si la friction du téléchargement direct s'avère excessive en
  usage réel.
