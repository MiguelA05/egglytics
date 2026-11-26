package com.egglytics.ui

import com.egglytics.domain.model.EggClassification.AAA
import com.egglytics.domain.model.EggClassification.JUMBO
import org.junit.Assert.assertEquals
import org.junit.Test

class GradedEggSummaryTest {
    @Test
    fun `partial buckets from different grades stay loose`() {
        val summary = requireNotNull(summarizeGradedEggs(mapOf(AAA to "9", JUMBO to "28")))
        assertEquals(0.toBigInteger(), summary.fullBuckets)
        assertEquals(37L, summary.looseEggs)
        assertEquals(37.toBigInteger(), summary.total)
    }

    @Test
    fun `each grade contributes its own full buckets and remainder`() {
        val summary = requireNotNull(summarizeGradedEggs(mapOf(AAA to "30", JUMBO to "62")))
        assertEquals(3.toBigInteger(), summary.fullBuckets)
        assertEquals(2L, summary.looseEggs)
        assertEquals(92.toBigInteger(), summary.total)
    }
}
