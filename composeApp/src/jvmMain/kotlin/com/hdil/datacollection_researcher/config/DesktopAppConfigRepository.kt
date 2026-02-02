package com.hdil.datacollection_researcher.config

import com.hdil.datacollection_researcher.credentials.AppDirProvider
import com.hdil.datacollection_researcher.credentials.DefaultAppDirProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DesktopAppConfigRepository(
    private val appDirProvider: AppDirProvider = DefaultAppDirProvider(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    },
) : AppConfigRepository {

    private fun configFile(): File = File(appDirProvider.appDir(), "app_config.json")

    override suspend fun loadOrDefault(): AppConfig = withContext(Dispatchers.IO) {
        val file = configFile()
        if (!file.exists()) return@withContext AppConfig()

        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            ?: return@withContext AppConfig()

        runCatching { json.decodeFromString<AppConfig>(raw) }
            .getOrElse { AppConfig() }
    }

    override suspend fun save(config: AppConfig): Unit = withContext(Dispatchers.IO) {
        val dir = appDirProvider.appDir().apply { mkdirs() }
        if (!dir.exists() || !dir.isDirectory) {
            throw IllegalStateException("설정 저장 폴더를 만들 수 없어요: ${dir.absolutePath}")
        }

        val file = configFile()
        val tmp = File(file.parentFile, file.name + ".tmp")
        val encoded = json.encodeToString(config)
        tmp.writeText(encoded, Charsets.UTF_8)
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
