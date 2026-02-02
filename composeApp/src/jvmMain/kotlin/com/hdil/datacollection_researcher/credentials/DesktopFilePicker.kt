package com.hdil.datacollection_researcher.credentials

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object DesktopFilePicker {
    fun pickJsonFile(parent: Frame? = null, initialDirectory: String? = null): File? {
        val dialog = FileDialog(parent, "Select credentials JSON", FileDialog.LOAD)
        if (!initialDirectory.isNullOrBlank()) {
            dialog.directory = initialDirectory
        }
        dialog.isVisible = true

        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null

        return File(dir, file)
    }
}
