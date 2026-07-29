package com.worxbend.zephyr.domain

enum class BatchItemStatus(val label: String) {
    Pending("Pending"),
    Running("Running"),
    Succeeded("Succeeded"),
    Failed("Failed"),
}

data class BatchInstallProgress(
    val target: InstallTarget,
    val status: BatchItemStatus = BatchItemStatus.Pending,
    val outcome: String? = null,
)
