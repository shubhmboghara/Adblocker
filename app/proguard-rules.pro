# Add project specific ProGuard rules here.
# Keep Room database entities, DAOs, and RoomDatabase implementation
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.example.adblocker.WhitelistEntry { *; }
-keep class com.example.adblocker.BlockedLogEntry { *; }
-keep class com.example.adblocker.GroupedBlockedLog { *; }

# Keep VPN Service and BootReceiver registered in AndroidManifest.xml
-keep class com.example.adblocker.AdBlockVpnService { *; }
-keep class com.example.adblocker.BootReceiver { *; }
-keep class com.example.adblocker.BlocklistUpdateWorker { *; }
