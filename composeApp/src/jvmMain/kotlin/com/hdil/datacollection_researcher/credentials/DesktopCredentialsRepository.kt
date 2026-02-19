package com.hdil.datacollection_researcher.credentials

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.prefs.Preferences

class DesktopCredentialsRepository(
    private val appDirProvider: AppDirProvider = DefaultAppDirProvider(),
) : CredentialsRepository {

    private val prefs: Preferences = Preferences
        .userRoot()
        .node("com/hdil/datacollection_researcher")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    override fun getLastSelectedPathOrNull(): String? =
        prefs.get(PREF_LAST_SELECTED_CREDENTIAL_PATH, null)

    override suspend fun loadStatus(): CredentialsStatus = withContext(Dispatchers.IO) {
        val configFile = appDirProvider.credentialsConfigFile()
        if (!configFile.exists()) return@withContext CredentialsStatus.NotSaved

        val content = runCatching { configFile.readText(Charsets.UTF_8) }.getOrNull()
            ?: return@withContext CredentialsStatus.NotSaved

        val path = runCatching {
            json.decodeFromString(CredentialsLocalConfig.serializer(), content).credentialPath
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return@withContext CredentialsStatus.NotSaved

        CredentialsStatus.Saved(path)
    }

    override suspend fun saveFromSelectedFile(selectedFile: File): CredentialsStatus = withContext(Dispatchers.IO) {
        if (!selectedFile.exists()) {
            throw IllegalArgumentException("선택한 파일을 찾을 수 없어요. 파일이 이동/삭제되지 않았는지 확인해 주세요.")
        }
        if (!selectedFile.isFile) {
            throw IllegalArgumentException("폴더가 아닌 JSON 파일을 선택해 주세요.")
        }

        val raw = runCatching { selectedFile.readText(Charsets.UTF_8) }
            .getOrElse {
                throw IllegalStateException(
                    "선택한 파일을 읽을 수 없어요. 권한을 확인해 주세요.",
                    it,
                )
            }

        // Phase 1: very light validation
        val trimmed = raw.trimStart()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            throw IllegalArgumentException("선택한 파일이 JSON 형식이 아닌 것 같아요. 서비스 계정 키(JSON)를 선택해 주세요.")
        }

        val appDir = appDirProvider.appDir().apply { mkdirs() }
        if (!appDir.exists() || !appDir.isDirectory) {
            throw IllegalStateException("설정 저장 폴더를 만들 수 없어요: ${appDir.absolutePath}")
        }

        val configFile = appDirProvider.credentialsConfigFile()

        // Atomic-ish write: temp then move
        val tmp = File(configFile.parentFile, configFile.name + ".tmp")
        val configJson = json.encodeToString(CredentialsLocalConfig.serializer(), CredentialsLocalConfig(credentialPath = selectedFile.absolutePath))
        tmp.writeText(configJson, Charsets.UTF_8)

        try {
            Files.move(
                tmp.toPath(),
                configFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Some Windows filesystems (or synced/remote dirs) don't support atomic move.
            Files.move(
                tmp.toPath(),
                configFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        prefs.put(PREF_LAST_SELECTED_CREDENTIAL_PATH, selectedFile.absolutePath)

        CredentialsStatus.Saved(selectedFile.absolutePath)
    }

    @Serializable
    private data class CredentialsLocalConfig(
        val credentialPath: String,
    )

    companion object {
        private const val PREF_LAST_SELECTED_CREDENTIAL_PATH = "lastSelectedCredentialsPath"
    }
}
