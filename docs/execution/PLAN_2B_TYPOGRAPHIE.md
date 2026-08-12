# Sous-lot 2b — Typographie de marque (Work Sans + Literata)

Découle de `PLAN_CADRAGE_IDENTITE_THEME.md` §5. Fait suite à 2a (palette).
Réf. : `main` @ `fd6a542` (à réactualiser à l'ouverture du lot).

Identité (doc §3.3) : **Work Sans** pour tout le chrome fonctionnel ;
**Literata** en accent narratif **ponctuel uniquement** (jamais dans la scale,
jamais dans boutons/nav). Licences OFL 1.1 déjà vérifiées à la source.

---

## 0. État actuel — audité

- `InkToneTypography` (`Type.kt`) = **polices système** : 12 styles en `FontFamily.SansSerif`, 3 (`bodyLarge/Medium/Small`, `:28-30`) en `FontFamily.Serif`. Aucune Work Sans / Literata.
- **Poids réellement utilisés par la scale** : `Normal` (400), `Medium` (500), `SemiBold` (600), `Bold` (700). → **le doc sous-spécifie** (« 400/500 suffisent ») : il en faut **quatre** pour rendre la scale fidèlement.
- Seule police bundlée : `opendyslexic_regular.otf` (accessibilité lecture, **hors marque**, ne pas toucher).
- Accroche narrative existante : `OnboardingScreen.kt:152` « Bienvenue sur InkTone » (+ titres des 3 cartes, `:152/:194/:265`).
- **Pas de carte « fin de livre »** dans `feature/reader` (aucun composant dédié) → cible Literata #2 sans ancrage (écart §F).
- Aucun downloadable font provider : cohérent avec l'offline-first (ADR-003), qui **impose** de bundler les TTF (pas de provider réseau).

---

## A. Décisions

**D-typo-1 — Variable font, pas statique.** Une TTF variable par famille, axe de poids exploité via `FontVariation`. `minSdk = 26` → l'axe poids est honoré sur tous les appareils cibles. Un seul fichier au lieu de 4 statiques par famille.

**D-typo-2 — Quatre poids câblés** (400/500/600/700), pas deux. Corrige le doc §3.3 (à consigner).

**D-typo-3 — Chrome entièrement Work Sans.** Les 15 styles de `InkToneTypography`, y compris les `body*` aujourd'hui en `Serif`. *Le `Serif` actuel semblait vouloir un ton « livre » dans le chrome, mais le doc réserve explicitement Literata à l'accent ponctuel — le body chrome passe donc en Work Sans, pas en Literata.* (Point à confirmer par Issa : c'est le seul renversement d'un choix existant.)

**D-typo-4 — Literata cantonné à l'accent narratif.** Défini comme famille séparée, **jamais** injecté dans `InkToneTypography`. Appliqué au cas par cas (§D).

---

## B. Assets à bundler — `core/designsystem/src/main/res/font/`

| Fichier | Source primaire (OFL 1.1) | Note |
|---|---|---|
| `work_sans_variable.ttf` | `github.com/weiweihuanghuang/Work-Sans` (`fonts/variable`) | axe `wght` 100–900 |
| `literata_variable.ttf` | `github.com/googlefonts/literata` (`fonts/variable`) | axe `wght` + `opsz` |

- **OFL exige** de livrer le fichier `OFL.txt`/`LICENSE` de chaque police avec les assets → les déposer sous `docs/legal/fonts/` ou à côté des TTF. Aucune attribution UI requise, seulement l'inclusion de la licence.
- *Optionnel (taille)* : sous-ensemble Latin + diacritiques FR (`é è ê à â î ô û ç œ æ …`) via `fonttools subset` — réduit le poids, à faire seulement si le budget AAB le justifie ; sinon TTF complète.

---

## C. `Type.kt` — famille chrome Work Sans

```kotlin
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

private fun workSans(w: Int) = Font(
    R.font.work_sans_variable,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val WorkSansFamily = FontFamily(workSans(400), workSans(500), workSans(600), workSans(700))
```

**Avant** (`Type.kt:19-33`) : chaque style en `fontFamily = FontFamily.SansSerif` (ou `.Serif` pour `body*`).
**Après** : `fontFamily = WorkSansFamily` sur **les 15 styles** (tailles, graisses, interlignes, letterSpacing **inchangés** — on ne touche qu'à la famille).

---

## D. Literata — accent narratif ponctuel

```kotlin
val NarrativeAccentFamily = FontFamily(
    Font(R.font.literata_variable, FontWeight.Normal,
         variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.literata_variable, FontWeight.SemiBold,
         variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
```

Application (à confirmer sur la portée exacte) :
- **Accroche de bienvenue onboarding** — `OnboardingScreen.kt:152` : `Text("Bienvenue sur InkTone", …, fontFamily = NarrativeAccentFamily)`. Étendre éventuellement aux titres des 3 cartes (`:194`, `:265`) — **choix de portée à valider** (accroche seule vs tous les titres onboarding).
- **Carte « fin de livre »** — **différé** : le composant n'existe pas encore (§0). À câbler quand la carte sera créée (probable lot reader), pas ici.

Ne jamais mettre `NarrativeAccentFamily` dans un bouton, une barre, la nav, ni dans `InkToneTypography`.

---

## E. Test de sortie — `TypographyBrandTest`

`core/designsystem/src/test/…/TypographyBrandTest.kt` (JUnit pur, pas de chargement de police — égalité de référence sur la `val` exportée) :

```kotlin
@Test fun tout_le_chrome_est_en_work_sans() {
    val styles = with(InkToneTypography) {
        listOf(displayLarge, displayMedium, displaySmall,
               headlineLarge, headlineMedium, headlineSmall,
               titleLarge, titleMedium, titleSmall,
               bodyLarge, bodyMedium, bodySmall,
               labelLarge, labelMedium, labelSmall)
    }
    styles.forEach { assertSame(WorkSansFamily, it.fontFamily) } // aucun SansSerif/Serif résiduel
}

@Test fun literata_n_est_pas_dans_la_scale() {
    InkToneTypography /* … */  // aucun style ne référence NarrativeAccentFamily
}
```
Fige le contrat : tout ajout futur d'un style en police système fait tomber le test.

---

## F. Écarts déclarés

- **Literata « fin de livre » différé** — pas de carte cible aujourd'hui.
- **Police de lecture intacte** — `OpenDyslexicFamily` et les polices de lecture utilisateur (`feature/reader`) restent hors marque, non modifiées.
- **Poids doc corrigé** — 400/500/600/700 (§A/D-typo-2), à porter au doc §3.3.
- **Taille AAB** — deux TTF variables ajoutées (~quelques centaines de Ko) ; sous-ensembler seulement si nécessaire (§B).

---

## G. Vérification device (V2206, protocole skill §Méthodologie)

1. Build + install ; le chrome rend visiblement **Work Sans** (comparer un libellé au Roboto précédent : proportions humanistes, `a`/`g` différents).
2. **Diacritiques FR** : é è ê à â î ô û ç œ æ — aucun glyphe manquant/tofu dans nav, réglages, statistiques.
3. **Onboarding** : « Bienvenue sur InkTone » en **Literata** (empattements visibles), le reste du chrome en Work Sans.
4. **Graisses** : titres (Bold/SemiBold) vs labels (Medium) nettement différenciés (les 4 poids se chargent).
5. **Non-régression lecture** : la police OpenDyslexic reste sélectionnable et s'applique au texte du livre, inchangée.
6. Screencaps clair + sombre.

---

## H. Commit

> `Applique Work Sans au chrome et Literata à l'accroche d'onboarding`

---

## I. Suite

Après vérif device : ouvrir **2c — iconographie** (spike Material Symbols Rounded d'abord). Ne rien anticiper ici.
