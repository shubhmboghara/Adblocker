# Privacy Policy for AdBlocker

**Effective Date:** November 14, 2026

AdBlocker is a local DNS filter for Android designed to block advertising and tracking domains directly on your device without collecting or transmitting any personal or browsing data.

## 1. How VpnService Is Used
- AdBlocker uses the Android `VpnService` API exclusively to establish a local virtual network interface (`tun`) on your device that intercepts DNS queries (UDP port 53) and blocks known DNS-over-HTTPS (DoH) bypass endpoints.
- All DNS filtering and domain matching against the blocklist and your personal whitelist happen **100% locally on your device**.
- AdBlocker does **not** route your general web traffic through any remote VPN server.

## 2. No Data Collection or Transmission
- **No domain names, DNS queries, browsing history, IP addresses, or personal identifiers ever leave your device** to any server operated by AdBlocker or its developers.
- The app contains no analytics SDKs, crash telemetry reporters, or advertising trackers.

## 3. Local Blocked-Domain Log & Whitelist
- Blocked domain queries are recorded in a local SQLite (Room) database on your device solely so you can inspect recently blocked domains and whitelist domains you wish to allow.
- The local log automatically retains at most the latest 500 entries and **can be cleared at any time** using the **Clear log** button on the Log screen or by uninstalling the app.

## 4. Network Requests Made by the App
AdBlocker makes only two types of outbound network connections:
1. **Blocklist Updates:** Periodic HTTPS downloads of the public hosts blocklist (by default from `https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts`, plus any custom `https://` hosts URL you explicitly configure in Settings).
2. **Upstream DNS Resolution:** Allowed (non-blocked) DNS queries are forwarded directly from your device to your chosen upstream DNS resolver (`8.8.8.8` Google, `1.1.1.1` Cloudflare, `9.9.9.9` Quad9, or your custom IPv4 DNS server).
