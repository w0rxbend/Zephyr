package com.worxbend.zephyr.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectivityTest {
    @Test
    fun classifiesNetworkRequirementsByActualSdkmanBehavior() {
        assertTrue(SdkmanTransaction.Install("java", "21.0.5-tem").requiresNetwork)
        assertTrue(SdkmanTransaction.RefreshMetadata.requiresNetwork)
        assertTrue(SdkmanTransaction.SelfUpdate.requiresNetwork)
        assertTrue(SdkmanTransaction.CleanLocalOnly("java", listOf("17.0.1-tem")).requiresNetwork)

        assertFalse(SdkmanTransaction.Uninstall("java", "17.0.1-tem").requiresNetwork)
        assertFalse(SdkmanTransaction.SetDefault("java", "21.0.5-tem").requiresNetwork)
    }
}
