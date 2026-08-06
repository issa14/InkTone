# Lot 3e — Couche TTS du lecteur

**Base :** `lot-2b-presentation-livres` à `6623a79` (lots 3a→3d intégrés). Référence cible : `UX_FLOW_DESIGN.md` § Lecture — couche TTS.

**Série :** 3a moteur ✅ → 3b chrome ✅ → 3c navigation ✅ → 3d réglages ✅ → **3e couche TTS** (ce lot) → 3f sélection au mot (conditionnel, décision produit post-V1).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

## Découpage en trois paliers poussables

Le lot 3d a montré le défaut d'un lot large sans jalon intermédiaire : cinq fonctions parallèles, une seule vérification à la fin, et une confusion qui a duré une session. Ce lot est découpé en **trois paliers, chacun poussable et vérifiable seul**, dans l'ordre de dépendance :

| Palier | Contenu | Vérifiable seul |
|---|---|---|
| **A** | Barre pilule (3e.1) | Oui — remplace le panneau, 5 contrôles |
| **B** | Repli en FAB (3e.2) | Oui — dépend de A |
| **C** | Onde sonore et geste d'arrêt (3e.3) | Oui — dépend de A |

**Pousser après chaque palier**, ne pas attendre la fin du lot. Si C dérape, A et B restent acquis.

## Vérification préalable

**Les cinq intents existent déjà** : `PreviousChapter` et `NextChapter` (`ReaderUiState.kt:181-182`), `SkipToPreviousSentence` et `SkipToNextSentence` (`:241-242`), Play/Pause. Avec `hasNextChapter`/`hasPreviousChapter` (`:87-88`) pour l'état désactivé aux extrémités. Ce lot est une couche de présentation, pas un chantier de domaine.

---

# PALIER A

## Tâche 3e.1 — Barre pilule flottante

Au lancement du TTS, le panneau unifié est **remplacé** par une barre pilule flottante à cinq contrôles :

`chapitre précédent · phrase précédente · lecture/pause · phrase suivante · chapitre suivant`

- Le contrôle central est le plus proéminent, comme le Play du panneau unifié.
- Les contrôles de chapitre sont **désactivés visuellement** aux extrémités du livre, via `hasPreviousChapter`/`hasNextChapter` — désactivés, pas masqués : une barre dont le nombre d'éléments change sous le doigt est déroutante.
- Le panneau unifié réapparaît à l'arrêt du TTS.

**Cette barre restitue la navigation par chapitre**, retirée du panneau au lot 3b et qui ne passait plus que par le Sommaire. C'est la contrepartie annoncée à l'époque.

**Point à trancher et à consigner :** pendant la lecture, comment atteindre le Sommaire, TT, ou les autres fonctions du panneau unifié ? La cible ne le dit pas. Proposition à valider sur appareil : **un tap sur la zone de lecture rappelle le panneau unifié complet**, la barre pilule restant la couche propre au TTS. Si ce n'est pas l'attendu, seule la règle d'affichage change.

`Ajoute la barre pilule de contrôle TTS`

### Vérifications device — palier A

| # | Avant (`6623a79`) | Après attendu |
|---|---|---|
| A1 | Le panneau unifié reste affiché pendant le TTS, seule l'icône Play devient Pause | Le panneau est remplacé par la barre pilule à 5 contrôles |
| A2 | Navigation par chapitre uniquement par le Sommaire | Accessible depuis la barre pilule |
| A3 | — | En début et fin de livre, les contrôles de chapitre sont grisés, pas absents |
| A4 | — | À l'arrêt du TTS, le panneau unifié revient |
| A5 | — | Pendant la lecture, un tap sur le texte rappelle le panneau complet (comportement à confirmer) |

---

# PALIER B

## Tâche 3e.2 — Repli en bouton flottant

Après **4 s** sans interaction, la barre pilule se replie en un bouton flottant unique. Un tap sur ce bouton la redéploie.

- Réutiliser le délai et le mécanisme d'`ImmersiveReaderChrome`, qui gère déjà l'auto-masquage du HUD à 4 s avec relance du délai à chaque interaction. **Ne pas réimplémenter un second minuteur** : deux mécanismes concurrents sur le même écran divergeront.
- Le repli ne doit pas interrompre la lecture ni le surlignage mot-à-mot, qui reste actif en permanence.
- Respecter `reduceMotion` (`UserPreferences.reduceMotion`) : sans animation, le repli est instantané. Le lecteur le fait déjà pour le surlignage mot-à-mot — même traitement.

`Replie la barre pilule en bouton flottant`

### Vérifications device — palier B

| # | Attendu |
|---|---|
| B1 | Sans y toucher, la barre pilule se replie en bouton après 4 s ; la lecture continue |
| B2 | Un tap sur le bouton la redéploie ; le délai repart |
| B3 | Le surlignage mot-à-mot reste actif pendant tout le cycle |
| B4 | Avec « Réduire les animations » activé, repli et déploiement sont instantanés, sans transition |

---

# PALIER C

## Tâche 3e.3 — Indicateur d'onde sonore et geste d'arrêt

**Onde sonore :** indicateur animé signalant que la synthèse est en cours, distinct de l'état Play/Pause du bouton. Il doit refléter la lecture **réelle**, pas une animation permanente : à l'arrêt ou pendant un blanc de synthèse, il s'arrête. Sinon c'est une décoration, contraire au critère 2. Respecter `reduceMotion` : dans ce cas, un état statique lisible plutôt qu'une animation.

**Geste de balayage vers le bas pour arrêter** — et ici, un conflit à trancher avant de coder.

Le lot 3d a **retiré le bouton Stop** parce que `ReaderViewModel.pausePlayback()` est le seul comportement disponible : il n'existe pas d'arrêt réel qui libère la synthèse et remette la position au début de la phrase. Le KDoc de `ReaderTtsPanel.kt:51-55` le documente.

Le balayage vers le bas ne peut donc pas « arrêter » au sens de la cible. Deux issues, toutes deux acceptables, **mais à choisir explicitement et à consigner** :

- **Implémenter un arrêt réel** dans le ViewModel et le moteur, ce qui restitue du même coup l'option d'un bouton Stop. Chiffrer avant de s'engager.
- **Définir le balayage comme « mettre en pause et replier »**, et corriger la cible en conséquence — ce serait honnête et cohérent avec la décision du lot 3d.

**Ne pas livrer un balayage qui appelle `pausePlayback()` en le nommant « arrêter »** : ce serait la même ambiguïté que les deux boutons identiques que le lot 3d a supprimée.

`Ajoute l onde sonore et le geste de balayage`

### Vérifications device — palier C

| # | Attendu |
|---|---|
| C1 | L'onde s'anime pendant la synthèse et **s'arrête** en pause — elle n'est pas décorative |
| C2 | Avec « Réduire les animations », un état statique lisible remplace l'animation |
| C3 | Le balayage vers le bas produit exactement le comportement décidé, et son libellé le décrit sans ambiguïté |
| C4 | Le balayage ne déclenche pas de défilement du texte au passage |

---

## Tâche 3e.4 — Tests

À écrire au fil des paliers, pas à la fin.

1. **Palier A** — les 5 contrôles émettent leur intent ; aucun callback vide. Les contrôles de chapitre sont désactivés aux extrémités.
2. **Palier A** — la barre pilule apparaît quand `isPlaying` passe à vrai, et le panneau unifié quand il repasse à faux.
3. **Palier B** — un seul minuteur d'inactivité dans le lecteur, pas deux. Test de non-régression contre la duplication de mécanisme.
4. **Palier B** — avec `reduceMotion`, aucune animation de repli.
5. **Palier C** — l'état de l'onde suit l'état réel de synthèse, pas `isPlaying` seul.
6. **Palier C** — le balayage émet l'intent décidé, et un seul.

`Ajoute les tests de la couche TTS`

---

## Tâche 3e.5 — Consigner dans la cible

Dans `UX_FLOW_DESIGN.md`, § Lecture — couche TTS : le comportement retenu pour l'accès aux fonctions du panneau pendant la lecture (3e.1), et surtout le sort du balayage vers le bas selon l'issue retenue en 3e.3 — arrêt réel implémenté, ou geste redéfini en pause avec correction de la cible.

`Consigne l état de la couche TTS dans la cible`

---

## Hors périmètre explicite

Implémentation de la sélection au mot → **lot 3f**, décision produit post-V1, non déclenchée.

Après ce lot, le lecteur est complet au regard de la cible. Restent hors série : Récents, Synchronisation, Galerie de thèmes et Studio, Onboarding, cartes manquantes des Réglages, sections manquantes des Statistiques, et l'audit du consentement Crashlytics.
