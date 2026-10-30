package com.weeklymeals

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the details when the app crashes, so the next time it opens it can show them
 * (with a Copy button) and the bug can be found and fixed.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    "Thyme Table ${Updater.currentVersionName(app)} · Android ${android.os.Build.VERSION.RELEASE}\n" +
                        "${java.util.Date()}\n\n" + sw.toString().take(12_000),
                )
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The last crash's details, if it hasn't been shown yet. */
    fun pending(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()

    fun clear(ctx: Context) { file(ctx).delete() }
}
