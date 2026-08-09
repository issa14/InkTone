# Lot Onboarding — périmètre (note de scope, pas un plan)

Ce fichier n'est pas un plan d'exécution complet — juste l'enregistrement
d'un élément de périmètre à traiter quand ce lot sera planifié, pour ne
pas le perdre entre-temps.

## Consentement crash reporting (ADR-014) — à auditer

**Origine :** commit `763aaa2 Ajoute le rapport de crash Firebase
Crashlytics en opt-in (ADR-014)`, livré sur la branche
`lot-2b-presentation-livres` pendant le lot 3c (navigation du lecteur).
Ce travail ne relève pas du lot 3c — c'est le consentement crash, l'une
des étapes fonctionnelles que la cible (`UX_FLOW_DESIGN.md`) renvoie à
l'écran d'onboarding.

**Statut du commit lui-même :** l'implémentation (module
`infrastructure/crashreporting`, binding Firebase/no-op selon
`google-services.json`, observation continue de
`UserPreferences.crashReportingEnabled`) a été vérifiée techniquement —
build vert avec et sans `google-services.json`,
`checkArchitectureRules` passant, installation et lancement réels sur
appareil sans crash. **Ce qui n'a PAS été vérifié**, faute d'être dans le
périmètre du lot qui l'a livré : le flux de consentement lui-même côté
UX, listé ci-dessous.

**À auditer quand ce lot sera planifié :**

- **Point de déclenchement réel du consentement** — `OnboardingScreen`/
  `OnboardingViewModel` (`feature/onboarding`) exposent déjà
  `OnboardingIntent.SetCrashReporting`, mais son câblage avec le
  `CrashReporter` réel (`infrastructure/crashreporting`, module `app`
  ajouté hors périmètre par le commit ci-dessus) n'a pas été audité comme
  un flux de bout en bout — seule l'observation continue de la préférence
  a été vérifiée isolément.
- **Formulation** — le texte présenté à l'utilisateur à l'écran
  d'onboarding pour expliquer honnêtement ce que Crashlytics collecte
  (ADR-014 : « avec une explication honnête de son contenu »).
- **Comportement en cas de refus** — confirmer qu'aucune donnée n'est
  collectée si l'utilisateur refuse à l'onboarding (pas seulement que le
  flag reste à `false` par défaut avant toute décision).
- **Conformité à ADR-014** — relire l'implémentation livrée contre
  chacun des points de la décision (opt-in explicite, réversible dans les
  réglages, no-op gracieux sans identifiant Firebase commis) avec le
  regard du lot Onboarding, pas seulement celui du lot qui l'a livrée.

**Règle pour la suite** (rappel, pas spécifique à cet item) : un travail
hors périmètre d'un lot ne voyage pas avec lui. S'il apparaît nécessaire
en cours de route, le signaler plutôt que le livrer — un commit non
planifié échappe par construction à la vérification du lot qui le
transporte.

---

## Audit réalisé (Lot 10, Tâche 10.5)

**Contexte du lot** : `CrashConsentStep` est retiré de l'onboarding (Tâche
10.3, décision actée depuis la conception — l'onboarding redevient une
pure présentation). Le consentement doit donc exister ailleurs. Les
quatre points ci-dessous sont audités contre l'état réel du code, pas
contre ce que l'onboarding affichait.

1. **Point de déclenchement réel — flux de bout en bout vérifié.**
   `SettingsIntent.SetCrashReportingEnabled` (carte Confidentialité,
   `SettingsScreen.kt`) → `preferencesRepository.update(...)` →
   `CrashReportingConsentObserver.start()` (démarré dans
   `InkToneApplication.onCreate`, observation continue de
   `UserPreferences.crashReportingEnabled`) → `CrashReporter
   .setCollectionEnabled(...)`. Chaîne complète, pas seulement
   l'observation isolée vérifiée au lot 3c.
2. **Formulation — écart trouvé et corrigé ce lot.** Le seul texte
   honnête sur le contenu collecté vivait dans `CrashConsentStep`,
   retiré. `ToggleSetting` (Réglages) n'avait qu'un libellé nu. Corrigé :
   description ajoutée sous le toggle « Rapports de crash » (« Envoie
   uniquement la trace d'erreur, la version de l'app et le modèle
   d'appareil. Jamais le contenu de vos livres ni vos annotations. »),
   même formulation que l'ancien onboarding.
3. **Comportement en cas de refus — conforme.** Désactivé par défaut à
   deux niveaux : `UserPreferences.crashReportingEnabled = false` (Kotlin)
   ET `firebase_crashlytics_collection_enabled=false` (méta-donnée
   manifest, couvre la fenêtre avant `onCreate`). Aucune collecte avant
   toute décision utilisateur. `NoOpCrashReporter` (build sans
   `google-services.json`) est un no-op réel, pas un flag ignoré.
4. **Conformité ADR-014 — les trois clauses de la décision sont
   respectées.** Opt-in explicite (défaut désactivé) ✓. Réversible à
   tout moment dans les Réglages ✓ (toggle, effet immédiat via
   l'observation continue). No-op gracieux sans identifiant Firebase
   commis ✓ (`NoOpCrashReporter`, lié par Hilt selon
   `BuildConfig.FIREBASE_CRASHLYTICS_ENABLED`).

**Conclusion** : conforme à ADR-014, à l'exception du point 2 (formulation
absente des Réglages), corrigé dans ce même lot.
