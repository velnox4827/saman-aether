package com.saman.tunnel

import java.io.File
import java.util.concurrent.Executors

object SubscriptionLockCheck {
    @JvmStatic fun main(args: Array<String>) {
        if (args.isNotEmpty()) {
            val data = File(args[0]); val lock = File(args[0] + ".lock")
            repeat(100) { StoreFileLock.withLock(lock) { val n = data.readText().toInt(); Thread.yield(); data.writeText((n + 1).toString()) } }
            return
        }
        val dir = java.nio.file.Files.createTempDirectory("subscription-lock").toFile()
        try {
            val data = File(dir, "counter").apply { writeText("0") }
            val java = File(System.getProperty("java.home"), "bin/java").path
            val processes = (1..3).map { ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), SubscriptionLockCheck::class.java.name, data.path).inheritIO().start() }
            processes.forEach { check(it.waitFor() == 0) }
            check(data.readText() == "300")
            val pool = Executors.newFixedThreadPool(4)
            try {
                val tasks = (1..4).map { pool.submit { repeat(25) { StoreFileLock.withLock(File(data.path + ".lock")) { data.writeText((data.readText().toInt() + 1).toString()) } } } }
                tasks.forEach { it.get() }
            } finally { pool.shutdownNow() }
            check(data.readText() == "400")
            check(runCatching { StoreFileLock.withLock(File(data.path + ".lock")) { error("abort") } }.isFailure)
            StoreFileLock.withLock(File(data.path + ".lock")) { check(data.readText() == "400") }
            println("Cross-process/thread store lock checks passed")
        } finally { dir.deleteRecursively() }
    }
}
