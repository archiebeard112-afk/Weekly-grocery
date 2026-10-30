package com.weeklymeals

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** A published build on GitHub. */
data class AppRelease(val versionCode: Int, val name: String, val apkUrl: String)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val release: AppRelease) : UpdateState()
    data class Downloading(val percent: Int) : UpdateState()
    data class Ready(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/** Checks the GitHub repo's latest Release and installs it. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Archiebeard112-afk/Weekly-grocery/releases/latest"
    private val http = OkHttpClient()

    fun currentVersionCode(ctx: Context): Int =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()

    fun currentVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** The newest Release, or null if none has been published yet. */
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(LATEST)
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw Exception("GitHub said ${resp.code}")
            val j = JSONObject(resp.body?.string().orEmpty())
            val code = j.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
            val assets = j.optJSONArray("assets") ?: return@withContext null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
            AppRelease(code, j.optString("name", "Version 1.$code"), apk.getString("browser_download_url"))
        }
    }

    /** Downloads the APK into the app's cache, reporting progress 0-100. */
    suspend fun download(ctx: Context, r: AppRelease, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "WeeklyMeals-${r.versionCode}.apk")
        http.newCall(Request.Builder().url(r.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download failed (${resp.code})")
            val body = resp.body ?: throw Exception("Download was empty")
            val total = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                    }
                }
            }
        }
        out
    }

    /** True if Android lets this app open the installer (asked once per phone). */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for Thyme Table. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the downloaded APK to Android's installer. */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
