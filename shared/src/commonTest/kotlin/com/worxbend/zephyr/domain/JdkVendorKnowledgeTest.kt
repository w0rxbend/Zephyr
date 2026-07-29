package com.worxbend.zephyr.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdkVendorKnowledgeTest {
    @Test
    fun curatedVendorKnowledgeIsVersionedUniqueAndSourced() {
        assertTrue(JDK_VENDOR_KNOWLEDGE_VERSION.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertEquals(
            JDK_VENDOR_KNOWLEDGE.size,
            JDK_VENDOR_KNOWLEDGE.distinctBy(JdkVendorKnowledge::sdkmanCode).size,
        )
        assertTrue(JDK_VENDOR_KNOWLEDGE.all { it.sourceUrl.startsWith("https://") })
        assertTrue(JDK_VENDOR_KNOWLEDGE.all { it.summary.isNotBlank() && it.supportCharacteristics.isNotBlank() })
    }

    @Test
    fun providerNamesResolveThroughCuratedKnowledgeWithSafeFallback() {
        assertEquals("Eclipse Temurin", javaProviderName("tem"))
        assertEquals("unknown", javaProviderName("unknown"))
        assertEquals(null, javaProviderName(null))
    }
}
