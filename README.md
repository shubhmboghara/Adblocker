<!--
Pre-Release Manual Test Checklist:
1. ON/OFF toggle: Tap the main circular button to turn Protection ON (accept VPN consent if prompted) and verify notification appears and status updates to "Protection ON"; tap again to turn Protection OFF and verify notification disappears.
2. Reboot persistence: With Protection ON, reboot the device and verify BootReceiver automatically restarts the foreground VPN service and displays the "Protection ON" notification.
3. Private DNS off and on: Test domain blocking with Android system Private DNS turned off (queries intercepted) and verify behavior when Private DNS is enabled.
4. Chrome secure DNS on and off: Test domain blocking in Chrome with "Use secure DNS" turned off and verify DoH fallback behavior when Chrome secure DNS is enabled.
5. Whitelist an app (Per-app bypass): In Settings -> Per-app bypass, check an installed browser or app, verify VPN restarts, and confirm that app bypasses VPN filtering while other apps remain filtered.
6. Change the upstream DNS: Switch between Google (8.8.8.8), Cloudflare (1.1.1.1), Quad9 (9.9.9.9), and a valid Custom IPv4 resolver in Settings and verify allowed domains resolve properly.
7. Airplane mode on and off with protection ON: Enable Airplane mode while Protection is ON, then disable Airplane mode and switch between Wi-Fi and mobile data to confirm NetworkCallback updates underlying networks and DNS resolution resumes seamlessly.
-->

# AdBlocker for Android

A local, privacy-first DNS ad blocker for Android built with Kotlin and Jetpack Compose.

## Build Instructions

To build a signed release App Bundle:
```bash
./gradlew bundleRelease
```
