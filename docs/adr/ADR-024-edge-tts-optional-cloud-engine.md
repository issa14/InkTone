# ADR-024 : Edge TTS — moteur cloud optionnel, jamais requis, jamais défaut

**Status :** Accepted
**Date :** 2026-08-14

## Context

Le Blueprint (§8.5, matrice des capacités) cite Edge TTS « pour mémoire »
comme moteur en ligne fournissant de vraies frontières de mot SSML, mais
le positionne « Optionnel, jamais requis pour l'usage quotidien » sans
ADR dédié — exactement la même incohérence que celle corrigée pour OPDS
par ADR-023 : une ligne de table, pas une décision formelle. Le domaine
est déjà préparé (`TtsEngineId.EDGE_TTS` dans l'énumération, champ
`TtsCapabilities.offline`), mais aucune implémentation n'existe.

L'implémentation legacy (`legacy/monolith`, archivée) intégrait Edge TTS
via un client WebSocket OkHttp (`service/edge/EdgeTtsClient.kt`) + un
décodeur MP3→PCM (`Mp3Decoder.kt`) sous le pattern `TtsProvider`
multi-binding. Ce code est une **référence de comportement**, pas un
patron à copier tel quel : il produit du `FloatArray` (incompatible avec
le `ByteArray` PCM16 du contrat `AudioSegment` actuel), il n'exploite pas
les frontières de mot SSML qu'Edge peut fournir, et son fallback
Edge→Piper vit dans le Repository (le projet actuel le fait au niveau
`TtsEngine`).

La nature du service est un fait à encadrer : Edge TTS est une **API non
officielle** (`wss://speech.platform.bing.com/...` avec un
`TrustedClientToken` figé, extraction reconnue du projet Python
`edge-tts`). Elle peut changer ou être bloquée sans préavis. Ce n'est pas
un défaut du moteur, c'est une contrainte de son statut — elle interdit
qu'Edge TTS soit un pilier de l'expérience de lecture.

## Decision

Edge TTS est intégré comme **moteur cloud optionnel**, selon trois règles
strictes qui le font coexister avec Offline First (ADR-003) sans le
violer :

1. **Jamais défaut, jamais requis.** Le moteur par défaut reste la chaîne
   offline existante (`FallbackTtsEngine` : Sherpa-ONNX → Android natif).
   Edge TTS n'est utilisé que si l'utilisateur l'a **explicitement
   sélectionné** (`VoiceProfile.engine == EDGE_TTS`) pour la publication
   ou le profil concerné. Aucun chemin de code ne sélectionne Edge
   implicitement, y compris en fallback.

2. **Repli automatique vers l'offline, sans interruption.** Tout échec
   réseau (`UnknownHostException`, `SocketTimeoutException`,
   `ConnectException`, `SocketException`) pendant une synthèse Edge
   déclenche un repli immédiat vers `FallbackTtsEngine` pour la phrase en
   cours et les suivantes de la même session — même sémantique que le
   repli Palier 2 → Palier 1 déjà en production (ADR-021, §8.12 « jamais
   d'interruption brutale »). Le repli est **vers l'offline**, jamais
   l'inverse : on ne bascule pas de Sherpa/Android vers Edge de sa propre
   initiative.

3. **`wordTimestamps` honnête.** Edge TTS est **capable** de fournir de
   vraies frontières de mot SSML (Blueprint §8.5), mais le client legacy
   ne les capturait pas, et le protocole exact des événements
   `Path:wordboundary` n'a **jamais été vérifié empiriquement** sur ce
   dépôt. En conséquence, la première version de l'adaptateur déclare
   `wordTimestamps = false` (surlignage honnêtement limité à la phrase,
   §8.9) tant qu'un spike n'a pas prouvé le round-trip et le parsing des
   frontières sur device réel. Toute future activation à `true` passe par
   le même standard de preuve que le CTC (voir
   `docs/execution/PROTOTYPE_ALIGNEMENT_CTC.md`) — jamais par une
   hypothèse sur le protocole.

**Placement du code :** dans `infrastructure/tts` (module existant), pas
de nouveau module. Le Blueprint §5.2 définit ce module comme
« Adaptateurs des moteurs TTS + gestion des modèles de voix » : Edge TTS
est un adaptateur de moteur TTS, et `EdgeTtsClient`/`Mp3Decoder` sont des
détails internes à cet adaptateur. Ajout de `libs.okhttp` (déjà au
catalogue, 4.12.0) aux dépendances du module — les bibliothèques
externes ne sont pas soumises à la matrice `checkArchitectureRules`, qui
ne borne que les dépendances inter-modules (`:infrastructure` → `:domain`
seul, inchangé).

**Stratégie de routage :** additive, sans refonte. `FallbackTtsEngine`
(chaîne offline binaire, testée) **n'est pas modifié**. Un nouvel
adaptateur `TtsEngine` de façade route selon `VoiceProfile.engine` :
`EDGE_TTS` → Edge avec repli vers `FallbackTtsEngine` ; tout autre
moteur → `FallbackTtsEngine` directement. Le chemin offline de production
reste donc binaire et couvert par ses tests existants
(`FallbackTtsEngineTest`, `TtsCapabilityConsistencyTest`) ; Edge est une
branche ajoutée, pas une refonte du tronc.

## Rationale

Le statut d'API non officielle interdit de faire d'Edge TTS un pilier,
mais sa valeur (voix neuronales de qualité, zéro modèle à télécharger,
zéro CPU consommé) est réelle pour qui choisit le cloud en connaissance
de cause. Les trois règles ci-dessus transforment ce choix en
**confort explicite** plutôt qu'en **dépendance silencieuse** : le pire
échec possible d'Edge (service bloqué, token révoqué) se résout par le
repli offline, exactement comme le repli Palier 2 → Palier 1 résout
l'absence de modèle vocal.

Le placement dans `infrastructure/tts` évite un nouveau module (et donc
la mise à jour de `settings.gradle.kts` + Blueprint §5.2 dans le même
commit, règle §5.3) pour un adaptateur dont la responsabilité est déjà
celle du module. Le précédent `infrastructure/opds`/`infrastructure/sync`
(modules réseau dédiés) n'est pas contredit : ces modules existent parce
qu'aucun module existant n'avait leur responsabilité ; ici, la
responsabilité « adaptateur de moteur TTS » existe déjà et accueille
naturellement un moteur de plus.

## Consequences

- `infrastructure/tts` gagne une dépendance réseau (`libs.okhttp`) — le
  module n'est plus exclusivement local. C'est un changement de nature à
  assumer et à documenter (ce que fait cet ADR), pas un accident.
- Le `TrustedClientToken` et l'endpoint Bing sont des constantes
  techniques du client, pas une surface stable du produit : toute
  évolution du service devra être traitée comme un correctif de
  l'adaptateur, sans attendre une mise à jour du domaine.
- La capacité `wordTimestamps = false` initiale impose que l'UI signale
  honnêtement la perte de surlignage mot-à-mot avec Edge (§8.10 : « le
  surlignage mot à mot n'est pas disponible avec ce moteur ») — pas de
  simulation (§8.9).
- Tests obligatoires : test JVM du routage et du repli Edge→offline
  (MockWebServer, déjà au catalogue), cohérence
  `TtsCapabilityConsistencyTest` pour l'adaptateur Edge (capabilités
  déclarées = comportement réel), et respect des garde-fous K5/K12
  (`check-no-manage-external-storage.sh`, `check-no-emoji.sh`).
- Le spike de validation des frontières de mot SSML est un **prérequis**
  à toute activation de `wordTimestamps = true` — il n'est pas une
  option du lot d'implémentation, mais une condition de la capacité.

## Alternatives Considered

- **Nouveau module `infrastructure/edgetts` dédié** : rejeté — la
  responsabilité « adaptateur de moteur TTS » existe déjà dans
  `infrastructure/tts` ; un module dédié fragmenterait la même
  responsabilité sur deux modules pour une préférence de pureté réseau,
  au prix d'une mise à jour de la table §5.2. (À reconsidérer seulement
  si un second moteur cloud non-TTS apparaissait.)
- **Refonte de `FallbackTtsEngine` en routeur N-moteurs** : rejeté pour
  ce lot — le fallback binaire offline est testé et en production ; le
  généraliser dans le même lot que l'ajout d'Edge mélangerait une refonte
  du tronc (risque de régression sur le chemin offline) avec une
  fonctionnalité additive. La façade routante proposée atteint le même
  résultat sans toucher au tronc.
- **`wordTimestamps = true` d'emblée (capacité SSML supposée)** : rejeté —
  le Blueprint §8.5 dit qu'Edge « peut » fournir des frontières, pas que
  le protocole a été vérifié sur ce dépôt ; déclarer `true` sans preuve
  violerait §8.9 (« un moteur ne fait jamais semblant ») et reproduirait
  le biais « document marqué résolu avant preuve » que §17.2 interdit.
- **Edge TTS comme moteur de repli automatique du Palier 1/2** : rejeté —
  inverserait Offline First en faisant dépendre la lecture quotidienne
  d'une API non officielle ; le repli ne va jamais de l'offline vers le
  cloud.
- **Réutiliser le contrat `SyncProvider` (Lot 11) pour Edge TTS** :
  rejeté — `SyncProvider` est un contrat d'upload/download vers un
  stockage ; Edge TTS est un moteur de synthèse, sémantique différente
  (même logique que le rejet du détournement de `SyncProvider` pour OPDS,
  ADR-023).
