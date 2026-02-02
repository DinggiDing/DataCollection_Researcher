package com.hdil.datacollection_researcher.credentials

data class CredentialsLocalConfig(
    val credentialPath: String,
)

sealed class CredentialsStatus {
    data object NotSaved : CredentialsStatus()
    data class Saved(val credentialPath: String) : CredentialsStatus()
}
