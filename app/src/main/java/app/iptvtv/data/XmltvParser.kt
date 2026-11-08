package app.iptvtv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.TimeZone

/** Strumieniowy parser XMLTV: zachowuje tylko programy z okna [from, to]. */
object XmltvParser {
    /** Ręczny parser "yyyyMMddHHmmss[ +hhmm]" — dużo szybszy niż SimpleDateFormat przy dziesiątkach tysięcy wpisów. */
    private fun time(raw: String?, localZone: TimeZone?): Long? {
        val t = raw?.trim() ?: return null
        if (t.length < 14) return null
        fun d(i: Int, n: Int): Int {
            var v = 0
            for (k in i until i + n) {
                val c = t[k]
                if (c !in '0'..'9') return -1
                v = v * 10 + (c - '0')
            }
            return v
        }
        val y = d(0, 4); val mo = d(4, 2); val da = d(6, 2)
        val h = d(8, 2); val mi = d(10, 2); val s = d(12, 2)
        if (y < 0 || mo !in 1..12 || da !in 1..31 || h < 0 || mi < 0 || s < 0) return null
        var offset = 0L
        val z = t.substring(14).trim()
        if (z.length >= 5 && (z[0] == '+' || z[0] == '-')) {
            val zh = z.substring(1, 3).toIntOrNull()
            val zm = z.substring(3, 5).toIntOrNull()
            if (zh != null && zm != null) offset = (zh * 3600L + zm * 60L) * (if (z[0] == '-') -1 else 1)
        }
        val naive = (daysFromCivil(y, mo, da) * 86400L + h * 3600L + mi * 60L + s) * 1000L
        if (localZone != null) {
            // Zignoruj przesunięcie z pliku: cyfry to czas ścienny w strefie urządzenia (uwzględnia zmianę czasu).
            return naive - localZone.getOffset(naive - localZone.rawOffset)
        }
        return naive - offset * 1000L
    }

    private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
        val y = if (m <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era.toLong() * 146097L + doe - 719468L
    }

    fun parse(input: InputStream, from: Long, to: Long, localZone: TimeZone? = null): List<ProgrammeEntity> {
        val out = ArrayList<ProgrammeEntity>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "programme") {
                val channel = p.getAttributeValue(null, "channel")?.trim()?.lowercase()
                val start = time(p.getAttributeValue(null, "start"), localZone)
                val stop = time(p.getAttributeValue(null, "stop"), localZone)
                val inWindow = channel != null && start != null && stop != null && stop > from && start < to
                if (!inWindow) {
                    // poza oknem czasowym: przeskocz zawartość bez budowania stringów
                    var depth = 1
                    while (depth > 0) {
                        val e = p.next()
                        if (e == XmlPullParser.END_DOCUMENT) break
                        if (e == XmlPullParser.START_TAG) depth++ else if (e == XmlPullParser.END_TAG) depth--
                    }
                    ev = p.next()
                    continue
                }
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
