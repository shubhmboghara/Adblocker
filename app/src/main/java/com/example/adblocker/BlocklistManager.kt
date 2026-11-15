package com.example.adblocker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    private var lookupTable: DomainLookupTable = CompactSortedDomainTable(emptyArray())

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

    fun isBlockedInTable(domain: String, table: DomainLookupTable): Boolean {
        return DomainMatcher.isDomainOrParentInSet(domain) { candidate ->
            table.contains(candidate)
        }
    }

    fun setTestBlocklist(domains: Collection<String>) {
        val set = HashSet<String>(domains.size)
        for (d in domains) {
            val clean = DomainMatcher.normalizeDomain(d)
            if (clean.isNotEmpty() && !IGNORED_HOSTS.contains(clean) && !isIpAddress(clean)) {
                set.add(clean)
            }
        }
        val table = buildLookupTable(set)
        lookupTable = table
        VpnStats.setBlocklistCount(table.size)
    }

    /**
     * Returns true if the given string is an IPv4 or IPv6 literal rather than a domain name.
     */
    fun isIpAddress(candidate: String): Boolean {
        if (candidate.contains(':')) return true
        val parts = candidate.split('.')
        if (parts.all { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }) {
            return true
        }
        return false
    }

    /**
     * Parses a single line in hosts format starting with 0.0.0.0 or 127.0.0.1 followed by a domain.
     * Strips inline comments (#...), trims whitespace, strips trailing dots, and lowercases.
     * Skips comments, blank lines, localhost, local, broadcasthost, and any IP address entry.
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

        val prefixIp = line.substring(tokenStart, tokenEnd)
        if (prefixIp != "0.0.0.0" && prefixIp != "127.0.0.1") {
            return null
        }

        var secondStart = tokenEnd
        while (secondStart < len && line[secondStart].isWhitespace()) secondStart++
        if (secondStart >= len) return null

        var secondEnd = secondStart
        while (secondEnd < len && !line[secondEnd].isWhitespace()) secondEnd++
        val rawDomain = line.substring(secondStart, secondEnd)

        val domain = rawDomain.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (
            domain.isEmpty() ||
            IGNORED_HOSTS.contains(domain) ||
            !domain.contains('.') ||
            isIpAddress(domain)
        ) {
            return null
        }
        return domain
    }

    /**
     * Reads one or more BufferedReaders line by line, deduplicates all valid domains,
     * and returns a CompactSortedDomainTable backed by a sorted Array<String>.
     */
    fun parseAndMergeStreams(readers: List<BufferedReader>): CompactSortedDomainTable {
        val deduplicated = HashSet<String>()
        for (reader in readers) {
            reader.forEachLine { line ->
                val domain = parseHostsLine(line)
                if (domain != null) {
                    deduplicated.add(domain)
                }
            }
        }
        return buildLookupTable(deduplicated)
    }

    fun parseHostsStream(
        reader: BufferedReader,
        @Suppress("UNUSED_PARAMETER") compactThreshold: Int = 0
    ): CompactSortedDomainTable {
        return parseAndMergeStreams(listOf(reader))
    }

    fun buildLookupTable(domains: HashSet<String>): CompactSortedDomainTable {
        val array = domains.toTypedArray()
        domains.clear()
        array.sort()
        return CompactSortedDomainTable(array)
    }

    fun getListsDir(context: Context): File {
        val dir = File(context.filesDir, Config.LISTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getSourceFile(context: Context, sourceId: String): File {
        return File(getListsDir(context), "$sourceId.txt")
    }

    /**
     * Merges all enabled blocklist source files from filesDir/lists/<id>.txt plus any custom blocklist file
     * into one deduplicated sorted String array, swapping it in atomically via the volatile lookupTable reference.
     * Falls back to assets/blocklist.txt (or legacy filesDir/blocklist.txt) only when no downloaded list exists.
     */
    fun loadBlocklist(context: Context, enabledIdsOverride: Set<String>? = null): Int {
        val enabledIds = enabledIdsOverride ?: runBlocking(Dispatchers.IO) {
            VpnStateStore.getEnabledSourceIds(context)
        }
        val customUrl = runBlocking(Dispatchers.IO) {
            VpnStateStore.getCustomBlocklistUrl(context).trim()
        }

        val mergedSet = HashSet<String>()
        val statusMap = LinkedHashMap<String, SourceStatus>()
        var anyDownloadedFileFound = false

        for (source in BlocklistSources.ALL) {
            val file = getSourceFile(context, source.id)
            if (file.exists() && file.length() > 0L) {
                anyDownloadedFileFound = true
                var sourceEntryCount = 0
                val isEnabled = enabledIds.contains(source.id)
                try {
                    BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
                        reader.forEachLine { line ->
                            val domain = parseHostsLine(line)
                            if (domain != null) {
                                sourceEntryCount++
                                if (isEnabled) {
                                    mergedSet.add(domain)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                statusMap[source.id] = SourceStatus(
                    id = source.id,
                    entryCount = sourceEntryCount,
                    lastUpdatedMillis = file.lastModified()
                )
            } else {
                statusMap[source.id] = SourceStatus(
                    id = source.id,
                    entryCount = 0,
                    lastUpdatedMillis = 0L
                )
            }
        }

        if (customUrl.startsWith("https://", ignoreCase = true)) {
            val customFile = getSourceFile(context, Config.CUSTOM_SOURCE_ID)
            if (customFile.exists() && customFile.length() > 0L) {
                anyDownloadedFileFound = true
                try {
                    BufferedReader(InputStreamReader(FileInputStream(customFile), Charsets.UTF_8)).use { reader ->
                        reader.forEachLine { line ->
                            val domain = parseHostsLine(line)
                            if (domain != null) {
                                mergedSet.add(domain)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Legacy single-file or bundled assets/blocklist.txt fallback when no downloaded list exists
        if (!anyDownloadedFileFound) {
            val legacyFile = File(context.filesDir, Config.LOCAL_BLOCKLIST_FILE)
            try {
                if (legacyFile.exists() && legacyFile.length() > 0L) {
                    BufferedReader(InputStreamReader(FileInputStream(legacyFile), Charsets.UTF_8)).use { reader ->
                        reader.forEachLine { line ->
                            val domain = parseHostsLine(line)
                            if (domain != null) {
                                mergedSet.add(domain)
                            }
                        }
                    }
                } else {
                    BufferedReader(
                        InputStreamReader(context.assets.open(Config.ASSET_BLOCKLIST_FILE), Charsets.UTF_8)
                    ).use { reader ->
                        reader.forEachLine { line ->
                            val domain = parseHostsLine(line)
                            if (domain != null) {
                                mergedSet.add(domain)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val newTable = buildLookupTable(mergedSet)
        // Atomic volatile swap so the DNS thread never sees a half-built list
        lookupTable = newTable
        VpnStats.setBlocklistCount(newTable.size)
        VpnStats.setSourceStatuses(statusMap)
        return newTable.size
    }

    /**
     * Downloads each enabled source separately to filesDir/lists/<id>.txt via a temp file first,
     * renaming only on success so a failed download never corrupts or deletes the existing working copy.
     */
    suspend fun downloadBlocklist(context: Context, customUrl: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            VpnStats.setDownloadingBlocklist(true)
            try {
                val enabledIds = VpnStateStore.getEnabledSourceIds(context)
                val resolvedCustomUrl = customUrl?.trim()
                    ?: VpnStateStore.getCustomBlocklistUrl(context).trim()

                var anySucceeded = false
                for (source in BlocklistSources.ALL) {
                    if (enabledIds.contains(source.id)) {
                        val ok = downloadSingleSource(context, source.id, source.url)
                        if (ok) {
                            anySucceeded = true
                        }
                    }
                }

                if (resolvedCustomUrl.startsWith("https://", ignoreCase = true)) {
                    val customOk = downloadSingleSource(context, Config.CUSTOM_SOURCE_ID, resolvedCustomUrl)
                    if (customOk) {
                        anySucceeded = true
                    }
                }

                loadBlocklist(context, enabledIds)
                anySucceeded
            } finally {
                VpnStats.setDownloadingBlocklist(false)
            }
        }

    suspend fun downloadSingleSourceById(context: Context, sourceId: String): Boolean =
        withContext(Dispatchers.IO) {
            val source = BlocklistSources.ALL.find { it.id == sourceId } ?: return@withContext false
            VpnStats.setDownloadingBlocklist(true)
            try {
                val ok = downloadSingleSource(context, source.id, source.url)
                loadBlocklist(context)
                ok
            } finally {
                VpnStats.setDownloadingBlocklist(false)
            }
        }

    private fun downloadSingleSource(context: Context, sourceId: String, urlString: String): Boolean {
        val listsDir = getListsDir(context)
        val tempFile = File(listsDir, "$sourceId.txt.tmp")
        val targetFile = File(listsDir, "$sourceId.txt")
        return try {
            var validDomainsWritten = 0
            BufferedWriter(OutputStreamWriter(FileOutputStream(tempFile, false), Charsets.UTF_8)).use { writer ->
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
                    BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                        reader.forEachLine { line ->
                            val domain = parseHostsLine(line)
                            if (domain != null) {
                                writer.write("0.0.0.0 ")
                                writer.write(domain)
                                writer.newLine()
                                validDomainsWritten++
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (validDomainsWritten > 0 && tempFile.exists() && tempFile.length() > 0L) {
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    }

    fun deleteAllDownloadedLists(context: Context) {
        val listsDir = File(context.filesDir, Config.LISTS_DIR_NAME)
        if (listsDir.exists()) {
            listsDir.listFiles()?.forEach { it.delete() }
            listsDir.delete()
        }
        val legacyFile = File(context.filesDir, Config.LOCAL_BLOCKLIST_FILE)
        if (legacyFile.exists()) {
            legacyFile.delete()
        }
        val tempLegacyFile = File(context.filesDir, Config.LOCAL_BLOCKLIST_TEMP_FILE)
        if (tempLegacyFile.exists()) {
            tempLegacyFile.delete()
        }
    }
}
