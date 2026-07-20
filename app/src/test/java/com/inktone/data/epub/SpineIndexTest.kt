package com.inktone.data.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests pour [SpineIndex.decodeHref] et [SpineIndex.normalizeHref] — reproduisent le
 * sous-problème découvert en testant la tâche 2.2 sur appareil (livre *Anna Karénine*,
 * probablement exporté par Calibre) : des hrefs de TOC avec leurs espaces/ancres
 * percent-encodés (`%20`, `%23`) au lieu de littéraux, qui échouaient entièrement à se
 * résoudre dans le spine. Voir PLAN_ACTION_TOP_TIER_CLAUDECODE.md §2.2bis.
 */
class SpineIndexTest {

    // ── decodeHref ──────────────────────────────────────────

    @Test
    fun `decodeHref laisse un href déjà littéral inchangé`() {
        assertEquals("content/chapitre1.html", SpineIndex.decodeHref("content/chapitre1.html"))
    }

    @Test
    fun `decodeHref convertit les espaces percent-encodés`() {
        assertEquals(
            "content/Tolstoi - Anna Karenine I_split_0.html",
            SpineIndex.decodeHref("content/Tolstoi%20-%20Anna%20Karenine%20I_split_0.html")
        )
    }

    @Test
    fun `decodeHref convertit l'ancre percent-encodée en dièse littéral`() {
        assertEquals(
            "content/split_76.html#cfs_3",
            SpineIndex.decodeHref("content/split_76.html%23cfs_3")
        )
    }

    @Test
    fun `decodeHref décode correctement les caractères UTF-8 multi-octets`() {
        // é = %C3%A9 en UTF-8
        assertEquals("chapitre_été.html", SpineIndex.decodeHref("chapitre_%C3%A9t%C3%A9.html"))
    }

    @Test
    fun `decodeHref ne convertit PAS un plus littéral en espace (contrairement à URLDecoder)`() {
        // Un + littéral dans un nom de fichier ne doit pas être corrompu — ce n'est pas une
        // séquence percent-encoded, contrairement à ce que ferait java.net.URLDecoder
        // (convention application/x-www-form-urlencoded, hors sujet pour un chemin de fichier).
        assertEquals("content/a+b.html", SpineIndex.decodeHref("content/a+b.html"))
    }

    @Test
    fun `decodeHref se replie sur le href original si la séquence percent est malformée`() {
        assertEquals("content/bad%ZZ.html", SpineIndex.decodeHref("content/bad%ZZ.html"))
    }

    @Test
    fun `decodeHref se replie sur le href original si le pourcentage est en fin de chaîne`() {
        assertEquals("content/incomplete%2", SpineIndex.decodeHref("content/incomplete%2"))
    }

    // ── normalizeHref (décodage + suppression ancre + chemin) ──

    @Test
    fun `normalizeHref garde le comportement existant pour un href déjà littéral`() {
        assertEquals("split_76.html", SpineIndex.normalizeHref("content/split_76.html#cfs_3"))
    }

    @Test
    fun `normalizeHref résout un href avec espaces et ancre percent-encodés — cas Anna Karénine`() {
        // Reproduit exactement le href observé dans les logs de l'audit 2.2bis.
        val raw = "content/Tolstoi%20-%20Anna%20Karenine%20I_split_76.html%23cfs_3"
        assertEquals("Tolstoi - Anna Karenine I_split_76.html", SpineIndex.normalizeHref(raw))
    }

    @Test
    fun `normalizeHref supprime bien l'ancre percent-encodée, pas seulement le préfixe de chemin`() {
        val raw = "content/split_0.html%23anchor"
        val normalized = SpineIndex.normalizeHref(raw)
        assertEquals("split_0.html", normalized)
    }
}
