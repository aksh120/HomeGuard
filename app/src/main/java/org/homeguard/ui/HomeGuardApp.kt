package org.homeguard.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.homeguard.HomeGuardApplication
import org.homeguard.R
import org.homeguard.core.model.AppProfile
import org.homeguard.ui.screens.AddPersonDialog
import org.homeguard.ui.screens.CaregiverDashboardScreen
import org.homeguard.ui.screens.EmergencyActiveScreen
import org.homeguard.ui.screens.EmergencyCountdownScreen
import org.homeguard.ui.screens.EmergencyInfoScreen
import org.homeguard.ui.screens.FireGuideScreen
import org.homeguard.ui.screens.MonitoringDashboardScreen
import org.homeguard.ui.screens.MonitorJoinScreen
import org.homeguard.ui.screens.NightModeScreen
import org.homeguard.ui.screens.NotificationPermissionGate
import org.homeguard.ui.screens.PowerCutScreen
import org.homeguard.ui.screens.ProtectedHomeScreen
import org.homeguard.ui.screens.RoleSelectionScreen
import org.homeguard.ui.screens.SetupAndSettingsScreen

@Composable
fun HomeGuardApp(
    viewModel: HomeViewModel = viewModel(),
    onRequestTorchPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? androidx.activity.ComponentActivity

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
    val households by viewModel.households.collectAsState()
    val watchedPeople by viewModel.watchedPeople.collectAsState()
    val householdMembers by viewModel.householdMembers.collectAsState()

    // Approval requests (Phase 3)
    val approvals by viewModel.approvals.collectAsState()
    val watchers by viewModel.watchers.collectAsState()
    val approvalError by viewModel.approvalError.collectAsState()
    val remoteEmergencies by viewModel.remoteEmergencies.collectAsState()
    val remoteCheckIns by viewModel.remoteCheckIns.collectAsState()
    val isRefreshingMonitoring by viewModel.isRefreshingMonitoring.collectAsState()
    val caregiverLoadState by viewModel.caregiverLoadState.collectAsState()
    val lastMonitoringRefresh by viewModel.lastMonitoringRefresh.collectAsState()
    val householdError by viewModel.householdError.collectAsState()

    // Caregiver-raised alarm (Phase 5)
    val alarmRaisedFor by viewModel.alarmRaisedFor.collectAsState()
    val alarmFailure by viewModel.alarmFailure.collectAsState()

    // Code-gated requests (Phase 3 enforcement)
    val myRequests by viewModel.myRequests.collectAsState()
    val approvalOutcome by viewModel.approvalOutcome.collectAsState()
    val modeSwitchRequested by viewModel.modeSwitchRequested.collectAsState()
    val modeSwitchError by viewModel.modeSwitchError.collectAsState()
    val pendingJoinNotice by viewModel.pendingJoinNotice.collectAsState()

    val profile = appConfig.profile
    val isMonitoringDevice = profile == AppProfile.MONITORING

    // Phase 2: adding another person is one invite code. Kept as local state rather than a
    // destination because it is a dialog over the dashboard, not a place the caregiver can land.
    var showAddPersonDialog by remember { mutableStateOf(false) }

    // D9: push silently does nothing without this permission, so the caregiver profile is gated on
    // it. Re-read on every recomposition, which covers returning from Android settings.
    val notificationsAllowed = remember(Unit) {
        HomeGuardApplication.hasNotificationPermission(context)
    }

    // True once the user has refused POST_NOTIFICATIONS at least once, which means the runtime
    // request will no longer show and only the settings screen can change it.
    val shouldShowRequestPermissionRationale = remember(Unit) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    }

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
        if (households.isEmpty()) {
            MonitorJoinScreen(
                isJoining = isRefreshingMonitoring,
                errorMessage = householdError,
                requestedHouseholdName = pendingJoinNotice?.householdName?.takeIf { it.isNotBlank() },
                approvalsRequired = pendingJoinNotice?.approvalsRequired ?: 0,
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
                    // Named households, never the invite code, which is a credential rather than a
                    // label. Falls back to a localised default when a house has no name yet.
                    householdName = households.firstOrNull { it.hasName }?.name
                        ?: stringResource(R.string.caregiver_default_household),
                    households = households,
                    people = watchedPeople,
                    members = householdMembers,
                    approvals = approvals,
                    approvalError = approvalError,
                    isSubmittingApproval = isRefreshingMonitoring,
                    emergencyEvents = remoteEmergencies,
                    checkIns = remoteCheckIns,
                    isRefreshing = isRefreshingMonitoring,
                    loadState = caregiverLoadState,
                    lastRefreshedAt = lastMonitoringRefresh,
                    onRefresh = {
                        viewModel.refreshMonitoring()
                        // A caregiver is also a *target* of requests: a resident can ask them to agree
                        // to that phone changing role, so they need codes to display, not only enter.
                        viewModel.refreshApprovals()
                        viewModel.refreshMyRequests()
                    },
                    onAddPerson = { showAddPersonDialog = true },
                    alarmRaisedFor = alarmRaisedFor,
                    alarmFailure = alarmFailure,
                    myRequests = myRequests,
                    approvalOutcome = approvalOutcome,
                    onRaiseAlarm = viewModel::raiseAlarmFor,
                    onStopWatching = viewModel::forgetHousehold,
                    onDeclineApproval = viewModel::cancelApproval,
                    onSubmitApprovalCode = viewModel::submitApprovalCode,
                    onDismissApprovalError = viewModel::dismissApprovalError
                )
            }
        }

        if (showAddPersonDialog) {
            AddPersonDialog(
                isJoining = isRefreshingMonitoring,
                errorMessage = householdError,
                onJoin = viewModel::joinHousehold,
                onDismissError = viewModel::dismissHouseholdError,
                onDismiss = {
                    showAddPersonDialog = false
                    viewModel.dismissHouseholdError()
                }
            )
        }

        // Only for the caregiver profile. The resident's own alerts are local notifications they
        // trigger on their own screen, so a permission gate here would be solving a problem they
        // do not have.
        if (!notificationsAllowed) {
            NotificationPermissionGate(
                permanentlyDenied = shouldShowRequestPermissionRationale,
                onRequestPermission = onRequestNotificationPermission,
                onOpenSettings = onOpenNotificationSettings
            )
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
                    approvals = approvals,
                    watchers = watchers,
                    modeSwitchRequested = modeSwitchRequested,
                    modeSwitchError = modeSwitchError,
                    onDeclineApproval = viewModel::cancelApproval,
                    onRequestModeSwitch = viewModel::requestModeSwitch,
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