package com.saman.tunnel

import java.io.File

/** Owns only the Xray child; the Android service still owns VPN and notifications. */
class XrayRunner(private val binary: File, private val directory: File, private val guard: File? = null) {
    private var child: Process? = null
    private val configFile get() = File(directory, "xray-active.json")

    @Synchronized fun start(config: String) {
        check(child == null) { "Xray قبلاً اجرا شده است" }
        check(binary.isFile && binary.canExecute()) { "ماژول Xray در این نسخه موجود یا قابل اجرا نیست" }
        check(guard == null || (guard.isFile && guard.canExecute())) { "محافظ چرخهٔ اجرای Xray موجود نیست" }
        check(directory.isDirectory || directory.mkdirs()) { "ذخیرهٔ تنظیمات اتصال ممکن نیست" }
        try {
            configFile.outputStream().use { stream ->
                stream.write(config.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(configFile.setReadable(false, false) && configFile.setReadable(true, true) &&
                configFile.setWritable(false, false) && configFile.setWritable(true, true)) {
                "خصوصی‌سازی فایل اتصال ممکن نیست"
            }
            val validation = command("run", "-test", "-config", configFile.absolutePath).start()
            try {
                check(awaitExit(validation, 15_000) && validation.exitValue() == 0) {
                    "Xray کانفیگ را نپذیرفت؛ نوع پروتکل، انتقال و تنظیمات امنیتی را بررسی کنید"
                }
            } finally {
                validation.destroy()
                awaitExit(validation, 2_000)
            }
            child = command("run", "-config", configFile.absolutePath).start()
        } catch (e: Exception) {
            configFile.delete()
            if (e is IllegalStateException) throw e
            throw IllegalStateException("اجرای ماژول Xray ممکن نشد")
        }
    }

    @Synchronized fun isRunning(): Boolean = child?.let(::alive) ?: false

    @Synchronized fun stop(): Boolean {
        val process = child
        if (process != null) {
            process.destroy()
            if (!awaitExit(process, 4_000)) return false
            child = null
        }
        configFile.delete()
        return true
    }

    private fun command(vararg args: String) = ProcessBuilder(listOfNotNull(guard?.absolutePath, binary.absolutePath) + args)
        .directory(directory)
        // Never persist raw core diagnostics: failed URLs/credentials may be echoed by upstream.
        .redirectErrorStream(true)
        .redirectOutput(File("/dev/null"))

    private fun alive(process: Process): Boolean = try {
        process.exitValue()
        false
    } catch (_: IllegalThreadStateException) { true }

    private fun awaitExit(process: Process, timeoutMillis: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (alive(process)) {
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(25)
        }
        return true
    }
}
