package com.worxbend.zephyr.sdkman

import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmCandidateMetadataCacheStoreTest {
    @Test
    fun cacheRoundTripsMetadataAndTimestampDeterministically() {
        val path = Files.createTempFile("zephyr-catalog", ".cache")
        try {
            val items = listOf(
                CandidateCatalogItem(
                    name = "gradle",
                    displayName = "Gradle",
                    stableVersion = "8.14",
                    description = "Build automation\twith unicode ✓",
                    websiteUrl = "https://gradle.org",
                    kind = CandidateKind.Sdk,
                    isInstalled = true,
                ),
            )
            val store = JvmCandidateMetadataCacheStore(path) { 42L }

            store.save(items)

            val loaded = store.load()!!
            assertEquals(42L, loaded.cachedAtEpochMillis)
            assertEquals(items.map { it.copy(isInstalled = false) }, loaded.items)
            assertEquals(
                renderCandidateCache(loaded),
                renderCandidateCache(parseCandidateCache(renderCandidateCache(loaded))),
            )
        } finally {
            path.deleteIfExists()
        }
    }
}
