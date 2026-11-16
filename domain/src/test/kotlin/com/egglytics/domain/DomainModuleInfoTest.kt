package com.egglytics.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainModuleInfoTest {
    @Test
    fun `domain constants match fixed specification`() {
        assertEquals(30, DomainModuleInfo.EGGS_PER_BUCKET)
        assertEquals(40, DomainModuleInfo.KG_PER_BAG)
    }
}
