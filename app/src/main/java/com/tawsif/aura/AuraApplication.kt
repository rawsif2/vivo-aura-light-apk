package com.tawsif.aura

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val output = StringWriter()
                error.printStackTrace(PrintWriter(output))
                File(filesDir, "crash-report.txt").writeText(
                    "Thread: ${thread.name}\nTime: ${System.currentTimeMillis()}\n\n$output"
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
