package com.saman.tunnel

import java.net.URI

object ReleaseVersion {
    private val androidTag = Regex("^v\\d+\\.\\d+\\.\\d+(?:-rc\\.\\d+)?$")

    fun isAndroidReleaseTag(tag: String): Boolean = androidTag.matches(tag)

    fun isTrustedReleaseUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme == "https" &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.path.startsWith("/velnox4827/saman-aether/releases/")
    }.getOrDefault(false)

    fun compare(left: String, right: String): Int {
        val a = parse(left)
        val b = parse(right)
        for (index in 0..2) {
            val compared = a.numbers[index].compareTo(b.numbers[index])
            if (compared != 0) return compared
        }
        if (a.releaseCandidate == b.releaseCandidate) return 0
        if (a.releaseCandidate == null) return 1
        if (b.releaseCandidate == null) return -1
        return a.releaseCandidate.compareTo(b.releaseCandidate)
    }

    private fun parse(raw: String): ParsedVersion {
        val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-rc\\.(\\d+))?$")
            .matchEntire(raw.removePrefix("v"))
            ?: return ParsedVersion(listOf(0, 0, 0), 0)
        return ParsedVersion(
            numbers = (1..3).map { match.groupValues[it].toInt() },
            releaseCandidate = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
        )
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val releaseCandidate: Int?
    )
}
