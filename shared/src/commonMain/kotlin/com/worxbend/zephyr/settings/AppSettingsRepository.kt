package com.worxbend.zephyr.settings

interface AppSettingsRepository {
    suspend fun load(): AppSettings
    suspend fun save(settings: AppSettings)
}

expect fun createAppSettingsRepository(): AppSettingsRepository
