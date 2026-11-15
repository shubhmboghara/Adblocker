package com.example.adblocker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

sealed interface DomainLookupTable {
    val size: Int
    fun contains(domain: String): Boolean
}

class HashSetDomainTable(private val set: HashSet<String>) : DomainLookupTable {
    override val size: Int get() = set.size
    override fun contains(domain: String): Boolean = set.contains(domain)
}

class CompactSortedDomainTable(private val sortedDomains: Array<String>) : DomainLookupTable {
    override val size: Int get() = sortedDomains.size
    override fun contains(domain: String): Boolean = sortedDomains.binarySearch(domain) >= 0
}

object BlocklistManager {
    private val IGNORED_HOSTS = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "ip6-localhost",
        "ip6-loopback",
        "ip6-localnet",
        "ip6-mcastprefix",
        "ip6-allnodes",
        "ip6-allrouters",
        "ip6-allhosts",
        "0.0.0.0",
        "127.0.0.1",
        "::1",
        "fe80::1%lo0",
        "ff00::0",
        "ff02::1",
        "ff02::2",
        "ff02::3"
    )

    @Volatile
    private var lookupTable: DomainLookupTable = HashSetDomainTable(HashSet())

    val currentSize: Int
        get() = lookupTable.size

    fun containsExact(domain: String): Boolean {
        return lookupTable.contains(DomainMatcher.normalizeDomain(domain))
    }

    fun isBlocked(domain: String): Boolean {
        val snapshot = lookupTable
        return DomainMatcher.isDomainOrParentInSet(domain) { candidate ->
            snapshot.contains(candidate)
        }
    }

    fun setTestBlocklist(domains: Collection<String>, compactThreshold: Int = Config.COMPACT_BLOCKLIST_THRESHOLD) {
        val set = HashSet<String>(domains.size)
        for (d in domains) {
            val clean = DomainMatcher.normalizeDomain(d)
            if (clean.isNotEmpty() && !IGNORED_HOSTS.contains(clean)) {
                set.add(clean)
            }
        }
        val table = buildLookupTable(set, compactThreshold)
        lookupTable = table
        VpnStats.setBlocklistCount(table.size)
    }

    /**
     * Parses a single line in hosts format (e.g. "0.0.0.0 ads.example.com" or "127.0.0.1 tracker.net")
     * or plain domain list format. Returns null for comments, blank lines, localhost, and 0.0.0.0.
     */
    fun parseHostsLine(rawLine: String): String? {
        val commentIdx = rawLine.indexOf('#')
        val line = (if (commentIdx >= 0) rawLine.substring(0, commentIdx) else rawLine).trim()
        if (line.isEmpty()) return null

        var tokenStart = 0
        val len = line.length
        while (tokenStart < len && line[tokenStart].isWhitespace()) tokenStart++
        var tokenEnd = tokenStart
        while (tokenEnd < len && !line[tokenEnd].isWhitespace()) tokenEnd++

        val firstToken = line.substring(tokenStart, tokenEnd)
        var secondStart = tokenEnd
        while (secondStart < len && line[secondStart].isWhitespace()) secondStart++

        val rawDomain = if (secondStart < len) {
            var secondEnd = secondStart
            while (secondEnd < len && !line[secondEnd].isWhitespace()) secondEnd++
            line.substring(secondStart, secondEnd)
        } else {
            firstToken
        }

        val domain = rawDomain.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (domain.isEmpty() || IGNORED_HOSTS.contains(domain) || !domain.contains('.')) {
            return null
        }
        return domain
    }

    /**
     * Reads a hosts stream line by line using BufferedReader (never readText()) to avoid OutOfMemoryError.
     */
    fun parseHostsStream(
        reader: BufferedReader,
        compactThreshold: Int = Config.COMPACT_BLOCKLIST_THRESHOLD
    ): DomainLookupTable {
        val tempSet = HashSet<String>()
        reader.forEachLine { line ->
            val domain = parseHostsLine(line)
            if (domain != null) {
                tempSet.add(domain)
            }
        }
        return buildLookupTable(tempSet, compactThreshold)
    }

    fun buildLookupTable(
        domains: HashSet<String>,
        compactThreshold: Int = Config.COMPACT_BLOCKLIST_THRESHOLD
    ): DomainLookupTable {
        return if (domains.size > compactThreshold) {
            val array = domains.toTypedArray()
            array.sort()
            domains.clear()
            CompactSortedDomainTable(array)
        } else {
            HashSetDomainTable(domains)
        }
    }

    /**
     * Loads the blocklist from filesDir/blocklist.txt if it exists, otherwise falls back to assets/blocklist.txt.
     * Swaps the lookup table atomically.
     */
    fun loadBlocklist(context: Context): Int {
        val localFile = File(context.filesDir, Config.LOCAL_BLOCKLIST_FILE)
        val table: DomainLookupTable = try {
            if (localFile.exists() && localFile.length() > 0L) {
                BufferedReader(InputStreamReader(FileInputStream(localFile), Charsets.UTF_8)).use { reader ->
                    parseHostsStream(reader)
                }
            } else {
                BufferedReader(
                    InputStreamReader(context.assets.open(Config.ASSET_BLOCKLIST_FILE), Charsets.UTF_8)
                ).use { reader ->
                    parseHostsStream(reader)
                }
            }
        } catch (_: Exception) {
            try {
                BufferedReader(
                    InputStreamReader(context.assets.open(Config.ASSET_BLOCKLIST_FILE), Charsets.UTF_8)
                ).use { reader ->
                    parseHostsStream(reader)
                }
            } catch (_: Exception) {
                HashSetDomainTable(HashSet())
            }
        }

        lookupTable = table
        VpnStats.setBlocklistCount(table.size)
        return table.size
    }

    /**
     * Downloads the StevenBlack hosts file (and optional HTTPS custom blocklist URL) to a temporary file
     * and atomically renames it to filesDir/blocklist.txt only if the download succeeds.
     */
    suspend fun downloadBlocklist(context: Context, customUrl: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            VpnStats.setDownloadingBlocklist(true)
            val tempFile = File(context.filesDir, Config.LOCAL_BLOCKLIST_TEMP_FILE)
            val targetFile = File(context.filesDir, Config.LOCAL_BLOCKLIST_FILE)
            try {
                val resolvedCustomUrl = customUrl?.trim()
                    ?: VpnStateStore.getCustomBlocklistUrl(context).trim()

                var linesWritten = 0
                BufferedWriter(OutputStreamWriter(FileOutputStream(tempFile, false), Charsets.UTF_8)).use { writer ->
                    linesWritten += downloadUrlToWriter(Config.DEFAULT_BLOCKLIST_URL, writer)
                    if (resolvedCustomUrl.startsWith("https://", ignoreCase = true)) {
                        try {
                            writer.newLine()
                            linesWritten += downloadUrlToWriter(resolvedCustomUrl, writer)
                        } catch (_: Exception) {
                            // Keep main blocklist intact even if optional custom URL fails
                        }
                    }
                }

                if (linesWritten > 0 && tempFile.exists() && tempFile.length() > 0L) {
                    val renamed = tempFile.renameTo(targetFile)
                    if (!renamed) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    loadBlocklist(context)
                    true
                } else {
                    tempFile.delete()
                    false
                }
            } catch (_: Exception) {
                tempFile.delete()
                false
            } finally {
                VpnStats.setDownloadingBlocklist(false)
            }
        }

    private fun downloadUrlToWriter(urlString: String, writer: BufferedWriter): Int {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = Config.HTTP_CONNECT_TIMEOUT_MS
            readTimeout = Config.HTTP_READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP status $code")
            }
            var count = 0
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.forEachLine { line ->
                    writer.write(line)
                    writer.newLine()
                    count++
                }
            }
            return count
        } finally {
            connection.disconnect()
        }
    }
}
