package com.worxbend.zephyr.data

import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmProxyConfigurationServiceTest {
    @Test
    fun passwordUsesSecretStoreAndOnlyEncodedProxyReachesChildEnvironment() = runBlocking {
        val preferences = Preferences.userRoot().node("/com/worxbend/zephyr/tests/proxy-${System.nanoTime()}")
        val secrets = RecordingProxySecrets()
        try {
            val service = JvmProxyConfigurationService(preferences, secrets)
            val result = service.save(
                ProxyConfiguration(true, "proxy.example.com", 8443, "build user"),
                "p@ss word",
            )

            assertTrue(result.success)
            assertEquals("p@ss word", secrets.value)
            assertFalse(preferences.keys().any { it.contains("password", ignoreCase = true) })
            assertEquals(
                "http://build%20user:p%40ss%20word@proxy.example.com:8443",
                service.environment()["HTTPS_PROXY"],
            )
            assertTrue(service.load().hasStoredPassword)
        } finally {
            preferences.removeNode()
            Preferences.userRoot().flush()
        }
    }

    @Test
    fun unavailableSecretServiceRefusesPasswordWithoutSavingCoordinates() = runBlocking {
        val preferences = Preferences.userRoot().node("/com/worxbend/zephyr/tests/proxy-${System.nanoTime()}")
        try {
            val service = JvmProxyConfigurationService(preferences, RecordingProxySecrets(canWrite = false))
            val result = service.save(
                ProxyConfiguration(true, "proxy.example.com", 8080, "alex"),
                "secret",
            )

            assertFalse(result.success)
            assertEquals("", preferences.get("proxy-host", ""))
        } finally {
            preferences.removeNode()
            Preferences.userRoot().flush()
        }
    }

    private class RecordingProxySecrets(
        private val canWrite: Boolean = true,
    ) : ProxySecretStore {
        var value: String? = null
        override fun read(): String? = value
        override fun write(secret: String): Boolean {
            if (!canWrite) return false
            value = secret
            return true
        }
        override fun clear(): Boolean {
            value = null
            return true
        }
    }
}
