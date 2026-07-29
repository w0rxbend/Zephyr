package com.worxbend.zephyr.sdkman

import com.worxbend.zephyr.data.CandidateMetadataCache
import com.worxbend.zephyr.domain.CandidateCatalogItem
import com.worxbend.zephyr.domain.CandidateKind
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Base64

interface CandidateMetadataCacheStore {
    fun load(): CandidateMetadataCache?
    fun save(items: List<CandidateCatalogItem>)
}

object NoOpCandidateMetadataCacheStore : CandidateMetadataCacheStore {
    override fun load(): CandidateMetadataCache? = null
    override fun save(items: List<CandidateCatalogItem>) = Unit
}

internal class JvmCandidateMetadataCacheStore(
    private val path: Path = defaultCandidateCachePath(),
    private val clock: () -> Long = System::currentTimeMillis,
) : CandidateMetadataCacheStore {
    override fun load(): CandidateMetadataCache? = runCatching {
        if (!Files.isRegularFile(path)) return null
        parseCandidateCache(Files.readString(path))
    }.getOrNull()

    override fun save(items: List<CandidateCatalogItem>) {
        runCatching {
            path.parent?.let(Files::createDirectories)
            Files.writeString(
                path,
                renderCandidateCache(CandidateMetadataCache(clock(), items)),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
    }
}

internal fun renderCandidateCache(cache: CandidateMetadataCache): String = buildString {
    appendLine("zephyr-candidate-cache=1")
    appendLine("cached-at-epoch-millis=${cache.cachedAtEpochMillis}")
    cache.items.sortedBy(CandidateCatalogItem::name).forEach { item ->
        appendLine(
            listOf(
                item.name.encoded(),
                item.displayName.encoded(),
                item.stableVersion.encodedNullable(),
                item.description.encodedNullable(),
                item.websiteUrl.encodedNullable(),
                item.kind.name,
            ).joinToString("\t"),
        )
    }
}

internal fun parseCandidateCache(content: String): CandidateMetadataCache {
    val lines = content.lineSequence().filter(String::isNotBlank).toList()
    require(lines.firstOrNull() == "zephyr-candidate-cache=1") { "Unsupported candidate cache schema." }
    val cachedAt = lines.getOrNull(1)
        ?.takeIf { it.startsWith("cached-at-epoch-millis=") }
        ?.substringAfter('=')
        ?.toLongOrNull()
        ?: error("Candidate cache timestamp is invalid.")
    val items = lines.drop(2).map { line ->
        val fields = line.split('\t')
        require(fields.size == 6) { "Malformed candidate cache entry." }
        CandidateCatalogItem(
            name = fields[0].decoded(),
            displayName = fields[1].decoded(),
            stableVersion = fields[2].decodedNullable(),
            description = fields[3].decodedNullable(),
            websiteUrl = fields[4].decodedNullable(),
            kind = CandidateKind.valueOf(fields[5]),
            isInstalled = false,
        )
    }
    require(items.distinctBy(CandidateCatalogItem::name).size == items.size) {
        "Candidate cache contains duplicate entries."
    }
    return CandidateMetadataCache(cachedAt, items.sortedBy(CandidateCatalogItem::name))
}

private fun String.encoded(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(StandardCharsets.UTF_8))

private fun String?.encodedNullable(): String = this?.encoded() ?: "-"

private fun String.decoded(): String =
    String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)

private fun String.decodedNullable(): String? = if (this == "-") null else decoded()

private fun defaultCandidateCachePath(): Path {
    val root = System.getenv("XDG_CACHE_HOME")
        ?.takeIf(String::isNotBlank)
        ?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".cache")
    return root.resolve("zephyr").resolve("candidate-catalog-v1.cache")
}
