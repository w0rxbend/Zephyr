package com.worxbend.zephyr.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiskImpactTest {
    @Test
    fun formatsBinaryDiskSizesForDesktopPresentation() {
        assertEquals("0 B", formatByteSize(0))
        assertEquals("1.0 KiB", formatByteSize(1_024))
        assertEquals("1.5 MiB", formatByteSize(1_572_864))
        assertEquals("2.0 GiB", formatByteSize(2_147_483_648))
    }

    @Test
    fun rejectsNegativeDiskMeasurements() {
        assertFailsWith<IllegalArgumentException> {
            DiskImpactEstimate(
                kind = DiskImpactKind.Reclaimable,
                bytes = -1,
                confidence = EstimateConfidence.Exact,
                explanation = "invalid",
            )
        }
    }
}
