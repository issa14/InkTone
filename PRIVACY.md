# Politique de confidentialité — InkTone

**Dernière mise à jour : 23 août 2026 · Version 1.0.0**

InkTone est un lecteur d'ebooks avec synthèse vocale, conçu pour fonctionner
sans connexion. Ce document décrit précisément ce que l'application collecte,
ce qu'elle envoie, et à qui.

En résumé : **InkTone ne collecte aucune donnée personnelle, n'affiche aucune
publicité et n'embarque aucun outil de mesure d'audience.** Vos livres, votre
position de lecture et vos statistiques restent sur votre appareil, sauf si
vous activez explicitement un service en ligne.

---

## 1. Ce qu'InkTone ne fait pas

- **Aucun compte à créer.** L'application s'utilise sans inscription.
- **Aucune télémétrie, aucune analytique.** Le code ne contient aucun SDK de
  mesure d'audience, aucun traceur publicitaire, aucun identifiant
  publicitaire.
- **Aucune publicité**, aucun contenu sponsorisé.
- **Aucune revente ni partage de données** avec des tiers à des fins
  commerciales — il n'y a rien à vendre.
- **Aucun accès global à votre stockage.** L'application n'a ni la permission
  `MANAGE_EXTERNAL_STORAGE`, ni `READ_EXTERNAL_STORAGE`. Elle n'accède qu'aux
  fichiers que vous désignez vous-même, via le sélecteur du système
  (Storage Access Framework).

## 2. Données traitées sur votre appareil

Tout ce qui suit est stocké **localement**, dans l'espace privé de
l'application, et n'est jamais transmis sans action de votre part :

| Donnée | Ce qu'elle contient |
|---|---|
| Bibliothèque | titres, auteurs, séries, étiquettes, couvertures des livres importés |
| Position de lecture | chapitre et emplacement exact dans le texte, par ouvrage |
| Signets, annotations, surlignages | vos marques et leurs notes |
| Statistiques de lecture | durées de lecture et d'écoute, séries de jours, horaires d'activité |
| Réglages | thème, taille du texte, voix, vitesse, règles de prononciation |

**Désinstaller l'application efface l'ensemble de ces données.** Elles ne
survivent nulle part ailleurs.

Les fichiers de vos livres, eux, restent là où vous les avez rangés :
InkTone les lit, il ne les déplace pas et n'en envoie jamais le contenu.

## 3. Connexions réseau

InkTone fonctionne intégralement hors ligne. Chaque connexion listée ci-dessous
est **soit désactivée par défaut, soit déclenchée par une action explicite**.

### 3.1 Téléchargement des voix

Les modèles de synthèse vocale ne sont pas embarqués dans l'application. Quand
vous en demandez un, il est téléchargé depuis les publications du projet
**sherpa-onnx**, sur GitHub :

- `github.com/k2-fsa/sherpa-onnx` — modèles de voix et d'alignement.

Aucune donnée personnelle n'accompagne cette requête. Elle relève de la
politique de confidentialité de GitHub.

### 3.2 Synchronisation (désactivée par défaut)

Si — et seulement si — vous configurez la synchronisation :

- **Google Drive** : l'application demande la portée `drive.appdata`, qui donne
  accès **au seul dossier privé d'InkTone**, invisible depuis votre Drive et
  inaccessible aux autres applications. InkTone ne peut ni lire ni modifier vos
  autres fichiers Drive. Points contactés : `accounts.google.com`,
  `oauth2.googleapis.com`, `www.googleapis.com`.
- **WebDAV** : le serveur que vous indiquez, et lui seul.

Sont synchronisés votre progression et vos statistiques de lecture, vos
signets, vos annotations et surlignages, vos thèmes personnalisés et vos règles
de prononciation — **jamais les fichiers de vos livres**.

Cet instantané est déposé en clair dans cet espace : il n'est pas chiffré par
un mot de passe comme l'est un export de sauvegarde. Il est protégé par le
compte qui l'héberge — le vôtre — et par rien d'autre.

Les jetons d'authentification et les identifiants WebDAV sont chiffrés au repos
par le trousseau Android (`EncryptedSharedPreferences`, AES-256-GCM). Vous
pouvez révoquer l'accès à tout moment depuis l'application, ou depuis les
réglages de votre compte Google.

### 3.3 Catalogues OPDS (aucun par défaut)

Si vous ajoutez un catalogue, InkTone contacte **l'adresse que vous avez
saisie**, avec les identifiants que vous lui confiez (chiffrés au repos). Ces
serveurs sont tiers : leurs pratiques de confidentialité leur appartiennent et
InkTone n'en répond pas.

### 3.4 Voix cloud Edge-TTS (désactivée par défaut)

Ce moteur optionnel s'appuie sur un service Microsoft
(`speech.platform.bing.com`). **Si vous l'activez, le texte à lire est envoyé à
Microsoft pour être synthétisé** — c'est la contrepartie inévitable d'une voix
distante. Les moteurs locaux (Sherpa-ONNX, voix système Android) n'envoient
rien.

Ce service s'appuie sur une API non officielle et peut cesser de fonctionner
sans préavis. Il relève des conditions d'utilisation de Microsoft, qu'InkTone
ne couvre pas.

### 3.5 Rapports de plantage (désactivés par défaut)

Le rapport de plantage utilise **Firebase Crashlytics** et n'est **jamais actif
tant que vous ne l'avez pas activé** dans les réglages
(`crashReportingEnabled`, `false` par défaut). Activé, il transmet à Google des
informations techniques sur l'incident : trace d'exécution, modèle d'appareil,
version d'Android, version de l'application. **Aucun contenu de livre, aucun
titre, aucune position de lecture n'y figure.**

Vous pouvez le désactiver à tout moment ; l'effet est immédiat.

## 4. Sauvegarde locale

L'export de sauvegarde produit un fichier **chiffré de bout en bout** :
AES/GCM, clé dérivée par PBKDF2 d'un mot de passe que vous choisissez. Ce mot
de passe n'est stocké nulle part, ni sur l'appareil ni ailleurs.

**Le perdre rend la sauvegarde définitivement illisible**, y compris pour vous.
C'est le prix d'un chiffrement dont personne d'autre ne détient la clé.

Le fichier est écrit à l'emplacement que vous désignez ; InkTone ne l'envoie
nulle part.

## 5. Permissions demandées

| Permission | Pourquoi |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | télécharger une voix, synchroniser, consulter un catalogue OPDS |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | poursuivre la narration écran éteint |
| `POST_NOTIFICATIONS` | afficher les contrôles de lecture et la progression d'un import |
| `WAKE_LOCK` | empêcher la mise en veille pendant la narration |
| `RECEIVE_BOOT_COMPLETED` | replanifier les tâches différées après un redémarrage |

InkTone ne demande **aucune** permission d'accès aux contacts, à la
localisation, au micro, à la caméra, ni au stockage global.

## 6. Enfants

InkTone ne s'adresse pas spécifiquement aux enfants et ne collecte sciemment
aucune donnée les concernant. Rien n'étant collecté, aucune donnée d'aucun
utilisateur, mineur ou non, ne se trouve entre nos mains.

## 7. Vos droits

Vos données étant exclusivement sur votre appareil :

- **Y accéder** : elles sont dans l'application, et l'export de sauvegarde
  vous en donne une copie complète.
- **Les effacer** : désinstaller l'application les supprime intégralement.
  L'écran Réglages permet aussi de réinitialiser les préférences et de vider
  le cache.
- **Les emporter** : l'export de sauvegarde (chiffré) et l'export de
  statistiques (CSV, JSON) sont à votre disposition.

Pour les données transmises à un service tiers que vous avez activé
(Google Drive, Microsoft, un serveur OPDS ou WebDAV), adressez-vous à ce
service : InkTone n'en détient pas de copie.

## 8. Modifications

Toute évolution de cette politique sera publiée dans ce fichier, dont
l'historique complet est consultable publiquement dans le dépôt Git. La date en
tête indique la dernière révision.

## 9. Contact

Pour toute question sur cette politique : **issadotnet@gmail.com**

Le code source complet est public et vérifiable :
[github.com/issa14/InkTone](https://github.com/issa14/InkTone). Chaque
affirmation de ce document peut être contrôlée dans le code.
