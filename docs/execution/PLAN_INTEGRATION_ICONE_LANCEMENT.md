# Plan — Intégration de l'icône de lancement InkTone

Lot autonome, atteignable et vérifiable sur device. Il **n'implémente pas**
l'identité de marque du thème (palette/typo/iconographie) — c'est le plan 2,
séparé. Ici : seulement rendre le cacatoès visible comme icône d'app.

Réf. dépôt : HEAD `d8fa078`. Assets source : `design/assets/icon/`.
Guide manuel préexistant : `design/assets/icon/GUIDE_INTEGRATION_ICONE.md`
(ce plan s'en écarte, cf. §1).

**Révision v2** — intègre la revue technique : couleur consolidée dans
`colors.xml`, script rendu déterministe (`-strip` + boucle ordonnée), format du
calque monochrome traité en décision conditionnelle (D5). Le qualificateur
`-v26` est **conservé** (D1b, motif consigné).

---

## 0. État actuel — audité, pas supposé

- `app/src/main/res/` ne contient **que** `xml/file_paths.xml` : aucun
  `mipmap-*`, aucun `mipmap-anydpi-v26/`, aucun `values/`.
- `AndroidManifest.xml:19-23` — le bloc `<application>` déclare
  `android:label="InkTone"` mais **aucun** `android:icon` ni
  `android:roundIcon`.
- `android:theme="@android:style/Theme.Material.Light.NoActionBar"` — thème
  **plateforme**, pas de thème custom (donc pas de splash Android 12+ à câbler
  ici ; cf. §6).
- `minSdk = 26` (`InkToneApplicationConventionPlugin.kt:32`).
- **Conséquence** : l'app s'installe avec l'icône Android par défaut.

Assets prêts dans `design/assets/icon/` (dimensions vérifiées) :

| Fichier | Rôle | Format constaté |
|---|---|---|
| `ic_launcher_foreground.png` | Calque premier plan (silhouette noire recentrée safe-zone 66/108) | 1024², RGBA |
| `ic_launcher_monochrome.png` | Calque themed icons Android 13+ | 1024², RGBA |
| `ic_notification.png` | Petit icône notification TTS (silhouette blanche) | 512², RGBA |
| `playstore_icon_512.png` | Fiche Play (aplati) | 512², RGB |

---

## 1. Décisions de conception

**D1 — Adaptive-icon uniquement, aucun mipmap legacy.**
`minSdk = 26` : tous les appareils cibles sont ≥ Android 8, donc supportent
l'adaptive icon. Les calques legacy carré/rond ne serviraient jamais. → **Écart
assumé vs le guide** (qui recommande la génération legacy « pour les appareils
< Android 8 » : ils n'existent pas dans notre base).

**D1b — Conserver le qualificateur `-v26` sur `mipmap-anydpi-v26/`.**
Ne PAS le simplifier en `mipmap-anydpi/`. Le `-v26` n'est pas du bruit : c'est
la **porte de version** qui restreint l'`<adaptive-icon>` à l'API ≥ 26. Le
retirer ne gagne rien (zéro fichier, zéro coût runtime) et crée une fragilité
latente : si le `minSdk` redescend un jour, ou qu'une variante à `minSdk`
inférieur consomme la ressource, un `mipmap-anydpi/ic_launcher.xml` sans repli
legacy **ni** garde de version serait sélectionné sur un appareil pré-26 →
échec de rendu. C'est aussi la forme standard (wizard, doc AOSP). *Consigné ici
pour qu'un audit ne relance pas la « simplification ».*

**D2 — Séparation des calques (clarifie le §3.2 du doc identité).**
Le « Noir `#000000` » du doc est la couleur de la **silhouette**, pas un fond :
- `background` = **couleur** crème `#FBFAF6` ;
- `foreground` = silhouette noire ;
- `monochrome` = même silhouette (le système la teinte en themed mode, le
  background est alors ignoré).

**D3 — Icône ronde fournie explicitement.** `ic_launcher_round.xml` identique à
`ic_launcher.xml` (certains launchers lisent `roundIcon` en priorité).

**D4 — Binaires commités et déterministes.** WebP sans perte générés par script
(§2.4), pas via le wizard (sortie non reproductible, non revue en diff). Le
**déterminisme binaire** vient du drapeau `-strip` (retrait ICC/EXIF/dates) ;
l'ordre de boucle n'affecte que les logs/diffs, pas les octets produits, mais on
l'ordonne quand même pour un diff propre.

**D5 — Format du calque monochrome : décision conditionnelle (à trancher).**
Le tinting themed-icon d'Android 13+ n'exige **pas** un vecteur : le système
applique un `ColorFilter` sur le **canal alpha** du calque `<monochrome>`, ce
qui fonctionne sur bitmap alpha comme sur vecteur. Donc :
- **Défaut (V-absente) — raster.** On garde `ic_launcher_monochrome` en WebP
  (silhouette noir/transparent). Tinting correct. Le cas « Funtouch OS ignore
  le monochrome des apps tierces » n'est corrigé par *aucun* format — c'est
  l'étape 4 du protocole device qui le tranche.
- **Variante (V-présente) — VectorDrawable hand-authored.** *Seulement si* on
  dispose de la **source vectorielle originale** du logo. On écrit alors à la
  main `res/drawable/ic_launcher_monochrome.xml` (jamais un auto-trace : infidèle
  ou `<path>` obèse), on retire le monochrome de la boucle §2.4, et l'adaptive
  XML référence `@drawable/…` au lieu de `@mipmap/…`. Strictement meilleur
  (netteté toute densité, un fichier, zéro métadonnée).
- **Question ouverte bloquante** : source vectorielle du logo disponible ?

**D6 — Couleur dans `colors.xml` standard.** Pas de fichier dédié
`ic_launcher_background.xml` (artefact de wizard). `colors.xml` ne porte **que**
cette couleur côté XML (le pont vers l'`<adaptive-icon>`) ; la palette de marque
reste dans `Color.kt` en Compose (plan 2) — **aucune duplication** de l'accent
violet en XML.

---

## 2. Fichiers à créer

### 2.1 `app/src/main/res/values/colors.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#FBFAF6</color>
</resources>
```

### 2.2 `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
```
> **Variante V (D5)** : si monochrome vectorisé, remplacer la dernière ligne par
> `<monochrome android:drawable="@drawable/ic_launcher_monochrome" />`.

### 2.3 `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
Contenu **identique** à 2.2.

### 2.4 Calques rasterisés — script déterministe
Canvas adaptive = 108dp → mdpi 108, hdpi 162, xhdpi 216, xxhdpi 324,
xxxhdpi 432 px. Silhouettes → **webp sans perte**, métadonnées retirées.

```bash
set -euo pipefail
SRC=design/assets/icon
DST=app/src/main/res

# Défaut D5 : les DEUX calques en raster.
# Variante V : retirer "ic_launcher_monochrome" de la ligne ci-dessous
# (il devient un VectorDrawable dans res/drawable/, cf. 2.2).
layers=(ic_launcher_foreground ic_launcher_monochrome)
densities=(mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432)

for layer in "${layers[@]}"; do
  for e in "${densities[@]}"; do
    d=${e%%:*}; px=${e##*:}
    mkdir -p "$DST/mipmap-$d"
    magick "$SRC/$layer.png" -resize "${px}x${px}" \
      -strip -define webp:lossless=true \
      "$DST/mipmap-$d/$layer.webp"
  done
done
```
Pas de `-trim` : les PNG sont déjà recentrés dans la safe-zone. (`magick` =
ImageMagick 7 ; sur IM6, remplacer par `convert`.)

---

## 3. Câblage manifeste

`app/src/main/AndroidManifest.xml`, bloc `<application>` (lignes 19-23).

**Avant :**
```xml
<application
    android:name=".InkToneApplication"
    android:label="InkTone"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

**Après :**
```xml
<application
    android:name=".InkToneApplication"
    android:label="InkTone"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

---

## 4. Vérification device (protocole `inktone-ui-design` §Méthodologie)

Device : Vivo V2206, Android 14 (Funtouch OS).

1. `./gradlew :app:installDebug`, lancer l'app.
2. **Écran d'accueil** : icône = cacatoès sur crème (plus le robot par défaut).
   Preuve : `adb exec-out screencap -p > /tmp/preuve_launcher.png`.
3. **Masque circulaire** : sur launcher à masque rond, ni le livre ni l'oiseau
   rognés (safe-zone vérifiée à la préparation).
4. **Themed icons ON** (Paramètres → Fond d'écran & style → icônes
   thématiques) : silhouette monochrome tintée, lisible. **Point de vérité
   Funtouch (D5)** : confirmer que Funtouch applique bien le calque monochrome
   des apps tierces — si ignoré, le format n'y change rien, écart à consigner.
   Preuve : screencap dédié.
5. **Infos de l'app** (appui long → ⓘ) : icône correcte partout (recents,
   partage, réglages).

---

## 5. Critères avant / après

- **Avant** : icône Android générique ; `res/` sans `mipmap-*` ; manifeste sans
  `android:icon`.
- **Après** : cacatoès crème visible partout ; monochrome tinté sous themed
  icons (ou écart Funtouch consigné) ; adaptive-icon unique gardée derrière
  `-v26` ; `android:icon` + `android:roundIcon` câblés ; preuves screencap
  jointes à la PR.

---

## 6. Hors périmètre — écarts déclarés

- **Icône de notification (`ic_notification`)** — différée au lot de
  consolidation Media3. Motif racine : la lecture passe encore par
  `AudioSegmentPlayer` (`ReaderViewModel`), pas par `AudioPlaybackService`
  (`infrastructure/media/AudioPlaybackService.kt`, `MediaSessionService`
  minimal, sans `MediaNotification.Provider`). Câbler
  `DefaultMediaNotificationProvider.setNotificationSmallIcon(...)` sur un service
  non exercé par le flux de lecture produirait un asset non vérifiable sur
  device → **décoratif** au sens de nos principes. À traiter quand la lecture
  migrera sur le service (même lot que le « vrai Stop »).
- **Icône de splash (SplashScreen API Android 12+)** — nécessite un thème
  custom `Theme.InkTone` (manifeste encore sur le thème plateforme). → **plan 2**.
- **`playstore_icon_512.png`** — upload Play Console (champ obligatoire), hors
  APK, hors ce lot.
- **Mipmaps legacy** — retirés du périmètre (D1).
- **Détail guide** : `GUIDE_INTEGRATION_ICONE.md` liste un
  `apercu_masque_circulaire.png` absent du dossier — le retirer du tableau ou
  régénérer l'aperçu (sans impact sur ce lot).

---

## 7. Commit (impératif français)

> `Intègre l'icône adaptative InkTone (launcher + monochrome) et la câble au manifeste`

---

## 8. Enchaînement

Une fois ce lot vérifié sur device, le **plan 2** cadrera l'identité au thème :
rampe d'accent violette (Deadly Depths), Work Sans + Literata en variable font,
Material Symbols Rounded (axe FILL), thème custom `Theme.InkTone` (qui débloque
le splash ci-dessus), avec re-vérification des ratios WCAG sur les vrais fonds
`background`/`surface` du thème (`Color.kt:34` `#FFFBF5`, `:66` `#0F1419`),
qui diffèrent des fonds supposés dans le doc identité.
