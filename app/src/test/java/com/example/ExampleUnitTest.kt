package com.example

import com.example.adblocker.BlocklistManager
import com.example.adblocker.CompactSortedDomainTable
import com.example.adblocker.Config
import com.example.adblocker.DnsPacketUtils
import com.example.adblocker.DohBlocker
import com.example.adblocker.DomainMatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class ExampleUnitTest {

    @Test
    fun parseDnsQuestionName_standardAndCompressionPointers_andMalformedAndTruncated() {
        // 1. Standard query for "ads.example.com"
        val query = DnsPacketUtils.buildSimpleDnsQuery("ads.example.com", queryId = 0x1A2B)
        assertEquals("ads.example.com", DnsPacketUtils.parseDnsQuestionName(query))

        // 2. Truncated packet (shorter than header + question)
        val truncated = query.copyOfRange(0, 14)
        assertNull(DnsPacketUtils.parseDnsQuestionName(truncated))

        // 3. Malformed packet (label length exceeds packet size)
        val malformed = ByteArray(20)
        malformed[4] = 0x00
        malformed[5] = 0x01 // QDCOUNT = 1
        malformed[12] = 50 // claims 50-byte label in 20-byte packet
        assertNull(DnsPacketUtils.parseDnsQuestionName(malformed))

        // 4. Compression pointer test: QNAME starts with "sub" and points to offset 22 ("example.com")
        val compressedPacket = byteArrayOf(
            0x12, 0x34, 0x01, 0x00,
            0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            // Offset 12: "sub" + pointer to offset 22 (0xC0, 0x16)
            0x03, 's'.code.toByte(), 'u'.code.toByte(), 'b'.code.toByte(),
            0xC0.toByte(), 0x16,
            // QTYPE (1) & QCLASS (1) at offsets 18..21
            0x00, 0x01, 0x00, 0x01,
            // Offset 22 (0x16): "example.com"
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00
        )
        assertEquals("sub.example.com", DnsPacketUtils.parseDnsQuestionName(compressedPacket))
    }

    @Test
    fun domainAndParentDomainMatching_blocklistAndWhitelistPrecedence() {
        val blocklist = setOf("ads.example.com", "tracker.net")
        val whitelist = setOf("example.com")

        // ads.example.com is in blocklist, but parent domain example.com is whitelisted -> allowed
        assertFalse(
            DomainMatcher.shouldBlockDomain(
                domain = "ads.example.com",
                isWhitelisted = { whitelist.contains(it) },
                isBlocked = { blocklist.contains(it) }
            )
        )

        // sub.tracker.net has parent tracker.net in blocklist and is not whitelisted -> blocked
        assertTrue(
            DomainMatcher.shouldBlockDomain(
                domain = "sub.tracker.net",
                isWhitelisted = { whitelist.contains(it) },
                isBlocked = { blocklist.contains(it) }
            )
        )

        // unrelated domain -> allowed
        assertFalse(
            DomainMatcher.shouldBlockDomain(
                domain = "wikipedia.org",
                isWhitelisted = { whitelist.contains(it) },
                isBlocked = { blocklist.contains(it) }
            )
        )
    }

    @Test
    fun buildNxDomainResponse_copiesIdAndQuestionAndSetsNxDomainFlags() {
        val query = DnsPacketUtils.buildSimpleDnsQuery("doubleclick.net", queryId = 0xCAFE)
        val response = DnsPacketUtils.buildNxDomainResponse(query)
        assertNotNull(response)
        response!!

        // Transaction ID copied
        assertEquals(0xCA.toByte(), response[0])
        assertEquals(0xFE.toByte(), response[1])

        // Flags: QR=1, RD=1 -> 0x81; RA=1, RCODE=3 (NXDOMAIN) -> 0x83
        assertEquals(0x81.toByte(), response[2])
        assertEquals(0x83.toByte(), response[3])

        // QDCOUNT = 1, ANCOUNT = 0
        assertEquals(0x00.toByte(), response[4])
        assertEquals(0x01.toByte(), response[5])
        assertEquals(0x00.toByte(), response[6])
        assertEquals(0x00.toByte(), response[7])

        // Question section copied identically
        val queryQuestion = query.copyOfRange(12, query.size)
        val responseQuestion = response.copyOfRange(12, response.size)
        assertArrayEquals(queryQuestion, responseQuestion)
    }

    @Test
    fun calculateIpv4Checksum_matchesRfc1071TestVector() {
        // RFC 1071 / Wikipedia IPv4 header checksum example (checksum bytes at 10..11 zeroed)
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x73,
            0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00,
            0xC0.toByte(), 0xA8.toByte(), 0x00, 0x01,
            0xC0.toByte(), 0xA8.toByte(), 0x00, 0xC7.toByte()
        )
        val checksum = DnsPacketUtils.calculateIpv4Checksum(header, 0, 20)
        assertEquals(0xB861, checksum)
    }

    @Test
    fun calculateIpv6UdpChecksum_computesNonZeroMandatoryChecksum() {
        val srcIp = ByteArray(16).apply { this[0] = 0xFD.toByte(); this[15] = 0x01 }
        val dstIp = ByteArray(16).apply { this[0] = 0xFD.toByte(); this[15] = 0x02 }
        val payload = DnsPacketUtils.buildSimpleDnsQuery("example.com", 0x1111)
        val udpLen = 8 + payload.size
        val udpSegment = ByteArray(udpLen)
        udpSegment[0] = 0x00
        udpSegment[1] = 53
        udpSegment[2] = 0x30
        udpSegment[3] = 0x39
        udpSegment[4] = ((udpLen ushr 8) and 0xFF).toByte()
        udpSegment[5] = (udpLen and 0xFF).toByte()
        System.arraycopy(payload, 0, udpSegment, 8, payload.size)

        val checksum = DnsPacketUtils.calculateIpv6UdpChecksum(srcIp, dstIp, udpSegment, udpLen)
        assertTrue(checksum in 1..0xFFFF)
    }

    @Test
    fun hostsFileParsing_skipsCommentsBlankLinesLocalhostAndZeroIp_andSupportsCompactTable() {
        val sampleHosts = """
            # Title comment
            127.0.0.1 localhost
            ::1 ip6-localhost
            0.0.0.0 0.0.0.0
            
            0.0.0.0 ads.example.com # inline comment
            127.0.0.1 tracker.example.org
            0.0.0.0 metrics.adserver.io
        """.trimIndent()

        val table = BlocklistManager.parseHostsStream(
            reader = BufferedReader(StringReader(sampleHosts)),
            compactThreshold = 2 // Force CompactSortedDomainTable since 3 > 2
        )
        assertTrue(table is CompactSortedDomainTable)
        assertEquals(3, table.size)
        assertTrue(table.contains("ads.example.com"))
        assertTrue(table.contains("tracker.example.org"))
        assertTrue(table.contains("metrics.adserver.io"))
        assertFalse(table.contains("localhost"))
        assertFalse(table.contains("0.0.0.0"))
    }

    @Test
    fun dohBlocker_respectsChosenUpstreamDns() {
        val blockedForCloudflare = DohBlocker.getBlockedIps(Config.DNS_CLOUDFLARE)
        assertFalse(blockedForCloudflare.contains(Config.DNS_CLOUDFLARE))
        assertTrue(blockedForCloudflare.contains(Config.DNS_GOOGLE))
        assertTrue(blockedForCloudflare.contains(Config.DNS_QUAD9))
    }
}
