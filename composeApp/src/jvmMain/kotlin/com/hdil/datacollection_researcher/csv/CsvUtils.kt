package com.hdil.datacollection_researcher.csv

object CsvUtils {
    fun escape(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuoting) return value
        return buildString {
            append('"')
            append(value.replace("\"", "\"\""))
            append('"')
        }
    }
}
