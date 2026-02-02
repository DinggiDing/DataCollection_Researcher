package com.hdil.datacollection_researcher.export

import com.hdil.datacollection_researcher.csv.CsvUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvEscapeTest {

    @Test
    fun `escapeCsv - no quoting when simple`() {
        assertEquals("abc", CsvUtils.escape("abc"))
    }

    @Test
    fun `escapeCsv - quotes when comma`() {
        assertEquals("\"a,b\"", CsvUtils.escape("a,b"))
    }

    @Test
    fun `escapeCsv - escapes quote`() {
        assertEquals("\"a\"\"b\"", CsvUtils.escape("a\"b"))
    }
}
