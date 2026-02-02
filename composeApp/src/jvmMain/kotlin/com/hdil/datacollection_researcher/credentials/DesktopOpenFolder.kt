package com.hdil.datacollection_researcher.credentials

import java.awt.Desktop
import java.io.File

object DesktopOpenFolder {
    fun openFolder(dir: File) {
        if (!dir.exists()) {
            throw IllegalArgumentException("폴더가 존재하지 않아요: ${dir.absolutePath}")
        }
        if (!dir.isDirectory) {
            throw IllegalArgumentException("폴더가 아니에요: ${dir.absolutePath}")
        }
        if (!Desktop.isDesktopSupported()) {
            throw IllegalStateException("이 환경에서는 폴더 열기를 지원하지 않아요.")
        }
        Desktop.getDesktop().open(dir)
    }
}
