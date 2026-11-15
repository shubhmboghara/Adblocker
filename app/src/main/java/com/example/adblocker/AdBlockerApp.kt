package com.example.adblocker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AdBlockerApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashSafeHandler()

        // Initialize repository and blocklist on background thread
        AdBlockRepository.getInstance(this)
        BlocklistUpdateWorker.schedulePeriodicRefresh(this)

        appScope.launch {
            BlocklistManager.loadBlocklist(this@AdBlockerApp)
            // Run the first download once on app start in a background coroutine
            BlocklistManager.downloadBlocklist(this@AdBlockerApp)
        }
    }

    private fun installCrashSafeHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = buildSanitizedStackTrace(thread, throwable)
                val crashFile = File(filesDir, Config.CRASH_REPORT_FILE)
                crashFile.writeText(report, Charsets.UTF_8)
            } catch (_: Exception) {
                // Ignore disk write errors during crash handling
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        /**
         * Builds a stack trace report containing only exception types and code stack frames,
         * omitting exception messages so no domain names or user strings can ever be written.
         */
        fun buildSanitizedStackTrace(thread: Thread, throwable: Throwable): String {
            val sb = StringBuilder()
            sb.appendLine("AdBlocker Local Crash Report")
            sb.appendLine("Thread: ${thread.name}")
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < 10) {
                val prefix = if (depth == 0) "Exception: " else "Caused by: "
                sb.appendLine("$prefix${current.javaClass.name}")
                for (element in current.stackTrace.take(40)) {
                    sb.appendLine("    at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
                }
                current = current.cause
                depth++
            }
            return sb.toString()
        }
    }
}
