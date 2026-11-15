package com.example.adblocker

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap? = null
)

class AdBlockViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = AdBlockRepository.getInstance(appContext)

    val settingsState: StateFlow<AdBlockerSettings> = VpnStateStore.settingsFlow(appContext)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AdBlockerSettings()
        )

    val isProtectionEnabled: StateFlow<Boolean> = VpnStats.isProtectionEnabled
    val blockedCount: StateFlow<Int> = VpnStats.blockedCount
    val blocklistCount: StateFlow<Int> = VpnStats.blocklistCount
    val sourceStatuses: StateFlow<Map<String, SourceStatus>> = VpnStats.sourceStatuses
    val whitelistSize: StateFlow<Int> = VpnStats.whitelistSize
    val lastDnsLatencyMs: StateFlow<Long> = VpnStats.lastDnsLatencyMs
    val droppedDohCount: StateFlow<Int> = VpnStats.droppedDohCount
    val isDownloadingBlocklist: StateFlow<Boolean> = VpnStats.isDownloadingBlocklist

    val whitelistEntries: StateFlow<List<WhitelistEntry>> = repository.whitelistFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val groupedBlockedLogs: StateFlow<List<GroupedBlockedLog>> = repository.groupedLogsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _crashReportText = MutableStateFlow<String?>(null)
    val crashReportText: StateFlow<String?> = _crashReportText.asStateFlow()

    private val _diagDnsTestResult = MutableStateFlow<String?>(null)
    val diagDnsTestResult: StateFlow<String?> = _diagDnsTestResult.asStateFlow()

    private val _diagBlockTestResult = MutableStateFlow<Boolean?>(null)
    val diagBlockTestResult: StateFlow<Boolean?> = _diagBlockTestResult.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (BlocklistManager.currentSize == 0) {
                BlocklistManager.loadBlocklist(appContext)
            }
            checkPendingCrashReport()
            val initialSettings = VpnStateStore.getSettings(appContext)
            if (initialSettings.disclosureAccepted && initialSettings.protectionEnabled) {
                val prepareIntent = VpnService.prepare(appContext)
                if (prepareIntent == null) {
                    VpnStats.setProtectionEnabled(true)
                    startServiceWithAction(AdBlockVpnService.ACTION_START)
                } else {
                    VpnStateStore.setProtectionEnabled(appContext, false)
                }
            } else {
                VpnStats.setProtectionEnabled(false)
            }
            loadInstalledLauncherApps()
        }
    }

    private fun checkPendingCrashReport() {
        val crashFile = File(appContext.filesDir, Config.CRASH_REPORT_FILE)
        if (crashFile.exists() && crashFile.length() > 0L) {
            runCatching {
                _crashReportText.value = crashFile.readText(Charsets.UTF_8)
            }
        }
    }

    fun dismissCrashReport() {
        viewModelScope.launch(Dispatchers.IO) {
            val crashFile = File(appContext.filesDir, Config.CRASH_REPORT_FILE)
            if (crashFile.exists()) {
                crashFile.delete()
            }
            _crashReportText.value = null
        }
    }

    private fun loadInstalledLauncherApps() {
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val apps = resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == appContext.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val iconBitmap = runCatching {
                    info.loadIcon(pm)?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                }.getOrNull()
                InstalledAppInfo(label = label, packageName = pkg, icon = iconBitmap)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        _installedApps.value = apps
    }

    fun acceptDisclosure(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            VpnStateStore.setDisclosureDecision(appContext, accepted = true)
            onComplete()
        }
    }

    fun declineDisclosure() {
        viewModelScope.launch {
            stopProtection()
            VpnStateStore.setDisclosureDecision(appContext, accepted = false)
        }
    }

    fun resetDisclosure(onNavigateToDisclosure: () -> Unit) {
        viewModelScope.launch {
            stopServiceAndSetOff()
            VpnStateStore.resetDisclosure(appContext)
            onNavigateToDisclosure()
        }
    }

    fun startProtectionAfterConsent() {
        viewModelScope.launch {
            if (!VpnStateStore.isDisclosureAccepted(appContext)) return@launch
            VpnStateStore.setProtectionEnabled(appContext, true)
            startServiceWithAction(AdBlockVpnService.ACTION_START)
        }
    }

    fun onVpnConsentDenied() {
        viewModelScope.launch {
            VpnStateStore.setProtectionEnabled(appContext, false)
        }
    }

    fun stopProtection() {
        viewModelScope.launch {
            stopServiceAndSetOff()
        }
    }

    private suspend fun stopServiceAndSetOff() {
        VpnStateStore.setProtectionEnabled(appContext, false)
        val stopIntent = Intent(appContext, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_STOP
        }
        runCatching {
            appContext.startService(stopIntent)
        }
    }

    fun allowDomain(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addToWhitelist(domain)
        }
    }

    fun removeWhitelistedDomain(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeFromWhitelist(domain)
        }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
        }
    }

    /**
     * Toggles one of the 5 built-in blocklist sources in DataStore and rebuilds the merged
     * sorted String array in the background without restarting the VPN service.
     */
    fun toggleBlocklistSource(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            VpnStateStore.setSourceEnabled(appContext, sourceId, enabled)
            withContext(Dispatchers.IO) {
                val sourceFile = BlocklistManager.getSourceFile(appContext, sourceId)
                if (enabled && (!sourceFile.exists() || sourceFile.length() == 0L)) {
                    BlocklistManager.downloadSingleSourceById(appContext, sourceId)
                } else {
                    BlocklistManager.loadBlocklist(appContext)
                }
            }
        }
    }

    /**
     * Re-downloads all enabled blocklist sources and rebuilds the merged sorted String array
     * without restarting the VPN service.
     */
    fun refreshAllBlocklistsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            BlocklistManager.downloadBlocklist(appContext)
        }
    }

    fun updateUpstreamDns(option: UpstreamDnsOption, customIp: String = "") {
        viewModelScope.launch {
            if (option == UpstreamDnsOption.CUSTOM && !VpnStateStore.isValidIpv4(customIp)) {
                return@launch
            }
            VpnStateStore.setUpstreamDns(appContext, option, customIp)
            restartVpnIfActive()
        }
    }

    fun toggleAppBypass(packageName: String, bypassed: Boolean) {
        viewModelScope.launch {
            VpnStateStore.toggleBypassedPackage(appContext, packageName, bypassed)
            restartVpnIfActive()
        }
    }

    fun saveAndRefreshCustomBlocklist(url: String) {
        viewModelScope.launch {
            if (!VpnStateStore.isValidHttpsUrl(url)) return@launch
            VpnStateStore.setCustomBlocklistUrl(appContext, url)
            withContext(Dispatchers.IO) {
                BlocklistManager.downloadBlocklist(appContext, url)
            }
            restartVpnIfActive()
        }
    }

    fun clearAllData(onNavigateToDisclosure: () -> Unit) {
        viewModelScope.launch {
            stopServiceAndSetOff()
            withContext(Dispatchers.IO) {
                VpnStateStore.clearAll(appContext)
                repository.clearAllTables()
                BlocklistManager.deleteAllDownloadedLists(appContext)
                BlocklistManager.loadBlocklist(appContext)
                VpnStats.resetStats()
            }
            onNavigateToDisclosure()
        }
    }

    fun runDiagnosticsDnsTest(
        formatSuccess: (Long) -> String,
        formatFailure: (String) -> String,
        runningText: String
    ) {
        viewModelScope.launch {
            _diagDnsTestResult.value = runningText
            val upstreamIp = settingsState.value.activeUpstreamDns
            val result = withContext(Dispatchers.IO) {
                var socket: DatagramSocket? = null
                val startNs = System.nanoTime()
                try {
                    val query = DnsPacketUtils.buildSimpleDnsQuery(Config.TEST_ALLOWED_DOMAIN)
                    socket = DatagramSocket()
                    socket.soTimeout = Config.UPSTREAM_DNS_TIMEOUT_MS
                    val addr = InetAddress.getByName(upstreamIp)
                    socket.send(DatagramPacket(query, query.size, addr, Config.DNS_PORT))
                    val replyBuf = ByteArray(1024)
                    val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                    socket.receive(replyPacket)
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                    VpnStats.setLastDnsLatencyMs(elapsedMs)
                    if (replyPacket.length >= 12) {
                        formatSuccess(elapsedMs)
                    } else {
                        formatFailure("Short reply")
                    }
                } catch (e: Exception) {
                    formatFailure(e.javaClass.simpleName)
                } finally {
                    socket?.close()
                }
            }
            _diagDnsTestResult.value = result
        }
    }

    fun runDiagnosticsBlockTest() {
        val wouldBlock = DomainMatcher.shouldBlockDomain(
            domain = Config.TEST_BLOCKED_DOMAIN,
            isWhitelisted = repository::isWhitelisted,
            isBlocked = BlocklistManager::isBlocked
        )
        _diagBlockTestResult.value = wouldBlock
    }

    private suspend fun restartVpnIfActive() {
        if (VpnStateStore.isProtectionEnabled(appContext) && VpnService.prepare(appContext) == null) {
            startServiceWithAction(AdBlockVpnService.ACTION_RESTART)
        }
    }

    private fun startServiceWithAction(action: String) {
        val intent = Intent(appContext, AdBlockVpnService::class.java).apply {
            this.action = action
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }
}
