package org.homeguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.homeguard.core.model.AppProfile
import org.homeguard.ui.screens.CaregiverDashboardScreen
import org.homeguard.ui.screens.EmergencyActiveScreen
import org.homeguard.ui.screens.EmergencyCountdownScreen
import org.homeguard.ui.screens.EmergencyInfoScreen
import org.homeguard.ui.screens.FireGuideScreen
import org.homeguard.ui.screens.MonitoringDashboardScreen
import org.homeguard.ui.screens.MonitorJoinScreen
import org.homeguard.ui.screens.NightModeScreen
import org.homeguard.ui.screens.PowerCutScreen
import org.homeguard.ui.screens.ProtectedHomeScreen
import org.homeguard.ui.screens.RoleSelectionScreen
import org.homeguard.ui.screens.SetupAndSettingsScreen

@Composable
fun HomeGuardApp(
    viewModel: HomeViewModel = viewModel(),
    onRequestTorchPermission: () -> Unit = {}
) {
    val context = LocalContext.current

    val currentDestination by viewModel.currentDestination.collectAsState()
    val safetyStatus by viewModel.safetyStatus.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val connectivityInfo by viewModel.connectivityInfo.collectAsState()
    val appConfig by viewModel.appConfig.collectAsState()
    val lastCheckInFormatted by viewModel.lastCheckInFormatted.collectAsState()
    val lastMotionAgoFormatted by viewModel.lastMotionAgoFormatted.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val communicationError by viewModel.communicationError.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()
    val fallDetectedAlert by viewModel.fallDetectedAlert.collectAsState()
    val checkInPromptVisible by viewModel.checkInPromptVisible.collectAsState()
    val safetyNoticeVisible by viewModel.safetyNoticeVisible.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val syncHealth by viewModel.syncHealth.collectAsState()

    // Monitoring state (section 6B)
    val householdInviteCode by viewModel.householdInviteCode.collectAsState()
    val monitoredDevice by viewModel.monitoredDevice.collectAsState()
    val householdMembers by viewModel.householdMembers.collectAsState()
    val remoteEmergencies by viewModel.remoteEmergencies.collectAsState()
    val remoteCheckIns by viewModel.remoteCheckIns.collectAsState()
    val isRefreshingMonitoring by viewModel.isRefreshingMonitoring.collectAsState()
    val caregiverLoadState by viewModel.caregiverLoadState.collectAsState()
    val lastMonitoringRefresh by viewModel.lastMonitoringRefresh.collectAsState()
    val householdError by viewModel.householdError.collectAsState()

    val profile = appConfig.profile
    val isMonitoringDevice = profile == AppProfile.MONITORING

    // Torch needs a runtime CAMERA grant; ask only when the user actually toggles it.
    val onFlashlightClick = {
        if (torchPermissionGranted(context)) {
            viewModel.toggleFlashlight()
        } else {
            onRequestTorchPermission()
        }
    }

    BackHandler(enabled = currentDestination != NavigationDestination.Home) {
        when (currentDestination) {
            NavigationDestination.EmergencyCountdown -> viewModel.cancelEmergencyCountdown()
            NavigationDestination.EmergencyActive -> viewModel.stopAlarmAndResolve()
            // Nothing lies behind the first-run fork, so there is nowhere to go back to.
            NavigationDestination.ProfileSelection -> Unit
            NavigationDestination.Setup -> {
                // Do not leave an incomplete setup; there is nothing behind it.
                if (appConfig.isSetupComplete) {
                    viewModel.navigateTo(NavigationDestination.Home)
                }
            }
            else -> viewModel.navigateTo(NavigationDestination.Home)
        }
    }

    // Nothing is reachable until a profile is chosen. This is the first fork of the two experiences.
    if (profile == null) {
        RoleSelectionScreen(onSelectProfile = viewModel::selectProfile)
        return
    }

    // A monitoring device never shows the protected-device home. It has no SOS, no check-in and no
    // flashlight to offer, so presenting that screen would be misleading in both directions.
    if (isMonitoringDevice) {
        // The join screen is shown whenever there is no household yet, regardless of destination.
        // It used to require destination == Home, which made it unreachable: a fresh install opens
        // on ProfileSelection or Setup, so the condition never held and the caregiver was dropped
        // straight onto an empty dashboard.
        if (householdInviteCode == null) {
            MonitorJoinScreen(
                isJoining = isRefreshingMonitoring,
                errorMessage = householdError,
                onJoin = viewModel::joinHousehold,
                onDismissError = viewModel::dismissHouseholdError,
                onSwitchToProtected = { viewModel.switchToProtectedProfile() }
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                CaregiverDashboardScreen(
                    householdName = householdInviteCode.orEmpty(),
                    device = monitoredDevice,
                    members = householdMembers,
                    emergencyEvents = remoteEmergencies,
                    checkIns = remoteCheckIns,
                    isRefreshing = isRefreshingMonitoring,
                    loadState = caregiverLoadState,
                    lastRefreshedAt = lastMonitoringRefresh,
                    onRefresh = viewModel::refreshMonitoring
                )
            }
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentDestination) {
            NavigationDestination.Home -> {
                ProtectedHomeScreen(
                    safetyStatus = safetyStatus,
                    batteryInfo = batteryInfo,
                    connectivityInfo = connectivityInfo,
                    appConfig = appConfig,
                    lastCheckInFormatted = lastCheckInFormatted,
                    lastMotionAgoFormatted = lastMotionAgoFormatted,
                    syncHealth = syncHealth,
                    pendingSyncCount = pendingSyncCount,
                    communicationError = communicationError,
                    fallDetectedAlert = fallDetectedAlert,
                    checkInPromptVisible = checkInPromptVisible,
                    safetyNoticeVisible = safetyNoticeVisible,
                    onCheckInClick = { viewModel.confirmCheckIn() },
                    onEmergencyClick = { viewModel.startEmergencyCountdown() },
                    onCallPrimaryClick = { viewModel.initiateCallToPrimary(context) },
                    onFlashlightClick = onFlashlightClick,
                    onPingBeaconClick = { viewModel.pingCaregiverBeacon() },
                    onDismissFallAlert = { viewModel.dismissFallAlert() },
                    onCheckInPromptDismiss = { viewModel.dismissCheckInPrompt() },
                    onCheckInPromptHelp = { viewModel.requestHelpFromPrompt() },
                    onSafetyNoticeDismiss = { viewModel.dismissSafetyNotice() },
                    onNavigate = { dest -> viewModel.navigateTo(dest) },
                    onDismissError = { viewModel.dismissCommunicationError() }
                )
            }

            NavigationDestination.EmergencyCountdown -> {
                EmergencyCountdownScreen(
                    countdownSeconds = countdownSeconds,
                    onCancelClick = { viewModel.cancelEmergencyCountdown() }
                )
            }

            NavigationDestination.EmergencyActive -> {
                EmergencyActiveScreen(
                    isInternetAvailable = connectivityInfo.isInternetAvailable,
                    pendingSyncCount = pendingSyncCount,
                    onStopAlarmClick = { viewModel.stopAlarmAndResolve() }
                )
            }

            NavigationDestination.NightMode -> {
                NightModeScreen(
                    batteryInfo = batteryInfo,
                    connectivityInfo = connectivityInfo,
                    onEmergencyClick = { viewModel.startEmergencyCountdown() },
                    onExitNightMode = { viewModel.toggleNightMode() }
                )
            }

            NavigationDestination.PowerCut -> {
                PowerCutScreen(
                    batteryInfo = batteryInfo,
                    isTorchOn = viewModel.isTorchOn.collectAsState().value,
                    appConfig = appConfig,
                    onToggleTorch = onFlashlightClick,
                    onCallPrimary = { viewModel.initiateCallToPrimary(context) },
                    onBackClick = { viewModel.navigateTo(NavigationDestination.Home) }
                )
            }

            NavigationDestination.EmergencyInfo -> {
                EmergencyInfoScreen(
                    appConfig = appConfig,
                    onOpenFireGuide = { viewModel.navigateTo(NavigationDestination.FireGuide) },
                    onBackClick = { viewModel.navigateTo(NavigationDestination.Home) }
                )
            }

            NavigationDestination.FireGuide -> {
                FireGuideScreen(
                    localEmergencyNumber = appConfig.localEmergencyNumber,
                    onBackClick = { viewModel.navigateTo(NavigationDestination.EmergencyInfo) }
                )
            }

            NavigationDestination.MonitoringDashboard -> {
                MonitoringDashboardScreen(
                    safetyStatus = safetyStatus,
                    batteryInfo = batteryInfo,
                    connectivityInfo = connectivityInfo,
                    lastCheckInFormatted = lastCheckInFormatted,
                    appConfig = appConfig,
                    recentEvents = recentEvents,
                    pendingSyncCount = pendingSyncCount,
                    onBackClick = { viewModel.navigateTo(NavigationDestination.Home) }
                )
            }

            NavigationDestination.Settings -> {
                SetupAndSettingsScreen(
                    currentConfig = appConfig,
                    isInitialSetup = false,
                    onSaveConfig = { updated -> viewModel.saveConfig(updated) },
                    onBackClick = { viewModel.navigateTo(NavigationDestination.Home) }
                )
            }

            NavigationDestination.Setup -> {
                SetupAndSettingsScreen(
                    currentConfig = appConfig,
                    isInitialSetup = true,
                    onSaveConfig = { updated -> viewModel.saveConfig(updated) },
                    onBackClick = { viewModel.navigateTo(NavigationDestination.Home) }
                )
            }

            NavigationDestination.ProfileSelection -> {
                RoleSelectionScreen(onSelectProfile = viewModel::selectProfile)
            }
        }
    }
}