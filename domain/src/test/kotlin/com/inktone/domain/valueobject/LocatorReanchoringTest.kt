package com.inktone.domain.valueobject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocatorReanchoringTest {

    private val oldText = "Il était une fois un roi qui avait trois fils. Le plus jeune partit seul sur la route du nord."

    private fun locatorAt(offset: Int, text: String = oldText) = Locator(
        resourceHref = "ch1.xhtml", chapterIndex = 0, charOffset = offset,
        text = LocatorText.capture(text, offset),
    )

    @Test
    fun `le contexte est capture de part et d'autre du point, borne aux bords du chapitre`() {
        val middle = LocatorText.capture(oldText, 40)!!
        assertEquals(oldText.substring(8, 40), middle.before)
        assertEquals(oldText.substring(40, 72), middle.after)

        val start = LocatorText.capture(oldText, 0)!!
        assertEquals("", start.before)
        assertEquals(32, start.after.length)
        assertNull(LocatorText.capture("", 0))
    }

    @Test
    fun `le contexte n'entre pas dans l'egalite de deux positions`() {
        val withContext = locatorAt(40)
        val without = withContext.copy(text = null)
        assertEquals(withContext, without)
        assertEquals(withContext.hashCode(), without.hashCode())
    }

    @Test
    fun `un texte inchange garde la meme position`() {
        val result = LocatorReanchoring.reanchor(locatorAt(40), oldText)
        assertEquals(40, result.locator.charOffset)
        assertTrue(result.anchored)
    }

    @Test
    fun `des espaces regroupes ou ajoutes ne font pas perdre le passage`() {
        // Le parseur a évolué : une espace devient deux, un saut de ligne apparaît.
        val newText = "Il  était une fois   un roi\nqui avait trois fils. Le plus jeune partit seul sur la route du nord."
        val result = LocatorReanchoring.reanchor(locatorAt(40), newText)

        assertTrue(result.anchored)
        // Le point reste entre « trois » et « fils », où il était.
        assertEquals(newText.indexOf(" fils"), result.locator.charOffset)
    }

    @Test
    fun `un retour a la ligne ajoute avant le passage deplace l'offset de sa longueur`() {
        val newText = "Chapitre un\n" + oldText
        val result = LocatorReanchoring.reanchor(locatorAt(40), newText)

        assertTrue(result.anchored)
        assertEquals(40 + "Chapitre un\n".length, result.locator.charOffset)
        // Le contexte est recapturé dans le nouveau texte.
        assertEquals(LocatorText.capture(newText, result.locator.charOffset), result.locator.text)
    }

    @Test
    fun `un passage repete est ancre sur l'occurrence la plus proche de l'ancien offset`() {
        val refrain = "Et le vent soufflait sur la plaine, encore et toujours. "
        val text = refrain + "Entre-deux. " + refrain + "Autre chose. " + refrain
        val second = text.indexOf(refrain, refrain.length) + 20
        val locator = Locator("ch.xhtml", 0, charOffset = second, text = LocatorText.capture(text, second))
        // Le texte évolue en amont : tout est décalé de 5 caractères.
        val newText = "XXXXX$text"

        val result = LocatorReanchoring.reanchor(locator, newText)

        assertTrue(result.anchored)
        assertEquals(second + 5, result.locator.charOffset)
    }

    @Test
    fun `un passage supprime n'est pas reancre et l'offset est seulement borne`() {
        val locator = locatorAt(80)
        val result = LocatorReanchoring.reanchor(locator, "Texte entièrement différent.")

        assertFalse(result.anchored)
        assertEquals("Texte entièrement différent.".length, result.locator.charOffset)
    }

    @Test
    fun `sans contexte, la position n'est pas reancree mais jamais supprimee`() {
        val locator = Locator("ch.xhtml", 0, charOffset = 10)
        val result = LocatorReanchoring.reanchor(locator, "Un texte assez long pour contenir l'offset.")

        assertFalse(result.anchored)
        assertEquals(10, result.locator.charOffset)
    }

    @Test
    fun `le reancrage est idempotent`() {
        val newText = "Chapitre un\n" + oldText
        val once = LocatorReanchoring.reanchor(locatorAt(40), newText)
        val twice = LocatorReanchoring.reanchor(once.locator, newText)

        assertEquals(once.locator.charOffset, twice.locator.charOffset)
        assertTrue(twice.anchored)
    }

    @Test
    fun `before seul puis after seul servent de repli`() {
        // Le texte juste après le point a changé : seul « before » est retrouvé.
        val locator = locatorAt(40)
        val newText = oldText.substring(0, 40) + "TEXTE REMPLACE QUI N'EXISTAIT PAS AVANT ICI."
        val result = LocatorReanchoring.reanchor(locator, newText)
        assertTrue(result.anchored)
        assertEquals(40, result.locator.charOffset)
    }
}
