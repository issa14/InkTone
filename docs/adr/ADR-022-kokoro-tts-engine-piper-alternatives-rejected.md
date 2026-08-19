# ADR-022 : Moteur de synthèse neuronale du Palier 2 — Kokoro retenu, alternatives Piper/VITS écartées

**Status : Accepted** · **Date : 2026-07-28**

> **Note (ADR-026, 2026-08-20)** — la prémisse « application commerciale à
> code source fermé » invoquée ici n'est plus vraie : le code est publié sous
> licence MIT. La décision de cet ADR reste valide pour des raisons
> indépendantes de cette prémisse — voir
> [ADR-026](ADR-026-licence-mit-ouverture-du-code.md).

**Context :** ADR-021 a posé l'architecture à paliers (Palier 1 Android natif, Palier 2 neuronal + alignement CTC). Trois candidats ont été évalués pour le Palier 2, chacun mesuré sur trois axes — latence réelle (device V2206, Snapdragon 680), qualité vocale (écoute humaine, référence 8/10 déjà établie), et licence (vérifiée à la source primaire, pas supposée) :

| Candidat | RTF mesuré | Qualité | Licence |
|---|---|---|---|
| **Kokoro** (`ff_siwis`, via Sherpa-ONNX) | ~4,7–7× (CPU+4 threads ; NNAPI testé et contre-productif, ~5,4× ; XNNPACK testé et contre-productif, ~5,2×) → **~4,7×** meilleure configuration mesurée (CPU+4 threads) | 8/10 (écoute humaine confirmée) | **Apache-2.0** — propre |
| `vits-piper-fr_FR-upmc-medium` | 0,331× | Bonne (écoute humaine confirmée) | CC-BY-SA 4.0 sur le dataset UPMC, **empilé sur une restriction non-commerciale explicite** de la voix de base `lessac` (données Blizzard Challenge 2013, licence confirmée à la source primaire — CSTR, University of Edinburgh : *« released under a license for non-commercial use only »*) |
| `vits-piper-fr_FR-mls-medium` | 0,375× | Mauvaise (écoute humaine sur 7 échantillons : *« aucun totalement intelligible, chuchotement asthmatique et murmures inaudibles »*) | CC-BY 4.0 pur (Multilingual LibriSpeech / OpenSLR-94, entraîné from scratch, aucune chaîne de provenance cachée) |

InkTone est gratuite, soutenue par un don volontaire optionnel (pas d'achat obligatoire, pas de fonctionnalité payante), cohérent avec la mission d'accessibilité universelle du projet (Blueprint §1.4). Un don volontaire, même symbolique, constitue une compensation monétaire au sens des définitions usuelles de « non-commercial » (Creative Commons notamment) — il ne sécurise donc pas l'usage d'une ressource restreinte à un usage non-commercial.

**Decision :** Kokoro (voix `ff_siwis`) est le moteur de synthèse retenu pour le Palier 2. `upmc-medium` et `mls-medium` sont écartés tous les deux, chacun pour une raison disqualifiante distincte — pas un compromis entre les deux.

**Rationale :** aucun des trois candidats n'est parfait sur les trois axes simultanément. Kokoro est le seul sans risque disqualifiant sur la licence ni sur la qualité — le seul axe où il est en retrait (latence) est un problème d'ingénierie qu'on peut atténuer, contrairement à une restriction de licence sur les données d'entraînement, qu'aucun changement de code ne peut lever. Pour un projet dont la mission repose explicitement sur l'accessibilité à tous, construire la fonctionnalité signature sur une fondation juridiquement fragile aurait été incohérent avec l'objectif du projet lui-même, indépendamment du risque légal en tant que tel.

**Consequences :**

Le budget §11.2 (tap → premier audio ≤ 1500 ms) n'est pas tenu par le Palier 2 tel que mesuré (~4,7×, CPU+4 threads, meilleure configuration mesurée), même après six leviers de configuration/matériel épuisés et vérifiés empiriquement (threads, NNAPI compilé et testé, authenticité int8 confirmée par inspection du graphe, G2P isolé et jugé négligeable, granularité réelle du streaming mesurée, proportionnalité du premier segment confirmée, et XNNPACK compilé depuis les sources et testé — RTF mesuré ~5,2×, lui aussi plus lent que CPU).

**Atténuation produit retenue** : le Palier 1 (Android natif, ~179 ms mesuré) reste le filet de sécurité automatique (`FallbackTtsEngine`, déjà en production) pour toute situation sensible à la latence. Pour la première phrase d'une session avec le Palier 2, la lecture *visuelle* du texte reste instantanée (déjà parsée et affichée) pendant que la synthèse audio rattrape en arrière-plan avec un indicateur de chargement — reformulation du budget pour ce mode précis plutôt que renoncement au principe de réactivité. Le streaming par segment (déjà prototypé : callback à 2 segments, premier segment mesurable indépendamment) reste une optimisation future si cette atténuation s'avère insuffisante à l'usage réel.

**Alternatives Considered :** `upmc-medium` (rejeté — licence disqualifiante, confirmée à la source primaire) ; `mls-medium` (rejeté — qualité vocale insuffisante, confirmée par écoute humaine sur 7 échantillons) ; entraînement d'un modèle VITS léger français from scratch sur données propres (non exploré — option future si l'atténuation produit s'avère insuffisante, hors du périmètre raisonnable d'un projet solo pour la v1).
