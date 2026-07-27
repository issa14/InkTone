package com.inktone.infrastructure.parser

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Regroupe les garde-fous de regression K3/K6/K7 (Blueprint §14.6) en un
 * seul point d'execution, pour un audit rapide — ne duplique aucun test,
 * reference seulement ceux qui existent deja depuis les Phases 2/3/4.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    HrefEncodingTest::class,     // K6 (Tache 4.3)
    DrmDetectionTest::class,     // K7 (Tache 4.4)
    // K3 (reprise de lecture) vit dans feature/reader :
    // ReadingResumeTest (Tache 3.6) — module different, pas inclus dans
    // cette Suite JUnit (limitation technique : une Suite ne traverse
    // pas les modules Gradle). Documente ici comme rappel, pas un oubli.
)
class RegressionGuardsSuite
