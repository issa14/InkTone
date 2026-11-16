# Import en masse : retrouver le budget de 500 EPUB en 5 minutes

**Statut :** à planifier (point séparé, ouvert pendant le lot 25, 2026-11-16).
Aucun changement de code fait : ce document consigne un constat et la première étape.

## Constat sur appareil (V2206, build `benchmark`, 2026-11-16)

Réimport de 551 fichiers (`/sdcard/My library`, 626 Mo) par « Importer » puis
« Tout sélectionner » : environ 2 à 3 minutes par lot de 50 livres, soit 25 à 30
minutes au total. Budget Blueprint §11.2 : 500 EPUB en 5 minutes au plus. L'écart
est d'un facteur 5 à 6. Chronométré à l'écran (compteur « Import en cours · n/50 »),
pas mesuré par poste.

## Ce que coûte un livre, d'après le code (hypothèses, à mesurer)

`ImportPublicationUseCase`, appelé par `ImportWorker` :

1. Empreinte : lecture complète du fichier.
2. Copie dans le stockage privé (ADR-028) : seconde lecture complète et écriture.
3. Analyse de la structure par Readium.
4. Pour un EPUB ou un PDF, chaque chapitre est analysé (`ChapterParser`), découpé en
   phrases, indexé en plein texte et sa pré-analyse écrite en base (lot 22) :
   probablement le poste dominant, payé intégralement pendant l'import.
5. Concurrence : 4 imports à la fois (`ImportWorker.MAX_CONCURRENT_IMPORTS`), lots de
   50 URI enchaînés l'un après l'autre (`WorkManagerImportScheduler`).

## Première étape

Mesurer avant de corriger : `ImportBenchmarkTest` (`infrastructure/worker`, androidTest)
mesure déjà le pipeline contre le budget §11.2. Le compléter d'un temps par poste
(empreinte, copie, structure, chapitres et index, écriture), puis décider, chiffres
en main, si l'indexation et la pré-analyse doivent quitter le chemin de l'import
(tâche de fond après coup) ou seulement devenir moins chères.

Contraintes inchangées : une seule ouverture ZIP par import (K2), WAL (K1), SAF
exclusivement (K5), détection DRM à l'import (K7).
