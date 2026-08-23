# Journal des modifications

Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).
Ce projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

## [1.0.0] — non publiée

Première version. Le dépôt a été réécrit intégralement le 26 juillet 2026
([ADR-019](docs/adr/ADR-019-full-rewrite-orphan-branch.md)) ; l'historique
antérieur est archivé en lecture seule et ne fait pas partie de cette version.

### Lecture

- Import et lecture d'ouvrages **EPUB, PDF et texte brut**, depuis n'importe
  quel dossier de l'appareil via le sélecteur du système.
- Défilement continu ou mode paginé.
- Taille de texte, interligne, marges, police et thème de lecture réglables.
- Luminosité propre au lecteur, indépendante du réglage système.
- Signets, annotations et surlignages, recherche plein texte, table des
  matières.
- Rappel de repos oculaire, préréglage d'accessibilité.

### Narration

- Synthèse neuronale **sur l'appareil** (Sherpa-ONNX), avec timestamps par mot
  et surlignage réellement synchronisé — jamais interpolé
  ([ADR-021](docs/adr/ADR-021-tts-word-timing-tiered-architecture.md)).
- Voix système Android en repli ; moteur cloud Edge-TTS optionnel, désactivé
  par défaut ([ADR-024](docs/adr/ADR-024-edge-tts-optional-cloud-engine.md)).
- Lecture enchaînée sans blanc entre les phrases
  ([ADR-025](docs/adr/ADR-025-playback-gapless.md)).
- Vitesse, intonation et gain réglables ; règles de prononciation
  personnalisées.
- La narration survit à la fermeture de l'écran de lecture : elle continue
  écran éteint, pilotable depuis la notification et l'écran verrouillé.
- Minuteur de sommeil, porté par la session et non par l'écran.

### Bibliothèque

- Séries, étiquettes, favoris, épinglage.
- Filtres, tris et trois dispositions d'affichage.
- Catalogues **OPDS** pour découvrir et importer de nouveaux ouvrages
  ([ADR-023](docs/adr/ADR-023-opds-scope-reintegration.md)).

### Statistiques

- Temps de lecture visuelle et temps d'écoute comptés séparément.
- Série de jours consécutifs, objectif quotidien, carte d'activité horaire.
- Vitesse de lecture, mesurée sur la seule lecture visuelle — celle d'une
  session narrée ne mesurerait que le débit du synthétiseur.
- Statistiques par ouvrage, export CSV et JSON.

### Données et confidentialité

- **Hors ligne par défaut** : import, lecture et synthèse sont locaux.
- Accès aux fichiers par le Storage Access Framework exclusivement ; la
  permission `MANAGE_EXTERNAL_STORAGE` est absente et son absence est
  vérifiée en intégration continue.
- Sauvegarde locale **chiffrée de bout en bout** (AES/GCM, clé dérivée par
  PBKDF2 d'un mot de passe utilisateur).
- Synchronisation optionnelle Google Drive (portée `drive.appdata`, dossier
  privé) ou WebDAV ; jetons et identifiants chiffrés au repos par le trousseau
  Android.
- Rapport de plantage sur consentement explicite, inactif par défaut, et sans
  effet si aucune configuration n'est fournie.
- Aucune télémétrie, aucune publicité, aucun compte à créer.

### Sous le capot

- Clean Architecture multi-module, présentation MVI, un état immuable par
  écran. Les règles de dépendance sont vérifiées par `checkArchitectureRules` :
  une violation fait échouer le build.
- Licence **MIT** et ouverture du code
  ([ADR-026](docs/adr/ADR-026-licence-mit-ouverture-du-code.md)).

### Limites connues de cette version

- **`arm64-v8a` uniquement.** Le code natif de synthèse n'est compilé que pour
  cette architecture : les appareils 32 bits et les émulateurs x86 ne sont pas
  pris en charge.
- **R8 n'est pas activé en release.** L'AAB tient largement dans le budget
  (30 Mo pour 60 visés), mais l'activation demande une validation des règles de
  conservation Readium/onnxruntime sur appareil, non faite à ce stade.
- **Les tests instrumentés ne s'exécutent pas en intégration continue** —
  migrations Room, DAO et accessibilité Compose exigent un appareil ou un
  émulateur, et restent vérifiés manuellement. La CI les **compile**
  désormais, ce qui garantit qu'ils resteront exécutables le jour où on
  les lance, mais ne remplace pas leur exécution.
- **La vitesse de lecture peut être surestimée.** Le comptage crédite les
  phrases franchies au défilement et ne distingue pas la lecture du survol :
  feuilleter rapidement gonfle la valeur affichée.
- **Le moteur cloud Edge-TTS s'appuie sur une API non officielle** et peut
  cesser de fonctionner sans préavis.
