package com.hdil.datacollection_researcher.credentials

import java.io.File

interface AppDirProvider {
    fun appDir(): File
    fun credentialsConfigFile(): File
}

class DefaultAppDirProvider : AppDirProvider {
    override fun appDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".datacollection_researcher")
    }

    override fun credentialsConfigFile(): File =
        File(appDir(), ".credentials.local.json")
}
