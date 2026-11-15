package com.example.adblocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    groupedLogs: List<GroupedBlockedLog>,
    whitelistEntries: List<WhitelistEntry>,
    onAllowDomain: (String) -> Unit,
    onRemoveWhitelistDomain: (String) -> Unit,
    onClearLog: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val whitelistedDomainSet = remember(whitelistEntries) {
        whitelistEntries.mapTo(HashSet()) { it.domain }
    }
    val timeFormatter = remember {
        SimpleDateFormat("HH:mm:ss • MMM d", Locale.getDefault())
    }
    val backCd = stringResource(R.string.navigate_back_cd)
    val clearLogCd = stringResource(R.string.clear_log_cd)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("log_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.log_screen_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("log_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backCd
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onClearLog,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = clearLogCd }
                            .testTag("clear_log_button")
                    ) {
                        Text(text = stringResource(R.string.clear_log_button))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (groupedLogs.isEmpty() && whitelistEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 420.dp)
                ) {
                    Text(
                        text = stringResource(R.string.log_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.log_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = groupedLogs,
                    key = { "log_${it.domain}" }
                ) { entry ->
                    val isAlreadyAllowed = whitelistedDomainSet.contains(entry.domain)
                    val allowCd = stringResource(R.string.allow_domain_cd, entry.domain)
                    val formattedTime = remember(entry.latestTimestamp) {
                        timeFormatter.format(Date(entry.latestTimestamp))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("log_row_${entry.domain}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = entry.domain,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$formattedTime • ${
                                        stringResource(
                                            R.string.log_repeat_count,
                                            entry.repeatCount
                                        )
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isAlreadyAllowed) {
                                OutlinedButton(
                                    onClick = { onRemoveWhitelistDomain(entry.domain) },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("allowed_badge_${entry.domain}")
                                ) {
                                    Text(text = stringResource(R.string.allowed_domain_badge))
                                }
                            } else {
                                Button(
                                    onClick = { onAllowDomain(entry.domain) },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .semantics { contentDescription = allowCd }
                                        .testTag("allow_button_${entry.domain}")
                                ) {
                                    Text(text = stringResource(R.string.allow_domain_button))
                                }
                            }
                        }
                    }
                }

                if (whitelistEntries.isNotEmpty()) {
                    item(key = "whitelist_header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.whitelisted_section_title,
                                whitelistEntries.size
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(
                        items = whitelistEntries,
                        key = { "wl_${it.domain}" }
                    ) { wl ->
                        val removeCd = stringResource(R.string.remove_whitelist_cd, wl.domain)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = wl.domain,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { onRemoveWhitelistDomain(wl.domain) },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .semantics { contentDescription = removeCd }
                                ) {
                                    Text(text = stringResource(R.string.remove_whitelist_button))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
