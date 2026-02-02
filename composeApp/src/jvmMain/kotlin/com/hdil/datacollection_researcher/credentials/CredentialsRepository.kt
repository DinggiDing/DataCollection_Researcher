package com.hdil.datacollection_researcher.credentials

import java.io.File

interface CredentialsRepository {
    /** Returns saved status based on `.credentials.local.json` existence. */
    suspend fun loadStatus(): CredentialsStatus

    /** Saves `.credentials.local.json` using the given credential JSON file. */
    suspend fun saveFromSelectedFile(selectedFile: File): CredentialsStatus

    fun getLastSelectedPathOrNull(): String?
}
