package com.worxbend.zephyr.domain

enum class RecoveryAction(val label: String) {
    Retry("Review and retry"),
    RefreshInstalled("Refresh local state"),
    RefreshMetadata("Refresh metadata"),
    ScanLocalOnly("Scan again"),
    OpenDiagnostics("Open diagnostics"),
}

data class RecoveryGuidance(
    val title: String,
    val steps: List<String>,
    val actions: List<RecoveryAction>,
)

fun SdkmanTransaction.recoveryGuidance(): RecoveryGuidance =
    when (this) {
        is SdkmanTransaction.Install -> RecoveryGuidance(
            title = "The installation did not complete",
            steps = listOf(
                "Confirm the version is still available in refreshed SDKMAN metadata.",
                "Check Diagnostics if SDKMAN or network failures continue.",
                "Review the transaction again before retrying.",
            ),
            actions = listOf(
                RecoveryAction.RefreshMetadata,
                RecoveryAction.OpenDiagnostics,
                RecoveryAction.Retry,
            ),
        )
        is SdkmanTransaction.Uninstall -> RecoveryGuidance(
            title = "The installed version was not removed",
            steps = listOf(
                "Refresh local state to confirm the version still exists.",
                "Ensure another version is the persisted default.",
                "Review the uninstall transaction again before retrying.",
            ),
            actions = listOf(
                RecoveryAction.RefreshInstalled,
                RecoveryAction.OpenDiagnostics,
                RecoveryAction.Retry,
            ),
        )
        is SdkmanTransaction.SetDefault -> RecoveryGuidance(
            title = "The default version was not changed",
            steps = listOf(
                "Refresh local state and confirm the target version is installed.",
                "Inspect SDKMAN integrity checks if the current symlink is invalid.",
                "Review the default-change transaction again before retrying.",
            ),
            actions = listOf(
                RecoveryAction.RefreshInstalled,
                RecoveryAction.OpenDiagnostics,
                RecoveryAction.Retry,
            ),
        )
        is SdkmanTransaction.CleanLocalOnly -> RecoveryGuidance(
            title = "Cleanup was incomplete",
            steps = listOf(
                "Run a new local-only scan so remote availability is verified again.",
                "Choose another default if any selected version is protected.",
                "Review the remaining cleanup steps before retrying.",
            ),
            actions = listOf(
                RecoveryAction.ScanLocalOnly,
                RecoveryAction.OpenDiagnostics,
                RecoveryAction.Retry,
            ),
        )
        SdkmanTransaction.RefreshMetadata -> RecoveryGuidance(
            title = "Candidate metadata was not refreshed",
            steps = listOf(
                "Check the active network or proxy configuration.",
                "Inspect Diagnostics for the SDKMAN installation state.",
                "Retry the metadata transaction when connectivity is available.",
            ),
            actions = listOf(
                RecoveryAction.OpenDiagnostics,
                RecoveryAction.Retry,
            ),
        )
        SdkmanTransaction.SelfUpdate -> RecoveryGuidance(
            title = "SDKMAN was not updated",
            steps = listOf(
                "Check network access and write permissions for the SDKMAN installation.",
                "Inspect Diagnostics before changing the installation manually.",
                "Retry the self-update transaction after resolving the reported failure.",
            ),
            actions = listOf(
                RecoveryAction.OpenDiagnostics,
                RecoveryAction.Retry,
            ),
        )
    }
