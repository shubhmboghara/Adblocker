package com.example.adblocker

object Config {
    const val VPN_SESSION_NAME = "AdBlocker"

    // Local VPN TUN IPv4 configuration
    const val VPN_IPV4_ADDRESS = "10.0.0.2"
    const val VPN_IPV4_PREFIX = 32
    const val VPN_IPV4_DNS = "10.0.0.1"
    const val VPN_IPV4_ROUTE_PREFIX = 32

    // Local VPN TUN IPv6 configuration
    const val VPN_IPV6_ADDRESS = "fd00::2"
    const val VPN_IPV6_PREFIX = 128
    const val VPN_IPV6_DNS = "fd00::1"
    const val VPN_IPV6_ROUTE_PREFIX = 128

    // Well-known Upstream DNS & DoH Provider IPv4 addresses
    const val DNS_GOOGLE = "8.8.8.8"
    const val DNS_GOOGLE_SECONDARY = "8.8.4.4"
    const val DNS_CLOUDFLARE = "1.1.1.1"
    const val DNS_CLOUDFLARE_SECONDARY = "1.0.0.1"
    const val DNS_QUAD9 = "9.9.9.9"
    const val DNS_QUAD9_SECONDARY = "149.112.112.112"
    const val DNS_ADGUARD_PRIMARY = "94.140.14.14"
    const val DNS_ADGUARD_SECONDARY = "94.140.15.15"

    const val DEFAULT_UPSTREAM_DNS = DNS_GOOGLE

    // Ports & Timeouts
    const val DNS_PORT = 53
    const val HTTPS_PORT = 443
    const val UPSTREAM_DNS_TIMEOUT_MS = 3_000
    const val HTTP_CONNECT_TIMEOUT_MS = 10_000
    const val HTTP_READ_TIMEOUT_MS = 30_000

    // Blocklist & Persistence configuration
    const val LISTS_DIR_NAME = "lists"
    const val CUSTOM_SOURCE_ID = "custom"
    const val ASSET_BLOCKLIST_FILE = "blocklist.txt"
    const val LOCAL_BLOCKLIST_FILE = "blocklist.txt"
    const val LOCAL_BLOCKLIST_TEMP_FILE = "blocklist.txt.tmp"
    const val CRASH_REPORT_FILE = "last_crash.txt"

    const val MAX_BLOCKED_LOG_ROWS = 500
    const val LOG_BATCH_INTERVAL_MS = 2_000L
    const val WORK_REPEAT_DAYS = 7L
    const val BLOCKLIST_WORK_NAME = "adblocker_periodic_blocklist_refresh"

    // Privacy policy & Diagnostics test domains
    const val PRIVACY_POLICY_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/readme.md"
    const val TEST_ALLOWED_DOMAIN = "example.com"
    const val TEST_BLOCKED_DOMAIN = "doubleclick.net"

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "adblocker_vpn_channel"
    const val NOTIFICATION_ID = 1001
}
