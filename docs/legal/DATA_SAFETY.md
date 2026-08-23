# Formulaire « Sécurité des données » (Play Console) — vérifié contre le code

**Vérifié le 23 août 2026** — contre le code réel, pas rempli de mémoire.
Toute case cochée ci-dessous doit rester traçable à un fichier précis
(`CLAUDE.md` §« le code fait foi »).

> **Version précédente (29 juillet 2026, Tâche 9.5.1) : périmée.** Elle
> affirmait « aucune dépendance d'authentification » et « les deux seuls
> accès réseau de l'app », deux constats invalidés depuis par la
> synchronisation Google Drive / WebDAV (Lot 11), les catalogues OPDS
> (Lot 13) et la voix cloud Edge-TTS (Lot 14). Ce document est le
> formulaire soumis à Google : il ne peut pas rester en retard sur le
> code.

Le texte destiné à l'utilisateur final est [`PRIVACY.md`](../../PRIVACY.md)
(racine du dépôt, lié depuis l'écran À propos —
`core/ui/.../AboutScreen.kt:244`). Les deux doivent dire la même chose.

## Principe

Tout ce qui suit est **désactivé par défaut**. Une installation neuve
qu'on n'a jamais configurée n'émet aucune requête réseau en dehors du
téléchargement d'une voix, déclenché par l'utilisateur.

## Types de données

| Donnée | Collectée ? | Partagée avec un tiers ? | Justification (code) |
|---|---|---|---|
| Fichiers des livres (EPUB/PDF/TXT) | Non | Non | Aucun chemin d'envoi. L'accès est en SAF local (`FileStorageService`), la sauvegarde s'écrit sur l'appareil (`BackupManager.exportTo`), et la charge utile de synchronisation n'inclut aucun fichier (`BackupManager.buildPayload`, l. 84-93). |
| Signets, annotations, surlignages | **Oui si la synchronisation est activée** | Non — vers l'espace privé de l'utilisateur uniquement | `buildPayloadForSync` inclut `bookmarks` et `annotations` (`BackupManager.kt:87,92`), téléversés par `SyncNowManager.synchronizeNow` (l. 87-89) dans `drive.appdata` ou le serveur WebDAV **choisi par l'utilisateur**. Désactivé par défaut (`UserPreferences`, aucun `SyncAccount` lié). |
| Progression et statistiques de lecture | **Oui si la synchronisation est activée** | Non — idem | `readingStates`, `readingSessions` (`BackupManager.kt:89-90`). Hors synchronisation : strictement local (`feature/statistics`, requêtes SQL sur la base locale). |
| Réglages (thèmes personnalisés, règles de prononciation) | **Oui si la synchronisation est activée** | Non — idem | `customThemes`, `pronunciationRules` (`BackupManager.kt:88,91`). |
| Texte du livre en cours de narration | **Oui si la voix Edge-TTS est activée** | **Oui — Microsoft** | `EdgeTtsClient` ouvre `wss://speech.platform.bing.com/...` et envoie la phrase à synthétiser. Moteur optionnel, **désactivé par défaut** (`UserPreferences.defaultTtsEngine = SHERPA_ONNX`, local). Les moteurs Sherpa-ONNX et voix système n'émettent rien. |
| Adresse du compte Google | Non (stockée localement) | Non | `SyncAccount.accountLabel` (`domain/model/SyncModels.kt:18-27`) sert à afficher le compte lié. Aucun appel ne la transmet ; la portée demandée est la seule `drive.appdata` (`GoogleAuthConfig.SCOPE`), sans portée de profil. |
| Jetons OAuth, identifiants WebDAV et OPDS | Non | Non | Stockés chiffrés au repos (`EncryptedSharedPreferences`, AES-256-GCM) : `WebDavCredentialsStore.kt`, `OpdsCredentialsStore`. Transmis au seul service auquel ils appartiennent. |
| Rapports de plantage | Optionnel, **opt-in** | Oui — Google (Firebase Crashlytics), si activé | `UserPreferences.crashReportingEnabled = false` par défaut (ADR-014), vérifié par test (`UserPreferencesTest`). Sans activation, l'implémentation est un no-op (`CrashReporterModule`). |
| Identifiants personnels (nom, e-mail de compte InkTone) | Non | Non | Aucun système de compte propre au projet (ADR-007). |
| Position, identifiant publicitaire, contacts, photos | Non | Non | Aucune permission ni SDK correspondant (`AndroidManifest.xml`, `libs.versions.toml`). |

## Accès réseau réels

Recensés dans le code, pas de mémoire :

1. **Téléchargement des modèles de voix et d'alignement** —
   `VoiceModelDownloader` (`infrastructure/tts`), GET vers une release
   publique `github.com/k2-fsa/sherpa-onnx`, intégrité vérifiée par
   SHA-256. Aucune donnée utilisateur n'accompagne la requête.
2. **Synchronisation Google Drive** (opt-in) — `accounts.google.com`,
   `oauth2.googleapis.com`, `www.googleapis.com`
   (`AppAuthGoogleAuthRepository`, `GoogleDriveSyncProvider`), portée
   `drive.appdata` uniquement.
3. **Synchronisation WebDAV** (opt-in) — le seul hôte saisi par
   l'utilisateur (`infrastructure/sync/webdav`).
4. **Catalogues OPDS** (aucun par défaut) — les seules adresses ajoutées
   par l'utilisateur (`feature/opds`, `OpdsDownloadWorker`).
5. **Voix cloud Edge-TTS** (opt-in) — `speech.platform.bing.com`
   (`EdgeTtsClient`), voir la ligne « texte du livre » ci-dessus.
6. **Rapports de plantage** (opt-in) — Firebase Crashlytics.

## Chiffrement

- **En transit** : HTTPS/WSS pour tous les points ci-dessus.
- **Au repos, sur l'appareil** : jetons et identifiants chiffrés
  (`EncryptedSharedPreferences`). La base de données de lecture, elle,
  n'est pas chiffrée — c'est le stockage privé de l'application.
- **Sauvegarde exportée** : chiffrée de bout en bout par mot de passe
  choisi par l'utilisateur (AES/GCM, clé dérivée PBKDF2 —
  `BackupCrypto`), le mot de passe n'étant stocké nulle part.
- **Instantané de synchronisation** : téléversé en **JSON clair**
  (`SyncNowManager.kt:88-89`) dans un espace privé de l'utilisateur
  (dossier applicatif Drive invisible des autres applications, ou son
  propre serveur WebDAV). À ne pas présenter comme chiffré de bout en
  bout dans le formulaire — ce n'est pas le cas.

## Suppression des données

Aucun compte à supprimer côté InkTone. L'utilisateur peut délier la
synchronisation depuis l'application (révocation du jeton), révoquer
l'accès depuis les réglages de son compte Google, et désinstaller
l'application pour effacer l'intégralité des données locales.

## Permission `INTERNET`

Déclarée (Tâche 9.2.2) et nécessaire aux six accès listés ci-dessus.
Aucune permission de stockage large : `MANAGE_EXTERNAL_STORAGE` est
interdite et vérifiée en CI (K5, `scripts/check-no-manage-external-storage.sh`).
