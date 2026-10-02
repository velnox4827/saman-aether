package com.saman.tunnel

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

/** Integrity checks for the official Psiphon executable bundled with Aether v2.1.0. */
internal object PsiphonBinaryPolicy {
    const val PACKAGED_NAME = "libpsiphon-tunnel-core.so"
    private const val ARM64_SHA256 = "7cab04ebf82ceed76ae53b99c306db26233b80825b930e2e7288f07a4087387a"
    private const val ARMV7_SHA256 = "719bf754d197ecb2b320e6251ace79109dc1972c6675206950bf8cb5a55f03ca"

    fun assetPath(abi: String): String? = when (abi) {
        "arm64-v8a" -> "psiphon/arm64-v8a/psiphon-tunnel-core"
        "armeabi-v7a" -> "psiphon/armeabi-v7a/psiphon-tunnel-core"
        else -> null
    }

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

internal fun psiphonAssetPath(abi: String): String? = PsiphonBinaryPolicy.assetPath(abi)
internal fun psiphonExpectedSha256(abi: String): String? = PsiphonBinaryPolicy.expectedSha256(abi)
internal fun psiphonIsValid(bytes: ByteArray, abi: String): Boolean = PsiphonBinaryPolicy.isValid(bytes, abi)
internal fun psiphonElfMatches(bytes: ByteArray, abi: String): Boolean = PsiphonBinaryPolicy.hasElfAbi(bytes, abi)
internal fun psiphonSha256(bytes: ByteArray): String = PsiphonBinaryPolicy.sha256(bytes)
internal fun psiphonBinaryName(): String = PsiphonBinaryPolicy.PACKAGED_NAME
internal fun psiphonInstalledPath(nativeLibraryDir: String): File = File(nativeLibraryDir, PsiphonBinaryPolicy.PACKAGED_NAME)
internal fun psiphonValidateInstalled(file: File, abi: String): Boolean = PsiphonBinaryPolicy.isValid(file, abi)
internal fun psiphonAetherCommit(): String = "6398931aeaa551248530cc164aea6d5f2c5fe4a2"
internal fun psiphonClientCommit(): String = "83aa73b9b982e7421e00117f5b0c5aceb5dda452"
internal fun psiphonLicenseUrl(): String = "https://github.com/CluvexStudio/psiphon-tunnel-core/blob/83aa73b9b982e7421e00117f5b0c5aceb5dda452/LICENSE"
internal fun psiphonArchiveSha256(abi: String): String? = when (abi) {
    "arm64-v8a" -> "797cda06f5d651603b3b0c0cb8de7a5832d69e62241b3396b289d8bb0fca5374"
    "armeabi-v7a" -> "2816df2a7cd7db675e73111e1131ec469afe252a35e4d3ac9bd226d479b5abf2"
    else -> null
}
internal fun psiphonRequiresBinary(mode: String): Boolean = AetherArguments.needsPsiphonBinary(mode)
internal fun psiphonExpectedAbi(is64Bit: Boolean): String = if (is64Bit) "arm64-v8a" else "armeabi-v7a"
internal fun psiphonOnlyArgs(binaryPath: String): List<String> = listOf(
    "--psiphon-only", "--bind", "127.0.0.1:1819", "--psiphon-http", "127.0.0.1:1820",
    "--psiphon-bin", binaryPath
)
internal fun psiphonVersionName(): String = "2.0.0"
internal fun psiphonVersionCode(): Int = 20000
internal fun psiphonReleaseTag(): String = "v2.0.0"
internal fun psiphonPriorReleaseTag(): String = "v1.10.0"
internal fun goolRemainsSelectable(): Boolean = true
internal fun psiphonRuntimeDeviceTestPerformed(): Boolean = false
internal fun psiphonLicenseName(): String = "GPL-3.0-or-later"
