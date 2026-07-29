package com.worxbend.zephyr

import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateCacheAgeTest {
    @Test
    fun cacheAgeUsesStableHumanScaleLabels() {
        assertEquals("just now", candidateCacheAgeLabel(1_000, 1_000))
        assertEquals("5 min old", candidateCacheAgeLabel(0, 5 * 60_000L))
        assertEquals("2 hr old", candidateCacheAgeLabel(0, 2 * 60 * 60_000L))
        assertEquals("2 days old", candidateCacheAgeLabel(0, 2 * 24 * 60 * 60_000L))
    }
}
