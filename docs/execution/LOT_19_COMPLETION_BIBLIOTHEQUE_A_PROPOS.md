# Lot 19 — Complétion fonctionnelle : Bibliothèque & À propos

**Base :** `main` + `LOT_17_CORRECTIONS_RAPIDES_UX.md` et
`LOT_18_DRAWER_NAVIGATION_UNIFIEE.md` mergés. Références :
`docs/execution/UX_FLOW_DESIGN.md` (sections Bottom sheet 3-points lignes
216-224, Bibliothèque écran complet lignes 110-115, À propos lignes
808-835, Synchronisation lignes 738-776), `LOT_13_CATALOGUES_OPDS.md`
(référence de style pour les écarts délibérés déclarés explicitement).

Dernier des 3 Lots de la série de réconciliation UX : regroupe les écarts
qui demandent du vrai travail de conception/domain, contrairement aux
corrections mécaniques du Lot 17 et au chantier de navigation pur du
Lot 18.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil ·
5. Écart déclaré.

## Constat vérifié

1. **Écran À propos très en retard sur sa spec.**
   `core/ui/src/main/kotlin/com/inktone/core/ui/AboutScreen.kt` — version
   actuelle ancienne et minimale : `Text("Version $versionName", ...)` non
   cliquable (l.51), deux `InfoCard` en pied de colonne (l.62-64), liste de
   licences statique (`LicenseRow`, l.68-75) sans badge coloré. La spec
   (`UX_FLOW_DESIGN.md:808-835`) décrit :
   - Badge de version cliquable (court/long), snackbar « Version
     officielle », copie diagnostic.
   - Grille 3 colonnes « Engagements & Confidentialité ».
   - Carte GitHub.
   - Carte « Signaler un problème » à bordure accentuée, email pré-rempli
     (recherche `grep -rln "Signaler"` : aucun résultat dans le code, hors
     le document lui-même).
   - Accordéon dépliable « Architecture Tech & Licences » avec badges de
     licence colorés (au lieu de la liste statique actuelle).
   Seule la correction Piper→Kokoro est déjà présente dans le texte (l.63,
   l.71). Dernière modification du fichier : 2026-08-11, antérieure à la
   dernière mise à jour du document (2026-08-14) — la conception n'a
   jamais été implémentée pour cet écran, pas une régression.
2. **Bottom sheet 3-points de la Bibliothèque incomplet.**
   `feature/library/.../LibraryScreen.kt:526-540` — 2 actions présentes
   (Importer, Actualiser) sur les 5 spécifiées
   (`UX_FLOW_DESIGN.md:216-224,110-115`) : Couverture par défaut,
   Reconstruire les couvertures, Ouvrir un livre au hasard, Synchroniser
   avec le cloud. Aucune des 4 chaînes correspondantes n'existe nulle part
   dans le code Kotlin (`grep` négatif). Deux sous-groupes de nature très
   différente :
   - « Ouvrir un livre au hasard » et « Synchroniser avec le cloud » :
     données déjà disponibles (liste de publications côté ViewModel,
     mécanisme de sync déjà opérationnel côté `feature/sync`/
     `infrastructure/sync` pour Google Drive) — ajout UI + câblage simple.
   - « Couverture par défaut » et « Reconstruire les couvertures » :
     `regenerateCovers`/`resetCovers` n'existent **nulle part** dans
     `domain`/`data` (confirmé par grep exhaustif) — nécessite un vrai use
     case + méthode repository avant tout bouton, pas un simple ajout
     Compose. À traiter comme sous-tâche séparée et signalée comme telle.
3. **Carte WebDAV — stub non fonctionnel.**
   `feature/sync/.../SyncConfigurationScreen.kt:291-313` (`WebDavCard()`) :
   texte fixe « WebDAV — Bientôt disponible », grisé en permanence,
   commentaire du code confirmant explicitement : *« aucune implémentation
   n'existe encore (WebDAV arrive après ce lot) »*. La spec
   (`UX_FLOW_DESIGN.md:738-776`) décrit une carte fonctionnelle complète
   (badge ACTIF/INACTIF, champs URL/Identifiant/Mot de passe, bouton
   Tester, badge Connecté, grisage conditionnel réciproque avec Google
   Drive — « Désactivez WebDAV pour connecter Google Drive »). La règle
   actée « mutuellement exclusif WebDAV/Google Drive » n'a donc aucune
   logique réelle à tester puisqu'un seul des deux fournisseurs existe.
   Probablement un différé assumé (cohérent avec le commentaire du code)
   plutôt qu'un oubli, mais jamais formellement déclaré comme tel dans
   `UX_FLOW_DESIGN.md` — la conclusion du document présente l'écran Sync
   comme entièrement conçu sans nuancer ce point.

## Tâches

1. **Écran À propos** — refonte selon la spec (`core/ui` uniquement, aucun
   changement domain/data) :
   - Badge de version cliquable avec copie diagnostic + snackbar.
   - Grille 3 colonnes Engagements & Confidentialité.
   - Carte GitHub + carte « Signaler un problème » (email pré-rempli, à
     déterminer avec l'utilisateur — pas d'adresse à inventer).
   - Accordéon « Architecture Tech & Licences » avec badges de licence
     colorés, remplaçant la liste statique actuelle.
   Commit : `Refond l'écran À propos selon la spec UX_FLOW_DESIGN`.
2. **Bottom sheet 3-points — sous-tâche simple** — ajouter « Ouvrir un
   livre au hasard » (sélection aléatoire dans `displayedPublications`) et
   « Synchroniser avec le cloud » (déclenche le mécanisme de sync existant,
   bascule vers l'écran de configuration si non configuré, comme spécifié).
   Commit : `Ajoute livre au hasard et synchroniser au menu 3-points`.
3. **Bottom sheet 3-points — sous-tâche domain/data** — use case
   `RegenerateCoversUseCase`/méthode repository `resetCoversToDefault`
   avant tout bouton UI. À cadrer précisément avant de coder (quelle est la
   source de la couverture par défaut ? régénération = re-extraction depuis
   le fichier source, ou re-génération de la couverture procédurale
   utilisée par `BookCover.kt` pour les livres sans couverture ? — vérifier
   `BookCover.kt:267-274` pour les palettes déjà en place). Progression
   live si l'opération est longue (cohérent avec la spec « avec
   progression live X/Y » du menu legacy cité dans
   `PLAN_ACTION_INKTONE_TOP_TIER.md`). Commit :
   `Ajoute régénération/réinitialisation des couvertures (domain+data+UI)`.
4. **Carte WebDAV** — décision à prendre en ouverture de tâche, pas ici :
   - Soit implémenter WebDAV (champs, test de connexion, badge, exclusion
     mutuelle avec Google Drive) — travail réseau conséquent, comparable au
     Lot 11 (Google Drive) en ampleur.
   - Soit documenter formellement le différé dans `UX_FLOW_DESIGN.md`
     (style « écarts délibérés » de `LOT_13_CATALOGUES_OPDS.md`), avec la
     justification déjà présente dans le commentaire du code, pour que la
     conclusion du document ne présente plus l'écran Sync comme
     entièrement opérationnel sans nuance.
   Commit selon l'option retenue.

## Ce qu'on ne fait pas dans ce Lot

- Corrections mécaniques (`lastOpened`, FAB Import, footer drawer, padding
  Reader, `StatusLineBar`, écarts mineurs, dérive documentaire) — Lot 17.
- Navigation unifiée du drawer — Lot 18.
- Tout item déjà confirmé conforme par l'audit (Statistiques, Galerie de
  thèmes/Studio, PDF, Bibliothèque état vide/peuplé, marque-pages/notes,
  reste du panneau Lecteur, Écran Opérationnel de Sync hors WebDAV) n'est
  pas retouché.

## Critères de sortie du Lot

- [ ] Écran À propos conforme à la spec, vérifié sur device (badge
      cliquable, grille, cartes, accordéon).
- [ ] Menu 3-points Bibliothèque : « Ouvrir un livre au hasard » et
      « Synchroniser avec le cloud » fonctionnels.
- [ ] Régénération/réinitialisation des couvertures fonctionnelle,
      testée (use case + repository), vérifiée sur device avec au moins
      un livre sans couverture.
- [ ] Statut de la carte WebDAV tranché : soit fonctionnelle et testée,
      soit formellement documentée comme différée dans
      `UX_FLOW_DESIGN.md`.
- [ ] `./gradlew build` vert.
