# Sous-lot 2c — Iconographie Material Symbols Rounded (Approche B)

Décision actée : **B** (VectorDrawables par icône, FILL 0 contour / FILL 1 plein,
deux états discrets). Suit 2a/2b. Réf. : `main` @ `fd6a542`.

> **Implémentation : GitHub Copilot.** Ce plan est écrit comme des instructions
> exécutables par un agent. Règles impératives pour l'agent :
> 1. **Ne jamais synthétiser de `pathData` d'icône.** Les VectorDrawables sont
>    **fournis** (assets réels Material Symbols, cf. §2) et déposés dans le dépôt ;
>    l'agent les référence, il n'en invente **aucun** tracé.
> 2. Aucune décision de conception laissée à l'agent : tout est tranché ci-dessous.
> 3. La **vérification device** (§7) est faite par Issa **après** la PR de l'agent,
>    pas par l'agent.

---

## 0. Décisions actées (les 3 points ouverts, tranchés)

1. **Statuts** → **mono** (pas d'état). `Error`, `Warning`, `Success` : glyphe **plein** au repos (saillance d'alerte). `Info` : glyphe **contour** au repos (informatif, non alarmant). Aucun variant sélectionné.
2. **Doublons** → **fusion sur le token sémantique existant**. `WbSunny` (inline, luminosité) fusionne dans `Brightness`. `Timer` (inline, minuterie de sommeil) devient **`SleepTimer`** (pas un `Timer` générique). `SkipPrevious/Next`, `SentencePrevious/Next`, `ChapterPrevious/Next` : réutiliser les tokens de nav existants, **ne pas** créer de doublon « Skip ».
3. **Génération des assets** → **fournis par Claude** (VectorDrawables réels issus de Material Symbols Rounded). Un agent LLM ne peut pas produire de tracé d'icône fiable ; les XML sont livrés prêts (§2), l'agent ne fait que les câbler.

---

## 1. Classification état / mono — **matérialisée par les assets**

Règle sans ambiguïté pour l'agent : **le split est porté par le jeu d'assets fourni**, pas par un jugement.
- Un symbole a un fichier `ic_<nom>_fill.xml` **⇒ il est à état** : `activeFill` non-null.
- Sinon **mono** : `activeFill = null`, et son unique asset (`ic_<nom>.xml`) est le glyphe de repos (contour, **ou** plein pour les statuts).

**Icônes à état** (un `_fill.xml` est fourni) : destinations de nav sélectionnables (`Reading`, `Recents`, `Stats`, `Sync`, `Theme`, `Settings`), bascules (`Bookmark`, `Favorite`, `Pin`), TTS actif (`Speaking`), modes segmentés (`ReadingModePaged`/`ReadingModeScroll`, `ViewGrid`/`ViewList`/`CoverOnly`). Toutes les autres = mono.

---

## 2. Assets fournis — `core/designsystem/src/main/res/drawable/`

- Convention : `ic_<nom>.xml` (repos) + `ic_<nom>_fill.xml` (état actif, icônes à état uniquement).
- Source : Material Symbols **Rounded**, `wght 400`, `GRAD 0`, `opsz 24`, grille **24dp**, **Apache 2.0** (fichier de licence joint sous `docs/legal/fonts/`).
- `android:autoMirrored="true"` posé sur les 7 AutoMirrored : `Back`, `Toc`, `ViewList`, `ChapterPrevious`, `ChapterNext`, `Reading`, `Speaking`.
- **Pas** de `android:tint` codé en dur (le tint vient du composable → l'accent violet 2a s'applique).
- Un **manifeste d'assets** (`docs/execution/ASSETS_ICONES_2C.md`) accompagne la livraison : table `AppSymbol → fichier(s) → nom Symbols source`, pour audit.

L'agent **vérifie** que chaque `AppSymbol` du §3 a ses fichiers présents ; il n'en crée aucun.

---

## 3. API `AppIcons` — pilotée par l'état (à écrire par l'agent)

```kotlin
enum class AppSymbol(
    @DrawableRes val default: Int,          // glyphe de repos (contour, ou plein pour un statut)
    @DrawableRes val activeFill: Int? = null // override quand selected (icônes à état)
) {
    Bookmark(R.drawable.ic_bookmark, R.drawable.ic_bookmark_fill),
    Favorite(R.drawable.ic_favorite, R.drawable.ic_favorite_fill),
    Error(R.drawable.ic_error),             // statut : default = glyphe plein fourni, pas d'activeFill
    Search(R.drawable.ic_search),           // mono
    // … tous les symboles (voir manifeste §2)
}

@Composable
fun AppIcon(
    symbol: AppSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val res = if (selected && symbol.activeFill != null) symbol.activeFill!! else symbol.default
    Icon(painterResource(res), contentDescription, modifier, tint)
}
```
- Les paires legacy (`Favorite`/`FavoriteBorder`, `Pin`/`PinOutlined`, `Success`/`SuccessOutlined`, etc.) **fusionnent** en un seul `AppSymbol` + `selected`. Supprimer les tokens `*Outline`/`*Border`/`*Outlined` redondants.
- `contentDescription` explicite (mettre `null` seulement si l'icône est purement décorative, doublée par un libellé texte).

---

## 4. Garde-fou anti-régression

Ajouter une règle **detekt** (`ForbiddenImport`) interdisant `androidx.compose.material.icons` **partout sauf** `AppIcons.kt`. Activée en **fin de 2c.3** (une fois les 47 fuites résorbées), pour ne pas bloquer les étapes intermédiaires. Critère : `./gradlew detekt` vert avec la règle active.

---

## 5. Mapping legacy → Symbols (points non triviaux)

| Legacy / inline | Décision | Symbols source |
|---|---|---|
| `WbSunny` (inline) | fusion → `Brightness` | `light_mode` |
| `Timer` (inline, sommeil) | → nouveau `SleepTimer` | `bedtime` |
| `PlayArrow` / `Pause` (inline) | 2 tokens mono `Play` / `Pause` (swap, pas un fill) | `play_arrow` / `pause` |
| `Add` / `Remove` (inline, steppers) | 2 tokens mono `Add` / `Remove` | `add` / `remove` |
| `SkipPrevious` / `SkipNext` (inline) | réutiliser `SentencePrevious`/`SentenceNext` ou `ChapterPrevious`/`ChapterNext` existants | (pas de nouveau token) |
| paires `Favorite`/`FavoriteBorder`… | fusion en 1 token à état | cf. §3 |

Le **bulk 1:1** (Search, Delete, Settings, Edit…) est confirmé dans le manifeste §2. L'agent ne devine aucun nom : il suit le manifeste.

---

## 6. Séquencement (une PR Copilot par pas, vérif device d'Issa entre chaque)

```
2c.1  Fondations : AppSymbol + AppIcon + intégration des assets fournis
      + centralisation des icônes inline (Play/Pause/Add/Remove/SleepTimer/Brightness).
      Critère : le module compile, AppIcons ne référence plus Icons.*.
2c.2  Rollout par module (une PR chacun) : library → reader → settings → statistics
      → search → sync → onboarding. Chaque écran : Icons.* / Icon(AppIcons.X)
      → AppIcon(AppSymbol.X, selected = …). Critère : zéro import material.icons dans le module.
2c.3  Cleanup : suppression des anciens val, activation du garde-fou §4.
      Critère : detekt vert avec la règle, grep `androidx.compose.material.icons` = AppIcons.kt seul.
```
Pas de big-bang : 2c.2 est **une PR par module**.

---

## 7. Vérification (device = Issa, après chaque PR)

- **Tests agent** (dans la PR) : chaque `AppSymbol.default`/`activeFill` pointe un drawable **existant** ; tout `activeFill != null` a son `_fill.xml`. Test unitaire d'énumération.
- **Device (Issa)** par module : rendu **Rounded** fidèle, tint violet (2a) clair/sombre, **état sélectionné plein** (nav, bascules), contour à l'inactif, **RTL** OK sur les 7 AutoMirrored (`LayoutDirection.Rtl`), tailles 20/24dp nettes. Screencaps.

---

## 8. Écarts / hors périmètre

- Pas d'animation de fill (choix B) — swap discret ; crossfade ajoutable plus tard sans changer les assets.
- `Info` = contour mono ; `Error`/`Warning`/`Success` = plein mono (§0).
- `LibraryIllustrations` et police/icônes de lecture : hors set chrome, non touchés.

---

## 9. Commits

- 2c.1 : `Introduit AppIcon piloté par l'état et intègre les assets Material Symbols Rounded`
- 2c.2.x : `Migre les icônes de <module> vers Material Symbols Rounded`
- 2c.3 : `Retire le set d'icônes legacy et verrouille la centralisation (detekt)`
