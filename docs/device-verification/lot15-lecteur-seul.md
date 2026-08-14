# Vérification Device — Lecteur gapless isolé (Lot 15, Tâche 2.2)

**Date** : 2026-08-14
**Branche** : `lot-15-gapless` (Palier 2, `d54a222`)
**Appareil cible** : Snapdragon 680 (V2206), Android 14
**Harnais** : `GaplessAudioPlayerAuditionTest` (instrumenté)

> **But** : vérifier **à l'oreille** le lecteur gapless isolé (non encore
> branché à l'UI — ce sera la Tâche 4.1), avant d'écrire l'ordonnanceur
> (Palier 3). Le test instrumenté de stress (Tâche 2.1) a déjà prouvé
> l'absence de SIGSEGV ; ici on juge la **qualité audio perceptible**.
>
> Lancer uniquement l'audition (sinon la suite de stress joue en même temps) :
> ```
> ./gradlew :infrastructure:media:connectedDebugAndroidTest \
>   -Pandroid.testInstrumentationRunnerArguments.class=com.inktone.infrastructure.media.GaplessAudioPlayerAuditionTest
> ```
> La séquence audible est détaillée dans le KDoc du harnais (phases 1 à 5).

## Séquence audible (logcat tag `GaplessAudition`)

### Phase 1 — Sherpa 22 050 Hz : 3 tons enchaînés (440/554/659 Hz)

- [x] Les 3 tons s'enchaînent **sans silence** entre eux (gapless)
- [x] Aucun craquement / clic au raccord entre deux tons
- Obs. : ___

### Phase 2 — Pause puis reprise

- [x] La pause coupe le son **immédiatement**
- [x] La reprise repart sans artefact ni clic
- Obs. : Reprise au début de la phrase comme attendu

### Phase 3 — Volume réduit (~50 %)

- [x] Le ton suivant est **nettement plus faible**
- Obs. : ___

### Phase 4 — Edge 24 kHz : 3 tons enchaînés (523/659/784 Hz)

- [x] Les 3 tons s'enchaînent **sans silence** (gapless à 24 kHz)
- [x] Hauteur correcte (24 kHz ≠ 22 050 Hz : pas de lecture accélérée/ralentie)
- Obs. : ___

### Phase 5 — Stop propre

- [x] Le son s'arrête **immédiatement** et définitivement
- Obs. : ___

## Verdict daté

- Date de capture : 14/08/2026
- Signataire : signé
- [x] Lecteur isolé validé — l'ordonnanceur (Palier 3, Tâche 3.1) peut démarrer.
- [ ] Écart signalé (détailler) : ___
- Remarques globales : ___
