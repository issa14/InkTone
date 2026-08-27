# ─────────────────────────────────────────────────────────────────────
# AUDIT_REACTIVITE_UX.md §3.2 — R8 activé en release.
#
# Règles de conservation pour les bibliothèques qui s'appuient sur JNI ou
# la réflexion et qui ne livrent AUCUNE règle consommateur. Vérifié sur le
# classpath réel (jamais supposé) :
#   • sherpa-onnx AAR (app/libs) : `proguard.txt` vide (0 octet).
#   • onnxruntime-android AAR : aucun fichier proguard.
#   • readium-shared / readium-streamer AAR : aucun fichier proguard.
# Hilt, Room, WorkManager, Media3 livrent leurs propres règles consommateur
# et n'ont pas besoin de traitement ici.
#
# La validation sur appareil du parcours import → lecture → TTS neuronal
# est OBLIGATOIRE : les tests JVM ne verraient pas une panne JNI ni de
# désérialisation.
# ─────────────────────────────────────────────────────────────────────

# ── JNI ─────────────────────────────────────────────────────────────
# libsherpa-onnx-jni.so (sherpa-onnx) et libonnxruntime.so résolvent les
# méthodes natives par nom de classe (RegisterNatives / mangling `Java_…`) :
# toute obfuscation des noms de classes casse la liaison native.

-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class ai.onnxruntime.** { *; }

# ── kotlinx.serialization (Readium + modèles du projet) ─────────────
# Readium publie ses modèles JSON (Publication, Locator, …) via
# kotlinx.serialization. Le plugin de sérialisation du projet ne génère des
# règles que pour les @Serializable du code du projet, pas pour ceux de la
# bibliothèque.

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, InnerClasses, Signature, *Annotation*
-dontnote kotlinx.serialization.AnnotationsKt

# Modèles du projet (ceinture de sécurité : le plugin kotlinx-serialization
# génère déjà ces règles pour les modules où il est appliqué).
-keep,includedescriptorclasses class com.inktone.**$$serializer { *; }
-keepclassmembers class com.inktone.** {
    *** Companion;
}
-keepclasseswithmembers class com.inktone.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Modèles de Readium (non couverts par le plugin du projet).
-keep,includedescriptorclasses class org.readium.**$$serializer { *; }
-keepclassmembers class org.readium.** {
    *** Companion;
}
-keepclasseswithmembers class org.readium.** {
    kotlinx.serialization.KSerializer serializer(...);
}
