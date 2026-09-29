package org.homeguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionImOk
import org.homeguard.core.designsystem.theme.ActionPrimary
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.designsystem.theme.TorchActive
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.NavigationDestination
import org.homeguard.ui.components.LargeActionButton
import org.homeguard.ui.components.SafetyStatusBanner
import org.homeguard.ui.components.TelemetryRow

@Composable
fun ProtectedHomeScreen(
    safetyStatus: SafetyStatus,
    batteryInfo: BatteryInfo,
    connectivityInfo: ConnectivityInfo,
    appConfig: AppConfig,
    lastCheckInFormatted: String,
    isTorchOn: Boolean,
    communicationError: String?,
    onCheckInClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    onCallPrimaryClick: () -> Unit,
    onFlashlightClick: () -> Unit,
    onNavigate: (NavigationDestination) -> Unit,
    onDismissError: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showCheckInConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF080C14),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "HomeGuard Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            if (appConfig.deviceMode == DeviceMode.MONITORING_DEVICE) {
                OutlinedButton(
                    onClick = { onNavigate(NavigationDestination.MonitoringDashboard) }
                ) {
                    Text(text = "Dashboard", color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Safety Status Banner
        SafetyStatusBanner(status = safetyStatus)

        Spacer(modifier = Modifier.height(14.dp))

        // Telemetry Row
        TelemetryRow(
            wifiConnected = connectivityInfo.isWifiConnected,
            batteryLevel = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            isMonitoringActive = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.last_checkin_format, lastCheckInFormatted),
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PRIMARY ACTION 1: I'M OK
        LargeActionButton(
            text = stringResource(R.string.action_im_ok),
            onClick = { showCheckInConfirmDialog = true },
            backgroundColor = ActionImOk,
            borderColor = ActionImOk,
            textColor = Color.White,
            icon = Icons.Default.Check,
            minHeight = 72
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PRIMARY ACTION 2: EMERGENCY (High tactile prominence)
        LargeActionButton(
            text = stringResource(R.string.action_emergency),
            onClick = onEmergencyClick,
            backgroundColor = ActionEmergency,
            borderColor = ActionEmergency,
            textColor = Color.White,
            icon = Icons.Default.Warning,
            minHeight = 84
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PRIMARY ACTION 3: CALL PRIMARY CONTACT
        LargeActionButton(
            text = stringResource(R.string.action_call_primary),
            onClick = onCallPrimaryClick,
            backgroundColor = DarkSurface,
            borderColor = BorderSubtle,
            textColor = TextPrimary,
            icon = Icons.Default.Call,
            minHeight = 64
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PRIMARY ACTION 4: FLASHLIGHT
        LargeActionButton(
            text = if (isTorchOn) stringResource(R.string.torch_on) else stringResource(R.string.action_flashlight),
            onClick = onFlashlightClick,
            backgroundColor = if (isTorchOn) TorchActive else DarkSurface,
            borderColor = if (isTorchOn) TorchActive else BorderSubtle,
            textColor = if (isTorchOn) DarkBackground else TextPrimary,
            icon = Icons.Default.FlashOn,
            minHeight = 64
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECONDARY UTILITIES GRID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UtilityCard(
                text = stringResource(R.string.action_night_mode),
                icon = Icons.Default.Bedtime,
                onClick = { onNavigate(NavigationDestination.NightMode) },
                modifier = Modifier.weight(1f)
            )
            UtilityCard(
                text = stringResource(R.string.action_power_cut),
                icon = Icons.Default.PowerOff,
                onClick = { onNavigate(NavigationDestination.PowerCut) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UtilityCard(
                text = stringResource(R.string.action_emergency_info),
                icon = Icons.Default.Info,
                onClick = { onNavigate(NavigationDestination.EmergencyInfo) },
                modifier = Modifier.weight(1f)
            )
            UtilityCard(
                text = stringResource(R.string.action_settings),
                icon = Icons.Default.Settings,
                onClick = { onNavigate(NavigationDestination.Settings) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    // Check-In Confirmation Dialog
    if (showCheckInConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCheckInConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.checkin_dialog_title),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.checkin_dialog_message),
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCheckInConfirmDialog = false
                        onCheckInClick()
                    }
                ) {
                    Text(text = stringResource(R.string.action_im_ok))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showCheckInConfirmDialog = false
                        onEmergencyClick()
                    }
                ) {
                    Text(text = stringResource(R.string.action_i_need_help), color = ActionEmergency)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Communication Error Dialog
    if (communicationError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = {
                Text(
                    text = stringResource(R.string.call_failed_title),
                    fontWeight = FontWeight.Bold,
                    color = ActionEmergency
                )
            },
            text = {
                Text(
                    text = communicationError,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(onClick = onDismissError) {
                    Text(text = "OK")
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun UtilityCard(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ActionPrimary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = text,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
