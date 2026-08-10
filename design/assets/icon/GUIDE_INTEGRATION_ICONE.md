# Intégration du logo InkTone (icône app)

## Ce qui a été préparé à partir de ton PNG

Le logo source (1024×1024, silhouette noire sur fond crème) a été décomposé en calques exploitables par le système d'icônes adaptatives d'Android, plus une icône de notification :

| Fichier | Usage | Format |
|---|---|---|
| `ic_launcher_foreground.png` | Calque premier plan (adaptive icon) | 1024×1024, fond transparent, silhouette noire recentrée dans la *safe zone* (66/108) |
| `ic_launcher_monochrome.png` | Icône thématisée Android 13+ (Material You) | Idem, même silhouette |
| `ic_notification.png` | Icône de la notification TTS (lecture en cours) | 512×512, silhouette blanche, fond transparent, marge plus large |
| `playstore_icon_512.png` | Fiche Play Store | 512×512, aplati, sans transparence |
| `apercu_masque_circulaire.png` | Vérification visuelle | Simulation du masque circulaire (le plus agressif) — aucun rognage, tu peux constater les marges |

Couleur de fond extraite du logo : **`#FBFAF6`** (le crème du fond).

J'ai vérifié le respect de la *safe zone* en simulant le masque circulaire (le plus strict des launchers) : aucun élément du perroquet ni du livre n'est rogné.

---

## 1. Icône de lancement (adaptive icon)

Dans Android Studio, sur le module `app` :

1. **File → New → Image Asset**
2. Icon Type : **Launcher Icons (Adaptive and Legacy)**
3. Onglet **Foreground Layer** :
   - Asset Type : *Image*
   - Path : `ic_launcher_foreground.png`
   - Trim : **No** (déjà recentré)
   - Resize : 100 %
4. Onglet **Background Layer** :
   - Asset Type : *Color*
   - Couleur : `#FBFAF6`
5. **Legacy** : laisse cochée la génération legacy (round + square) pour les appareils < Android 8 — Studio la génère automatiquement à partir des deux calques, pas besoin de fichier séparé.
6. Next → Finish. Ça régénère tous les `mipmap-*dpi/ic_launcher.png|webp` + `mipmap-anydpi-v26/ic_launcher.xml`.

Pense à **supprimer les anciens assets** de l'icône signet-égaliseur (bordeaux) dans les dossiers `mipmap-*` si Studio ne les écrase pas tous (noms de fichiers différents éventuels).

---

## 2. Icône thématisée (Android 13+, Material You)

Selon la version d'Android Studio, l'onglet **Monochrome** est proposé directement dans le même wizard (à côté de Foreground/Background) — dans ce cas, fournis `ic_launcher_monochrome.png` là.

Si ton wizard ne propose pas cet onglet, ajoute-le à la main dans `mipmap-anydpi-v26/ic_launcher.xml` (et `ic_launcher_round.xml`) :

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
</adaptive-icon>
```

Et dans `res/values/colors.xml` :

```xml
<color name="ic_launcher_background">#FBFAF6</color>
```

Sans cette entrée `monochrome`, l'icône InkTone s'affichera simplement en mode "non thématisé" quand l'utilisateur active les icônes Material You — pas bloquant, mais autant le faire proprement puisque le fichier est prêt.

---

## 3. Icône de notification (lecture TTS en cours)

Comme InkTone tourne un service TTS en foreground pendant la narration, la notification persistante a besoin de sa propre icône : **une seule couleur (blanc) + alpha**, Android ignore le RGB et applique sa propre teinte.

1. **File → New → Image Asset**
2. Icon Type : **Notification Icons**
3. Path : `ic_notification.png`
4. Trim : No, Resize : 100 %
5. Ça génère `drawable-*dpi/ic_notification.png` (ou un `ic_notification.xml` vectoriel selon l'option choisie).

Référence-la dans le `NotificationCompat.Builder` (ou `MediaStyle`) du service de lecture :

```kotlin
.setSmallIcon(R.drawable.ic_notification)
```

Si une icône de notification différente (bordeaux) existe déjà dans le code du service TTS, c'est le seul endroit à modifier — le reste (foreground/background) ne touche pas cette référence.

---

## 4. Play Store

`playstore_icon_512.png` est prêt à l'emploi : Play Console → Présence sur le Store → Ressources graphiques → **Icône de l'application** (512×512, PNG 32 bits, pas de transparence — déjà le cas ici).

---

## 5. Bonus — écran de démarrage (SplashScreen API)

Si InkTone utilise `androidx.core:core-splashscreen` (recommandé sur Android 12+ au lieu d'un splash maison), le même `ic_launcher_foreground.png` peut servir de `windowSplashScreenAnimatedIcon` dans le thème splash — évite de fabriquer un troisième asset. À vérifier selon la contrainte de taille (l'icône de splash doit tenir dans un cercle de 240dp, contre 108dp pour le launcher ; comme le tien respecte déjà largement la safe zone avec de la marge, il devrait passer sans retouche).

---

## Résumé rapide

- Fond adaptive icon : `#FBFAF6`
- Foreground + monochrome : mêmes fichiers, même position (safe zone respectée, vérifié au masque circulaire)
- Notification : fichier séparé, blanc, alpha uniquement
- Play Store : fichier flatten, prêt à uploader tel quel
