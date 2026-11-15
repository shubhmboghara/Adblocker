package com.example.adblocker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isProtectionOn: Boolean,
    blockedCount: Int,
    blocklistCount: Int,
    isDownloadingBlocklist: Boolean,
    crashReportText: String?,
    onToggleProtection: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissCrashReport: () -> Unit
) {
    val context = LocalContext.current
    val toggleCd = if (isProtectionOn) {
        stringResource(R.string.toggle_protection_cd_on)
    } else {
        stringResource(R.string.toggle_protection_cd_off)
    }
    val openLogCd = stringResource(R.string.open_log_cd)
    val openSettingsCd = stringResource(R.string.open_settings_cd)
    val copiedToastText = stringResource(R.string.crash_copied_toast)

    val buttonContainerColor by animateColorAsState(
        targetValue = if (isProtectionOn) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "toggle_bg_color"
    )

    val buttonContentColor by animateColorAsState(
        targetValue = if (isProtectionOn) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "toggle_fg_color"
    )

    val ringBorderColor by animateColorAsState(
        targetValue = if (isProtectionOn) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "toggle_ring_color"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isProtectionOn) 1.04f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "toggle_scale"
    )

    if (crashReportText != null) {
        AlertDialog(
            onDismissRequest = onDismissCrashReport,
            title = {
                Text(text = stringResource(R.string.crash_dialog_title))
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.crash_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = crashReportText.take(600),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AdBlocker Crash Report", crashReportText))
                        Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                        onDismissCrashReport()
                    },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("crash_copy_button")
                ) {
                    Text(text = stringResource(R.string.crash_copy_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissCrashReport,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("crash_dismiss_button")
                ) {
                    Text(text = stringResource(R.string.crash_dismiss_button))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("app_title_text")
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = openSettingsCd
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Large circular ON/OFF button in the center
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(208.dp)
                    .scale(buttonScale)
                    .clip(CircleShape)
                    .background(buttonContainerColor)
                    .border(width = 6.dp, color = ringBorderColor, shape = CircleShape)
                    .semantics {
                        contentDescription = toggleCd
                        role = Role.Switch
                    }
                    .clickable(onClick = onToggleProtection)
                    .testTag("toggle_protection_button")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = buttonContentColor,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isProtectionOn) {
                            stringResource(R.string.toggle_on_label)
                        } else {
                            stringResource(R.string.toggle_off_label)
                        },
                        style = MaterialTheme.typography.displayMedium,
                        color = buttonContentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Status text announced by TalkBack when protection changes between ON and OFF
            Text(
                text = if (isProtectionOn) {
                    stringResource(R.string.protection_on)
                } else {
                    stringResource(R.string.protection_off)
                },
                style = MaterialTheme.typography.headlineMedium,
                color = if (isProtectionOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("protection_status_text")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Blocked-requests counter under the status text
            Text(
                text = stringResource(R.string.blocked_requests_count, blockedCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("blocked_requests_text")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Blocklist count under the counter
            Text(
                text = stringResource(R.string.blocklist_domains_count, blocklistCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("blocklist_count_text")
            )

            if (isDownloadingBlocklist) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.blocklist_updating_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plain text note under the blocklist count
            Text(
                text = stringResource(R.string.private_dns_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag("private_dns_note_text")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Small "Log" button under the blocklist count
            FilledTonalButton(
                onClick = onOpenLog,
                modifier = Modifier
                    .sizeIn(minWidth = 96.dp, minHeight = 48.dp)
                    .semantics { contentDescription = openLogCd }
                    .testTag("open_log_button")
            ) {
                Text(text = stringResource(R.string.open_log_button))
            }
        }
    }
}
