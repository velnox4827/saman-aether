package com.saman.tunnel

import java.io.File
import java.nio.file.Files

fun main() = runXrayRunnerCheck()

fun runXrayRunnerCheck() {
    val dir = Files.createTempDirectory("xray-runner-check").toFile()
    try {
        val shell = if (File("/system/bin/sh").isFile) "/system/bin/sh" else "/bin/sh"
        val binary = File(dir, "core").apply {
            writeText("#!$shell\ncase \"\$*\" in *-test*) exit 0;; esac\nexec sleep 30\n")
            setExecutable(true)
        }
        val runner = XrayRunner(binary, dir, System.getenv("XRAY_GUARD")?.let(::File))
        runner.start("{}")
        check(runner.isRunning()) { "Started process must remain alive" }
        check(File(dir, "xray-active.json").exists())
        check(runCatching { runner.start("{}") }.isFailure) { "Duplicate start must fail" }
        check(runner.stop()) { "Stop must reap process" }
        check(!runner.isRunning())
        check(!File(dir, "xray-active.json").exists()) { "Stop removes active credential file" }
        check(runner.stop()) { "Stop must be idempotent" }
        binary.writeText("#!$shell\nexit 1\n")
        check(runCatching { runner.start("secret config") }.isFailure)
        check(!File(dir, "xray-active.json").exists()) { "Validation failure removes credential file" }
        check(runCatching { XrayRunner(File(dir, "missing"), dir).start("{}") }.isFailure)
        println("PASS: Xray process start, duplicate guard, stop/reap, validation failure, private config cleanup")
    } finally { dir.deleteRecursively() }
}
