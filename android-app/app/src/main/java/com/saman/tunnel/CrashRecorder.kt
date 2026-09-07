package com.saman.tunnel

import android.content.Context
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashRecorder {
    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val writer = StringWriter()
                throwable.printStackTrace(PrintWriter(writer))

                LogStore.append(
                    appContext,
                    "FATAL_JAVA",
                    "pid=${Process.myPid()} thread=${thread.name} " +
                        "${throwable.javaClass.name}: ${throwable.message.orEmpty()}\n" +
                        writer.toString().take(16000)
                )
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
