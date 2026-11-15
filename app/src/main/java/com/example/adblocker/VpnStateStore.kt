package com.example.adblocker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "adblocker_prefs")

enum class UpstreamDnsOption(val defaultIp: String) {
    GOOGLE(Config.DNS_GOOGLE),
    CLOUDFLARE(Config.DNS_CLOUDFLARE),
    QUAD9(Config.DNS_QUAD9),
    CUSTOM("")
}

data class AdBlockerSettings(
    val protectionEnabled: Boolean = false,
    val disclosureAccepted: Boolean = false,
    val disclosureDeclined: Boolean = false,
    val upstreamOption: UpstreamDnsOption = UpstreamDnsOption.GOOGLE,
    val customDnsIp: String = "",
    val customBlocklistUrl: String = "",
    val enabledSourceIds: Set<String> = BlocklistSources.DEFAULT_ENABLED_IDS,
    val bypassedPackages: Set<String> = emptySet()
) {
    val activeUpstreamDns: String
        get() = when (upstreamOption) {
            UpstreamDnsOption.GOOGLE -> Config.DNS_GOOGLE
            UpstreamDnsOption.CLOUDFLARE -> Config.DNS_CLOUDFLARE
            UpstreamDnsOption.QUAD9 -> Config.DNS_QUAD9
            UpstreamDnsOption.CUSTOM -> customDnsIp.ifBlank { Config.DEFAULT_UPSTREAM_DNS }
        }
}

object VpnStateStore {
    private val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
    private val KEY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
    private val KEY_DISCLOSURE_DECLINED = booleanPreferencesKey("disclosure_declined")
    private val KEY_UPSTREAM_OPTION = stringPreferencesKey("upstream_option")
    private val KEY_CUSTOM_DNS_IP = stringPreferencesKey("custom_dns_ip")
    private val KEY_CUSTOM_BLOCKLIST_URL = stringPreferencesKey("custom_blocklist_url")
    private val KEY_ENABLED_SOURCE_IDS = stringSetPreferencesKey("enabled_source_ids")
    private val KEY_BYPASSED_PACKAGES = stringSetPreferencesKey("bypassed_packages")

    fun settingsFlow(context: Context): Flow<AdBlockerSettings> {
        return context.dataStore.data.map { prefs ->
            val optionName = prefs[KEY_UPSTREAM_OPTION] ?: UpstreamDnsOption.GOOGLE.name
            val option = runCatching { UpstreamDnsOption.valueOf(optionName) }
                .getOrDefault(UpstreamDnsOption.GOOGLE)
            AdBlockerSettings(
                protectionEnabled = prefs[KEY_PROTECTION_ENABLED] ?: false,
                disclosureAccepted = prefs[KEY_DISCLOSURE_ACCEPTED] ?: false,
                disclosureDeclined = prefs[KEY_DISCLOSURE_DECLINED] ?: false,
                upstreamOption = option,
                customDnsIp = prefs[KEY_CUSTOM_DNS_IP] ?: "",
                customBlocklistUrl = prefs[KEY_CUSTOM_BLOCKLIST_URL] ?: "",
                enabledSourceIds = prefs[KEY_ENABLED_SOURCE_IDS] ?: BlocklistSources.DEFAULT_ENABLED_IDS,
                bypassedPackages = prefs[KEY_BYPASSED_PACKAGES] ?: emptySet()
            )
        }
    }

    suspend fun getSettings(context: Context): AdBlockerSettings {
        return settingsFlow(context).first()
    }

    suspend fun isProtectionEnabled(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_PROTECTION_ENABLED] ?: false }.first()
    }

    suspend fun isDisclosureAccepted(context: Context): Boolean {
        return context.dataStore.data.map { it[KEY_DISCLOSURE_ACCEPTED] ?: false }.first()
    }

    suspend fun getCustomBlocklistUrl(context: Context): String {
        return context.dataStore.data.map { it[KEY_CUSTOM_BLOCKLIST_URL] ?: "" }.first()
    }

    suspend fun getEnabledSourceIds(context: Context): Set<String> {
        return context.dataStore.data.map {
            it[KEY_ENABLED_SOURCE_IDS] ?: BlocklistSources.DEFAULT_ENABLED_IDS
        }.first()
    }

    suspend fun setProtectionEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROTECTION_ENABLED] = enabled
        }
        VpnStats.setProtectionEnabled(enabled)
    }

    suspend fun setDisclosureDecision(context: Context, accepted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISCLOSURE_ACCEPTED] = accepted
            prefs[KEY_DISCLOSURE_DECLINED] = !accepted
            if (!accepted) {
                prefs[KEY_PROTECTION_ENABLED] = false
            }
        }
        if (!accepted) {
            VpnStats.setProtectionEnabled(false)
        }
    }

    suspend fun resetDisclosure(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISCLOSURE_ACCEPTED] = false
            prefs[KEY_DISCLOSURE_DECLINED] = false
            prefs[KEY_PROTECTION_ENABLED] = false
        }
        VpnStats.setProtectionEnabled(false)
    }

    suspend fun setUpstreamDns(context: Context, option: UpstreamDnsOption, customIp: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPSTREAM_OPTION] = option.name
            if (option == UpstreamDnsOption.CUSTOM) {
                prefs[KEY_CUSTOM_DNS_IP] = customIp.trim()
            }
        }
    }

    suspend fun setCustomBlocklistUrl(context: Context, url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BLOCKLIST_URL] = url.trim()
        }
    }

    suspend fun setSourceEnabled(context: Context, sourceId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_ENABLED_SOURCE_IDS] ?: BlocklistSources.DEFAULT_ENABLED_IDS).toMutableSet()
            if (enabled) {
                current.add(sourceId)
            } else {
                current.remove(sourceId)
            }
            prefs[KEY_ENABLED_SOURCE_IDS] = current
        }
    }

    suspend fun toggleBypassedPackage(context: Context, packageName: String, bypassed: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_BYPASSED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (bypassed) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            prefs[KEY_BYPASSED_PACKAGES] = current
        }
    }

    suspend fun clearAll(context: Context) {
        context.dataStore.edit { it.clear() }
        VpnStats.setProtectionEnabled(false)
    }

    fun isValidIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        val parts = trimmed.split('.')
        if (parts.size != 4) return false
        for (part in parts) {
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return false
            val num = part.toIntOrNull() ?: return false
            if (num !in 0..255) return false
        }
        return true
    }

    fun isValidHttpsUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isEmpty() || (trimmed.startsWith("https://", ignoreCase = true) && trimmed.length > 10)
    }
}
