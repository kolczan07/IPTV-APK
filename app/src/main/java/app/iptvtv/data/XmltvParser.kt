package app.iptvtv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Strumieniowy parser XMLTV: zachowuje tylko programy z okna [from, to]. */
object XmltvParser {
    private val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ROOT)
    private val noZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun time(raw: String?): Long? {
        val t = raw?.trim() ?: return null
        if (t.length < 14) return null
        return try {
            val base = t.take(14)
            val zone = t.drop(14).trim()
            (if (zone.isEmpty()) noZone.parse(base) else withZone.parse("$base $zone"))?.time
        } catch (e: Exception) {
            null
        }
    }

    fun parse(input: InputStream, from: Long, to: Long): List<ProgrammeEntity> {
        val out = ArrayList<ProgrammeEntity>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "programme") {
                val channel = p.getAttributeValue(null, "channel")?.trim()?.lowercase()
                val start = time(p.getAttributeValue(null, "start"))
                val stop = time(p.getAttributeValue(null, "stop"))
                var title: String? = null
                var desc: String? = null
                while (true) {
                    val inner = p.next()
                    if (inner == XmlPullParser.END_DOCUMENT) break
                    if (inner == XmlPullParser.END_TAG && p.name == "programme") break
                    if (inner == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "title" -> { val s = p.nextText(); if (title == null) title = s }
                            "desc" -> { val s = p.nextText(); if (desc == null) desc = s }
                        }
                    }
                }
                if (channel != null && start != null && stop != null && !title.isNullOrBlank() &&
                    stop > from && start < to
                ) {
                    out += ProgrammeEntity(
                        channelKey = channel, start = start, stop = stop,
                        title = title.trim(), description = desc?.trim()?.ifBlank { null },
                    )
                }
            }
            ev = p.next()
        }
        return out
    }
}
