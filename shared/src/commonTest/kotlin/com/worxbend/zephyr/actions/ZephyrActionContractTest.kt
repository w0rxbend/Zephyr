package com.worxbend.zephyr.actions

import kotlin.test.Test
import kotlin.test.assertEquals

class ZephyrActionContractTest {
    @Test
    fun stableActionIdsAreUniqueAndRequestsRejectUnknownSurfaceArea() {
        assertEquals(ZEPHYR_ACTIONS.size, ZEPHYR_ACTIONS.distinctBy { it.id }.size)
        assertEquals(null, ZephyrActionRequest(ZephyrActionIds.RefreshInstalled).validationError())
        assertEquals("Unknown action ID.", ZephyrActionRequest("plugin.unknown").validationError())
        assertEquals(
            "Action contains unsupported parameters.",
            ZephyrActionRequest(ZephyrActionIds.RefreshInstalled, parameters = mapOf("path" to "/tmp")).validationError(),
        )
    }
}
