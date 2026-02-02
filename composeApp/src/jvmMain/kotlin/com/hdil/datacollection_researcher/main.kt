package com.hdil.datacollection_researcher

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DataCollection_Researcher",
    ) {
        App()
    }
}