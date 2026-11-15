# Google Play Store Listing — AdBlocker

## Short Description (73 characters)
Local DNS filter using Android VpnService. Private, on-device protection.

## Full Description
AdBlocker is a lightweight, transparent DNS filter for Android that helps block known advertising and tracking domains using a local on-device blocklist.

### How It Works
AdBlocker uses the Android `VpnService` API to create a local virtual network interface on your device. This local interface intercepts outbound DNS requests (UDP port 53) and checks the queried domain name against an on-device hosts blocklist and your personal whitelist:
- If a domain matches the blocklist (and is not whitelisted), AdBlocker immediately returns an `NXDOMAIN` response locally on your device.
- If a domain is allowed, the DNS request is forwarded directly from your device to your selected upstream DNS resolver (Google, Cloudflare, Quad9, or a custom IPv4 DNS server).
- All DNS filtering happens locally on your device. Your general internet traffic is not routed through any remote VPN server.

### Important Scope & Limitations
- AdBlocker operates strictly at the DNS level. It does not inspect, decrypt, or modify HTTPS web page content.
- Because DNS filtering only blocks domain resolution, AdBlocker does **not** block all advertisements and cannot block ads served from the same domain as primary content (such as YouTube video ads or first-party sponsored posts).

### Key Features
- **On-Device DNS Filtering:** Supports both IPv4 and IPv6 DNS interception with DNS-over-HTTPS (DoH) resolver bypass protection.
- **Auto-Updating Blocklist:** Periodically downloads the community StevenBlack hosts list over HTTPS and supports adding a custom HTTPS hosts URL.
- **Custom Whitelist & Local Log:** Inspect recently blocked domains in a local-only log (capped at 500 entries) and allow specific domains with one tap.
- **Per-App Bypass:** Choose specific installed apps to bypass the local DNS VPN interface.
- **Configurable Upstream DNS:** Choose between Google, Cloudflare, Quad9, or your own custom IPv4 resolver.

### Privacy Commitment
AdBlocker collects no personal data, browsing history, or DNS queries. No telemetry or analytics are included, and no data is ever transmitted to the developer or third parties.

---

## Permissions & Justifications

- `android.permission.BIND_VPN_SERVICE`: Required by the Android OS so the system can bind to `AdBlockVpnService` and establish the local DNS-filtering `tun` interface.
- `android.permission.INTERNET`: Required to forward allowed DNS queries to your chosen upstream DNS resolver and to download hosts blocklist updates over HTTPS.
- `android.permission.FOREGROUND_SERVICE`: Required to keep the local DNS VPN service running continuously with a visible status notification while protection is active.
- `android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED`: Required on Android 14+ to run the user-initiated `VpnService` in the foreground.
- `android.permission.POST_NOTIFICATIONS`: Required on Android 13+ to display the ongoing "Protection ON" notification with a quick "Turn off" action.
- `android.permission.RECEIVE_BOOT_COMPLETED`: Required so `BootReceiver` can automatically resume DNS protection after a device reboot if protection was left ON.

---

## Google Play Data Safety Form Answers

- **Does your app collect or share any of the required user data types?**
  - **No.** AdBlocker does not collect or share any user data.
- **Is any user data sent off the device?**
  - **No.** All DNS filtering, whitelist matching, and crash report generation happen strictly on the device. The only outbound network traffic consists of downloading public hosts files over HTTPS and forwarding allowed DNS queries directly to the user's chosen DNS resolver.
- **Why is the blocked-domain log considered local-only?**
  - The blocked-domain log is stored exclusively in an internal SQLite (Room) database inside the app's private storage directory (`filesDir` / database storage), is automatically trimmed to the most recent 500 rows, is never transmitted over the network, and can be cleared at any time via the "Clear log" or "Clear all data" buttons.
