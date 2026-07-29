package com.worxbend.zephyr

import com.worxbend.zephyr.domain.JavaVersion

internal enum class JavaVersionGrouping(val label: String) {
    None("None"),
    FeatureVersion("By Version"),
    Provider("By Provider"),
}

internal fun List<JavaVersion>.filterByQuery(query: String): List<JavaVersion> =
    if (query.isBlank()) {
        this
    } else {
        filter { version ->
            version.identifier.contains(query, ignoreCase = true) ||
                version.featureVersion.contains(query, ignoreCase = true) ||
                version.providerName.orEmpty().contains(query, ignoreCase = true)
        }
    }

internal fun List<JavaVersion>.groupBy(grouping: JavaVersionGrouping): Map<String, List<JavaVersion>> =
    when (grouping) {
        JavaVersionGrouping.None -> mapOf("" to this)
        JavaVersionGrouping.FeatureVersion -> groupBy { "JDK ${it.featureVersion}" }
        JavaVersionGrouping.Provider -> groupBy { it.providerName ?: "Other Providers" }
    }
