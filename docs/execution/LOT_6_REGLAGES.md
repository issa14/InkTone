# Lot 6 — Écran Réglages

**Base :** `main` à `755578ef`. Branche : `lot-6-reglages`. Référence cible : `UX_FLOW_DESIGN.md` § Réglages.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Écart de structure

L'écran actuel a **7 sections** qui ne correspondent pas au découpage cible en **6 cartes** : Lecture, Voix, Langue, Confidentialité, Apparence, Accessibilité, À propos. Ce n'est pas un problème de contenu manquant seulement — c'est une réorganisation.

| Carte cible | État |
|---|---|
| 1. Présets rapides | ❌ Un simple `Button` « Appliquer le preregalage d'accessibilite » (`:181-186`) : pas un toggle, pas de préset Mode sombre, aucune désapplication |
| 2. Lecture | ⚠️ Éclatée sur « Lecture » (`:124-130`) et « Voix » (`:131-145`). Manquent : vitesse d'élocution, intonation, écouter un extrait |
| 3. Appareil | ⚠️ Les 4 réglages existent (`:157-180`) mais sur deux sections. **Le thème système n'est réglable nulle part** |
| 4. Données | ❌ Absente. `BackupManager` existe, testé, branché nulle part |
| 5. Prononciation | ⚠️ Écran séparé atteint par une ligne « Gerer » (`:144`), pas la carte inline |
| 6. Performance & Bien-être | ❌ Absente — mais les trois préférences existent déjà |

Sections codées **sans contrepartie cible** : Langue (`:146-150`), Confidentialité (`:151-156`), À propos (`:188-190`).

**Point important sur le thème.** `SettingRow("Theme", preferences.theme.name)` (`:125`) porte sur `ReadingTheme` — le thème **de lecture**. La cible attend ici un thème **système** Système / Clair / Sombre, qui n'existe dans aucune préférence. Ne pas confondre les deux : ce sont deux réglages distincts, et le second est à créer.

## Découpage en deux paliers poussables

| Palier | Contenu | Risque |
|---|---|---|
| **A** | Restructuration + cartes 1, 2, 3, 6 | Faible — les préférences existent |
| **B** | Carte Données + Prononciation inline | Élevé — SAF, actions destructives |

**Pousser après le palier A.**

---

# PALIER A

## Tâche 6.1 — Restructurer en six cartes

Passer des 7 sections aux 6 cartes cibles. Les sections Langue et Confidentialité n'ont pas de place dans la cible : les rattacher à la carte **Appareil**, ou les consigner comme ajouts assumés. **Ne pas les supprimer** — ce sont des réglages fonctionnels, notamment le consentement Crashlytics.

La ligne « À propos » reste : c'est l'un des deux chemins vers cet écran.

**Corriger au passage :**
- **Les accents.** « Reglages », « Theme », « Accessibilite », « Confidentialite », « Regles de prononciation », « Moteur par defaut »… Un écran entier sans accents dans une application française d'abord.
- **Le `PickerDialog`** (`:331`) place « Annuler » dans le slot `confirmButton` et n'a aucun bouton de confirmation. Défaut signalé à l'audit initial, toujours présent. La sélection au clic est défendable ; le libellé dans ce slot ne l'est pas.

`Restructure l ecran Reglages en six cartes`

---

## Tâche 6.2 — Carte Présets rapides

Deux cartes-boutons empilées, avec **toggle** — donc réversibles, ce que le bouton actuel n'est pas.

- **Mode sombre** → `ReadingTheme.OBSIDIAN` + thème système sombre.
- **Accessibilité** → OpenDyslexic + 24 sp + thème clair + `reduceMotion` + **bascule en mode Liste** (décision actée au lot 2b).

Un toggle implique de savoir **désappliquer**. Deux approches à trancher explicitement : mémoriser l'état antérieur pour le restaurer, ou revenir aux valeurs par défaut. La seconde est plus simple et plus prévisible — la première surprend si l'utilisateur a modifié des réglages entre-temps.

Le toggle doit refléter l'état réel : si l'utilisateur change manuellement un des réglages du préset, le toggle se désactive. Un toggle qui reste allumé sur un préset partiellement défait serait un contrôle menteur.

`Ajoute la carte des presets rapides`

---

## Tâche 6.3 — Carte Lecture

Regrouper les réglages existants et compléter :

| Réglage | État |
|---|---|
| Moteur | ✅ existe |
| Voix | ⚠️ existe mais en **cycle** (`nextVoiceProfileId`), pas en dialogue |
| Vitesse d'élocution | ❌ à ajouter — `VoiceProfile.speed`, **déjà branché à la synthèse** au lot 3d. Réutiliser ce câblage, ne pas en créer un second |
| Gain audio | ✅ existe |
| Intonation | ❌ à ajouter — `VoiceProfileEntity.pitch` existe ; vérifier qu'il atteint la synthèse comme `speed`, et **le signaler** sinon plutôt que d'exposer un curseur sans effet |
| Écouter un extrait | ❌ à ajouter — synthèse d'une phrase courte avec les réglages courants |

Le sélecteur de voix passe du cycle au dialogue : avec plusieurs profils, cycler devient impraticable.

`Complete la carte Lecture des reglages`

---

## Tâche 6.4 — Carte Appareil

Deux sous-sections dans une carte : **Apparence** (thème système, couleurs dynamiques) et **Accessibilité** (réduire les animations, police système).

**Le thème système est à créer** : ajouter la préférence Système / Clair / Sombre, l'appliquer au thème de l'application, et **ne pas la confondre** avec `preferences.theme` qui régit le thème de lecture. Deux réglages, deux champs.

`Ajoute la carte Appareil et le theme systeme`

---

## Tâche 6.5 — Carte Performance & Bien-être

**Pure UI** — les trois préférences existent déjà (`dailyGoalMinutes`, `eyeRestReminderEnabled`, `eyeRestReminderIntervalMinutes`), les deux dernières posées au lot 3d précisément pour que cette carte s'y branche sans refonte.

- Objectif quotidien : dialogue-curseur, 10 à 120 min.
- Rappel de repos oculaire : interrupteur.
- Intervalle : pas de 15 min, désactivé quand le rappel est éteint.

`Ajoute la carte Performance et Bien-etre`

---

## Tâche 6.6 — Tests du palier A

1. Chaque préset applique **tous** ses réglages, et les défait tous.
2. Modifier manuellement un réglage d'un préset actif éteint le toggle.
3. Le thème système et le thème de lecture sont **deux** valeurs indépendantes : changer l'un ne modifie pas l'autre.
4. Le curseur de vitesse écrit dans le profil de voix — même cible que le panneau du lecteur, pas un second emplacement.
5. L'intervalle de repos oculaire est inopérant quand le rappel est éteint.
6. Non-régression : aucun libellé sans accent dans l'écran.

`Ajoute les tests des cartes de reglages`

### Vérifications device — palier A

| # | Avant (`755578ef`) | Après attendu |
|---|---|---|
| A1 | 7 sections | 6 cartes conformes |
| A2 | Bouton d'accessibilité à sens unique | Toggle réversible ; le désactiver restaure l'état |
| A3 | Vitesse non réglable depuis les Réglages | Réglable ; la valeur est **la même** que dans le panneau Voix du lecteur |
| A4 | — | « Écouter un extrait » parle, avec les réglages courants |
| A5 | Thème système non réglable | Système / Clair / Sombre change l'app sans toucher au thème de lecture |
| A6 | — | Objectif quotidien et rappel oculaire réglables ; l'intervalle se grise |

---

# PALIER B

## Tâche 6.7 — Carte Données

`BackupManager` (`data/backup/BackupManager.kt:28`) expose `exportTo(destinationUri, appVersion)` et `importFrom(sourceUri): ImportBackupResult`. Il est **testé et branché nulle part** : l'utilisateur n'a aujourd'hui aucun moyen d'exporter ses données.

| Entrée | À faire |
|---|---|
| Dossier des modèles | Afficher le chemin ; en lecture seule si le déplacement n'est pas géré — **le signaler** plutôt que d'offrir un chemin non modifiable |
| Exporter | SAF création de document → `exportTo` avec la version réelle de l'app |
| Importer | SAF ouverture → `importFrom`, **avec avertissement préalable** : l'import remplace les données |
| Vider le cache | Afficher la **taille réelle** avant, confirmation, puis vidage |
| Réinitialiser | Couleur d'alerte, confirmation explicite |

**Trois exigences :**

- **Les résultats doivent remonter.** `ImportBackupResult` est un type de retour — le montrer, ne pas le jeter. C'est exactement le défaut corrigé au lot 5 pour l'import de livres.
- **Aucune action destructive sans confirmation.** Import, vidage, réinitialisation. La confirmation nomme ce qui sera perdu, sur le modèle de celle du lot 2b.
- **La taille du cache doit être calculée**, pas estimée ni omise. Un chiffre affiché doit être vrai.

`Ajoute la carte Donnees et branche la sauvegarde`

---

## Tâche 6.8 — Prononciation inline

Convertir l'écran séparé en **carte inline** : en-tête « Dictionnaire phonétique (n) », liste des règles, bouton `+` ouvrant un dialogue modal.

**Vérifier avant de supprimer la route** que rien d'autre n'y mène — le panneau Voix du lecteur porte un lien « Ajouter une règle de prononciation » depuis le lot 3d. Si ce lien pointe vers `PronunciationRulesRoute`, soit la route est conservée pour lui, soit le lien est redirigé. **Ne pas casser un chemin existant** : c'est le défaut du lot 1.

`Convertit la prononciation en carte inline`

---

## Tâche 6.9 — Tests du palier B

1. Export puis import restitue les données — aller-retour complet, pas seulement l'appel.
2. Chaque cas d'`ImportBackupResult` produit un retour visible et distinct.
3. Les trois actions destructives sont bloquées sans confirmation ; refuser n'appelle rien.
4. La taille de cache affichée correspond à la taille réelle.
5. Le lien du panneau Voix du lecteur mène toujours quelque part.

`Ajoute les tests de la carte Donnees`

### Vérifications device — palier B

| # | Attendu |
|---|---|
| B1 | Exporter crée un fichier réel, ouvrable hors de l'app |
| B2 | Réinstaller l'app, importer la sauvegarde : livres, marque-pages et annotations sont restitués |
| B3 | Importer un fichier invalide : message clair, aucune donnée perdue |
| B4 | Vider le cache : la taille affichée avant correspond, et retombe après |
| B5 | Les trois actions destructives demandent confirmation ; annuler ne fait rien |
| B6 | Le lien « Ajouter une règle de prononciation » du lecteur fonctionne toujours |

Le point B2 est le seul qui prouve que la sauvegarde sert à quelque chose. Un export qui produit un fichier illisible passerait tous les autres.

---

## Tâche 6.10 — Consigner

Dans `UX_FLOW_DESIGN.md`, § Réglages : le sort des sections Langue et Confidentialité (rattachées ou assumées en plus), l'approche retenue pour la désapplication des présets, et l'état du dossier des modèles s'il reste en lecture seule.

`Consigne l etat de l ecran Reglages dans la cible`

---

## Hors périmètre explicite

Récents, Synchronisation, Galerie de thèmes et Studio, Onboarding, sections manquantes des Statistiques, audit Crashlytics, lot 3f.
