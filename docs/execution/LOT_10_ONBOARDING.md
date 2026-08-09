# Lot 10 — Onboarding

**Base :** `main` à `d79f6bed`. Branche : `lot-10-onboarding`. Référence cible : `UX_FLOW_DESIGN.md` § Onboarding.

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

## Contexte

Dernier chantier de la série hors Synchronisation, et **le seul qui n'a jamais avancé** : `OnboardingContract.kt:5` porte toujours `Welcome, CrashConsent, VoiceDownload, Done`, et aucune référence à `Onboarding` n'existe dans le module `app`. L'écran n'a jamais été affiché à personne.

Ses deux dépendances sont désormais levées : **textes validés** (ci-dessous) et **illustrations fournies** en Compose Canvas.

## Textes validés — à reprendre à la lettre

**Carte 1 — Bienvenue**
- Titre : « Bienvenue sur InkTone »
- Corps : « Lisez avec les yeux, continuez avec les oreilles. Une expérience de lecture unifiée qui s'adapte à votre rythme. »

**Carte 2 — Conçu pour votre confort**
- Titre : « Conçu pour votre confort »
- Bloc gauche : « Lecture sur mesure » — « Thèmes, typographie et mise en page entièrement personnalisables pour un confort visuel absolu. »
- Bloc droit : « Narration naturelle » — « Des voix ultra-réalistes et fluides. Ajustez la vitesse et laissez-vous porter par l'histoire. »

**Carte 3 — Clôture**
- Titre : « Votre prochaine histoire vous attend »
- Sous-texte : « Importez vos livres et commencez l'expérience InkTone. »
- Bouton : « Commencer »

---

## Tâche 10.1 — Intégrer les illustrations

Le code fourni (`OnboardingIllustrations.kt`) est juste dans son intention et le motif est le bon — livre ouvert et ondes sonores traduisent exactement « lisez avec les yeux, continuez avec les oreilles ». **Six corrections avant intégration.**

**1. Les couleurs codées en dur — la plus importante.**

```
val InkToneBordeaux = Color(0xFF7A1F3D)
val NeutralIconColor = Color(0xFFE6E1D8)
```

`NeutralIconColor` est un crème clair : sur un fond clair il sera quasi invisible. Le commentaire du fichier le reconnaît (« à lier à ton MaterialTheme.colorScheme.onSurface idéalement »).

**C'est l'argument même qui a fait préférer les vectoriels composés à une image figée** : l'adaptation au thème. Avec des littéraux, on obtient une image figée écrite en Kotlin. Passer les deux couleurs en paramètres, avec pour défauts `MaterialTheme.colorScheme.onSurface` et `MaterialTheme.colorScheme.primary`.

*Note :* le bordeaux `#7A1F3D` est la couleur signature du projet, déjà celle de l'icône d'application. Le conserver comme accent est légitime — mais il doit venir du thème, où il est déjà défini, pas d'un littéral dupliqué.

**2. Les valeurs en pixels bruts.** `CornerRadius(12f, 12f)`, `CornerRadius(50f, 50f)`, `floatArrayOf(20f, 30f)`, `+ 20f`, `+ 10f` sont des pixels, pas des `dp`. Dans un `Canvas`, `size` est en pixels : un rayon de 12 px sur un écran en densité 3× fait 4 dp au lieu de 12. Le rendu changera d'un appareil à l'autre. Tout convertir par `.dp.toPx()`.

**3. `quadraticBezierTo` est déprécié** depuis Compose UI 1.7 — le BOM du projet est `2024.09.02`. Utiliser `quadraticTo`. Compile aujourd'hui, avec avertissement ; autant le faire proprement.

**4. La taille est imposée de l'intérieur.** `modifier.size(200.dp)` et `.size(220.dp)` s'appliquent **après** le modificateur de l'appelant et gagnent donc sur lui. Sur un petit écran ou en paysage, l'illustration ne pourra pas se réduire. Laisser l'appelant dimensionner, ou faire de la taille un paramètre avec défaut.

**5. Cartes 1 et 3 trop proches.** Les deux montrent un livre entouré d'ondes. Dans un pager de trois cartes, la première et la dernière se ressembleront fortement. La carte 3 étant l'appel à l'action, la différencier — par exemple par une composition plus resserrée, ou en supprimant le livre au profit des seules ondes convergentes. **Point à juger sur appareil**, pas sur le code.

**6. Sémantique.** Les `Canvas` n'ont aucune description. Décoratives, elles doivent l'être **explicitement** (`contentDescription = null` et exclusion de l'arbre d'accessibilité), pour que TalkBack lise le texte de la carte et pas des éléments muets.

**Débordement à vérifier :** les rayons `(i * 30).dp.toPx()` et `(i * 25).dp.toPx() + 20.dp.toPx()` peuvent dépasser les bornes du `Canvas` et se retrouver rognés. Visible seulement au rendu.

`Integre les illustrations vectorielles de l onboarding`

---

## Tâche 10.2 — Réécrire l'écran

L'écran actuel est un `when` sur un enum avec des `Column` et `Button` nus. La cible demande un **`HorizontalPager` à trois cartes** :

- Balayage horizontal entre les cartes.
- **Trois indicateurs de position**.
- Bouton **« Passer »** sur les cartes 1 et 2.
- Bouton **« Commencer »** sur la carte 3, qui termine l'onboarding.

`Reecrit l onboarding en pager de trois cartes`

---

## Tâche 10.3 — Retirer les deux étapes fonctionnelles

Décision actée depuis la conception : *« l'onboarding reste une pure présentation, sans rien de fonctionnel »*. Retirer `CrashConsent` et `VoiceDownload` de `OnboardingStep`, ainsi que les intents `SetCrashReporting` et `StartVoiceDownload`.

**Vérifier — et c'est la partie qui compte — que les deux fonctions existent bien à leur point de besoin réel :**

- **Consentement crash** : réglable dans la carte Confidentialité des Réglages depuis le lot 6. Confirmer.
- **Téléchargement de voix** : doit être déclenché au premier usage du TTS. **Si ce point de besoin n'existe pas, le signaler** — retirer l'étape sans vérifier laisserait l'utilisateur sans aucun moyen d'obtenir une voix. Ce serait pire que la situation actuelle.

`Retire les etapes fonctionnelles de l onboarding`

---

## Tâche 10.4 — Route et premier lancement

- `OnboardingRoute` dans `Routes.kt`, destination dans le `NavHost`.
- Indicateur « onboarding vu » persisté dans `UserPreferences`.
- `startDestination` arbitrée dessus : onboarding au premier lancement, Bibliothèque ensuite.

**Deux pièges :** l'onboarding ne doit pas réapparaître après une rotation ou un retour d'arrière-plan ; et le retour système depuis la carte 1 ne doit pas laisser l'utilisateur sur un écran vide.

`Cable l onboarding au premier lancement`

---

## Tâche 10.5 — Audit du consentement Crashlytics

Consigné dans `docs/execution/LOT_ONBOARDING_PERIMETRE.md` depuis le lot 3c, où le commit était arrivé hors périmètre et n'a donc **jamais été vérifié pour ce qu'il est**. Quatre points : point de déclenchement réel, formulation du consentement, comportement en cas de refus, conformité à ADR-014.

`Audite le consentement Crashlytics arrive hors perimetre au lot 3c`

---

## Tâche 10.6 — Illustration de l'état vide de la Bibliothèque

Dette du lot 2a.6, correctement signalée à l'époque : l'illustration « étagère avec emplacements en pointillés » n'a pas été produite, `AppIcons.Reading` sert de repli et n'a **pas** été présenté comme conforme (`LibraryScreen.kt:774-780`).

Le même procédé qu'en 10.1 la rend réalisable. La produire ici ferme la dernière dette d'illustration du projet.

`Ajoute l illustration de l etat vide de la bibliotheque`

---

## Tâche 10.7 — Tests

1. Le pager compte trois cartes ; « Passer » n'apparaît pas sur la troisième.
2. « Commencer » et « Passer » terminent tous deux l'onboarding et posent l'indicateur.
3. Au second lancement, l'onboarding **ne s'affiche pas**.
4. Aucune étape de consentement ni de téléchargement ne subsiste — non-régression de 10.3.
5. Les illustrations ne contiennent **aucune** couleur littérale ; changer de thème change leur rendu.
6. TalkBack lit les textes des cartes, pas des éléments graphiques muets.

`Ajoute les tests de l onboarding`

---

## Tâche 10.8 — Consigner

Dans `UX_FLOW_DESIGN.md`, § Onboarding : retirer la mention « inventé par Claude, à valider » sur les textes, désormais validés. Consigner le parti pris d'illustrations vectorielles composées plutôt qu'assets générés, et le résultat de l'audit Crashlytics.

`Consigne l etat de l onboarding dans la cible`

---

## Vérifications sur appareil

| # | Avant (`d79f6bed`) | Après attendu |
|---|---|---|
| 1 | L'onboarding n'existe pas | Premier lancement sur installation neuve : les trois cartes s'affichent |
| 2 | — | Balayage entre les cartes, indicateurs de position corrects |
| 3 | — | « Passer » sur 1 et 2, « Commencer » sur 3 ; les deux mènent à la Bibliothèque |
| 4 | — | Second lancement : accès direct à la Bibliothèque |
| 5 | — | Rotation pendant l'onboarding : pas de redémarrage du pager |
| 6 | — | Illustrations lisibles en thème **clair et sombre**, sans zone invisible |
| 7 | — | Rendu identique en proportions sur un écran petit ou en paysage, sans rognage des ondes |
| 8 | — | Cartes 1 et 3 suffisamment différenciées à l'usage |
| 9 | — | Lancer le TTS sans voix installée : le téléchargement est bien proposé |
| 10 | — | État vide de la Bibliothèque : illustration d'étagère, plus l'icône générique |

Le point 9 est le plus important : il vérifie que retirer l'étape de téléchargement n'a pas rendu les voix inaccessibles. Le point 6 vérifie que la correction 1 a bien été faite — c'est l'objet même du choix des vectoriels.

---

## Après ce lot

Ne reste que la **Synchronisation**, dernière destination masquée du drawer, en attente d'un arbitrage de périmètre V1. Plus les dettes de fond : test flake jamais diagnostiqué, vérification gestuelle manuelle du conflit pager/sélection, lot 3f devenu décidable mais non chiffré.
