package com.example.adblocker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    isVpnRunning: Boolean,
    activeUpstreamDns: String,
    blocklistCount: Int,
    whitelistSize: Int,
    blockedCount: Int,
    lastDnsLatencyMs: Long,
    droppedDohCount: Int,
    dnsTestResult: String?,
    blockTestResult: Boolean?,
    onRunDnsTest: () -> Unit,
    onRunBlockTest: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val backCd = stringResource(R.string.navigate_back_cd)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.diagnostics_screen_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DiagnosticRow(
                        label = stringResource(R.string.diag_vpn_running),
                        value = if (isVpnRunning) {
                            stringResource(R.string.diag_yes)
                        } else {
                            stringResource(R.string.diag_no)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_upstream_dns),
                        value = activeUpstreamDns
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_blocklist_count),
                        value = blocklistCount.toString()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_whitelist_size),
                        value = whitelistSize.toString()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_blocked_count),
                        value = blockedCount.toString()
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_last_latency),
                        value = if (lastDnsLatencyMs >= 0L) {
                            stringResource(R.string.diag_latency_ms, lastDnsLatencyMs)
                        } else {
                            stringResource(R.string.diag_latency_na)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DiagnosticRow(
                        label = stringResource(R.string.diag_dropped_doh),
                        value = droppedDohCount.toString()
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onRunDnsTest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("diag_test_dns_button")
                    ) {
                        Text(text = stringResource(R.string.diag_test_dns_button))
                    }

                    if (dnsTestResult != null) {
                        Text(
                            text = dnsTestResult,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("diag_dns_test_result")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    FilledTonalButton(
                        onClick = onRunBlockTest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("diag_test_block_button")
                    ) {
                        Text(text = stringResource(R.string.diag_test_block_button))
                    }

                    if (blockTestResult != null) {
                        Text(
                            text = if (blockTestResult) {
                                stringResource(R.string.diag_block_test_blocked)
                            } else {
                                stringResource(R.string.diag_block_test_allowed)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("diag_block_test_result")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
