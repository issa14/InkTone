# Sous-lot 2d — Thème custom `Theme.InkTone` + splash

Dernier volet identité (après 2a palette, 2b typo, 2c iconographie). Débloque
l'icône de splash laissée en attente au lot 1. Réf. : `main` @ `ca3c503`.

> Implémentation : **GitHub Copilot** (tout est du XML de thème + Kotlin, aucun
> asset à synthétiser). Vérification device = **Issa** après la PR.

---

## 0. État audité

- Manifeste : `android:theme="@android:style/Theme.Material.Light.NoActionBar"` — thème **plateforme, clair uniquement**, pas de `Theme.InkTone`. → en mode sombre, le `windowBackground` reste **clair** ⇒ **flash blanc** avant que Compose ne peigne le fond sombre.
- `MainActivity` (`ComponentActivity`) : `super.onCreate()` puis `setContent { … InkToneTheme(…) }`. **Aucun** `installSplashScreen()`, aucun `enableEdgeToEdge()`. Contient un contournement explicite : `if (hasSeenOnboarding == null) return@Surface` (un frame vide le temps que Room charge la préférence).
- `core-splashscreen` **absent** du catalogue.
- Assets réutilisables présents : `@mipmap/ic_launcher_foreground` (silhouette, déjà centrée safe-zone). `colors.xml` = `ic_launcher_background #FBFAF6` seulement.
- `compileSdk 35 / targetSdk 34`. 2a mergée (fonds Compose réels : `#FFFBF5` clair / `#0F1419` sombre).

---

## 1. Décisions actées

**D1 — `windowBackground` = fond Compose exact**, pas le crème du logo. Clair `#FFFBF5`, sombre `#0F1419` (pas `#FBFAF6`), pour que la transition splash → Surface Compose soit **invisible** (sinon flash d'une nuance).

**D2 — Barres système couleur du fond**, pas une barre violette. `statusBarColor` = `windowBackground` ; `windowLightStatusBar` = **true** en clair (icônes sombres) / **false** en sombre. (Le doc ne demande pas de barre teintée.)

**D3 — Splash via `androidx.core:core-splashscreen 1.0.1`.** Icône = `@mipmap/ic_launcher_foreground` (réutilisée) ; fond splash = `windowBackground` (day/night) ; `postSplashScreenTheme = @style/Theme.InkTone`.

**D4 — Edge-to-edge HORS périmètre.** Le thème est *prêt* pour l'edge-to-edge, mais `enableEdgeToEdge()` + gestion des insets est un chantier à part, rattaché au **lot SDK 36** (où Android 15+ l'impose). Ne pas l'introduire ici (risque de régressions de layout).

**D5 — Splash tenu jusqu'au chargement de la préférence (recommandé).** Brancher `setKeepOnScreenCondition` sur l'état « onboarding pas encore chargé » : le splash couvre exactement le frame vide que `MainActivity` contourne déjà. Nécessite d'exposer ce flag au niveau Activity (§5).

---

## 2. Dépendance — `core-splashscreen`

`gradle/libs.versions.toml` :
```toml
[versions]
androidxCoreSplashscreen = "1.0.1"
[libraries]
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "androidxCoreSplashscreen" }
```
`app/build.gradle.kts` : `implementation(libs.androidx.core.splashscreen)`

---

## 3. Ressources thème (à créer)

### `app/src/main/res/values/colors.xml` (compléter)
```xml
<color name="brand_background">#FFFBF5</color>
```
### `app/src/main/res/values-night/colors.xml` (nouveau)
```xml
<resources><color name="brand_background">#0F1419</color></resources>
```

### `app/src/main/res/values/themes.xml` (nouveau)
```xml
<resources>
    <style name="Theme.InkTone" parent="@android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/brand_background</item>
        <item name="android:statusBarColor">@color/brand_background</item>
        <item name="android:navigationBarColor">@color/brand_background</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>

    <style name="Theme.InkTone.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/brand_background</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.InkTone</item>
    </style>
</resources>
```
### `app/src/main/res/values-night/themes.xml` (nouveau)
```xml
<resources>
    <style name="Theme.InkTone" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/brand_background</item>
        <item name="android:statusBarColor">@color/brand_background</item>
        <item name="android:navigationBarColor">@color/brand_background</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
    <!-- Theme.InkTone.Starting hérité de values/ ; seul brand_background bascule via values-night -->
</resources>
```
> `windowLightNavigationBar` (icônes nav sombres) est API 27+ : ignoré sur API 26, acceptable ; l'ajouter en `values-v27` si le rendu le justifie.

---

## 4. Manifeste

**Avant** : `android:theme="@android:style/Theme.Material.Light.NoActionBar"`
**Après** : `android:theme="@style/Theme.InkTone.Starting"`
(sur `<application>` ; l'activité hérite. Le `postSplashScreenTheme` rebascule sur `Theme.InkTone` après le splash.)

---

## 5. `MainActivity`

**Avant** :
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { … }
}
```
**Après** :
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splash = installSplashScreen()          // AVANT super.onCreate
    super.onCreate(savedInstanceState)
    // D5 : tenir le splash tant que la préférence n'est pas chargée
    val ready = MutableStateFlow(false)
    splash.setKeepOnScreenCondition { !ready.value }
    setContent {
        …
        // remonter le flag quand hasSeenOnboarding != null :
        LaunchedEffect(hasSeenOnboarding) { if (hasSeenOnboarding != null) ready.value = true }
        …
    }
}
```
> Si D5 est jugée trop couplée, s'en tenir à `installSplashScreen()` seul (le frame vide reste, mais couvert par le splash système ~instantané). Le flag `ready` est le seul point demandant un peu de soin.

---

## 6. Écarts / hors périmètre

- **Edge-to-edge** différé au lot SDK 36 (D4).
- **Centrage de l'icône dans le masque circulaire du splash** : `ic_launcher_foreground` est centré safe-zone launcher, mais le gabarit splash (cercle ~240dp, icône ~2/3) est plus serré — à **juger sur device** (§7) ; si trop grand, fournir un `@drawable/ic_splash` dédié avec plus de marge. C'est la reprise du point laissé ouvert au lot 1.
- **Icône de notification** : toujours dans le lot Media3, hors 2d.

---

## 7. Vérification device (Issa, après la PR)

1. **Démarrage à froid, mode clair** : splash = silhouette sur crème `#FFFBF5`, puis app — **aucune** rupture de teinte splash → contenu.
2. **Démarrage à froid, mode sombre** (système en sombre) : splash sur fond **sombre** `#0F1419`, **plus de flash blanc**.
3. **Barres système** : icônes lisibles dans les deux modes (sombres sur clair, claires sur sombre).
4. **Icône splash** : ni rognée ni surdimensionnée dans le cercle (sinon → `ic_splash` dédié, §6).
5. **Non-régression** : pas de frame vide visible au lancement (D5) ; navigation/onboarding inchangés.
Screencaps clair + sombre, démarrage à froid.

---

## 8. Commit

> `Ajoute le thème Theme.InkTone et l'écran de démarrage de marque`
