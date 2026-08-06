# Lot 3d — Sous-écrans de réglages du lecteur

**Base :** `lot-2b-presentation-livres` à `40114ca` (lots 3a, 3b, 3c intégrés). Référence cible : `UX_FLOW_DESIGN.md` § Lecture — HUD (TT, Thème, Luminosité, Minuteur, Haut-parleur).

**Série :** 3a moteur ✅ → 3b chrome ✅ → 3c navigation ✅ → **3d réglages** (ce lot) → 3e couche TTS → 3f sélection au mot (conditionnel, décision produit post-V1).

## Contrat applicable (inchangé)

1. Atteignable · 2. Zéro décoration · 3. Testé · 4. Vérifié sur appareil · 5. Écart déclaré.

Claude Code ne déclare pas le lot terminé : il livre, signale ce qu'il n'a pas pu vérifier, la clôture se fait sur appareil.

## Vérification préalable du périmètre

Deux surprises, dans les deux sens, établies par lecture du code avant rédaction :

- **La vitesse TTS est bien moins coûteuse que prévu.** `VoiceProfile.speed` existe (`VoiceProfile.kt:16`) et est **déjà consommé à la synthèse** : `SherpaOnnxTtsEngine.kt:146` (`speed = voiceProfile.speed`) et `AndroidNativeTtsEngine.kt:162` (`setSpeechRate`). Les deux moteurs déclarent `speedControl = true`. Le curseur mort est un branchement UI, pas un chantier domaine.
- **L'interligne n'existe nulle part.** Aucune occurrence de `lineHeight` dans le domaine ni dans `UserPreferences`. C'est le seul vrai ajout de modèle du lot.

---

## Tâche 3d.1 — Panneau Voix

`ReaderTtsPanel.kt`. Trois défauts, dont deux sont des contrôles trompeurs.

**Curseur de vitesse mort.** `ReaderScreen.kt` passe `currentSpeed = 1.0f` en dur avec un `TODO`, et `onSpeedChange = { }` vide. Le curseur revient à 1,0× à chaque ouverture et son déplacement n'a aucun effet — c'est l'antipattern « contrôle décoratif », le dernier du lecteur.

À brancher sur le profil de voix actif : lire `VoiceProfile.speed`, écrire par la mise à jour du profil. **Ne pas ajouter de champ à `UserPreferences`** : la vitesse appartient au profil de voix, et c'est ce que la synthèse lit déjà. Ajouter un second emplacement créerait deux sources pour la même valeur.

**Bouton Stop qui met en pause.** `onStop` est câblé sur `ReaderIntent.Pause` (`ReaderScreen.kt`). Un bouton Stop rouge distinct du bouton Pause doit arrêter : libérer la synthèse et remettre la position au début de la phrase courante, pas suspendre. Si l'arrêt réel n'est pas disponible dans le moteur, **le signaler** plutôt que de garder deux boutons au même comportement — dans ce cas, retirer le Stop.

**Sélecteur de voix absent.** La cible demande le nom réel de la voix active, au format `ff_siwis · Kokoro · Français`. Les profils existent en base (`VoiceProfileEntity`). Ajouter la sélection, et le lien **« Ajouter une règle de prononciation »** vers `PronunciationRulesRoute` — atteignable depuis le lot 1.

**Retirer les puces de minuteur** (15/30/45/60) qui vivent aujourd'hui dans ce panneau : elles appartiennent au Minuteur (3d.4).

`Branche la vitesse de lecture et complète le panneau Voix`

---

## Tâche 3d.2 — Panneau TT

`ReaderSettingsPanel.kt`. Le panneau mélange aujourd'hui thème et taille ; la cible en fait un panneau de typographie seule.

| Cible | État |
|---|---|
| Bottom sheet | ✅ déjà le cas |
| Aperçu du **vrai texte** en direct | ❌ absent |
| Curseur de taille **continu** | ⚠️ `steps = 19` → paliers discrets (`ReaderSettingsPanel.kt:88`) |
| Curseur d'**interligne** | ❌ absent |
| Thème | ⚠️ **à retirer** : 3 cartes (`ReaderSettingsPanel.kt:63-78`) devenues redondantes depuis la bascule cyclique du lot 3b |

**Aperçu en direct :** afficher un extrait du texte réellement en cours de lecture, mis à jour pendant le déplacement des curseurs. Pas un faux texte d'exemple — c'est l'intérêt de la décision cible.

**Interligne — seul ajout de modèle du lot.** Ajouter un champ à `UserPreferences` (avec sa valeur par défaut et sa persistance DataStore), l'appliquer au `TextStyle` de rendu, et **vérifier qu'il alimente bien `PaginationStyleKey`**. C'est exactement le scénario que la tâche 3b.2 anticipait : la clé déclare `lineHeightSp` et était alimentée par `baseTextStyle` réel. Si le câblage est correct, changer l'interligne repagine automatiquement. **Le vérifier, pas le supposer** — c'est le test 4.

**Curseur continu :** `steps = 0`. Arrondir à l'affichage si nécessaire, mais ne pas contraindre la valeur.

`Reconstruit le panneau TT avec aperçu en direct et interligne`

---

## Tâche 3d.3 — Luminosité

Absente du code. C'est elle qui porte la **5ᵉ icône de la rangée 3**, laissée vide depuis le lot 3b précisément parce que son action n'existait pas.

- Barre flottante déclenchée par l'icône, appliquée **à la fenêtre du lecteur seulement** (`WindowManager.LayoutParams.screenBrightness`), pas au réglage système.
- Restaurer le comportement système à la sortie du lecteur — ne pas laisser l'app modifier la luminosité au-delà de son propre écran.
- Prévoir une position explicite « valeur système » distincte du minimum, sinon l'utilisateur ne peut plus revenir au comportement par défaut.
- Persister la valeur pour qu'elle survive à la réouverture.

**Ajouter l'icône à la rangée 3 en même temps que l'action**, jamais avant. Mettre à jour la consignation du lot 3b, qui annonce son absence.

`Ajoute le réglage de luminosité du lecteur`

---

## Tâche 3d.4 — Minuteur

**État :** le tap sur « Veille » **cycle** entre 15/30/45/60 sans rien ouvrir (`ReaderScreen.kt`, `nextSleepTimerMinutes`), avec un commentaire assumant l'écart. Des puces existent, mais enfouies dans le panneau Voix.

**Cible :** un panneau à deux fonctions — puces **15 / 30 / 45** et une **roue de sélection personnalisée** pour une durée libre.

- Retirer le cycle et `nextSleepTimerMinutes`.
- Afficher le temps restant quand un minuteur est actif, et permettre de l'annuler.
- La valeur 60 des puces actuelles n'est pas dans la cible : la roue la couvre.

`Remplace le cycle de veille par le panneau de minuterie`

---

## Tâche 3d.5 — Rappel de repos oculaire

Absent du code. Seconde fonction du sous-écran Minuteur dans la cible, à ne pas confondre avec la minuterie de veille : celle-ci arrête la lecture, celui-là invite à faire une pause.

- Intervalle par défaut **1 h**, réglable.
- À l'échéance : popup + **compte à rebours de 60 s**, avec possibilité de reporter ou de reprendre immédiatement.
- Le réglage d'intervalle appartient à la carte « Performance & Bien-être » des Réglages (lot Réglages, non livré). Le stocker dès maintenant dans `UserPreferences` pour que cette carte s'y branche sans refonte, et exposer l'intervalle dans le panneau Minuteur en attendant.

**Point de vigilance :** un rappel qui s'ouvre pendant une lecture TTS ne doit pas couper l'audio sans prévenir, ni se superposer à l'annonce TalkBack — le lot 1 a déjà eu un correctif sur le chevauchement TalkBack/TTS. Décider explicitement du comportement audio et le consigner.

`Ajoute le rappel de repos oculaire`

---

## Tâche 3d.6 — Tests

1. **Vitesse** — déplacer le curseur écrit dans le profil de voix actif ; rouvrir le panneau restitue la valeur, pas 1,0×. Test de non-régression de l'antipattern décoratif.
2. **Vitesse en synthèse** — la valeur écrite est bien celle passée à `generate` / `setSpeechRate`, pas seulement persistée.
3. **Stop** — Stop et Pause produisent des états **distincts** ; si le Stop réel n'est pas disponible, le test doit constater son absence du panneau, pas son équivalence à Pause.
4. **Interligne et pagination** — changer l'interligne modifie `PaginationStyleKey` et déclenche un recalcul ; changer le thème ne le fait toujours pas. C'est la validation de l'anticipation du lot 3b.2.
5. **Curseur de taille** — valeurs continues, pas de paliers.
6. **Panneau TT** — plus de cartes de thème ; l'aperçu affiche bien un extrait du texte courant.
7. **Minuteur** — les puces 15/30/45 et la roue produisent la même sorte d'état ; annuler remet à zéro ; plus de comportement cyclique.
8. **Rangée 3** — 5 icônes, chacune émettant son intent. Non-régression inverse du test du lot 3b, qui vérifiait l'absence de Luminosité.

`Ajoute les tests des sous-écrans de réglages`

---

## Tâche 3d.7 — Consigner dans la cible

Dans `UX_FLOW_DESIGN.md`, § Lecture — HUD, mettre à jour l'état d'implémentation du lot 3b : la rangée 3 porte désormais ses 5 icônes, Luminosité incluse. Consigner le comportement audio retenu pour le rappel de repos oculaire, et le sort du bouton Stop.

`Consigne l état des sous-écrans de réglages dans la cible`

---

## Vérifications sur appareil — lot 3d

| # | Avant (`40114ca`) | Après attendu |
|---|---|---|
| 1 | Curseur de vitesse : revient à 1,0× à chaque ouverture, sans effet | La voix accélère et ralentit réellement ; la valeur survit à la fermeture du livre |
| 2 | Stop et Pause font la même chose | Comportements distincts, ou Stop retiré et signalé |
| 3 | Aucun nom de voix affiché | Nom réel de la voix active ; le lien prononciation ouvre l'écran |
| 4 | Panneau « Aa » : thème + taille par paliers | Typographie seule, curseurs continus, aperçu du texte réel qui bouge en direct |
| 5 | Interligne non réglable | Le régler change l'espacement **et** le nombre de pages annoncé |
| 6 | Rangée 3 : 4 icônes | 5 icônes, Luminosité comprise |
| 7 | — | La luminosité change dans le lecteur, revient à la normale en sortant |
| 8 | Tap sur Veille : cycle 15→30→45→60 sans rien ouvrir | Ouvre le panneau ; puces et roue fonctionnent ; temps restant visible et annulable |
| 9 | — | Rappel de repos oculaire à l'échéance : popup, compte à rebours 60 s, report possible |
| 10 | — | Déclencher le rappel **pendant une lecture TTS** : comportement audio conforme à ce qui a été décidé, aucune annonce TalkBack qui se superpose à la voix |

Le point 5 est le plus révélateur : il valide en une manipulation le garde-fou posé au lot 3b.2, dont c'était la raison d'être.

---

## Hors périmètre explicite

Barre pilule TTS, repli en FAB, onde sonore, swipe-down → **lot 3e**.

Implémentation de la sélection au mot → **lot 3f**, décision produit post-V1, non déclenchée.

Carte « Performance & Bien-être » de l'écran Réglages → lot Réglages. Ce lot pose seulement le champ d'intervalle dans `UserPreferences` pour qu'elle s'y branche.

Audit du consentement Crashlytics → lot Onboarding (`docs/execution/LOT_ONBOARDING_PERIMETRE.md`).
