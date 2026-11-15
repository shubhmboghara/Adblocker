# Changelog

## [1.0.0] - 2026-11-14

### Added
- **DNS-level blocking**: Local IPv4 and IPv6 UDP port 53 DNS interception via `VpnService`, returning `NXDOMAIN` for blocked domains and dropping TCP port 443 connections to known DoH resolvers.
- **Auto-updating blocklist**: Downloads the StevenBlack hosts list atomically via `HttpURLConnection` on first launch and refreshes every 7 days via `WorkManager` (`NetworkType.CONNECTED`, `requiresBatteryNotLow`), with a bundled fallback blocklist and compact sorted-array storage for large lists (>150,000 domains).
- **Whitelist**: Local Room `whitelist` table synchronized with an in-memory set supporting exact and parent-domain matching.
- **Blocked log**: Batched background logging (flushed every 2 seconds, capped at latest 500 rows) with a Compose Log screen showing repeat counts, timestamps, one-tap domain allowing, and log clearing.
- **Per-app bypass**: Searchable list of launcher apps in Settings using `<queries>` manifest declaration and `VpnService.Builder.addDisallowedApplication()`.
- **Custom upstream DNS**: Configurable upstream resolver supporting Google (`8.8.8.8`), Cloudflare (`1.1.1.1`), Quad9 (`9.9.9.9`), and validated custom IPv4 addresses.
- **Custom blocklist**: Support for merging an additional HTTPS hosts file URL during blocklist refreshes.
- **Boot persistence**: Persistent state in Preferences DataStore and `BootReceiver` (`BOOT_COMPLETED`) to automatically resume foreground VPN protection after device restart.
