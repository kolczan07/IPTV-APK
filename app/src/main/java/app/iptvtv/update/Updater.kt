package app.iptvtv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Zwraca informacje o nowszej wersji albo null, gdy nie ma nowszej. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "iptv-tv-updater")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null // brak wydań
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val json = JSONObject(resp.body!!.string())
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
        }
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "update.apk")
        val req = Request.Builder().url(info.apkUrl).header("User-Agent", "iptv-tv-updater").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: info.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
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
