package org.homeguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.ui.screens.EmergencyActiveScreen
import org.homeguard.ui.screens.EmergencyCountdownScreen
import org.homeguard.ui.screens.EmergencyInfoScreen
import org.homeguard.ui.screens.FireGuideScreen
import org.homeguard.ui.screens.MonitoringDashboardScreen
import org.homeguard.ui.screens.NightModeScreen
import org.homeguard.ui.screens.PowerCutScreen
import org.homeguard.ui.screens.ProtectedHomeScreen
import org.homeguard.ui.screens.SetupAndSettingsScreen

@Composable
fun HomeGuardApp(
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    val currentDestination by viewModel.currentDestination.collectAsState()
    val safetyStatus by viewModel.safetyStatus.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val connectivityInfo by viewModel.connectivityInfo.collectAsState()
    val appConfig by viewModel.appConfig.collectAsState()
    val lastCheckInFormatted by viewModel.lastCheckInFormatted.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val communicationError by viewModel.communicationError.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()

    // Handle system back navigation safely
    BackHandler(enabled = currentDestination != NavigationDestination.Home) {
        when (currentDestination) {
            NavigationDestination.EmergencyCountdown -> viewModel.cancelEmergencyCountdown()
            NavigationDestination.EmergencyActive -> viewModel.stopAlarmAndResolve()
            NavigationDestination.Setup -> {
                // If initial setup is incomplete, stay on setup
                if (appConfig.isSetupComplete) {
                    viewModel.navigateTo(NavigationDestination.Home)
                }
            }
            else -> viewModel.navigateTo(NavigationDestination.Home)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        when (currentDestination) {
            NavigationDestination.Home -> {
                ProtectedHomeScreen(
                    safetyStatus = safetyStatus,
                    batteryInfo = batteryInfo,
                    connectivityInfo = connectivityInfo,
                    appConfig = appConfig,
                    lastCheckInFormatted = lastCheckInFormatted,
                    isTorchOn = isTorchOn,
                    communicationError = communicationError,
                    onCheckInClick = { viewModel.confirmCheckIn() },
                    onEmergencyClick = { viewModel.startEmergencyCountdown() },
                    onCallPrimaryClick = { viewModel.initiateCallToPrimary(context) },
                    onFlashlightClick = { viewModel.toggleFlashlight() },
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
                    isTorchOn = isTorchOn,
                    appConfig = appConfig,
                    onToggleTorch = { viewModel.toggleFlashlight() },
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
        }
    }
}
