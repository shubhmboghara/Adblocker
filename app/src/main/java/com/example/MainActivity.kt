package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.adblocker.AboutScreen
import com.example.adblocker.AdBlockViewModel
import com.example.adblocker.DiagnosticsScreen
import com.example.adblocker.DisclosureScreen
import com.example.adblocker.LogScreen
import com.example.adblocker.MainScreen
import com.example.adblocker.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

object Routes {
    const val DISCLOSURE = "disclosure"
    const val MAIN = "main"
    const val LOG = "log"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DIAGNOSTICS = "diagnostics"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AdBlockerAppNavigation()
            }
        }
    }
}

@Composable
fun AdBlockerAppNavigation(
    adBlockViewModel: AdBlockViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val settings by adBlockViewModel.settingsState.collectAsStateWithLifecycle()
    val isProtectionEnabled by adBlockViewModel.isProtectionEnabled.collectAsStateWithLifecycle()
    val blockedCount by adBlockViewModel.blockedCount.collectAsStateWithLifecycle()
    val blocklistCount by adBlockViewModel.blocklistCount.collectAsStateWithLifecycle()
    val sourceStatuses by adBlockViewModel.sourceStatuses.collectAsStateWithLifecycle()
    val whitelistSize by adBlockViewModel.whitelistSize.collectAsStateWithLifecycle()
    val lastDnsLatencyMs by adBlockViewModel.lastDnsLatencyMs.collectAsStateWithLifecycle()
    val droppedDohCount by adBlockViewModel.droppedDohCount.collectAsStateWithLifecycle()
    val isDownloadingBlocklist by adBlockViewModel.isDownloadingBlocklist.collectAsStateWithLifecycle()
    val whitelistEntries by adBlockViewModel.whitelistEntries.collectAsStateWithLifecycle()
    val groupedBlockedLogs by adBlockViewModel.groupedBlockedLogs.collectAsStateWithLifecycle()
    val installedApps by adBlockViewModel.installedApps.collectAsStateWithLifecycle()
    val crashReportText by adBlockViewModel.crashReportText.collectAsStateWithLifecycle()
    val diagDnsTestResult by adBlockViewModel.diagDnsTestResult.collectAsStateWithLifecycle()
    val diagBlockTestResult by adBlockViewModel.diagBlockTestResult.collectAsStateWithLifecycle()

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            adBlockViewModel.startProtectionAfterConsent()
        } else {
            adBlockViewModel.onVpnConsentDenied()
        }
    }

    val requestVpnPrepareAndStart = {
        if (settings.disclosureAccepted) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnConsentLauncher.launch(prepareIntent)
            } else {
                adBlockViewModel.startProtectionAfterConsent()
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        requestVpnPrepareAndStart()
    }

    val startDestination = if (settings.disclosureAccepted) {
        Routes.MAIN
    } else {
        Routes.DISCLOSURE
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.DISCLOSURE) {
            DisclosureScreen(
                disclosureDeclined = settings.disclosureDeclined,
                onAgree = {
                    adBlockViewModel.acceptDisclosure {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.DISCLOSURE) { inclusive = true }
                        }
                    }
                },
                onDecline = {
                    adBlockViewModel.declineDisclosure()
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                isProtectionOn = isProtectionEnabled || settings.protectionEnabled,
                blockedCount = blockedCount,
                blocklistCount = blocklistCount,
                isDownloadingBlocklist = isDownloadingBlocklist,
                crashReportText = crashReportText,
                onToggleProtection = {
                    val currentlyOn = isProtectionEnabled || settings.protectionEnabled
                    if (currentlyOn) {
                        adBlockViewModel.stopProtection()
                    } else if (settings.disclosureAccepted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                requestVpnPrepareAndStart()
                            }
                        } else {
                            requestVpnPrepareAndStart()
                        }
                    }
                },
                onOpenLog = {
                    navController.navigate(Routes.LOG)
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onDismissCrashReport = {
                    adBlockViewModel.dismissCrashReport()
                }
            )
        }

        composable(Routes.LOG) {
            LogScreen(
                groupedLogs = groupedBlockedLogs,
                whitelistEntries = whitelistEntries,
                onAllowDomain = adBlockViewModel::allowDomain,
                onRemoveWhitelistDomain = adBlockViewModel::removeWhitelistedDomain,
                onClearLog = adBlockViewModel::clearLogs,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                sourceStatuses = sourceStatuses,
                isDownloadingBlocklist = isDownloadingBlocklist,
                installedApps = installedApps,
                onToggleBlocklistSource = adBlockViewModel::toggleBlocklistSource,
                onRefreshNow = adBlockViewModel::refreshAllBlocklistsNow,
                onSelectUpstreamDns = adBlockViewModel::updateUpstreamDns,
                onSaveCustomBlocklist = adBlockViewModel::saveAndRefreshCustomBlocklist,
                onToggleAppBypass = adBlockViewModel::toggleAppBypass,
                onResetDisclosure = {
                    adBlockViewModel.resetDisclosure {
                        navController.navigate(Routes.DISCLOSURE) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onClearAllData = {
                    adBlockViewModel.clearAllData {
                        navController.navigate(Routes.DISCLOSURE) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onOpenAbout = {
                    navController.navigate(Routes.ABOUT)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(
                onOpenDiagnostics = {
                    if (BuildConfig.DEBUG) {
                        navController.navigate(Routes.DIAGNOSTICS)
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DIAGNOSTICS) {
            val runningText = context.getString(R.string.diag_testing_status)
            DiagnosticsScreen(
                isVpnRunning = isProtectionEnabled,
                activeUpstreamDns = settings.activeUpstreamDns,
                blocklistCount = blocklistCount,
                whitelistSize = whitelistSize,
                blockedCount = blockedCount,
                lastDnsLatencyMs = lastDnsLatencyMs,
                droppedDohCount = droppedDohCount,
                dnsTestResult = diagDnsTestResult,
                blockTestResult = diagBlockTestResult,
                onRunDnsTest = {
                    adBlockViewModel.runDiagnosticsDnsTest(
                        formatSuccess = { ms -> context.getString(R.string.diag_dns_test_success, ms) },
                        formatFailure = { err -> context.getString(R.string.diag_dns_test_failure, err) },
                        runningText = runningText
                    )
                },
                onRunBlockTest = {
                    adBlockViewModel.runDiagnosticsBlockTest()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
