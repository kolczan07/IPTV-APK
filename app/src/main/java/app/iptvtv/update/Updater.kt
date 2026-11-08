package app.iptvtv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Aktualizacje z GitHub Releases: sprawdzenie, pobranie APK i uruchomienie instalatora systemowego. */
class Updater(
    private val ctx: Context,
    private val repo: String,
    private val currentVersion: String,
) {
    // Jak w MD-Tools: systemowy HttpURLConnection (stos sieciowy platformy, ten sam co w przeglądarkowych
    // pobraniach), bez własnych ustawień protokołu — u nas OkHttp dostawał 504 tam, gdzie MD-Tools działa.
    private fun open(url: String, connectMs: Int, readMs: Int, userAgent: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            if (userAgent != null) setRequestProperty("User-Agent", userAgent)
        }

    /** Zwraca informacje o nowszej wersji albo null, gdy nie ma nowszej. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = open("https://api.github.com/repos/$repo/releases/latest", 10_000, 15_000, "IPTV-TV/$currentVersion")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode == 404) return@withContext null // brak wydań
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            if (!isNewer(tag, currentVersion)) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return@withContext UpdateInfo(
                        version = tag.removePrefix("v"),
                        notes = json.optString("body"),
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size"),
                    )
                }
            }
            null
        } finally {
            c.disconnect()
        }
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                return downloadOnce(info, onProgress)
            } catch (e: IOException) {
                last = e
                kotlinx.coroutines.delay(1500L * (attempt + 1))
            }
        }
        throw last ?: IOException("nieznany błąd")
    }

    private suspend fun downloadOnce(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "update.apk")
        val c = open(info.apkUrl, 15_000, 30_000)   // bez własnego User-Agenta, jak w MD-Tools
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
            c.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        file
    }

    /** Uruchamia instalator. Zwraca false, gdy trzeba najpierw zezwolić na instalację z tej aplikacji. */
    fun install(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}

internal fun isNewer(remote: String, local: String): Boolean {
    fun parts(s: String) = s.trim().trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
