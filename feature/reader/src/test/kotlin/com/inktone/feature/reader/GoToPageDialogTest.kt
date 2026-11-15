package com.inktone.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoToPageDialogTest {

    @Test
    fun `un numero de page valide donne son index`() {
        assertEquals(0, parsePageInput("1", 356))
        assertEquals(199, parsePageInput(" 200 ", 356))
        assertEquals(355, parsePageInput("356", 356))
    }

    @Test
    fun `un numero hors du livre ou illisible est refuse`() {
        assertNull(parsePageInput("0", 356))
        assertNull(parsePageInput("357", 356))
        assertNull(parsePageInput("", 356))
        assertNull(parsePageInput("abc", 356))
    }
}
