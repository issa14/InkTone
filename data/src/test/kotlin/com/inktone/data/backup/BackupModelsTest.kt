package com.inktone.data.backup

import com.inktone.domain.valueobject.Locator
import com.inktone.domain.valueobject.LocatorText
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ADR-027 : le contexte textuel d'une position voyage avec la sauvegarde et la synchronisation. */
class BackupModelsTest {

    @Test
    fun `le contexte textuel fait l'aller-retour de la charge utile`() {
        val locator = Locator(
            "ch1.xhtml", 2, charOffset = 40,
            text = LocatorText(before = "avant le point", after = "après le point"),
        )

        val json = backupJson.encodeToString(locator.toBackup())
        val restored = backupJson.decodeFromString<LocatorBackup>(json).toDomain()

        assertEquals(locator.text, restored.text)
        assertEquals(locator, restored)
    }

    @Test
    fun `une sauvegarde anterieure sans contexte reste lisible`() {
        val legacy = """{"resourceHref":"ch1.xhtml","chapterIndex":2,"charOffset":40}"""

        val restored = backupJson.decodeFromString<LocatorBackup>(legacy).toDomain()

        assertEquals(40, restored.charOffset)
        assertNull(restored.text)
    }
}
