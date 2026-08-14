# Lot 14 — Edge TTS (moteur cloud optionnel)

**Base :** `main`. Références : `ADR-024` (périmètre Edge TTS encadré, ce lot),
`ADR-004` (abstraction TTS capability-aware), `ADR-021` (architecture à paliers),
`ADR-022` (Kokoro retenu, Piper écarté), `ADR-003` (Offline First),
Blueprint §8 (TTS Engine, en particulier §8.4 capacités, §8.5 matrice, §8.9
synchronisation mot, §8.10 sélection du moteur), `CLAUDE.md` §13.5 (legacy =
référence de comportement, jamais copié tel quel). Implémentation legacy de
référence : `legacy/monolith` → `app/src/main/java/com/inktone/service/edge/`
(`EdgeTtsClient.kt`, `Mp3Decoder.kt`) et `domain/provider/EdgeTtsProvider.kt`.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu
vérifier, la clôture se fait sur appareil.

## Écarts délibérés par rapport au legacy

Le code legacy est une référence de comportement, pas un patron à copier.
Cinq écarts sont actés — à ne pas « corriger en sens inverse » en exécution :

1. **PCM16 `ByteArray`, pas `FloatArray`.** Le legacy décode le MP3 en
   `FloatArray` normalisé [-1.0, 1.0] (`Mp3Decoder` → `SynthesisResult.samples`).
   Le contrat actuel `AudioSegment.audioData` est du `ByteArray` PCM16 signé
   little-endian (voir `SherpaOnnxTtsEngine`, KDoc « PCM16 signé, little-endian »).
   `Mp3Decoder` sortira donc directement le `ByteArray` PCM16 (le `ShortArray`
   intermédiaire de `MediaCodec` est déjà du PCM16 — une copie little-endian
   via `ByteBuffer`, pas une normalisation). Aucune conversion Float→Short
   en aval.
2. **`wordTimestamps` non supposé.** Le Blueprint §8.5 dit qu'Edge « peut »
   fournir des frontières de mot SSML ; le legacy ne les capturait pas
   (`wordBoundaryEnabled: false`). Déclarer `true` sans preuve violerait
   §8.9. Le Palier 1 (spike) tranche ; en attendant, `wordTimestamps = false`
   (surlignage honnêtement limité à la phrase).
3. **Routage par façade additive, pas de refonte du tronc.** Le legacy
   routait Edge→Piper dans le Repository. Ici, `FallbackTtsEngine` (chaîne
   offline binaire, testée) n'est pas modifié ; une façade `SelectiveTtsEngine`
   ajoute la branche Edge. Le chemin offline de production reste couvert par
   ses tests existants.
4. **`TrustedClientToken` est une constante technique, pas un secret.** Le
   token (`6A5AA1D4EAFF4E9FB37E23D68491D6F4`) est public dans le projet
   Python `edge-tts`. Il reste une constante dans le client, documentée
   comme telle — pas de passage par `local.properties`/`BuildConfig` (réservé
   aux vrais secrets, cf. `infrastructure/sync` pour OAuth).
5. **Piper reste écarté** (ADR-021/022). Le sélecteur de moteur affiche
   aujourd'hui `PIPER` sans implémentation — écart déclaré, traité au Palier 4
   (masquage/retrait de l'option), pas réintroduit ici.

## Décisions actées

1. **Placement : `infrastructure/tts` existant, pas de nouveau module.**
   Le Blueprint §5.2 définit ce module comme « Adaptateurs des moteurs TTS +
   gestion des modèles de voix » : Edge TTS est un adaptateur de moteur.
   Ajout de `libs.okhttp` + `libs.okhttp.mockwebserver` (déjà au catalogue,
   4.12.0) aux dépendances du module. La matrice `checkArchitectureRules`
   reste inchangée (`:infrastructure` → `:domain` seul ; les bibliothèques
   externes ne sont pas bornées).
2. **Capacités déclarées honnêtement.** `EdgeTtsEngine.capabilities` :
   `offline = false`, `wordTimestamps = selon verdict du Palier 1` (faux par
   défaut), `sentenceTimestamps = true`, `languages = ["fr"]`,
   `streamingSynthesis = false` (synthèse phrase par phrase, pas de flux),
   `speedControl = true` (taux SSML), `pitchControl = false` (le client v1
   fige `pitch="+0Hz"`, cf. legacy), `modelSizeMb = 0` (aucun modèle local),
   `license = "Microsoft (API non officielle, edge-tts)"`.
3. **Routage par `VoiceProfile.engine`, jamais par une seconde source.**
   La façade `SelectiveTtsEngine` route sur le `engine` du `VoiceProfile`
   effectivement passé à `synthesize(sentence, voiceProfile)` — c'est la
   seule information de moteur qui circule jusqu'au contrat `TtsEngine`,
   et c'est cohérent avec la règle de précédence du Blueprint §3.3
   (surcharge de publication > préférences globales). Le `id`/`capabilities`
   exposés par la façade reflètent toujours le moteur réellement actif
   (même discipline que `FallbackTtsEngine`).
4. **Repli Edge → offline, jamais l'inverse.** Toute erreur réseau
   (`UnknownHostException`, `SocketTimeoutException`, `ConnectException`,
   `SocketException`) pendant une synthèse Edge déclenche un repli immédiat
   vers `FallbackTtsEngine` pour la phrase en cours et les suivantes de la
   même session — sémantique identique au repli Palier 2 → Palier 1
   (ADR-021, §8.12). Le repli ne va jamais de l'offline vers le cloud, et
   Edge n'est jamais choisi implicitement (ADR-024, règle 1).
5. **Erreurs permanentes ≠ erreurs transitoires.** Un échec non réseau
   (handshake rejeté, 403, token révoqué, SSML mal formé) est remonté
   immédiatement, sans retry, et **ne** déclenche **pas** le repli offline
   silencieux : c'est une erreur réelle à signaler, pas une panne de
   connectivité à masquer. Le retry (3 tentatives, backoff exponentiel
   500 ms → 1000 ms) ne s'applique qu'aux erreurs transitoires (même
   classification `isNetworkError` que le legacy).
6. **Voix : deux voix françaises, bornées.** `fr-FR-VivienneNeural`
   (défaut), `fr-FR-HenriNeural`. Toute `VoiceProfile.voice` hors de cette
   liste retombe sur la voix par défaut (jamais une voix invalide envoyée
   au serveur). Le mapping affichable suit `ReaderTtsPanel` : « Vivienne » /
   « Henri », pas l'identifiant brut.
7. **Aucun emoji** dans le code de production (K12) ; icônes via `AppIcons`.
   Pas d'ajout d'icône ad hoc hors du registre `core/designsystem`.
8. **Tests par couche.** `EdgeTtsClient` (protocole WebSocket, auth, retry)
   est testable en JVM pur via `MockWebServer` (le module active déjà
   `unitTests.isReturnDefaultValues`). `Mp3Decoder` (MediaCodec) est testé
   en instrumenté (`androidTest`) sur fixture MP3 réelle. La cohérence
   capacité ↔ comportement rejoint `TtsCapabilityConsistencyTest`.

## Défaut préalable à corriger (hors code, à signaler)

Le sélecteur de moteur (`SettingsScreen`, `PickerDialog` sur
`TtsEngineId.entries`) expose déjà `EDGE_TTS` **et** `PIPER` sans
implémentation derrière : `FallbackTtsEngine` ignore `VoiceProfile.engine` et
tente toujours Sherpa → Android. Sélectionner Edge ou Piper aujourd'hui ne
change donc rien à la lecture — c'est un état d'UI trompeur au regard de
§8.9 (« un moteur ne fait jamais semblant »). Ce lot corrige Edge (Palier 3-4)
et masque Piper (Palier 4) ; le défaut est documenté ici, pas réparé
silencieusement hors périmètre.

**Défaut préalable n°2 — divergence `defaultTtsEngine` ↔ `VoiceProfile.engine`.**
Vérifié dans le code réel (`SettingsViewModel`, ligne ~99) :
`SetDefaultTtsEngine` ne met à jour que `preferences.defaultTtsEngine`, sans
créer ni mettre à jour le `VoiceProfile` actif. Or `resolveVoiceProfile`
(`ReaderViewModel`, ligne ~350) résout le profil depuis `activeVoiceProfileId`,
avec repli sur `DEFAULT_VOICE_PROFILE` (`engine = ANDROID_NATIVE`) si absent.
Comme la façade `SelectiveTtsEngine` routera sur `voiceProfile.engine` (décision
actée 3), sélectionner Edge dans les Réglages **ne routerait jamais vers Edge**
tant qu'aucun `VoiceProfile.engine == EDGE_TTS` n'est actif. Ce n'est pas un
simple défaut d'affichage : c'est la condition même du routage. Le correctif
est un prérequis de la Tâche 4.1 (synchroniser le profil actif au changement
de moteur), pas une optimisation cosmétique — à traiter avant la vérification
device de bout en bout (Tâche 4.3).

---

# PALIER 0 — Gouvernance (préalable, bloquant)

## Tâche 0.1 — Faire accepter ADR-024

- `ADR-024` est rédigé en `Status : Proposed`. L'acceptation (Proposed →
  Accepted) est une décision **produit** — réintégrer un moteur en ligne
  dans un produit offline-first — pas une décision que Claude Code tranche
  seul (même gate que ADR-023 au Lot 13).
- Tant que l'ADR n'est pas accepté, ne pas commencer les Paliers 1-4.

`Ajoute ADR-024 (Edge TTS moteur cloud optionnel)`

## Tâche 0.2 — Vérifier l'absence de référence fantôme

- Confirmer qu'aucun document d'exécution ne référence un « ADR différant
  Edge TTS » inexistant (leçon ADR-023 : la correction de référence fantôme
  doit se faire dans le lot, pas après).
- Vérifié à ce jour : les mentions d'Edge TTS dans ADR-004/013/021 et le
  Blueprint §8.5 sont cohérentes avec ADR-024 (aucune correction nécessaire).
  À re-confirmer une seule fois en début d'exécution, sans modification si
  rien n'a changé.

`Vérifie les références Edge TTS dans les documents`

---

# PALIER 1 — Spike protocole WebSocket (dérisquage, bloquant pour la capacité)

Le Palier 1 répond à **deux** questions avant tout code de production :
(a) le protocole WebSocket Bing fonctionne-t-il depuis Android (round-trip
complet config + SSML → MP3 → PCM audible) ; (b) les frontières de mot SSML
(`wordBoundaryEnabled: true`) sont-elles réellement extractibles ? La réponse
(b) fige la valeur de `wordTimestamps` pour toute la suite du lot.

## Tâche 1.1 — Rédiger le protocole du spike

- Nouveau document `docs/execution/SPIKE_EDGE_TTS_WEBSOCKET.md`, calqué sur
  la discipline de `PROTOTYPE_ALIGNEMENT_CTC.md` (§1-9, preuves par logs
  device, jamais de conclusion sans capture réelle).
- Contenu : objectif, conditions (device réel + réseau), protocole exact
  (URL, en-têtes, trames config/SSML), critères de preuve du round-trip
  (nombre d'octets MP3 reçus, PCM décodé non vide, durée cohérente), et
  protocole dédié aux word boundaries (activer `wordBoundaryEnabled: true`,
  capturer les trames `Path:wordboundary`, en extraire offset/durée/texte).
- Le spike peut être un test instrumenté `androidTest` dans
  `infrastructure/tts` (même famille que les spikes existants du module) ou
  un scratchpad documenté — trancher ici, pas en cours de route.

`Ajoute le protocole du spike WebSocket Edge TTS`

## Tâche 1.2 — Prouver le round-trip sur device

- Connexion WebSocket OkHttp brute à l'endpoint Bing, envoi de la trame
  `speech.config` puis du SSML, collecte des chunks binaires MP3, fermeture
  sur `Path:turn.end`.
- Décodage MP3 → PCM via `MediaCodec`/`MediaExtractor` (pipeline minimal,
  pas encore factorisé en `Mp3Decoder` de production).
- Preuve exigée : log du nombre de chunks, octets MP3 totaux, échantillons
  PCM décodés, durée audio calculée — sur **device réel**, pas en émulateur
  ni par inférence.

`Prouve le round-trip WebSocket Edge TTS sur device`

## Tâche 1.3 — Trancher la capacité word-boundaries

- Reprendre le round-trip avec `wordBoundaryEnabled: true`, capturer et
  parser les trames `Path:wordboundary`.
- Verdict écrit dans `SPIKE_EDGE_TTS_WEBSOCKET.md` :
  - **Extractibles** → `wordTimestamps` cible = `true`, et le Palier 3
    implémentera le parsing + remplissage de `AudioSegment.wordTimestamps`
    (remappés sur le texte affiché via `PronunciationRuleApplier`, même
    mécanique que `SherpaOnnxTtsEngine`).
  - **Non extractibles / non fiables** → `wordTimestamps` = `false` confirmé,
    écart consigné (surlignage limité à la phrase, §8.9).
- Aucune valeur supposée : le verdict découle exclusivement des captures
  device du Palier 1.

`Consigne le verdict word-boundaries Edge TTS`

---

# PALIER 2 — Client Edge TTS (infrastructure/tts)

Le Palier 2 livre les deux composants techniques du client, sans aucune
exposition au domaine au-delà de ce que le Palier 3 consommera. Aucune
classe de ce palier n'implémente `TtsEngine`.

## Tâche 2.1 — Ajouter OkHttp au module tts

- `infrastructure/tts/build.gradle.kts` : `implementation(libs.okhttp)` et
  `testImplementation(libs.okhttp.mockwebserver)`.
- Justifier le choix OkHttp par commentaire (le projet n'embarque ni
  Retrofit ni Ktor ; `infrastructure/sync` et `infrastructure/opds`
  construisent déjà leurs clients sur `OkHttpClient` brut — même sobriété).
- Ne rien coder d'autre dans ce commit : la dépendance arrive avec le
  premier fichier qui l'utilise (Tâche 2.2), pas seule.

`Ajoute OkHttp au module tts`

## Tâche 2.2 — `EdgeTtsClient` (WebSocket, auth, retry)

Fichier `infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/EdgeTtsClient.kt`.

- Portage du protocole validé au Palier 1 dans une classe `@Singleton`
  injectable, **sans** dépendance Compose/Room (module `infrastructure`).
- Auth `Sec-MS-GEC` : SHA-256(`{ticks}{TrustedClientToken}`) uppercase, avec
  `ticks` = timestamp Windows FILETIME arrondi à la tranche de 5 minutes —
  porté tel quel du legacy (algorithme déjà éprouvé, pas à réinventer).
  `TrustedClientToken` en constante documentée (décision actée 4).
- Trames config + SSML identiques au protocole validé ; SSML avec échappement
  XML (`&`, `<`, `>`, `"`, `'`) et `prosody rate` dérivé de la vitesse.
- Retry 3 tentatives sur erreurs **transitoires** uniquement
  (`isNetworkError`), backoff exponentiel ; erreurs permanentes remontées
  immédiatement (décision actée 5).
- `suspend fun synthesize(text, voiceName, speed): ByteArray` (MP3 brut) —
  le client retourne les octets MP3, le décodage reste la responsabilité de
  `Mp3Decoder` (séparation testable).
- Sortie réseau : `withContext(Dispatchers.IO)`, `CompletableDeferred` +
  `WebSocketListener`, timeout de synthèse (15 s par défaut, comme le legacy).

`Ajoute EdgeTtsClient (WebSocket, auth, retry)`

## Tâche 2.3 — `Mp3Decoder` (MP3 → PCM16)

Fichier `infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/Mp3Decoder.kt`.

- Pipeline `MediaExtractor` + `MediaCodec` → `ShortArray` PCM16, puis copie
  `ByteArray` little-endian via `ByteBuffer` (décision actée 1 — pas de
  `FloatArray`, pas de normalisation).
- Retourner `(audioData: ByteArray, sampleRate: Int)` ; le fichier temporaire
  MP3 (cache dir, supprimé en `finally`) est conservé comme dans le legacy —
  `MediaExtractor` exige une source fichier, acceptable pour des phrases de
  quelques centaines de Ko ; à documenter, pas à optimiser prématurément.
- Garder la boucle de décodage correcte du legacy (pattern `outputDone`,
  jamais une boucle infinie sur `outputBuffers.isNotEmpty()`).

`Ajoute Mp3Decoder (MP3 vers PCM16)`

## Tâche 2.4 — Tests du client

- **JVM (`src/test`)**, `MockWebServer` :
  - `EdgeTtsClientTest` : handshake WebSocket (upgrade accepté), envoi des
    deux trames (config puis SSML) dans l'ordre, collecte des chunks binaires,
    complétion sur `turn.end`, timeout (réponse jamais close → exception),
    retry sur erreur transitoire, **pas** de retry sur erreur permanente.
  - Test pur de `generateSecMsGec` (valeur déterministe pour un ticks connu)
    et de `buildSsml` (échappement XML, taux de vitesse positif/négatif).
  - `isNetworkError` : classification par type d'exception, pas par message.
- **Instrumenté (`androidTest`)**, fixture MP3 réelle :
  - `Mp3DecoderTest` : décodage d'un MP3 connu → PCM16 non vide, sampleRate
    conforme, aucune exception sur flux vide (échec clair, pas de crash).
- Aucun test réseau réel en CI : le round-trip est prouvé au Palier 1 sur
  device, le Palier 2 ne rejoue que des comportements sur MockWebServer.

`Ajoute les tests du client Edge TTS`

---

# PALIER 3 — Adaptateur TtsEngine et routage (infrastructure/tts)

Le Palier 3 branche le client au contrat `TtsEngine` du domaine et ajoute la
façade de routage. Aucun changement de domaine : `TtsEngineId.EDGE_TTS`,
`TtsCapabilities.offline` et `VoiceProfile.engine` existent déjà.

## Tâche 3.1 — `EdgeTtsEngine` (implémente `TtsEngine`)

Fichier `infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/EdgeTtsEngine.kt`.

- `@Singleton`, dépend de `EdgeTtsClient` + `Mp3Decoder` + `PronunciationRuleApplier`.
- `id = TtsEngineId.EDGE_TTS` ; `capabilities` conformes à la décision actée 2
  (dont `wordTimestamps` = verdict du Palier 1).
- `synthesize(sentence, voiceProfile)` :
  1. Applique les règles de prononciation (`PronunciationRuleApplier.apply`),
     même point d'intégration que les deux autres moteurs (reste réversible,
     Tâche 8.3) ;
  2. Résout la voix (`voiceProfile.voice` si dans les deux voix autorisées,
     sinon défaut — décision actée 6) ;
  3. `EdgeTtsClient.synthesize(...)` → MP3 → `Mp3Decoder.decode(...)` →
     `AudioSegment(audioData, durationMs, wordTimestamps, sampleRate)`.
- `durationMs` calculée depuis `audioData.size / sampleRate` (PCM16 mono :
  `(octets / 2) / sampleRate * 1000`), jamais supposée.
- `wordTimestamps` : `emptyList()` si verdict faux ; sinon rempli depuis les
  frontières parsées (remappées sur le texte affiché via `remapToOriginal`).
- `observePlaybackEvents()` : flux d'événements phrase uniquement
  (`SentenceStarted`/`SentenceCompleted`) tant que `wordTimestamps` est faux —
  cohérent avec §8.9 (jamais d'événements mot inventés).

`Ajoute EdgeTtsEngine (TtsEngine, capacités honnêtes)`

## Tâche 3.2 — `SelectiveTtsEngine` (façade de routage)

Fichier `infrastructure/tts/src/main/kotlin/com/inktone/infrastructure/tts/SelectiveTtsEngine.kt`.

- `@Singleton`, dépend de `EdgeTtsEngine` + `FallbackTtsEngine` (inchangé).
- Routage sur `voiceProfile.engine` :
  - `EDGE_TTS` → tente `EdgeTtsEngine` ; sur erreur **réseau** transitoire,
    bascule vers `FallbackTtsEngine` pour la phrase en cours et les suivantes
    de la même session (état `@Volatile`, même pattern que `FallbackTtsEngine`) ;
  - tout autre moteur (`SHERPA_ONNX`, `ANDROID_NATIVE`) → `FallbackTtsEngine`
    directement (le tronc offline binaire reste la référence).
- `id`/`capabilities` reflètent le moteur réellement actif (décision actée 3) ;
  `observePlaybackEvents()` délègue au moteur actif.
- Aucune logique de sélection de moteur par défaut ici : la façade ne choisit
  jamais Edge d'elle-même (ADR-024, règle 1).

`Ajoute SelectiveTtsEngine (routage par moteur)`

## Tâche 3.3 — Câblage DI

- `infrastructure/tts/.../di/TtsModule.kt` :
  - `bindTtsEngine(impl: SelectiveTtsEngine): TtsEngine` remplace le binding
    principal `FallbackTtsEngine` (le contrat `TtsEngine` injecté par le
    Reader/Player devient la façade) ;
  - `FallbackTtsEngine` reste fourni tel quel (déjà `@Singleton` via son
    constructeur, aucun binding `@Binds` dédié supplémentaire nécessaire s'il
    est directement constructible — à vérifier contre le graphe Hilt réel) ;
  - `EdgeTtsEngine` fourni (`@Binds` ou constructeur `@Inject`, selon le
    graphe existant).
- Aucun nouveau qualifier (`@Palier*`) nécessaire : la façade reçoit les
  moteurs par leurs types concrets.
- Le commentaire de `ReaderViewModel` (« injecte AndroidNativeTtsEngine
  (Palier 1) ») est **obsolète** — le corriger dans ce commit (il pointe déjà
  vers `FallbackTtsEngine` en réalité, désormais `SelectiveTtsEngine`).

`Câble Edge TTS dans la DI`

## Tâche 3.4 — Tests de routage et de repli

- **JVM (`src/test`)** :
  - `SelectiveTtsEngineTest` (fakes `TtsEngine`, comme `FallbackTtsEngineTest`) :
    routage `EDGE_TTS` → Edge, routage `SHERPA_ONNX`/`ANDROID_NATIVE` → offline,
    repli Edge→offline sur erreur réseau, **pas** de repli sur erreur
    permanente, `id`/`capabilities` reflétant le moteur actif après repli.
  - Non-régression : `FallbackTtsEngineTest` existant reste vert, inchangé.
- **`TtsCapabilityConsistencyTest`** (instrumenté) : ajouter `EdgeTtsEngine`
  — ses capacités déclarées correspondent à son comportement réel (hors
  réseau : `offline = false` vérifié, `wordTimestamps` cohérent avec le
  verdict du Palier 1).
- Test dédié que la façade ne sélectionne **jamais** Edge sans
  `voiceProfile.engine == EDGE_TTS` (garde-fou ADR-024, règle 1).

`Ajoute les tests de routage et de repli Edge TTS`

---

# PALIER 4 — Durcissement, UI et vérification device

## Tâche 4.1 — Sélecteur de moteur honnête

- `SettingsScreen` (`PickerDialog` « Moteur TTS ») : libellés lisibles au lieu
  de `TtsEngineId.name` brut — « Sherpa-ONNX (Kokoro) », « Voix système »,
  « Edge (cloud) » (mapping déjà partiel dans `ReaderTtsPanel`).
- **Signaler les capacités perdues/gagnées** au changement (Blueprint §8.10) :
  sélectionner Edge affiche un avertissement explicite « Surlignage mot à mot
  indisponible avec ce moteur » (ou « disponible » selon le verdict du
  Palier 1) et « Nécessite une connexion » — jamais un changement silencieux.
- **Masquer `PIPER`** de la liste (ADR-021/022 : moteur écarté) — corriger le
  défaut préalable documenté, sans le réintroduire.
- **Synchroniser le profil actif au changement de moteur (défaut préalable
  n°2).** `SetDefaultTtsEngine` doit, dans la même opération, créer ou mettre
  à jour le `VoiceProfile` actif avec `engine = moteur sélectionné` (même
  mécanique que `updateActiveVoiceProfile` : s'il n'existe pas de profil
  actif, en créer un avec le moteur choisi ; s'il existe, copier avec le
  nouveau `engine`). Sans cela, `resolveVoiceProfile` continue de rendre
  l'ancien profil (ou le repli `ANDROID_NATIVE`) et la façade ne route jamais
  vers Edge — défaut à corriger **avant** la vérification device de bout en
  bout, pas découvert en debug.
- Vérifier la chaîne complète : sélectionner Edge dans les Réglages produit
  un `VoiceProfile.engine == EDGE_TTS` actif, qui atteint bien la façade de
  routage (le réglage `defaultTtsEngine` et le profil actif ne divergent
  plus).

`Ajoute les libellés moteur lisibles et le signal des capacités`

## Tâche 4.2 — Cas limites

- Réseau absent au démarrage d'une lecture Edge : repli immédiat offline,
  sans crash ni boucle (décision actée 4).
- Token révoqué / handshake rejeté (erreur permanente) : message clair,
  pas de retry infini, pas de repli silencieux (décision actée 5).
- Annulation (`CancellationException`) : jamais retryée ni avalée (legacy
  le faisait déjà correctement — conserver la règle).
- Deux synthèses Edge concurrentes sur la même session : sérialisées ou
  explicitement rejetées, jamais deux WebSockets concurrents non maîtrisés.

`Durcit les cas limites Edge TTS`

## Tâche 4.3 — Vérification sur device réel

- Contrat point 4 : lecture TTS réelle avec la voix Edge sur device
  (Snapdragon 680 de référence), phrases audibles, repli offline vérifié en
  coupant le réseau en cours de session.
- Vérifier l'accessibilité (TalkBack) du sélecteur de moteur et de
  l'avertissement de capacités (convention déjà établie pour tout nouvel
  écran).
- Si un critère n'est pas vérifiable (ex. réseau bloqué), écart déclaré
  explicitement, jamais simulé.

`Vérifie Edge TTS sur device réel`

## Tâche 4.4 — Build vert et garde-fous

- `./gradlew build` vert — inclut `checkArchitectureRules` sur
  `infrastructure/tts` (la dépendance OkHttp ne doit pas violer la matrice).
- `bash scripts/check-no-emoji.sh` (K12) et
  `bash scripts/check-no-manage-external-storage.sh` (K5) verts.
- Aucun `fallbackToDestructiveMigration` ni `MANAGE_EXTERNAL_STORAGE`
  introduit (hors périmètre, mais le build doit le confirmer).

`Vérifie le build et les garde-fous`
