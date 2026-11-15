package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.adblocker.AdBlockRepository
import com.example.adblocker.BlocklistManager
import com.example.adblocker.Config
import com.example.adblocker.VpnStateStore
import com.example.adblocker.VpnStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig
import java.io.File

@RunWith(RobolectricTestRunner::class)
@RoboConfig(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun appName_isAdBlocker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AdShield", appName)
    }

    @Test
    fun freshInstall_startsWithDisclosureUnaccepted_andProtectionOff() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        VpnStateStore.clearAll(context)

        val settings = VpnStateStore.getSettings(context)
        assertFalse(settings.disclosureAccepted)
        assertFalse(settings.protectionEnabled)
    }

    @Test
    fun toggleState_survivesProcessRecreationInDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        VpnStateStore.clearAll(context)

        VpnStateStore.setDisclosureDecision(context, accepted = true)
        VpnStateStore.setProtectionEnabled(context, enabled = true)

        val reloadedSettings = VpnStateStore.getSettings(context)
        assertTrue(reloadedSettings.disclosureAccepted)
        assertTrue(reloadedSettings.protectionEnabled)
    }

    @Test
    fun clearAllData_resetsDataStoreRoomAndBlocklistFile_andReturnsToDisclosureState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AdBlockRepository.getInstance(context)

        VpnStateStore.setDisclosureDecision(context, accepted = true)
        VpnStateStore.setProtectionEnabled(context, enabled = true)
        repository.addToWhitelist("allowed.example.com")

        val localBlocklist = File(context.filesDir, Config.LOCAL_BLOCKLIST_FILE)
        localBlocklist.writeText("0.0.0.0 custom.ad.example.com\n")
        BlocklistManager.loadBlocklist(context)
        assertTrue(BlocklistManager.isBlocked("custom.ad.example.com"))

        // Perform Clear all data steps
        VpnStateStore.clearAll(context)
        repository.clearAllTables()
        if (localBlocklist.exists()) {
            localBlocklist.delete()
        }
        BlocklistManager.loadBlocklist(context)
        VpnStats.resetStats()

        val afterClear = VpnStateStore.getSettings(context)
        assertFalse(afterClear.disclosureAccepted)
        assertFalse(afterClear.protectionEnabled)
        assertFalse(localBlocklist.exists())
        assertTrue(BlocklistManager.currentSize > 100)
    }
}
