# Sous-lot 2a — Palette de marque (accent violet)

Découle du plan-cadre `PLAN_CADRAGE_IDENTITE_THEME.md`. Décisions actées :
- **D0 = C** — violet par défaut, Material You en option (on inverse le défaut du réglage).
- **Périmètre = court** — on brandit la famille `primary` (accent) ; `secondary`/`tertiary`/`error` restent génériques en **intérimaire** (séance couleurs différée).
- **Correction WCAG sombre** — consignée (voir §E).

Réf. : `main` @ `fd6a542`.

---

## A. Reconstruire la famille `primary` — `Color.kt`

Remplace le bleu par la rampe Deadly Depths. **Contrastes tous re-vérifiés sur les vrais fonds/containers** (formule WCAG 2.x) :

| Rôle | Clair | Sombre | Contraste vérifié |
|---|---|---|---|
| `primary` | `#2C1E67` (Accent700) | `#7661D1` (Accent500) | clair 13.68:1 /bg ✓ · sombre 3.88:1 /bg → **non-texte** (cf. §E) |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | clair 14.11:1 · sombre 4.77:1 (texte AA ✓) |
| `primaryContainer` | `#E4DFF6` (Cont.100) | `#2C1E67` (Accent700) | — |
| `onPrimaryContainer` | `#19113B` (Accent900) | `#A698E1` (Cont.300) | clair 13.66:1 · sombre 5.51:1 ✓ |
| `inversePrimary` | `#7661D1` | `#7661D1` | remplace le `#0066FF`/`#AAC7FF` résiduels |
| `surfaceTint` | = `primary` | = `primary` | évite le tint bleu des surfaces élevées |

**Avant** (`Color.kt:18-21`, schéma clair) — `primary = #0066FF`, `primaryContainer = #D6E3FF`, `onPrimaryContainer = #001B3E`.
**Après** — valeurs de la colonne « Clair » ci-dessus.

**Avant** (`Color.kt:50-53`, schéma sombre) — `primary = #3399FF`, `primaryContainer = #004A9C`, etc.
**Après** — colonne « Sombre ». Corriger aussi `inversePrimary = #0066FF` (bloc sombre) → `#7661D1`.

**Ne PAS toucher** (intérimaire, périmètre court) : `secondary` (orange `#C04000`/`#FF8C5A`), `tertiary` (teal), `error`, ni les fonds/surfaces. → écart §D.

---

## B. Basculer le défaut Dynamic Color (D0 = C)

Passer le défaut de `dynamicColorEnabled` à **false** :
- `domain/.../UserPreferences.kt:20` : `val dynamicColorEnabled: Boolean = false`
- `infrastructure/database/.../UserPreferencesEntity.kt:18` : idem `= false`

**Immutabilité des migrations — ne pas éditer `Migrations.kt:53`.** Cette migration (`DEFAULT 1`) est déjà livrée et fingerprintée par Room ; la réécrire est un antipattern (et casserait la vérif de schéma). Conséquence assumée :
- **Installs neuves** : pas de ligne → défaut Kotlin `false` → **chrome violet par défaut** ✓ (l'objectif D0).
- **Installs existantes** (tes appareils de test qui ont déjà migré) : conservent `1` en base. Elles gardent Material You jusqu'à bascule manuelle du réglage, ou `adb shell pm clear` / effacement des données.

Population pré-release = tes seuls devices de test → impact négligeable, aucune migration destructrice justifiée. **Pas** de nouvelle migration qui forcerait `0` (elle écraserait un choix utilisateur délibéré).

---

## C. Test de sortie — `PaletteContrastTest`

`core/designsystem/src/test/.../PaletteContrastTest.kt`, via `calculateContrastRatio` (`ContrastRatio.kt:14`). Asserts (seuils = plancher, pas la valeur exacte) :

```kotlin
// Fonds réels du thème
val lightBg = Color(0xFFFFFBF5)   // Color.kt:34
val darkBg  = Color(0xFF0F1419)   // Color.kt:66

// primary utilisable en icône/bouton (non-texte) : >= 3:1 sur son fond
assertTrue(calculateContrastRatio(lightBg, Color(0xFF2C1E67)) >= 3.0)   // ~13.68
assertTrue(calculateContrastRatio(darkBg,  Color(0xFF7661D1)) >= 3.0)   // ~3.88

// étiquette sur bouton rempli : onPrimary >= 4.5:1
assertTrue(calculateContrastRatio(Color(0xFF2C1E67), Color.White) >= 4.5) // ~14.11
assertTrue(calculateContrastRatio(Color(0xFF7661D1), Color.White) >= 4.5) // ~4.77

// containers : onPrimaryContainer >= 4.5:1
assertTrue(calculateContrastRatio(Color(0xFFE4DFF6), Color(0xFF19113B)) >= 4.5) // ~13.66
assertTrue(calculateContrastRatio(Color(0xFF2C1E67), Color(0xFFA698E1)) >= 4.5) // ~5.51

// GARDE-FOU sombre : primary NE passe PAS le seuil texte → accent-texte sombre doit être Cont.300
assertTrue(calculateContrastRatio(darkBg, Color(0xFF7661D1)) < 4.5)     // documente la limite
assertTrue(calculateContrastRatio(darkBg, Color(0xFFA698E1)) >= 4.5)    // ~7.23
```
Le dernier bloc **fige** la correction §E dans le code : si quelqu'un « corrige » l'accent-texte sombre en `#7661D1`, le test tombe.

---

## D. Écarts déclarés

- **Sémantiques intérimaires** : `secondary` reste orange, `tertiary` teal, `error` rouge. Un `primary` violet à côté d'un `secondary` orange **peut jurer** sur certains composants (chips, containers secondaires). Assumé pour le lot court ; à trancher en séance couleurs. Si le rendu device jure trop, ça priorise la séance.
- **Accent-texte sombre** : `primary` sombre est non-texte ; tout texte/lien accentué en sombre utilise `#A698E1` (§C garde-fou).
- **Défaut Dynamic Color sur installs existantes** : cf. §B (conservent `true`).

---

## E. Corrections à porter à `IDENTITE_INKTONE.md`

- **§3.2** : ajouter que sur fond sombre réel `#0F1419`, `Accent500 #7661D1` = **3.88:1** → non-texte uniquement ; accent-**texte** sombre = `AccentContainer300 #A698E1` (7.23:1). (Le doc laissait entendre 500 proche du seuil texte — faux sur le fond réel.)
- **§6 tokens** : fonds de référence = `#FFFBF5` / `#0F1419` (réels), pas `#FBFAF6` / `#121212`.
- **§5 registre** : supprimer les lignes fantômes « Typographie (§3.3) — À définir » et « Iconographie chrome (§3.4) — À définir » (doublons contredisant les lignes « Validé »).
- **Registre** : ajouter l'entrée D0 = C (violet par défaut, Material You en option, défaut réglage inversé), datée.

---

## F. Vérification device (V2206, protocole skill §Méthodologie)

1. Build + install ; **install neuve** (ou `pm clear`) pour voir le défaut D0.
2. Chrome (TopAppBar, boutons, FAB, indicateur TTS) en **violet**, pas bleu ni couleur du fond d'écran. Screencap clair + sombre.
3. Réglages → « Couleurs dynamiques » : **off par défaut** ; l'activer bascule bien vers Material You (non-régression du réglage livré).
4. Mode sombre : vérifier qu'aucun **texte/lien** accentué n'est en `#7661D1` (lisibilité) — doit être `#A698E1`.
5. Repérer tout composant `secondary`/`tertiary` intérimaire qui jure (donnée pour la séance couleurs).

---

## G. Commit

> `Applique l'accent de marque violet au chrome et bascule Material You en option`

---

## H. Suite

Après vérif device de 2a : ouvrir **2b (typographie)**. Ne rien anticiper de 2b/2c ici.
