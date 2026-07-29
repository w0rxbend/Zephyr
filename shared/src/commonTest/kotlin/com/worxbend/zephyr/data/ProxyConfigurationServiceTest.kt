package com.worxbend.zephyr.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyConfigurationServiceTest {
    @Test
    fun validatesProxyCoordinatesWithoutAcceptingEmbeddedCredentials() {
        assertEquals(null, ProxyConfiguration(true, "proxy.example.com", 8443, "alex").validationError())
        assertEquals(
            "Enter a hostname without a scheme, path, or credentials.",
            ProxyConfiguration(true, "https://proxy.example.com", 8443).validationError(),
        )
        assertEquals(
            "Proxy port must be between 1 and 65535.",
            ProxyConfiguration(true, "proxy.example.com", 0).validationError(),
        )
    }
}
