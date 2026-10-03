package com.habitminer.data

/**
 * Minimal RFC-4180 CSV reader for HabitMiner's own export files: handles quoted fields
 * containing commas, doubled quotes and newlines. Pure Kotlin so it can be unit tested.
 */
object CsvParser {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field.clear()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString())
                        field.clear()
                        rows.add(row.toList())
                        row.clear()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row.toList())
        }
        return rows.filter { r -> r.any { it.isNotEmpty() } }
    }

    /** Rows as maps keyed by the header row, so columns can be added or reordered between versions. */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        return rows.drop(1).map { r -> header.indices.associate { header[it] to (r.getOrNull(it) ?: "") } }
    }
}
