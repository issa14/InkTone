package com.inktone.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapedForLikeTest {
    @Test
    fun neutralise_les_jokers_et_l_antislash() {
        assertEquals("50\\% de a\\_b", "50% de a_b".escapedForLike())
        assertEquals("c:\\\\dir", "c:\\dir".escapedForLike())
        assertEquals("élève", "élève".escapedForLike())
    }
}
