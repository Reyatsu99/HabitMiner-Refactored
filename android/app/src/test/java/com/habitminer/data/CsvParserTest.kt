package com.habitminer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {
    @Test
    fun `reads quoted fields with commas, quotes and newlines`() {
        val text = "id,name,note\n1,\"Snap, Chat\",\"say \"\"hi\"\"\"\n2,Plain,\"two\nlines\"\n"
        val rows = CsvParser.parseWithHeader(text)
        assertEquals(2, rows.size)
        assertEquals("Snap, Chat", rows[0]["name"])
        assertEquals("say \"hi\"", rows[0]["note"])
        assertEquals("two\nlines", rows[1]["note"])
    }

    @Test
    fun `missing trailing columns read as empty and blank lines are skipped`() {
        val rows = CsvParser.parseWithHeader("a,b,c\r\n1,2\r\n\r\n3,4,5")
        assertEquals(2, rows.size)
        assertEquals("", rows[0]["c"])
        assertEquals("5", rows[1]["c"])
    }

    @Test
    fun `round-trips what ExportManager writes`() {
        // ExportManager quotes values containing commas, quotes or newlines and doubles quotes.
        val contextJson = "{\"lastApp\":\"com.whatsapp\",\"lightLux\":12.0}"
        val escaped = "\"" + contextJson.replace("\"", "\"\"") + "\""
        val rows = CsvParser.parseWithHeader("id,contextJson\n7,$escaped\n")
        assertEquals(contextJson, rows.single()["contextJson"])
    }
}
