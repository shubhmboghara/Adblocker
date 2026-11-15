package com.example.adblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = VpnStateStore.getSettings(context)
                if (settings.disclosureAccepted && settings.protectionEnabled) {
                    val prepareIntent = VpnService.prepare(context)
                    if (prepareIntent == null) {
                        val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
                            action = AdBlockVpnService.ACTION_START
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
