package com.worxbend.zephyr.domain

enum class ConnectivityState(val label: String) {
    Unknown("Unknown"),
    Checking("Checking"),
    Online("Online"),
    Offline("Offline"),
}

data class ConnectivityStatus(
    val state: ConnectivityState,
    val checkedAtEpochMillis: Long? = null,
    val detail: String? = null,
)

val SdkmanTransaction.requiresNetwork: Boolean
    get() = when (this) {
        is SdkmanTransaction.Install,
        is SdkmanTransaction.BatchInstall,
        is SdkmanTransaction.CleanLocalOnly,
        SdkmanTransaction.RefreshMetadata,
        SdkmanTransaction.SelfUpdate,
        -> true
        is SdkmanTransaction.Uninstall,
        is SdkmanTransaction.BatchUninstall,
        is SdkmanTransaction.SetDefault,
        -> false
    }
