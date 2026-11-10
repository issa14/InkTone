package com.inktone.infrastructure.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vecteurs générés par l'implémentation Python de référence
 * (`edge_tts.drm.generate_sec_ms_gec_v2`, edge-tts >= 7.3.0) avec le
 * TrustedClientToken public. Tout écart d'un bit = 403 en production.
 */
class EdgeTtsGecTokenTest {

    private fun gec(unixSeconds: Long) =
        EdgeTtsGecToken.generate(unixSeconds, EdgeTtsClient.TRUSTED_CLIENT_TOKEN)

    @Test
    fun correspond_aux_vecteurs_de_reference() {
        val expected = mapOf(
            0L to "52BC7A17B50DFEC9-28C4394775C8751CD2F6859C030C06DB1489ACAB3B3A7651831FC26895177ED6",
            300L to "F7535A20A262E331-28675BF85C7106BBF20035A78FCB0B6890813EAD5C3B983EF3424931B720BABB",
            1_700_000_000L to "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2",
            1_789_000_000L to "63DEFE91AD9FDACC-D4920F2673D134C3A79C63A155396BAC3F0BCCC008F7A8FB88A3D65ED4998B9A",
            1_789_000_299L to "11B314F1D8E21A68-84A7B09005D697046FD186A915B0F337E8C0251BED7D5797C3062C5A169B9A6D",
        )
        expected.forEach { (ts, token) -> assertEquals("ts=$ts", token, gec(ts)) }
    }

    @Test
    fun arrondit_a_la_fenetre_de_cinq_minutes() {
        assertEquals(gec(0), gec(1))
        assertEquals(gec(0), gec(299))
        assertNotEquals(gec(299), gec(300))
    }

    @Test
    fun format_16_hex_tiret_64_hex() {
        val token = gec(1_789_000_000L)
        assertTrue(Regex("^[0-9A-F]{16}-[0-9A-F]{64}$").matches(token))
    }

    @Test
    fun version_prefixee_par_2() {
        assertTrue(EdgeTtsClient.SEC_MS_GEC_VERSION.startsWith("2-"))
    }
}
