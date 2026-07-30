package com.worxbend.zephyr

internal enum class ShellLayout {
    Narrow,
    Medium,
    Wide,
}

internal fun shellLayoutForWidth(widthDp: Float): ShellLayout =
    when {
        widthDp < 900f -> ShellLayout.Narrow
        widthDp < 1180f -> ShellLayout.Medium
        else -> ShellLayout.Wide
    }

internal val ShellLayout.hasPersistentNavigation: Boolean
    get() = this != ShellLayout.Narrow

internal val ShellLayout.showsFullToolbar: Boolean
    get() = this == ShellLayout.Wide
