package com.worxbend.zephyr

internal fun candidateCacheAgeLabel(
    cachedAtEpochMillis: Long,
    nowEpochMillis: Long,
): String {
    val ageMillis = (nowEpochMillis - cachedAtEpochMillis).coerceAtLeast(0)
    val minutes = ageMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1 -> "just now"
        hours < 1 -> "$minutes min old"
        days < 1 -> "$hours hr old"
        else -> "$days day${if (days == 1L) "" else "s"} old"
    }
}
