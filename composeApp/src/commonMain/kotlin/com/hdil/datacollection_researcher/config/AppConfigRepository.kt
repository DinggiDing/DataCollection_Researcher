package com.hdil.datacollection_researcher.config

interface AppConfigRepository {
    suspend fun loadOrDefault(): AppConfig
    suspend fun save(config: AppConfig)
}
