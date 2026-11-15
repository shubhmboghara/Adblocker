package com.example.adblocker

import java.util.Locale

object DnsPacketUtils {
    private const val DNS_HEADER_SIZE = 12
    private const val MAX_COMPRESSION_JUMPS = 16

    /**
     * Parses the first DNS question name (QNAME) from a raw DNS UDP payload.
     * Supports standard labels as well as RFC 1035 compression pointers (0xC0 mask).
     * Returns null if the packet is malformed or truncated.
     */
    fun parseDnsQuestionName(dnsPayload: ByteArray, length: Int = dnsPayload.size): String? {
        val limit = length.coerceAtMost(dnsPayload.size)
        if (limit < DNS_HEADER_SIZE + 5) return null

        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        if (qdCount <= 0) return null

        val builder = StringBuilder()
        var pos = DNS_HEADER_SIZE
        var jumps = 0
        var jumped = false
        var endOfQuestionNamePos = -1

        while (true) {
            if (pos >= limit) return null
            val lenOrPtr = dnsPayload[pos].toInt() and 0xFF

            if (lenOrPtr == 0) {
                if (!jumped) {
                    endOfQuestionNamePos = pos + 1
                }
                break
            }

            val pointerBits = lenOrPtr and 0xC0
            if (pointerBits == 0xC0) {
                // Compression pointer requires 2 bytes
                if (pos + 1 >= limit) return null
                val b2 = dnsPayload[pos + 1].toInt() and 0xFF
                val targetOffset = ((lenOrPtr and 0x3F) shl 8) or b2
                if (targetOffset < DNS_HEADER_SIZE || targetOffset >= limit || targetOffset == pos) {
                    return null
                }
                if (!jumped) {
                    endOfQuestionNamePos = pos + 2
                    jumped = true
                }
                pos = targetOffset
                jumps++
                if (jumps > MAX_COMPRESSION_JUMPS) return null
            } else if (pointerBits != 0) {
                // Invalid label type bits (0x40 or 0x80)
                return null
            } else {
                // Standard label of length lenOrPtr (1..63)
                val labelLen = lenOrPtr
                pos++
                if (pos + labelLen > limit) return null
                if (builder.isNotEmpty()) {
                    builder.append('.')
                }
                for (i in 0 until labelLen) {
                    val ch = (dnsPayload[pos + i].toInt() and 0xFF).toChar()
                    if (ch.code < 0x20 || ch.code > 0x7E) return null
                    builder.append(ch)
                }
                pos += labelLen
            }
        }

        // Verify QTYPE (2 bytes) and QCLASS (2 bytes) exist after the question name in the original stream
        if (endOfQuestionNamePos < 0 || endOfQuestionNamePos + 4 > limit) {
            return null
        }

        val domain = builder.toString().lowercase(Locale.ROOT)
        return domain.ifEmpty { null }
    }

    /**
     * Finds the byte offset immediately after the first Question entry (QNAME + QTYPE + QCLASS).
     * Returns -1 if malformed or truncated.
     */
    fun findQuestionSectionEnd(dnsPayload: ByteArray, length: Int = dnsPayload.size): Int {
        val limit = length.coerceAtMost(dnsPayload.size)
        if (limit < DNS_HEADER_SIZE + 5) return -1

        var pos = DNS_HEADER_SIZE
        while (pos < limit) {
            val b = dnsPayload[pos].toInt() and 0xFF
            if (b == 0) {
                val endPos = pos + 1 + 4 // null byte + QTYPE (2) + QCLASS (2)
                return if (endPos <= limit) endPos else -1
            } else if ((b and 0xC0) == 0xC0) {
                val endPos = pos + 2 + 4 // 2-byte pointer + QTYPE (2) + QCLASS (2)
                return if (endPos <= limit) endPos else -1
            } else if ((b and 0xC0) != 0) {
                return -1
            } else {
                pos += 1 + b
            }
        }
        return -1
    }

    /**
     * Builds a DNS NXDOMAIN response (RCODE = 3) copying the 2-byte query ID and the first Question section.
     */
    fun buildNxDomainResponse(dnsQuery: ByteArray, length: Int = dnsQuery.size): ByteArray? {
        val limit = length.coerceAtMost(dnsQuery.size)
        if (parseDnsQuestionName(dnsQuery, limit) == null) return null
        val questionEnd = findQuestionSectionEnd(dnsQuery, limit)
        if (questionEnd < DNS_HEADER_SIZE + 5) return null

        val response = ByteArray(questionEnd)
        // Copy Transaction ID (bytes 0..1)
        response[0] = dnsQuery[0]
        response[1] = dnsQuery[1]

        // Flags: QR = 1 (Response), Opcode = copied, AA = 0, TC = 0, RD = copied
        val rdAndOpcode = dnsQuery[2].toInt() and 0x79
        response[2] = (0x80 or rdAndOpcode).toByte()
        // RA = 1, Z = 0, RCODE = 3 (NXDOMAIN) -> 0x83
        response[3] = 0x83.toByte()

        // QDCOUNT = 1
        response[4] = 0x00
        response[5] = 0x01
        // ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        for (i in 6..11) {
            response[i] = 0x00
        }

        // Copy Question section verbatim
        System.arraycopy(dnsQuery, DNS_HEADER_SIZE, response, DNS_HEADER_SIZE, questionEnd - DNS_HEADER_SIZE)
        return response
    }

    /**
     * Builds a minimal Type A (IPv4) DNS query packet for a domain (used by Diagnostics Test DNS).
     */
    fun buildSimpleDnsQuery(domain: String, queryId: Int = 0x4242): ByteArray {
        val labels = domain.trim().trimEnd('.').split('.').filter { it.isNotEmpty() }
        val qnameLen = labels.sumOf { it.length + 1 } + 1
        val packet = ByteArray(DNS_HEADER_SIZE + qnameLen + 4)

        packet[0] = ((queryId ushr 8) and 0xFF).toByte()
        packet[1] = (queryId and 0xFF).toByte()
        // Standard query with recursion desired (0x0100)
        packet[2] = 0x01
        packet[3] = 0x00
        // QDCOUNT = 1
        packet[4] = 0x00
        packet[5] = 0x01

        var offset = DNS_HEADER_SIZE
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            packet[offset++] = bytes.size.toByte()
            System.arraycopy(bytes, 0, packet, offset, bytes.size)
            offset += bytes.size
        }
        packet[offset++] = 0x00 // End of QNAME
        // QTYPE = A (1)
        packet[offset++] = 0x00
        packet[offset++] = 0x01
        // QCLASS = IN (1)
        packet[offset++] = 0x00
        packet[offset] = 0x01

        return packet
    }

    /**
     * Calculates the 16-bit one's complement IPv4 header checksum.
     * Bytes 10..11 within the header range are treated as zero during summation.
     */
    fun calculateIpv4Checksum(header: ByteArray, offset: Int = 0, length: Int = 20): Int {
        var sum = 0L
        var i = 0
        while (i < length - 1) {
            if (i != 10) {
                val word = ((header[offset + i].toInt() and 0xFF) shl 8) or
                    (header[offset + i + 1].toInt() and 0xFF)
                sum += word
            }
            i += 2
        }
        if (length % 2 != 0) {
            val lastByte = (header[offset + length - 1].toInt() and 0xFF) shl 8
            sum += lastByte
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    /**
     * Calculates the 16-bit UDP checksum over the IPv6 pseudo-header (RFC 2460 / RFC 8200).
     * Per RFC 8200, if the computed checksum is 0x0000, it must be represented as 0xFFFF.
     */
    fun calculateIpv6UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpPacket: ByteArray,
        udpLength: Int = udpPacket.size
    ): Int {
        var sum = 0L

        // 16-byte source IPv6 address
        for (i in 0 until 16 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
        }
        // 16-byte destination IPv6 address
        for (i in 0 until 16 step 2) {
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        // 32-bit UDP length in pseudo-header
        sum += (udpLength ushr 16) and 0xFFFF
        sum += udpLength and 0xFFFF
        // Next Header = 17 (UDP)
        sum += 17

        // UDP header + payload (skipping UDP checksum bytes at indices 6..7)
        var i = 0
        while (i < udpLength - 1) {
            if (i != 6) {
                val word = ((udpPacket[i].toInt() and 0xFF) shl 8) or (udpPacket[i + 1].toInt() and 0xFF)
                sum += word
            }
            i += 2
        }
        if (udpLength % 2 != 0) {
            sum += (udpPacket[udpLength - 1].toInt() and 0xFF) shl 8
        }

        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val checksum = (sum.inv() and 0xFFFFL).toInt()
        return if (checksum == 0) 0xFFFF else checksum
    }

    /**
     * Wraps a DNS response payload in an IPv4 + UDP packet by swapping source/destination
     * IPv4 addresses and UDP ports from the incoming request packet and recalculating the IPv4 header checksum.
     */
    fun buildIpv4UdpResponsePacket(
        requestPacket: ByteArray,
        requestIhl: Int,
        dnsResponse: ByteArray,
        dnsResponseLength: Int = dnsResponse.size
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val udpLen = udpHeaderLen + dnsResponseLength
        val totalLen = ipHeaderLen + udpLen
        val out = ByteArray(totalLen)

        // Version (4) + IHL (5 -> 20 bytes)
        out[0] = 0x45.toByte()
        out[1] = 0x00
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        // Identification
        out[4] = requestPacket[4]
        out[5] = requestPacket[5]
        // Flags + Fragment Offset (Don't Fragment = 0x4000)
        out[6] = 0x40.toByte()
        out[7] = 0x00
        // TTL = 64, Protocol = 17 (UDP)
        out[8] = 64.toByte()
        out[9] = 17.toByte()
        // Checksum bytes 10..11 zeroed before calculation
        out[10] = 0
        out[11] = 0

        // Swap Source IPv4 (bytes 12..15) and Destination IPv4 (bytes 16..19)
        System.arraycopy(requestPacket, 16, out, 12, 4)
        System.arraycopy(requestPacket, 12, out, 16, 4)

        val ipChecksum = calculateIpv4Checksum(out, 0, ipHeaderLen)
        out[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        out[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header: Swap Source Port (requestIhl..requestIhl+1) and Destination Port (requestIhl+2..requestIhl+3)
        out[ipHeaderLen] = requestPacket[requestIhl + 2]
        out[ipHeaderLen + 1] = requestPacket[requestIhl + 3]
        out[ipHeaderLen + 2] = requestPacket[requestIhl]
        out[ipHeaderLen + 3] = requestPacket[requestIhl + 1]
        // UDP Length
        out[ipHeaderLen + 4] = ((udpLen ushr 8) and 0xFF).toByte()
        out[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        // UDP Checksum (0 = optional in IPv4)
        out[ipHeaderLen + 6] = 0
        out[ipHeaderLen + 7] = 0

        // Copy DNS Response Payload
        System.arraycopy(dnsResponse, 0, out, ipHeaderLen + udpHeaderLen, dnsResponseLength)
        return out
    }

    /**
     * Wraps a DNS response payload in an IPv6 + UDP packet by swapping source/destination
     * IPv6 addresses and UDP ports from the incoming request packet and calculating the mandatory IPv6 UDP checksum.
     */
    fun buildIpv6UdpResponsePacket(
        requestPacket: ByteArray,
        dnsResponse: ByteArray,
        dnsResponseLength: Int = dnsResponse.size
    ): ByteArray {
        val ipv6HeaderLen = 40
        val udpHeaderLen = 8
        val udpLen = udpHeaderLen + dnsResponseLength
        val totalLen = ipv6HeaderLen + udpLen
        val out = ByteArray(totalLen)

        // Version (6), Traffic Class (0), Flow Label (0)
        out[0] = 0x60.toByte()
        out[1] = 0x00
        out[2] = 0x00
        out[3] = 0x00
        // Payload Length (UDP header + DNS payload)
        out[4] = ((udpLen ushr 8) and 0xFF).toByte()
        out[5] = (udpLen and 0xFF).toByte()
        // Next Header = 17 (UDP), Hop Limit = 64
        out[6] = 17.toByte()
        out[7] = 64.toByte()

        val srcIp = ByteArray(16)
        val dstIp = ByteArray(16)
        // Swap incoming dst (24..39) -> outgoing src (8..23), incoming src (8..23) -> outgoing dst (24..39)
        System.arraycopy(requestPacket, 24, srcIp, 0, 16)
        System.arraycopy(requestPacket, 8, dstIp, 0, 16)
        System.arraycopy(srcIp, 0, out, 8, 16)
        System.arraycopy(dstIp, 0, out, 24, 16)

        val udpSegment = ByteArray(udpLen)
        // Swap UDP ports from offset 40 of requestPacket
        udpSegment[0] = requestPacket[ipv6HeaderLen + 2]
        udpSegment[1] = requestPacket[ipv6HeaderLen + 3]
        udpSegment[2] = requestPacket[ipv6HeaderLen]
        udpSegment[3] = requestPacket[ipv6HeaderLen + 1]
        udpSegment[4] = ((udpLen ushr 8) and 0xFF).toByte()
        udpSegment[5] = (udpLen and 0xFF).toByte()
        udpSegment[6] = 0
        udpSegment[7] = 0
        System.arraycopy(dnsResponse, 0, udpSegment, udpHeaderLen, dnsResponseLength)

        val udpChecksum = calculateIpv6UdpChecksum(srcIp, dstIp, udpSegment, udpLen)
        udpSegment[6] = ((udpChecksum ushr 8) and 0xFF).toByte()
        udpSegment[7] = (udpChecksum and 0xFF).toByte()

        System.arraycopy(udpSegment, 0, out, ipv6HeaderLen, udpLen)
        return out
    }
}
