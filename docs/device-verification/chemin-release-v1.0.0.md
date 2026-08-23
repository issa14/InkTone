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
   (Google Cloud → Identifiants), avec un délai de propagation et une
   réinstallation de l'app pour purger l'état AppAuth en cache.

## Reste ouvert

- **Un seul couple ID/schéma dans `local.properties`**, lu identiquement
  par tous les buildTypes (`infrastructure/sync/build.gradle.kts`).
  Depuis le passage au client release, la synchronisation des builds
  **debug** ne fonctionne plus. Un couple par buildType est nécessaire
  pour que les deux coexistent.
- **Écran de consentement OAuth** : tant qu'il est en état « Test », les
  jetons de rafraîchissement expirent au bout de 7 jours — la
  synchronisation se délierait toute seule chaque semaine. Passage en
  Production à faire avant publication, en vérifiant la classification
  de la portée `drive.appdata` affichée par la console.
