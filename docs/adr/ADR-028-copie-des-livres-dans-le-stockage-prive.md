# ADR-028 : Copie des livres importés dans le stockage privé de l'application

**Status :** Accepted
**Date :** 2026-11-15

## Context

Un livre importé n'était qu'un lien vers le fichier choisi par l'utilisateur :
`Publication.fileUri` gardait l'URI SAF du sélecteur, et l'import prenait une
permission de lecture durable sur elle (audit UX, constat **D55**).

Ce lien casse facilement :

- le fichier est déplacé, renommé ou supprimé (gestionnaire de fichiers,
  nettoyage du dossier Téléchargements) ;
- la permission durable n'a pas pu être prise : l'échec était avalé
  (`SafFileStorageService.persistReadPermission`, sous `runCatching`) ;
- Android borne le nombre de permissions durables par application.

Dans chacun de ces cas, le livre restait en bibliothèque, avec sa couverture
et sa progression, mais ne s'ouvrait plus (« corrompu »), sans moyen de le
relocaliser.

Les livres téléchargés depuis un catalogue OPDS ne sont pas concernés : ils
sont déjà écrits dans le stockage de l'application (`opds_downloads/`) et
servis par le FileProvider de l'application (Lot 13). En revanche, leur
fichier n'était jamais supprimé avec le livre (constat **D63**).

L'auteur a décidé le principe le 2026-11-12 (audit UX §8) : un livre importé
est copié dans le stockage privé de l'application.

## Decision

1. **À l'import**, après le calcul de l'empreinte et la détection des
   doublons, le fichier est copié dans `files/library/<empreinte>.<extension>`
   (stockage interne, invisible pour l'utilisateur) et servi par le
   FileProvider de l'application (`content://<application>.fileprovider/library/…`).
   `Publication.fileUri` désigne cette copie. Les parseurs lisent une URI
   `content://` comme avant : ils ne changent pas.
2. **Un fichier qui appartient déjà à l'application** (téléchargement OPDS)
   n'est pas recopié.
3. **Plus de permission durable** sur le fichier d'origine : il n'est lu
   qu'une fois, pendant l'import, sous la permission accordée par le
   sélecteur. K5 tient : l'accès au fichier d'origine passe toujours par
   SAF, et `MANAGE_EXTERNAL_STORAGE` reste interdit.
4. **Copie impossible** (espace disque insuffisant) : l'import échoue avec
   un message explicite. Aucun livre n'est créé sans sa copie.
5. **Suppression d'un livre** : son fichier est supprimé s'il appartient à
   l'application (copie de bibliothèque ou téléchargement OPDS). Cela
   corrige aussi D63.
6. **Rattrapage des livres déjà importés** : au lancement, la tâche de
   maintenance qui régénère les pré-analyses copie d'abord chaque livre dont
   le fichier n'appartient pas encore à l'application, puis fait pointer
   `fileUri` sur la copie. Elle ne réussit que si la permission sur le
   fichier d'origine tient encore. Un échec laisse le livre tel quel, et la
   tâche le retentera au lancement suivant.

## Alternatives écartées

- **Garder le lien et proposer de relocaliser le fichier** : traite le
  symptôme, laisse l'utilisateur gérer ses fichiers, et dépend toujours du
  plafond des permissions durables.
- **Stockage externe propre à l'application** (`getExternalFilesDir`, comme
  les téléchargements OPDS) : visible et modifiable par un gestionnaire de
  fichiers. Le stockage interne garantit que le fichier d'un livre ne
  disparaît qu'avec le livre, ou avec les données de l'application.
- **Nommer la copie par l'identifiant du livre** : l'identifiant n'existe
  qu'après l'analyse. L'empreinte existe dès le début de l'import, et
  désigne déjà le livre de façon unique (détection des doublons).

## Consequences

- **Double occupation disque** tant que l'utilisateur garde le fichier
  d'origine. Il peut le supprimer sans perdre le livre.
- Effacer les données de l'application efface les livres eux-mêmes, plus
  seulement leur progression. La sauvegarde (D58) devra en tenir compte.
- Les livres déjà importés dont la permission est perdue ne sont pas
  rattrapables : ils restent non ouvrables, comme avant. Rien ne les
  signale encore en bibliothèque.
- Un import qui échoue faute d'espace est classé « corrompu » dans le
  résumé d'import, qui n'a pas de catégorie dédiée ; le détail du fichier
  donne la vraie raison.
