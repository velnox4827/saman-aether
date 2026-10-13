package com.saman.tunnel

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/** Integrity checks for the official Psiphon executable bundled with Aether v2.3.0. */
internal object PsiphonBinaryPolicy {
    const val PACKAGED_NAME = "libpsiphon-tunnel-core.so"
    private const val ARM64_SHA256 = "7cab04ebf82ceed76ae53b99c306db26233b80825b930e2e7288f07a4087387a"
    private const val ARMV7_SHA256 = "719bf754d197ecb2b320e6251ace79109dc1972c6675206950bf8cb5a55f03ca"

    fun expectedSha256(abi: String): String? = when (abi) {
        "arm64-v8a" -> ARM64_SHA256
        "armeabi-v7a" -> ARMV7_SHA256
        else -> null
    }

    fun isValid(bytes: ByteArray, abi: String): Boolean =
        expectedSha256(abi) == sha256(bytes) && hasElfAbi(bytes, abi)

    fun isValid(file: File, abi: String): Boolean =
        file.isFile && file.canExecute() && expectedSha256(abi) == sha256(file) && hasElfAbi(file, abi)

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun hasElfAbi(file: File, abi: String): Boolean = FileInputStream(file).use { input ->
        val header = ByteArray(20)
        input.read(header) == header.size && hasElfAbi(header, abi)
    }

    internal fun hasElfAbi(bytes: ByteArray, abi: String): Boolean {
        if (bytes.size < 20 || bytes[0] != 0x7f.toByte() ||
            bytes[1] != 'E'.code.toByte() || bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte() ||
            bytes[5] != 1.toByte()) return false
        val elfClass = bytes[4].toInt() and 0xff
        val machine = (bytes[18].toInt() and 0xff) or ((bytes[19].toInt() and 0xff) shl 8)
        return when (abi) {
            "arm64-v8a" -> elfClass == 2 && machine == 183
            "armeabi-v7a" -> elfClass == 1 && machine == 40
            else -> false
        }
    }
}
