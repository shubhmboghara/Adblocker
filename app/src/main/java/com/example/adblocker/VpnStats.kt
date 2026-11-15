package com.example.adblocker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VpnStats {
    private val _isProtectionEnabled = MutableStateFlow(false)
    val isProtectionEnabled: StateFlow<Boolean> = _isProtectionEnabled.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _blocklistCount = MutableStateFlow(0)
    val blocklistCount: StateFlow<Int> = _blocklistCount.asStateFlow()

    private val _sourceStatuses = MutableStateFlow<Map<String, SourceStatus>>(emptyMap())
    val sourceStatuses: StateFlow<Map<String, SourceStatus>> = _sourceStatuses.asStateFlow()

    private val _whitelistSize = MutableStateFlow(0)
    val whitelistSize: StateFlow<Int> = _whitelistSize.asStateFlow()

    private val _lastDnsLatencyMs = MutableStateFlow(-1L)
    val lastDnsLatencyMs: StateFlow<Long> = _lastDnsLatencyMs.asStateFlow()

    private val _droppedDohCount = MutableStateFlow(0)
    val droppedDohCount: StateFlow<Int> = _droppedDohCount.asStateFlow()

    private val _isDownloadingBlocklist = MutableStateFlow(false)
    val isDownloadingBlocklist: StateFlow<Boolean> = _isDownloadingBlocklist.asStateFlow()

    fun setProtectionEnabled(enabled: Boolean) {
        _isProtectionEnabled.value = enabled
    }

    fun incrementBlocked() {
        _blockedCount.update { it + 1 }
    }

    fun setBlocklistCount(count: Int) {
        _blocklistCount.value = count
    }

    fun setSourceStatuses(statuses: Map<String, SourceStatus>) {
        _sourceStatuses.value = statuses
    }

    fun setWhitelistSize(size: Int) {
        _whitelistSize.value = size
    }

    fun setLastDnsLatencyMs(latencyMs: Long) {
        _lastDnsLatencyMs.value = latencyMs
    }

    fun incrementDroppedDoh() {
        _droppedDohCount.update { it + 1 }
    }

    fun setDownloadingBlocklist(downloading: Boolean) {
        _isDownloadingBlocklist.value = downloading
    }

    fun resetStats() {
        _blockedCount.value = 0
        _lastDnsLatencyMs.value = -1L
        _droppedDohCount.value = 0
        _sourceStatuses.value = emptyMap()
    }
}
