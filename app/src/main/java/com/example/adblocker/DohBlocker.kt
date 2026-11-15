package com.example.adblocker

object DohBlocker {
    val DOH_IPS: Set<String> = setOf(
        Config.DNS_GOOGLE,
        Config.DNS_GOOGLE_SECONDARY,
        Config.DNS_CLOUDFLARE,
        Config.DNS_CLOUDFLARE_SECONDARY,
        Config.DNS_QUAD9,
        Config.DNS_QUAD9_SECONDARY,
        Config.DNS_ADGUARD_PRIMARY,
        Config.DNS_ADGUARD_SECONDARY
    )

    fun getBlockedIps(chosenUpstream: String): Set<String> {
        val normalized = chosenUpstream.trim()
        return DOH_IPS.filterTo(HashSet()) { it != normalized }
    }

    fun formatIpv4Address(packet: ByteArray, offset: Int): String {
        return "${packet[offset].toInt() and 0xFF}." +
            "${packet[offset + 1].toInt() and 0xFF}." +
            "${packet[offset + 2].toInt() and 0xFF}." +
            "${packet[offset + 3].toInt() and 0xFF}"
    }

    fun shouldDropIpv4Packet(packet: ByteArray, length: Int, chosenUpstream: String): Boolean {
        if (length < 20) return false
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return false
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 4) return false

        val protocol = packet[9].toInt() and 0xFF
        // Protocol 6 = TCP
        if (protocol != 6) return false

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != Config.HTTPS_PORT) return false

        val dstIp = formatIpv4Address(packet, 16)
        if (dstIp == chosenUpstream.trim()) return false
        return DOH_IPS.contains(dstIp)
    }
}
