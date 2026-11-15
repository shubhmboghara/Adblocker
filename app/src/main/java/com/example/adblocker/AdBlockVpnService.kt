package com.example.adblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class AdBlockVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    private var activeUpstreamDns: String = Config.DEFAULT_UPSTREAM_DNS

    private var dnsThread: Thread? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var repository: AdBlockRepository

    override fun onCreate() {
        super.onCreate()
        repository = AdBlockRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnAndPersistOff()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                startForegroundWithNotification()
                restartVpnTunnel()
                return START_STICKY
            }
            else -> {
                startForegroundWithNotification()
                if (!isRunning || vpnInterface == null) {
                    startVpnTunnel()
                }
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopVpnAndPersistOff()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnelInternal()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Config.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(Config.NOTIFICATION_ID, notification)
        }
    }

    private fun restartVpnTunnel() {
        stopTunnelInternal()
        startVpnTunnel()
    }

    private fun startVpnTunnel() {
        val settings = runBlocking(Dispatchers.IO) {
            VpnStateStore.getSettings(applicationContext)
        }
        if (!settings.disclosureAccepted) {
            stopVpnAndPersistOff()
            return
        }

        activeUpstreamDns = settings.activeUpstreamDns

        if (BlocklistManager.currentSize == 0) {
            serviceScope.launch {
                BlocklistManager.loadBlocklist(applicationContext)
            }
        }

        val establishedPfd = try {
            val builder = Builder()
                .setSession(Config.VPN_SESSION_NAME)
                // IPv4 address, DNS server, and route
                .addAddress(Config.VPN_IPV4_ADDRESS, Config.VPN_IPV4_PREFIX)
                .addDnsServer(Config.VPN_IPV4_DNS)
                .addRoute(Config.VPN_IPV4_DNS, Config.VPN_IPV4_ROUTE_PREFIX)
                // IPv6 address, DNS server, and route
                .addAddress(Config.VPN_IPV6_ADDRESS, Config.VPN_IPV6_PREFIX)
                .addDnsServer(Config.VPN_IPV6_DNS)
                .addRoute(Config.VPN_IPV6_DNS, Config.VPN_IPV6_ROUTE_PREFIX)

            // Add routes for well-known DoH provider IPs so traffic to them enters the tun
            for (dohIp in DohBlocker.DOH_IPS) {
                builder.addRoute(dohIp, 32)
            }

            // Apply per-app VPN bypass
            for (pkg in settings.bypassedPackages) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    // Ignore uninstalled packages
                }
            }

            builder.establish()
        } catch (_: Exception) {
            null
        }

        if (establishedPfd == null) {
            stopVpnAndPersistOff()
            return
        }

        vpnInterface = establishedPfd
        isRunning = true
        VpnStats.setProtectionEnabled(true)
        serviceScope.launch {
            VpnStateStore.setProtectionEnabled(applicationContext, true)
        }

        registerNetworkChangeCallback()

        val pfd = establishedPfd
        dnsThread = Thread(
            { runPacketLoop(pfd) },
            "AdBlocker-DnsLoop"
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(32767)

        while (isRunning && !Thread.currentThread().isInterrupted) {
            val length = try {
                // Blocks on read without polling or sleep loops
                inputStream.read(packetBuffer)
            } catch (_: IOException) {
                break
            }

            if (length <= 0) {
                if (!isRunning) break
                continue
            }

            try {
                handleIncomingPacket(packetBuffer, length, outputStream)
            } catch (_: Exception) {
                // One malformed packet never kills the loop
            }
        }
    }

    private fun handleIncomingPacket(
        packet: ByteArray,
        length: Int,
        outputStream: FileOutputStream
    ) {
        if (length < 20) return
        val version = (packet[0].toInt() ushr 4) and 0x0F

        when (version) {
            4 -> handleIpv4Packet(packet, length, outputStream)
            6 -> handleIpv6Packet(packet, length, outputStream)
        }
    }

    private fun handleIpv4Packet(
        packet: ByteArray,
        length: Int,
        outputStream: FileOutputStream
    ) {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return

        val currentUpstream = activeUpstreamDns

        // Drop TCP port 443 packets destined to DoH IPs (except the user's chosen upstream)
        if (DohBlocker.shouldDropIpv4Packet(packet, length, currentUpstream)) {
            VpnStats.incrementDroppedDoh()
            return
        }

        val protocol = packet[9].toInt() and 0xFF
        // Handle only UDP (17)
        if (protocol != 17) return

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != Config.DNS_PORT) return

        val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        val dnsOffset = ihl + 8
        val dnsLength = (udpLength - 8).coerceAtMost(length - dnsOffset)
        if (dnsLength <= 12) return

        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val responseDns = processDnsQuery(dnsPayload, currentUpstream) ?: return

        val replyPacket = DnsPacketUtils.buildIpv4UdpResponsePacket(
            requestPacket = packet,
            requestIhl = ihl,
            dnsResponse = responseDns
        )
        synchronized(outputStream) {
            outputStream.write(replyPacket)
        }
    }

    private fun handleIpv6Packet(
        packet: ByteArray,
        length: Int,
        outputStream: FileOutputStream
    ) {
        val ipv6HeaderLen = 40
        if (length < ipv6HeaderLen + 8) return

        val nextHeader = packet[6].toInt() and 0xFF
        // Handle only UDP (17)
        if (nextHeader != 17) return

        val dstPort = ((packet[ipv6HeaderLen + 2].toInt() and 0xFF) shl 8) or
            (packet[ipv6HeaderLen + 3].toInt() and 0xFF)
        if (dstPort != Config.DNS_PORT) return

        val udpLength = ((packet[ipv6HeaderLen + 4].toInt() and 0xFF) shl 8) or
            (packet[ipv6HeaderLen + 5].toInt() and 0xFF)
        val dnsOffset = ipv6HeaderLen + 8
        val dnsLength = (udpLength - 8).coerceAtMost(length - dnsOffset)
        if (dnsLength <= 12) return

        val currentUpstream = activeUpstreamDns
        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val responseDns = processDnsQuery(dnsPayload, currentUpstream) ?: return

        val replyPacket = DnsPacketUtils.buildIpv6UdpResponsePacket(
            requestPacket = packet,
            dnsResponse = responseDns
        )
        synchronized(outputStream) {
            outputStream.write(replyPacket)
        }
    }

    /**
     * Evaluates a DNS query against the whitelist and blocklist:
     * - Returns an NXDOMAIN DNS response if blocked.
     * - Forwards via a protected DatagramSocket to the chosen upstream IPv4 DNS resolver if allowed.
     */
    private fun processDnsQuery(dnsQuery: ByteArray, upstreamIp: String): ByteArray? {
        val domain = DnsPacketUtils.parseDnsQuestionName(dnsQuery) ?: return null

        val shouldBlock = DomainMatcher.shouldBlockDomain(
            domain = domain,
            isWhitelisted = repository::isWhitelisted,
            isBlocked = BlocklistManager::isBlocked
        )

        if (shouldBlock) {
            VpnStats.incrementBlocked()
            repository.enqueueBlockedDomain(domain)
            return DnsPacketUtils.buildNxDomainResponse(dnsQuery)
        }

        return forwardDnsQueryProtected(dnsQuery, upstreamIp)
    }

    private fun forwardDnsQueryProtected(dnsQuery: ByteArray, upstreamIp: String): ByteArray? {
        var socket: DatagramSocket? = null
        val startNs = System.nanoTime()
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) {
                return null
            }
            socket.soTimeout = Config.UPSTREAM_DNS_TIMEOUT_MS

            val upstreamAddress = InetAddress.getByName(upstreamIp)
            val outPacket = DatagramPacket(dnsQuery, dnsQuery.size, upstreamAddress, Config.DNS_PORT)
            socket.send(outPacket)

            val responseBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(inPacket)

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            VpnStats.setLastDnsLatencyMs(elapsedMs)

            responseBuffer.copyOfRange(0, inPacket.length)
        } catch (_: SocketTimeoutException) {
            // Drop timed-out query after 3 seconds and continue
            null
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun registerNetworkChangeCallback() {
        unregisterNetworkChangeCallback()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    setUnderlyingNetworks(arrayOf(network))
                } catch (_: Exception) {
                }
            }

            override fun onLost(network: Network) {
                try {
                    setUnderlyingNetworks(null)
                } catch (_: Exception) {
                }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkChangeCallback() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    private fun stopVpnAndPersistOff() {
        stopTunnelInternal()
        VpnStats.setProtectionEnabled(false)
        runBlocking(Dispatchers.IO) {
            VpnStateStore.setProtectionEnabled(applicationContext, false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTunnelInternal() {
        isRunning = false
        unregisterNetworkChangeCallback()

        dnsThread?.interrupt()
        dnsThread = null

        try {
            vpnInterface?.close()
        } catch (_: IOException) {
        } finally {
            vpnInterface = null
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            Config.NOTIFICATION_CHANNEL_ID,
            getString(R.string.vpn_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdBlockVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Config.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                getString(R.string.vpn_notification_turn_off),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.adblocker.action.START"
        const val ACTION_STOP = "com.example.adblocker.action.STOP"
        const val ACTION_RESTART = "com.example.adblocker.action.RESTART"
    }
}
