# Vérification du chemin release — v1.0.0

**Date :** 2026-08-23 — **Appareil :** V2206 (Snapdragon 680, Android 14)
— **Base :** `main` après `97645377`.

Jusqu'ici, aucun artefact de release n'avait jamais été produit **ni
exécuté** : `./gradlew build` et `assembleDebugAndroidTest` ne disent
rien du buildType release, qui a sa propre signature, son propre
packaging natif et son propre `google-services.json` actif.

## Constaté sur l'artefact réel

| Point | Résultat |
|---|---|
| `./gradlew bundleRelease` | succès |
| Taille de l'AAB | **30,0 Mo** (budget Blueprint §11.2 : 60 Mo) |
| Signature | `jarsigner -verify` → `jar verified`, alias `INKTONE` |
| Certificat | RSA 2048, SHA256withRSA, valide jusqu'au 03/12/2053 |
| SHA-1 du certificat | `6E:3D:BF:3B:19:C3:B4:81:FE:1C:99:8D:00:41:E8:87:BD:69:ED:AA` |
| ABI embarquées | `arm64-v8a` uniquement |
| `uses-permission` du manifeste fusionné | les 7 documentées dans `PRIVACY.md`, aucune de plus |
| APK release installé sur device | démarre ; cycle de synchronisation Drive complet (liaison, synchronisation, déliaison) |

`DUMP` et `BIND_JOB_SERVICE` apparaissent dans le manifeste fusionné mais
**ne sont pas** des `uses-permission` : ce sont des attributs de
protection posés par `profileinstaller` et WorkManager sur leurs propres
composants. Ne pas les rajouter à la politique de confidentialité.

## OAuth Google — deux pièges payés en vrai

1. **Un client OAuth Android est lié à un couple `package + SHA-1`.** Le
   client utilisé en développement porte le SHA-1 de la clé *debug* : il
   ne peut pas servir à un build signé avec `inktone-release.jks`. Il
   faut un **second client**, créé avec le SHA-1 ci-dessus et le package
   `com.inktone.app`.
2. **Le schéma d'URI personnalisé est désactivé par défaut** sur tout
   client Android récemment créé — le flux AppAuth échoue alors avec
   `Error 400: invalid_request — Custom URI scheme is not enabled for
   your Android client`. À activer explicitement dans la fiche du client
   (Google Cloud → Identifiants), **sur chacun des deux clients**, avec un
   délai de propagation et une réinstallation de l'app pour purger l'état
   AppAuth en cache.

Empreintes des deux clés de signature, à retrouver sur la fiche du client
OAuth correspondant :

| Clé | SHA-1 |
|---|---|
| release (`inktone-release.jks`) | `6E:3D:BF:3B:19:C3:B4:81:FE:1C:99:8D:00:41:E8:87:BD:69:ED:AA` |
| debug (`~/.android/debug.keystore`, machine de développement) | `19:FB:11:89:0F:56:9A:94:BA:B9:F1:2A:14:7A:DF:1B:8F:E3:00:E7` |

## Un client OAuth par buildType

Corollaire du piège n°1, traité dans la foulée : `local.properties` porte
désormais deux couples, et le build choisit selon le buildType.

| Clé | Utilisée par |
|---|---|
| `GOOGLE_OAUTH_CLIENT_ID` / `GOOGLE_OAUTH_REDIRECT_SCHEME` | `release` et `benchmark` |
| `GOOGLE_OAUTH_CLIENT_ID_DEBUG` / `GOOGLE_OAUTH_REDIRECT_SCHEME_DEBUG` | `debug`, avec repli sur les clés ci-dessus si absentes |

Les trois valeurs d'un même buildType (les deux `BuildConfig` et le
placeholder `appAuthRedirectScheme`) doivent désigner **le même client** :
les séparer produit une redirection qui n'appartient pas au `clientId`
annoncé, donc un `invalid_request` que rien dans le code n'explique.

Vérifié en fabriquant un `..._DEBUG` factice : le `BuildConfig` de la
variante debug et le manifeste fusionné de `app` prennent bien la valeur
debug, la variante release reste inchangée.

**Tant que les clés `*_DEBUG` ne sont pas renseignées, la synchronisation
des builds debug ne fonctionne pas** — le repli utilise le client
release, dont le SHA-1 ne correspond pas à la clé de signature debug.

Une fois les deux clients configurés et les clés renseignées, le cycle
complet a été vérifié sur appareil dans les deux variantes : liaison,
synchronisation et déliaison fonctionnent en debug comme en release.

## Reste ouvert

- **Écran de consentement OAuth** : tant qu'il est en état « Test », les
  jetons de rafraîchissement expirent au bout de 7 jours — la
  synchronisation se délierait toute seule chaque semaine. Passage en
  Production à faire avant publication, en vérifiant la classification
  de la portée `drive.appdata` affichée par la console.
