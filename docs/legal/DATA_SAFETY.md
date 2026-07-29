# Formulaire « Sécurité des données » (Play Console) — vérifié contre le code

**Vérifié le 29 juillet 2026** (Tâche 9.5.1) — contre le code réel, pas
rempli de mémoire. Toute case cochée ci-dessous doit rester traçable à
un fichier/commit précis (`CLAUDE.md` §"le code fait foi").

| Donnée | Collectée ? | Partagée avec un tiers ? | Justification (code) |
|---|---|---|---|
| Contenu des livres (texte, annotations, signets) | Non | Non | Offline-first (Blueprint §1.4) — aucun appel réseau ne transmet de contenu de publication. `BackupManager` (`data/src/main/kotlin/com/inktone/data/backup/BackupManager.kt`) écrit uniquement en local via SAF. |
| Rapports de crash | Optionnel, opt-in | Non (selon fournisseur, à préciser si Crashlytics activé) | `UserPreferences.crashReportingEnabled = false` par défaut (ADR-014), vérifié explicitement par test (`UserPreferencesTest.kt`, Tâche 9.2.3). |
| Statistiques de lecture (temps, séries) | Oui, mais **locales uniquement** | Non | `feature/statistics` (Tâche 8.6) — `GetStatisticsUseCase` lit uniquement `ReadingSessionRepository`/`PublicationRepository` locaux, aucun appel réseau. |
| Identifiants personnels (email, nom, compte) | Non | Non | Aucun système de compte dans le projet (ADR-007, Blueprint §2.7) — vérifié : aucune dépendance d'authentification dans `gradle/libs.versions.toml`. |
| Position/appareil | Non | Non | Aucune permission de localisation déclarée (`AndroidManifest.xml`). |
| Identifiant publicitaire | Non | Non | Aucun SDK publicitaire dans les dépendances. |

## Accès réseau réels (les deux seuls dans l'app)

1. `VoiceModelDownloader` (`infrastructure/tts`) — télécharge le modèle de
   voix Kokoro depuis une release GitHub publique (Tâche 9.0.1),
   vérifié par SHA-256. Aucune donnée utilisateur envoyée, uniquement
   une requête HTTP GET vers une URL fixe.
2. Rapport de crash optionnel (ci-dessus).

**Permission `INTERNET`** ajoutée en Tâche 9.2.2 — manquante jusqu'ici
alors que (1) l'exige ; sans elle, tout téléchargement réel aurait échoué
avec `SecurityException` sur un device (jamais testé sur device avant
cette tâche, Tâche 5.6/8.7 validées uniquement avec des fakes en test
unitaire).

Voir `docs/legal/POLITIQUE_CONFIDENTIALITE.md` pour le texte destiné à
l'utilisateur final.
