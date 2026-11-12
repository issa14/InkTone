package com.inktone.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultVoiceProfileTest {

    /**
     * Audit UX D73 : sans profil enregistré, le Lecteur parlait avec la voix
     * système pendant que les Réglages affichaient le moteur des préférences.
     */
    @Test
    fun le_profil_par_defaut_suit_le_moteur_des_preferences() {
        val profile = VoiceProfile.defaultFor(UserPreferences(defaultTtsEngine = TtsEngineId.EDGE_TTS))

        assertEquals(TtsEngineId.EDGE_TTS, profile.engine)
        assertEquals(availableVoicesFor(TtsEngineId.EDGE_TTS).first(), profile.voice)
    }
}
