# Lot 11 — Synchronisation

**Base :** `main` à `194220ca`. Référence cible : `UX_FLOW_DESIGN.md` § Écran Synchronisation (Configuration + Opérationnel).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare aucun palier clos : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Décisions actées

1. **Google Drive en V1 via `drive.appdata`, WebDAV ensuite.** Décision révisée. `drive.appdata` est une portée **sensible et non restreinte** : pas d'audit de sécurité tiers, seulement la validation classique de l'écran de consentement (politique de confidentialité, vidéo de démonstration, vérification de marque). En mode test, 100 comptes sont disponibles immédiatement, sans attente — la V1 n'est donc pas bloquée par Google. Les données vivent dans le dossier applicatif masqué, invisible du Drive de l'utilisateur.

   L'exclusivité mutuelle imposée par la cible force de toute façon une frontière fournisseur propre : WebDAV sera une seconde implémentation derrière la même interface.

   **Prérequis hors code — pris en charge par Issa, hors périmètre de Claude Code** : projet Google Cloud, écran de consentement en mode Test, identifiant client OAuth et empreintes SHA-1 debug et release. Partir du principe que l'environnement cloud est correctement configuré ; se concentrer sur le code client.
2. **Métadonnées seules** — progression, marque-pages, annotations, règles de prononciation, thèmes, réglages. **Aucun fichier EPUB.** La charge utile tient en quelques centaines de kilo-octets.
3. **Flotte d'appareils et journal d'activité en V1** — réalisables **sans serveur**, sous forme de fichiers de registre dans le dossier distant.
4. **Conflit de position : arbitrage par l'utilisateur.** Voir palier D, qui en précise les limites.

## Deux défauts préalables à corriger

- **`BackupManager` ne sauvegarde pas les annotations.** Ses dépendances couvrent marque-pages, prononciation, positions, sessions, publications et thèmes — `AnnotationRepository` est absent. Un export perd aujourd'hui tous les surlignages et notes, alors que le lot 4 en a fait un contenu de premier plan. Défaut hérité du lot 6.
- **`BackupManager` ne chiffre pas.** L'export est un JSON en clair, alors que la cible décrit une sauvegarde locale **E2EE avec mot de passe**.

## Quatre paliers poussables

| Palier | Contenu | Vérifiable seul |
|---|---|---|
| **A** | Socle : charge utile, chiffrement, contrat fournisseur | Oui — par tests |
| **B** | WebDAV + écran Configuration | Oui |
| **C** | Écran Opérationnel, flotte, journal, planification | Oui |
| **D** | Détection et arbitrage des conflits | Oui |

**Pousser après chaque palier.** C'est le plus gros lot de la série ; sans jalons, on reproduit le lot 3d.

---

# PALIER A — Socle

## Tâche 11.1 — Compléter et chiffrer la charge utile

- Ajouter `AnnotationRepository` à `BackupManager` et à `BackupPayload`. Les annotations portent extrait, note, couleur, épinglage et locators depuis le lot 4 — tout doit être sérialisé.
- **Chiffrement E2EE à mot de passe** pour le fichier local `.rfbackup`. Dérivation de clé depuis le mot de passe, jamais le mot de passe stocké. **Un mot de passe perdu rend la sauvegarde irrécupérable** — le dire explicitement dans l'UI plutôt que le découvrir.
- **Compatibilité ascendante :** les fichiers exportés avant ce lot sont en clair et sans annotations. L'import doit les accepter, ou refuser avec un message clair. Ne pas planter.
- La **même sérialisation** sert au fichier local et à la synchronisation distante — une seule définition de charge utile, pas deux qui divergeront.

`Complete et chiffre la charge utile de sauvegarde`

---

## Tâche 11.2 — Contrat fournisseur et état

- Interface `SyncProvider` dans `domain/service` (téléverser, télécharger, lister, supprimer), implémentations dans `infrastructure`. Même discipline que `ImportScheduler` et `ImportProgressObserver` : les modules `feature` ne touchent ni le réseau ni les identifiants.
- `SyncUiState` scellé, étendu par rapport à la cible :
  - `Unconfigured`, `Authenticating`, `Configured`, édition en cours — les quatre cas du document ;
  - **`Syncing`** — transfert en cours. Absent de la cible, indispensable : sans lui, « Synchroniser maintenant » accepte les clics répétés et lance plusieurs transferts concurrents. Le bouton doit devenir un indicateur de progression et refuser les clics.
  - **Échec persistant** — une snackbar ne suffit pas pour une synchro **automatique** qui échoue alors que l'utilisateur est ailleurs (jeton expiré, serveur injoignable). Prévoir une **bannière d'avertissement persistante** en haut du Dashboard tant que la dernière tentative automatique a échoué, avec l'accès à la reprise. Les snackbars restent le retour des actions **manuelles**, conformément à la cible.
- `SyncAccount` **unique** dans `Configured` : c'est ce qui matérialise l'exclusivité mutuelle. Un seul compte, pas une liste.
- **Identité d'appareil** : identifiant stable généré au premier lancement, persisté, plus un nom lisible. Il sert à la flotte (palier C) **et** à la détection de conflits (palier D) — le poser ici évite de le rétrofitter deux fois.
- **Horodatage par entité** sur les données synchronisées. Sans lui, aucun conflit n'est détectable : c'est le préalable silencieux du palier D.

`Ajoute le contrat fournisseur et l etat de synchronisation`

---

## Tâche 11.3 — Tests du palier A

1. Aller-retour complet : exporter, chiffrer, importer, tout est restitué — **annotations comprises**.
2. Mauvais mot de passe : échec clair, aucune donnée écrasée.
3. Un fichier au format antérieur (clair, sans annotations) est importé ou refusé proprement.
4. L'identifiant d'appareil est stable entre deux lancements.
5. Le mot de passe n'apparaît nulle part en clair, ni en préférences ni dans le fichier.

`Ajoute les tests du socle de synchronisation`

---

# PALIER B — Google Drive et Configuration

## Tâche 11.4 — Obtention et rafraîchissement du jeton

**Tâche distincte du client REST, et c'est le vrai travail Android.** Le `tokenProvider: suspend () -> String` est le bon découplage, mais il faut quelqu'un derrière.

- **Vérifié en amont :** `GoogleSignIn` étant déprécié, les deux voies retenues sont **AppAuth-Android** (`net.openid:appauth`) ou la couche **Authorization API de Google Identity Services**. Choisir **la plus légère des deux** et justifier le choix — poids ajouté à l'APK et surface d'API à maintenir.
- Implémenter derrière une interface `AuthRepository` : construction de l'intent d'autorisation, traitement du retour dans l'Activity, exposition du jeton au `tokenProvider`.
- **Ne pas se bloquer sur les clés** : `clientId` et `redirectUri` sont fournis séparément. Prévoir leur injection, ne pas les coder en dur, et rendre l'absence de configuration explicite plutôt que silencieuse.
- Portée demandée : **exclusivement** `https://www.googleapis.com/auth/drive.appdata`. Aucune autre.
- Rafraîchissement transparent : le fournisseur de jeton rend un jeton valide ou échoue explicitement, il ne rend jamais un jeton périmé.
- **Révocation côté utilisateur** : si l'accès est retiré depuis le compte Google, l'app doit repasser en `Unconfigured` avec un message clair, pas boucler sur des 401.

`Ajoute l obtention et le rafraichissement du jeton Google`

---

## Tâche 11.5 — Client REST Google Drive

Client léger sur OkHttp — **ne pas embarquer la bibliothèque Google Drive**, dont le poids est disproportionné pour quatre opérations.

- **Deux hôtes distincts**, à ne pas confondre : `.../drive/v3/files` pour lecture, recherche et métadonnées ; `.../upload/drive/v3/files` pour création et mise à jour de contenu.
- **`multipart/related`, pas `form-data`.** `MultipartBody.Builder` produit du `multipart/form-data` par défaut, que Drive rejette. Forcer le type **et** passer `uploadType=multipart` en paramètre.
- **`appDataFolder` explicite des deux côtés** : `parents: ["appDataFolder"]` à la création, `spaces=appDataFolder` à la lecture et à la recherche. Omettre le second renvoie une liste vide alors que le fichier existe — panne silencieuse.
- `response.use { }` sur chaque réponse.
- Distinguer les échecs — jeton invalide, quota dépassé, réseau, fichier absent — chacun avec son message. Leçon du lot 5 : ne pas réduire plusieurs causes à un `else`.
- **Aucun verrou distant disponible.** La cohérence repose sur les horodatages par entité (palier A) et le relire-avant-écrire du registre (palier C). Ne pas compter sur les sémantiques de Drive.

`Ajoute le client REST Google Drive sur le dossier applicatif`

---

## Tâche 11.6 — Écran Configuration

Route, destination, et **réactivation de l'item « Synchronisation » du drawer** — dernière des quatre destinations masquées au lot 1.

Topbar : retour, « Configuration Sync », bouton **Enregistrer**.

**Comportement d'Enregistrer.** Avec Drive, l'autorisation réussie **est** la validation : il n'y a pas d'URL à saisir ni de test séparé. Enregistrer n'est actif qu'une fois un compte réellement lié. La règle « Enregistrer déclenche le test et reste sur place en cas d'échec » redeviendra pertinente quand WebDAV arrivera — la consigner pour ce moment-là.

- **Carte Google Drive** : badge ACTIF/INACTIF ; si actif, bordure et fond teintés succès, avatar, adresse du compte lié, badge « Connecté », bouton Déconnecter à droite. Le bouton de connexion lance le flux d'autorisation (11.4).
- **Carte WebDAV** : présente mais **grisée et désactivée**, message « Bientôt disponible ». La cible prévoit le grisage quand l'autre fournisseur est actif ; ici il est permanent, faute d'implémentation. **Un bouton désactivé qui explique pourquoi n'est pas un contrôle décoratif ; un bouton actif qui ne ferait rien le serait.** À consigner.
- **Carte Fichier local** : badge « AUTONOME », champ mot de passe E2EE, boutons Exporter / Importer côte à côte. **Toujours active**, indépendante du cloud.
  - **Bascule afficher/masquer** sur le champ de mot de passe.
  - **Avertissement sous le champ**, non négociable : « Ce mot de passe ne peut pas être récupéré. S'il est perdu, votre sauvegarde sera illisible. » C'est la seule perte de données irréversible du lot.
  - Elle remplace le branchement fait au lot 6 dans les Réglages — décider si celui-ci reste ou pointe ici, et ne pas laisser deux chemins divergents.
- **Retours par snackbar temporaire**, pas de carte de statut permanente. La cible insiste : une carte de statut s'était glissée par erreur dans le mockup initial.

**Transition entre Configuration et Opérationnel** : `AnimatedContent` en fondu plutôt qu'un remplacement sec. **Respecter `reduceMotion`** — la préférence existe et est déjà honorée par le surlignage TTS et la couche pilule ; une transition qui l'ignorerait serait une régression d'accessibilité.

`Ajoute l ecran de configuration de synchronisation`

---

## Tâche 11.7 — Tests du palier B

1. Un jeton expiré est rafraîchi de façon transparente ; un jeton révoqué produit un retour explicite, pas une boucle de 401.
2. Le jeton n'est jamais persisté en clair.
3. Enregistrer bascule vers l'écran Opérationnel ; Déconnecter revient à `Unconfigured`.
4. La carte WebDAV est inerte et explicite — aucun clic ne déclenche quoi que ce soit.
5. Une recherche sans `spaces=appDataFolder` échoue le test : la requête doit cibler le dossier applicatif.
6. L'envoi utilise `multipart/related` et `uploadType=multipart` — un `form-data` doit faire échouer le test.
7. Le mot de passe E2EE est masqué par défaut ; la bascule l'affiche.

`Ajoute les tests de la configuration de synchronisation`

---

# PALIER C — Opérationnel

## Tâche 11.8 — Dashboard, flotte, journal

- **Header profil** : avatar à initiale, identifiant, fournisseur actif, bouton « Gérer » vers la Configuration.
- **Action rapide** : horodatage relatif de la dernière synchro et bouton pleine largeur « Synchroniser maintenant ».
- **Flotte d'appareils** : fichier de registre distant, une entrée par appareil — identifiant, nom, type, dernière activité. Badge « (Cet appareil) » sur l'appareil courant, point vert ou gris selon la fraîcheur.
  - **Écriture concurrente** : deux appareils qui se synchronisent en même temps peuvent s'écraser mutuellement. Écrire son entrée sans réécrire celles des autres, ou relire avant d'écrire. À traiter, pas à découvrir.
  - **Ne pas écrire de signal de présence périodique.** Un « heartbeat » cadencé multiplierait les requêtes, consommerait de la batterie et se heurterait aux quotas d'API — ce qui deviendra bloquant avec Google Drive. La dernière activité se met à jour **uniquement lors d'une synchronisation réelle**. Le statut affiché reflète donc la dernière synchro, pas une présence en ligne — l'écrire ainsi dans l'UI.
  - **Retirer un appareil de la liste**, par balayage ou menu contextuel. **Attention au libellé** : sans serveur, ce n'est **pas une révocation**. L'appareil retiré détient toujours les identifiants et se réinscrira à sa prochaine synchronisation. C'est un nettoyage de liste — dire « Retirer de la liste », jamais « Révoquer », et ne promettre aucune sécurité. La vraie révocation consiste à changer le mot de passe WebDAV.
- **Journal d'activité** : **5 à 10 derniers événements** affichés, fichier distant plafonné. Chaque type d'événement porte une **icône de forme distincte** via `AppIcons` — succès, échec réseau, import manuel. La couleur vient **en renfort, jamais seule** : elle ne distingue rien pour un utilisateur daltonien ni pour TalkBack.
- **Deux interrupteurs** : synchro automatique en arrière-plan, Wi-Fi uniquement. **« Wi-Fi uniquement » est grisé quand la synchro automatique est éteinte** — il n'y a plus rien à restreindre. Même patron que l'intervalle de repos oculaire au lot 6. Via WorkManager, déjà utilisé pour l'import, avec ses contraintes réseau — ne pas écrire une seconde planification.

`Ajoute l ecran operationnel de synchronisation`

---

## Tâche 11.9 — Tests du palier C

1. Deux appareils simulés apparaissent tous deux dans la flotte, sans que l'un efface l'autre.
2. « Wi-Fi uniquement » actif : aucune synchro déclenchée en données mobiles.
3. Le journal est plafonné ; au-delà, les plus anciens événements sortent.
4. Sans compte configuré, l'écran Opérationnel n'est pas atteignable.
5. « Synchro automatique » éteinte : « Wi-Fi uniquement » est inopérant et grisé.
6. Pendant un transfert, « Synchroniser maintenant » refuse un second déclenchement.
7. Un échec de synchro automatique laisse une bannière persistante, pas une snackbar déjà disparue.
8. Aucune écriture distante n'a lieu en dehors d'une synchronisation réelle — pas de signal périodique.
9. Le journal distingue les types d'événements par la **forme** de l'icône, pas seulement par la couleur.

`Ajoute les tests de l ecran operationnel`

---

# PALIER D — Conflits

## Tâche 11.10 — Détection et arbitrage

**La cible ne décrit aucun écran de conflit** — c'est un manque de conception, pas un oubli d'implémentation. À maquetter et consigner.

**Portée de l'arbitrage — précision nécessaire.** « Demander à l'utilisateur » ne peut pas s'appliquer à tout, sous peine de rendre la synchronisation insupportable :

| Donnée | Résolution |
|---|---|
| **Position de lecture** | **Arbitrage utilisateur** — c'est une valeur unique par livre, un choix est nécessaire |
| Marque-pages, annotations | **Fusion silencieuse** — données additives, l'union est le bon résultat |
| Suppressions | Marqueur de suppression horodaté, sinon un élément supprimé réapparaît à chaque synchro |
| Réglages, thèmes | Dernier écrit gagne |

**La synchro en arrière-plan ne peut rien demander.** Elle doit donc **différer** le conflit — le consigner et le présenter à la prochaine ouverture de l'app — jamais trancher seule ni bloquer.

**L'arbitrage présente les deux options avec leur contexte** : appareil d'origine, date, et progression (« Chapitre 12, 34,7 % » contre « Chapitre 8, 21,2 % »). Un choix entre deux dates nues est ininterprétable.

### Composant `SyncConflictBottomSheet`

Conçu directement en code, sans maquette préalable — exception assumée à la méthode habituelle, à consigner.

`ModalBottomSheet` Material 3, `Column` avec `Arrangement.spacedBy(16.dp)`, même respiration que l'onboarding.

- **Titre** « Conflit de synchronisation » avec une icône d'alerte **passée par `AppIcons`**, jamais un `Icons.Outlined.*` direct — convention posée au lot 1.
- **Nom du livre concerné**, en évidence. Le conflit porte sur **une position de lecture pour un livre**, pas sur une sauvegarde entière.
- **Deux blocs comparatifs**, chacun avec : origine (« Sur cet appareil » / « Depuis Drive »), horodatage, **et surtout chapitre + pourcentage**. La version la plus récente est signalée visuellement — mais **elle n'est pas présélectionnée** : la plus récente n'est pas nécessairement celle que l'utilisateur veut garder, c'est tout le sens d'un arbitrage.
- **Libellés d'action orientés lecture, pas fichier** : « Reprendre au chapitre 12 » / « Reprendre au chapitre 8 ». Ne **pas** écrire « écraser le local » ou « écraser le cloud » — ce vocabulaire suggère un remplacement de sauvegarde entière et contredit la matrice ci-dessus.
- **File de conflits** : plusieurs livres peuvent diverger, et la synchro d'arrière-plan les accumule jusqu'à la prochaine ouverture. Présenter les conflits successivement, ou offrir un récapitulatif — mais ne jamais en perdre un silencieusement.

**Contrainte à ne pas enfreindre :** ce composant n'arbitre **que** la position de lecture. Aucun chemin de l'UI ne doit permettre d'écraser en bloc les annotations ou les marque-pages — ils fusionnent, sans question posée.

`Ajoute la detection et l arbitrage des conflits de position`

---

## Tâche 11.11 — Tests et consignation

1. Deux positions divergentes déclenchent une demande d'arbitrage ; le choix est appliqué et propagé.
2. Deux jeux d'annotations divergents **fusionnent** sans rien demander — et aucun chemin de l'UI ne permet d'écraser un jeu par l'autre.
2 bis. Le composant d'arbitrage affiche chapitre et pourcentage des deux côtés, pas seulement des horodatages.
2 ter. Trois livres en conflit produisent trois arbitrages ; aucun n'est perdu.
3. Un élément supprimé sur un appareil ne réapparaît pas après synchronisation.
4. Un conflit détecté en arrière-plan ne se résout pas seul : il attend l'ouverture de l'app.
5. Une synchro interrompue en cours ne laisse pas de données partielles.

Consigner dans `UX_FLOW_DESIGN.md` : Drive différé et sa raison, l'écran de conflit absent de la conception initiale, la matrice de résolution ci-dessus, et la mise à jour de la note de drawer du lot 1 — plus aucune destination masquée.

`Ajoute les tests de conflit et consigne l etat de la synchronisation`

---

## Vérifications sur appareil

**Deux appareils sont nécessaires** pour les points 5 à 9 — ou un appareil plus un émulateur.

| # | Attendu |
|---|---|
| 1 | L'item « Synchronisation » apparaît dans le drawer et ouvre la Configuration |
| 2 | Révoquer l'accès depuis le compte Google : l'app repasse en non configuré avec un message clair, sans boucler |
| 3 | Export local chiffré, réimport réussi, **annotations restituées** |
| 4 | Mot de passe erroné à l'import : échec propre, données existantes intactes |
| 5 | Deux appareils configurés : chacun voit l'autre dans la flotte |
| 6 | Lire sur A, synchroniser, ouvrir sur B : la position est reprise |
| 7 | Annoter sur A et B hors ligne, synchroniser : **toutes** les annotations sont présentes |
| 8 | Positions divergentes : l'arbitrage s'affiche avec chapitre et pourcentage des deux côtés |
| 9 | Supprimer un marque-page sur A, synchroniser : il ne revient pas depuis B |
| 10 | « Wi-Fi uniquement » : aucune synchro en données mobiles |
| 11 | Carte WebDAV : grisée, explicite, inerte |
| 12 | Retirer un appareil de la liste : il disparaît — et **réapparaît** s'il se synchronise à nouveau, conformément au libellé |
| 13 | Couper le réseau pendant une synchro auto : bannière persistante au retour sur le Dashboard |
| 14 | Avec « Réduire les animations » : la bascule Configuration/Opérationnel est instantanée |

Le point 7 est celui que je passerais en premier : la fusion silencieuse des données additives est ce qui distingue une synchronisation d'un écrasement.

---

## Après ce lot

Les dettes de fond : test flake jamais diagnostiqué, vérification gestuelle manuelle du conflit pager/sélection, lot 3f non chiffré. Puis **WebDAV** comme second fournisseur derrière la même interface, et la validation OAuth de l'écran de consentement pour lever le plafond des 100 comptes de test.
