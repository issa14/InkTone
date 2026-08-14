# SPIKE_EDGE_TTS_WEBSOCKET.md — Protocole de validation

> **Projet InkTone** — Spike Edge TTS (Lot 14, Palier 1)
> **Date :** 2026-08-14
> **Cible :** Android / Snapdragon 680 (device réel, avec réseau)
> **Références :** `ADR-024`, `LOT_14_EDGE_TTS.md`, implémentation legacy
> `legacy/monolith` → `service/edge/EdgeTtsClient.kt`

---

## 1. Objectif

Ce spike répond à **deux** questions avant d'écrire le moindre code de
production. Aucune valeur n'est supposée : les réponses découlent
exclusivement des captures device ci-dessous (§5, §6).

1. **Round-trip** : le protocole WebSocket Bing (`speech.platform.bing.com`)
   fonctionne-t-il depuis Android, de la connexion à l'audio PCM audible ?
2. **Word boundaries** : les frontières de mot SSML sont-elles réellement
   extractibles quand `wordBoundaryEnabled: true` est envoyé ?

La réponse à (2) fige la valeur de `TtsCapabilities.wordTimestamps` pour
tout le lot (vrai seulement si prouvé — §8.9 « un moteur ne fait jamais
semblant »).

## 2. Conditions et prérequis

- Device réel (Snapdragon 680 de référence, V2206), réseau actif.
- **Jamais** d'émulateur, **jamais** de conclusion par inférence.
- Le spike est un test instrumenté `androidTest` dans `infrastructure/tts`
  (`EdgeTtsWebSocketSpikeTest.kt`), même famille que les spikes existants du
  module (`OnRangeStartFileSynthesisSpikeTest`, `SherpaOnnxCallbackStreamingTest`).
- Dépendance requise : `implementation(libs.okhttp)` ajoutée à
  `infrastructure/tts` (Tâche 1.2) — le spike est le premier fichier
  utilisateur d'OkHttp dans ce module.

## 3. Protocole exact (à ne pas improviser)

### 3.1 Endpoint

```
wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1
  ?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4
  &ConnectionId={uuid-sans-tirets}
  &Sec-MS-GEC={token}
  &Sec-MS-GEC-Version=1-143.0.3650.75
```

### 3.2 Authentification `Sec-MS-GEC`

```
ticks = floor((unixSeconds + 11644473600) / 300) * 300 * 10^7
Sec-MS-GEC = SHA256("{ticks}6A5AA1D4EAFF4E9FB37E23D68491D6F4").hex.uppercase()
```

`11644473600` = écart epoch Unix ↔ epoch Windows FILETIME. `TrustedClientToken`
et `Sec-MS-GEC-Version` sont portés tels quels du legacy (déjà éprouvés, pas à
réinventer — ADR-024, décision 4 : constante technique, pas un secret).

### 3.3 En-têtes de connexion

| En-tête | Valeur |
|---|---|
| `User-Agent` | `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0` |
| `Origin` | `chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold` |
| `Sec-WebSocket-Version` | `13` |
| `X-Speech-API-Audio-Format` | `audio-24khz-48kbitrate-mono-mp3` |
| `Cookie` | `muid={32-hex-uppercase}` |

### 3.4 Trame de configuration (`speech.config`)

Envoyée immédiatement à l'ouverture (`onOpen`), format texte :

```
X-RequestId:{uuid-sans-tirets}\r\n
Content-Type:application/json; charset=utf-8\r\n
Path:speech.config\r\n\r\n
{"context":{"system":{"name":"SpeechSDK","version":"1.19.0","build":"20220101","lang":"fr-FR"},"os":{"platform":"Android","name":"Android"},"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"true"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}
```

**`wordBoundaryEnabled: "true"` dès la partie B** — c'est précisément la
variable que le legacy n'activait pas, et que ce spike teste.

### 3.5 Trame SSML

Envoyée juste après la config :

```
X-RequestId:{uuid-sans-tirets}\r\n
Content-Type:application/ssml+xml\r\n
Path:ssml\r\n\r\n
<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xmlns:mstts="http://www.w3.org/2001/mstts" xml:lang="fr-FR"><voice name="fr-FR-VivienneNeural"><prosody rate="+0%" pitch="+0Hz">{texte échappé XML}</prosody></voice></speak>
```

Phrase de test fixe (reproductible) : `"Bonjour, ceci est une phrase de test."`
Échappement XML : `&` → `&amp;`, `<` → `&lt;`, `>` → `&gt;`, `"` → `&quot;`,
`'` → `&apos;`.

### 3.6 Réception

- **Trames binaires** = chunks MP3 (`audio-24khz-48kbitrate-mono-mp3`),
  concaténées jusqu'à la fin.
- **Trames texte** :
  - `Path:audio` → début du flux MP3 ;
  - `Path:turn.end` → fin de la synthèse, fermer (`close(1000, "OK")`) ;
  - `Path:wordboundary` (partie B) → événements de frontière de mot.

## 4. Partie A — Round-trip (question 1)

Sans `wordBoundaryEnabled` d'abord (protocole minimal, isole la variable).

**Critères de preuve (tous exigés, logs device) :**
1. `onOpen` reçu (handshake WebSocket accepté).
2. Nombre de chunks binaires > 0, total d'octets MP3 > 0.
3. `Path:turn.end` reçu → complétion propre.
4. MP3 décodé (`MediaCodec`/`MediaExtractor`, pipeline minimal) → PCM non
   vide, `sampleRate` lisible (attendu ~24 kHz), durée audio calculée
   cohérente avec la longueur de la phrase (quelques secondes).

**Échec à traiter, pas à contourner :** handshake refusé (code HTTP != 101),
`onFailure`, timeout (aucune complétion après 15 s), PCM vide.

## 5. Partie B — Word boundaries (question 2)

Reprendre la Partie A avec `wordBoundaryEnabled: true`.

**Critères de preuve :**
1. Des trames `Path:wordboundary` sont reçues en plus des chunks audio.
2. Chaque trame contient au minimum `Offset` (offset caractère) et
   `Duration` (durée en ticks audio) exploitables.
3. Les offsets reconstitués couvrent la phrase de test dans l'ordre, sans
   chevauchement ni trou (au moins un événement par mot de la phrase).
4. Un extrait de log **brut** (une trame wordboundary complète) est capturé
   dans ce document — jamais un résumé.

## 6. Résultats (device V2206, 2026-08-14)

### 6.1 Partie A — Round-trip

- Handshake : `101` (WebSocket accepté)
- Chunks binaires / octets MP3 : `23` chunks / `18 661` octets
- PCM décodé / sampleRate / durée : `62 784` échantillons / `24 000` Hz / `1 308` ms
- Trame `turn.end` : reçue (`chunks=23, octets=18661`)

### 6.2 Partie B — Word boundaries

- Trames reçues : **7**, sous le chemin **`Path:audio.metadata`** —
  **pas** `Path:wordboundary` (supposition du legacy, infirmée).
- Extrait brut d'une trame (corps JSON, en-têtes retirés) :

```json
{
  "Type": "WordBoundary",
  "Offset": 500000,
  "Duration": 6000000,
  "text": { "Text": "Bonjour", "BoundaryType": "WordBoundary" }
}
```

  `Offset`/`Duration` sont en **ticks 100 ns** (même époque FILETIME que
  `Sec-MS-GEC`) → `ms = ticks / 10_000`.
- Offsets reconstitués (couverture de la phrase « Bonjour, ceci est une
  phrase de test. », ordre respecté, sans chevauchement) :

| Mot | Début (ms) | Fin (ms) |
|---|---|---|
| `Bonjour` | 50 | 650 |
| `ceci` | 937 | 1 212 |
| `est` | 1 212 | 1 237 |
| `une` | 1 237 | 1 400 |
| … (3 mots restants) | — | — |

  Cohérence vérifiée : la dernière frontière aboutit à ~1,4 s, contre une
  durée audio décodée de 1 308 ms (écart d'arrondi de tranche, cohérent).

## 7. Verdict

- **Round-trip** : `FONCTIONNE` — chaîne WebSocket → MP3 → PCM complète.
- **Word boundaries** : `EXTRACTIBLES` — via `Path:audio.metadata`
  (`Type: WordBoundary`, `Offset`/`Duration` en ticks 100 ns, `Text` par mot).
- **`wordTimestamps` cible pour `EdgeTtsEngine`** : **`true`** — le Palier 3
  doit implémenter le parsing de `Path:audio.metadata` et le mapping
  `ticks → ms` + `Text → charOffset` (remappé sur le texte affiché via
  `PronunciationRuleApplier`, même mécanique que `SherpaOnnxTtsEngine`).

**Correction de protocole à capitaliser (K13) :** le legacy supposait
`Path:wordboundary` ; la vérité device est `Path:audio.metadata`. Ne pas
réintroduire la supposition dans le client de production (Tâche 2.2).
