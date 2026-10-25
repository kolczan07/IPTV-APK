package app.iptvtv.data

data class ParsedChannel(
    val tvgId: String?,
    val name: String,
    val logo: String?,
    val group: String?,
    val number: Int?,
    val url: String,
)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(lines: Sequence<String>): List<ParsedChannel> {
        val out = ArrayList<ParsedChannel>()
        var attrs: Map<String, String>? = null
        var name = ""
        for (raw in lines) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = line.substringAfterLast('"', line).substringAfter(',', "").trim()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    attrs = (attrs ?: emptyMap()) + ("group-title" to line.substringAfter(':').trim())
                }
                line.startsWith("#") -> Unit
                attrs != null -> {
                    val a = attrs
                    out += ParsedChannel(
                        tvgId = a["tvg-id"]?.ifBlank { null },
                        name = name.ifBlank { a["tvg-name"] ?: line },
                        logo = a["tvg-logo"]?.ifBlank { null },
                        group = a["group-title"]?.ifBlank { null },
                        number = (a["tvg-chno"] ?: a["channel-number"])?.toIntOrNull(),
                        url = line,
                    )
                    attrs = null
                }
            }
        }
        return out
    }
}
