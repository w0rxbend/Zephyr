package com.worxbend.zephyr.actions

const val ZEPHYR_ACTION_CONTRACT_VERSION = 1

object ZephyrActionIds {
    const val RefreshInstalled = "zephyr.read.refresh-installed"
    const val ScanLocalOnly = "zephyr.read.scan-local-only"
    const val RefreshConnectivity = "zephyr.read.refresh-connectivity"
    const val RefreshMetadata = "zephyr.review.refresh-metadata"
    const val CheckSdkmanUpdates = "zephyr.review.check-sdkman-updates"
}

data class ZephyrActionRequest(
    val id: String,
    val contractVersion: Int = ZEPHYR_ACTION_CONTRACT_VERSION,
    val parameters: Map<String, String> = emptyMap(),
)

data class ZephyrActionDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val requiresReview: Boolean,
    val allowedParameters: Set<String> = emptySet(),
)

val ZEPHYR_ACTIONS: List<ZephyrActionDescriptor> = listOf(
    ZephyrActionDescriptor(ZephyrActionIds.RefreshInstalled, "Refresh installed state", "Reload local SDKMAN candidates.", false),
    ZephyrActionDescriptor(ZephyrActionIds.ScanLocalOnly, "Scan local-only versions", "Compare local versions with SDKMAN metadata.", false),
    ZephyrActionDescriptor(ZephyrActionIds.RefreshConnectivity, "Check connectivity", "Refresh SDKMAN service reachability.", false),
    ZephyrActionDescriptor(ZephyrActionIds.RefreshMetadata, "Refresh metadata", "Open review for an SDKMAN metadata update.", true),
    ZephyrActionDescriptor(ZephyrActionIds.CheckSdkmanUpdates, "Check SDKMAN updates", "Open review for SDKMAN self-update.", true),
)

fun ZephyrActionRequest.validationError(): String? {
    if (contractVersion != ZEPHYR_ACTION_CONTRACT_VERSION) return "Unsupported action contract version."
    val descriptor = ZEPHYR_ACTIONS.firstOrNull { it.id == id } ?: return "Unknown action ID."
    if (!parameters.keys.all { it in descriptor.allowedParameters }) return "Action contains unsupported parameters."
    if (parameters.values.any { value -> value.length > 512 || value.any { it.code < 0x20 } }) {
        return "Action parameter contains invalid content."
    }
    return null
}

fun interface ZephyrActionHandler {
    fun handle(request: ZephyrActionRequest): Boolean
}
