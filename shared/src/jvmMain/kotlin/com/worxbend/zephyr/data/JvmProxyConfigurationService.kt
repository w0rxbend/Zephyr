package com.worxbend.zephyr.data

import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface ProxySecretStore {
    fun read(): String?
    fun write(secret: String): Boolean
    fun clear(): Boolean
}

internal class SecretToolProxyStore(
    private val executable: File = File("/usr/bin/secret-tool"),
) : ProxySecretStore {
    override fun read(): String? = run("lookup", "application", "zephyr", "kind", "proxy")?.trimEnd()

    override fun write(secret: String): Boolean =
        run("store", "--label=Zephyr proxy password", "application", "zephyr", "kind", "proxy", input = secret) != null

    override fun clear(): Boolean =
        run("clear", "application", "zephyr", "kind", "proxy") != null

    private fun run(vararg arguments: String, input: String? = null): String? {
        if (!executable.canExecute()) return null
        return runCatching {
            val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
                .redirectErrorStream(true)
                .start()
            if (input != null) {
                process.outputStream.bufferedWriter().use { it.write(input) }
            }
            val output = process.inputStream.bufferedReader().readText()
            output.takeIf { process.waitFor() == 0 }
        }.getOrNull()
    }
}

internal class JvmProxyConfigurationService(
    private val preferences: Preferences = Preferences.userNodeForPackage(JvmProxyConfigurationService::class.java),
    private val secrets: ProxySecretStore = SecretToolProxyStore(),
) : ProxyConfigurationService {
    override suspend fun load(): ProxyConfiguration = withContext(Dispatchers.IO) { loadNow() }

    override suspend fun save(configuration: ProxyConfiguration, newPassword: String?): ProxySaveResult =
        withContext(Dispatchers.IO) {
            configuration.validationError()?.let { return@withContext ProxySaveResult(false, it) }
            if (!newPassword.isNullOrEmpty() && !secrets.write(newPassword)) {
                return@withContext ProxySaveResult(false, "Linux Secret Service is unavailable; the password was not saved.")
            }
            preferences.putBoolean(ENABLED, configuration.enabled)
            preferences.put(HOST, configuration.host.trim())
            preferences.putInt(PORT, configuration.port)
            preferences.put(USERNAME, configuration.username.trim())
            preferences.flush()
            ProxySaveResult(true, "Proxy configuration saved.")
        }

    override suspend fun clearPassword(): ProxySaveResult = withContext(Dispatchers.IO) {
        if (secrets.read() == null || secrets.clear()) {
            ProxySaveResult(true, "Stored proxy password cleared.")
        } else {
            ProxySaveResult(false, "Linux Secret Service could not clear the proxy password.")
        }
    }

    internal fun environment(): Map<String, String> {
        val configuration = loadNow()
        if (!configuration.enabled || configuration.validationError() != null) return emptyMap()
        val password = secrets.read()
        val credentials = when {
            configuration.username.isBlank() -> ""
            password == null -> "${configuration.username.urlEncoded()}@"
            else -> "${configuration.username.urlEncoded()}:${password.urlEncoded()}@"
        }
        val proxy = "http://$credentials${configuration.host}:${configuration.port}"
        return mapOf(
            "HTTP_PROXY" to proxy,
            "HTTPS_PROXY" to proxy,
            "http_proxy" to proxy,
            "https_proxy" to proxy,
        )
    }

    private fun loadNow(): ProxyConfiguration =
        ProxyConfiguration(
            enabled = preferences.getBoolean(ENABLED, false),
            host = preferences.get(HOST, ""),
            port = preferences.getInt(PORT, 8080),
            username = preferences.get(USERNAME, ""),
            hasStoredPassword = secrets.read() != null,
        )

    private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

    private companion object {
        const val ENABLED = "proxy-enabled"
        const val HOST = "proxy-host"
        const val PORT = "proxy-port"
        const val USERNAME = "proxy-username"
    }
}

actual fun createProxyConfigurationService(): ProxyConfigurationService = JvmProxyConfigurationService()
