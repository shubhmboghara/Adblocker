package com.example.adblocker

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class AdBlockRepository private constructor(private val dao: AdBlockDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingLogQueue = ConcurrentLinkedQueue<BlockedLogEntry>()

    @Volatile
    private var whitelistMemorySet: HashSet<String> = HashSet()

    val whitelistFlow: Flow<List<WhitelistEntry>> = dao.getAllWhitelistFlow()
    val groupedLogsFlow: Flow<List<GroupedBlockedLog>> = dao.getGroupedBlockedLogsFlow()

    init {
        // Keep in-memory whitelist HashSet synchronized whenever the whitelist table changes
        scope.launch {
            dao.getAllWhitelistFlow().collect { entries ->
                val updated = HashSet<String>(entries.size)
                for (entry in entries) {
                    updated.add(DomainMatcher.normalizeDomain(entry.domain))
                }
                whitelistMemorySet = updated
                VpnStats.setWhitelistSize(updated.size)
            }
        }

        // Batch blocked_log inserts every 2 seconds on a background thread so DNS thread never waits
        scope.launch {
            while (isActive) {
                delay(Config.LOG_BATCH_INTERVAL_MS)
                flushPendingLogs()
            }
        }
    }

    fun isWhitelisted(domain: String): Boolean {
        val snapshot = whitelistMemorySet
        return DomainMatcher.isDomainOrParentInSet(domain) { candidate ->
            snapshot.contains(candidate)
        }
    }

    fun enqueueBlockedDomain(domain: String, timestamp: Long = System.currentTimeMillis()) {
        val normalized = DomainMatcher.normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            pendingLogQueue.add(BlockedLogEntry(domain = normalized, timestamp = timestamp))
        }
    }

    suspend fun flushPendingLogs() {
        if (pendingLogQueue.isEmpty()) return
        val batch = ArrayList<BlockedLogEntry>()
        while (true) {
            val item = pendingLogQueue.poll() ?: break
            batch.add(item)
        }
        if (batch.isNotEmpty()) {
            dao.insertBlockedLogs(batch)
            dao.trimBlockedLogs(Config.MAX_BLOCKED_LOG_ROWS)
        }
    }

    suspend fun addToWhitelist(domain: String) {
        val normalized = DomainMatcher.normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            dao.insertWhitelist(WhitelistEntry(normalized))
        }
    }

    suspend fun removeFromWhitelist(domain: String) {
        val normalized = DomainMatcher.normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            dao.removeWhitelist(normalized)
        }
    }

    suspend fun clearLogs() {
        pendingLogQueue.clear()
        dao.clearBlockedLogs()
    }

    suspend fun clearAllTables() {
        pendingLogQueue.clear()
        dao.clearBlockedLogs()
        dao.clearWhitelist()
        whitelistMemorySet = HashSet()
        VpnStats.setWhitelistSize(0)
    }

    companion object {
        @Volatile
        private var INSTANCE: AdBlockRepository? = null

        fun getInstance(context: Context): AdBlockRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdBlockRepository(
                    AppDatabase.getInstance(context).adBlockDao()
                ).also { INSTANCE = it }
            }
        }
    }
}
