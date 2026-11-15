package com.example.adblocker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AdBlockerSettings,
    installedApps: List<InstalledAppInfo>,
    onSelectUpstreamDns: (UpstreamDnsOption, String) -> Unit,
    onSaveCustomBlocklist: (String) -> Unit,
    onToggleAppBypass: (String, Boolean) -> Unit,
    onResetDisclosure: () -> Unit,
    onClearAllData: () -> Unit,
    onOpenAbout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var customIpInput by rememberSaveable(settings.customDnsIp) {
        mutableStateOf(settings.customDnsIp)
    }
    var customIpError by rememberSaveable { mutableStateOf(false) }

    var customUrlInput by rememberSaveable(settings.customBlocklistUrl) {
        mutableStateOf(settings.customBlocklistUrl)
    }
    var customUrlError by rememberSaveable { mutableStateOf(false) }

    var appSearchQuery by rememberSaveable { mutableStateOf("") }

    val filteredApps = remember(installedApps, appSearchQuery) {
        val q = appSearchQuery.trim().lowercase()
        if (q.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    val backCd = stringResource(R.string.navigate_back_cd)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_screen_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Upstream DNS Section
            item(key = "upstream_dns_section") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.upstream_dns_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val dnsOptions = listOf(
                            UpstreamDnsOption.GOOGLE to stringResource(R.string.dns_google_label),
                            UpstreamDnsOption.CLOUDFLARE to stringResource(R.string.dns_cloudflare_label),
                            UpstreamDnsOption.QUAD9 to stringResource(R.string.dns_quad9_label),
                            UpstreamDnsOption.CUSTOM to stringResource(R.string.dns_custom_label)
                        )

                        dnsOptions.forEach { (option, label) ->
                            val selected = settings.upstreamOption == option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sizeIn(minHeight = 48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            if (option != UpstreamDnsOption.CUSTOM) {
                                                customIpError = false
                                                onSelectUpstreamDns(option, customIpInput)
                                            } else {
                                                if (VpnStateStore.isValidIpv4(customIpInput)) {
                                                    customIpError = false
                                                    onSelectUpstreamDns(UpstreamDnsOption.CUSTOM, customIpInput)
                                                } else {
                                                    onSelectUpstreamDns(UpstreamDnsOption.CUSTOM, Config.DEFAULT_UPSTREAM_DNS)
                                                }
                                            }
                                        }
                                    )
                                    .testTag("dns_radio_${option.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customIpInput,
                            onValueChange = {
                                customIpInput = it
                                customIpError = false
                            },
                            label = { Text(text = stringResource(R.string.dns_custom_label)) },
                            placeholder = { Text(text = stringResource(R.string.custom_dns_placeholder)) },
                            isError = customIpError,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_dns_input")
                        )
                        if (customIpError) {
                            Text(
                                text = stringResource(R.string.custom_dns_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (VpnStateStore.isValidIpv4(customIpInput)) {
                                    customIpError = false
                                    onSelectUpstreamDns(UpstreamDnsOption.CUSTOM, customIpInput)
                                } else {
                                    customIpError = true
                                }
                            },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("apply_custom_dns_button")
                        ) {
                            Text(text = stringResource(R.string.apply_custom_dns_button))
                        }
                    }
                }
            }

            // 2. Custom Blocklist URL Section
            item(key = "custom_blocklist_section") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.custom_blocklist_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = {
                                customUrlInput = it
                                customUrlError = false
                            },
                            placeholder = { Text(text = stringResource(R.string.custom_blocklist_placeholder)) },
                            isError = customUrlError,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_blocklist_input")
                        )
                        if (customUrlError) {
                            Text(
                                text = stringResource(R.string.custom_blocklist_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (VpnStateStore.isValidHttpsUrl(customUrlInput)) {
                                    customUrlError = false
                                    onSaveCustomBlocklist(customUrlInput)
                                } else {
                                    customUrlError = true
                                }
                            },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("save_custom_blocklist_button")
                        ) {
                            Text(text = stringResource(R.string.save_refresh_blocklist_button))
                        }
                    }
                }
            }

            // 3. Actions: About, Reset Disclosure, Clear All Data
            item(key = "settings_actions_section") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenAbout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("open_about_button")
                        ) {
                            Text(text = stringResource(R.string.open_about_button))
                        }

                        OutlinedButton(
                            onClick = onResetDisclosure,
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("reset_disclosure_button")
                        ) {
                            Text(text = stringResource(R.string.reset_disclosure_button))
                        }

                        OutlinedButton(
                            onClick = onClearAllData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("clear_all_data_button")
                        ) {
                            Text(
                                text = stringResource(R.string.clear_all_data_button),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 4. Per-App Bypass Header & Search
            item(key = "per_app_bypass_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.per_app_bypass_section_title,
                            settings.bypassedPackages.size
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text(text = stringResource(R.string.search_apps_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_apps_input")
                    )
                }
            }

            items(
                items = filteredApps,
                key = { "app_${it.packageName}" }
            ) { appInfo ->
                val isChecked = settings.bypassedPackages.contains(appInfo.packageName)
                val bypassCd = stringResource(R.string.bypass_app_cd, appInfo.label)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onToggleAppBypass(appInfo.packageName, !isChecked)
                        }
                        .semantics { contentDescription = bypassCd }
                        .testTag("bypass_row_${appInfo.packageName}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appInfo.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = appInfo.packageName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                onToggleAppBypass(appInfo.packageName, checked)
                            }
                        )
                    }
                }
            }
        }
    }
}
