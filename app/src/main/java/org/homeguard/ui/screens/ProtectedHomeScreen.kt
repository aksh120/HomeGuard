package org.homeguard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionPrimary
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.designsystem.theme.TorchActive
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.NavigationDestination
import org.homeguard.ui.components.CyberActionTile
import org.homeguard.ui.components.CyberAegisHeroOrb
import org.homeguard.ui.components.TactileHoldSosRadial
import org.homeguard.ui.components.TelemetryCockpitHUD

@Composable
fun ProtectedHomeScreen(
    safetyStatus: SafetyStatus,
    batteryInfo: BatteryInfo,
    connectivityInfo: ConnectivityInfo,
    appConfig: AppConfig,
    lastCheckInFormatted: String,
    lastMotionAgoFormatted: String,
    isAcousticListening: Boolean,
    isTorchOn: Boolean,
    communicationError: String?,
    fallDetectedAlert: Boolean,
    smokeAlarmDetectedAlert: Boolean,
    onCheckInClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    onCallPrimaryClick: () -> Unit,
    onFlashlightClick: () -> Unit,
    onPingBeaconClick: () -> Unit,
    onDismissFallAlert: () -> Unit,
    onDismissSmokeAlert: () -> Unit,
    onNavigate: (NavigationDestination) -> Unit,
    onDismissError: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF060911),
                        Color(0xFF090D16),
                        Color(0xFF0D1322)
                    )
                )
            )
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Futuristic Cyber-Aegis Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0B101D),
                    border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.a11y_app_logo),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.home_app_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusSafe)
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_sentinel_active),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPingBeaconClick) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = stringResource(R.string.a11y_ping_beacon),
                        tint = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = { onNavigate(NavigationDestination.Settings) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.a11y_settings),
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // HERO HUD: The Cyber-Aegis Pulse Orb
        CyberAegisHeroOrb(
            status = safetyStatus,
            onTapConfirmOk = onCheckInClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // HIGH-DENSITY TELEMETRY COCKPIT HUD
        TelemetryCockpitHUD(
            batteryLevel = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            wifiConnected = connectivityInfo.isWifiConnected,
            isAcousticListening = isAcousticListening,
            lastMotionAgoFormatted = lastMotionAgoFormatted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // TACTICAL HOLD-TO-CHARGE SOS RADIAL TRIGGER
        TactileHoldSosRadial(
            onEmergencyTriggered = onEmergencyClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        // TACTICAL HARDWARE CONTROLS GRID
        Text(
            text = stringResource(R.string.home_utility_cockpit),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = Color(0xFF64748B),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Flashlight Tactile Tile
            CyberActionTile(
                icon = Icons.Default.FlashOn,
                title = stringResource(R.string.home_torch),
                status = if (isTorchOn) stringResource(R.string.tile_active) else stringResource(R.string.tile_standby),
                accentColor = if (isTorchOn) TorchActive else Color(0xFF64748B),
                onClick = onFlashlightClick,
                modifier = Modifier.weight(1f)
            )

            // Primary Contact Call Tile
            CyberActionTile(
                icon = Icons.Default.Call,
                title = stringResource(R.string.action_call_primary),
                status = appConfig.primaryContact.name.ifBlank { stringResource(R.string.call_direct) },
                accentColor = ActionPrimary,
                onClick = onCallPrimaryClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Night Mode Tile
            CyberActionTile(
                icon = Icons.Default.Bedtime,
                title = stringResource(R.string.home_night_aegis),
                status = stringResource(R.string.tile_dark_hud),
                accentColor = Color(0xFF818CF8),
                onClick = { onNavigate(NavigationDestination.NightMode) },
                modifier = Modifier.weight(1f)
            )

            // Power Cut Defense Tile
            CyberActionTile(
                icon = Icons.Default.PowerOff,
                title = stringResource(R.string.home_power_outage),
                status = stringResource(R.string.tile_battery_guard),
                accentColor = Color(0xFFFBBF24),
                onClick = { onNavigate(NavigationDestination.PowerCut) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emergency Info Dossier Tile
            CyberActionTile(
                icon = Icons.Default.Info,
                title = stringResource(R.string.home_safety_dossier),
                status = stringResource(R.string.tile_offline_vault),
                accentColor = Color(0xFF2DD4BF),
                onClick = { onNavigate(NavigationDestination.EmergencyInfo) },
                modifier = Modifier.weight(1f)
            )

            // Fire Evacuation Guide Tile
            CyberActionTile(
                icon = Icons.Default.LocalFireDepartment,
                title = stringResource(R.string.home_fire_evacuation),
                status = stringResource(R.string.tile_offline_guide),
                accentColor = Color(0xFFF87171),
                onClick = { onNavigate(NavigationDestination.FireGuide) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Caregiver Radar Beacon Tile
            CyberActionTile(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.home_radar_beacon),
                status = stringResource(R.string.tile_ping_chirp),
                accentColor = Color(0xFF38BDF8),
                onClick = onPingBeaconClick,
                modifier = Modifier.weight(1f)
            )

            // Sentinel Audit Dashboard Tile
            CyberActionTile(
                icon = Icons.Default.Check,
                title = stringResource(R.string.home_sentinel_audit),
                status = stringResource(R.string.tile_event_stream),
                accentColor = Color(0xFF10B981),
                onClick = { onNavigate(NavigationDestination.MonitoringDashboard) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // AUTOMATED FALL IMPACT ALERT DIALOG
    if (fallDetectedAlert) {
        AlertDialog(
            onDismissRequest = onDismissFallAlert,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusEmergency)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.fall_alert_title), fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.fall_alert_body),
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissFallAlert()
                        onCheckInClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSafe)
                ) {
                    Text(text = stringResource(R.string.fall_alert_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onDismissFallAlert()
                        onEmergencyClick()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusEmergency)
                ) {
                    Text(text = stringResource(R.string.fall_alert_sos), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF140707)
        )
    }

    // AUTOMATED ACOUSTIC SMOKE ALARM ALERT DIALOG
    if (smokeAlarmDetectedAlert) {
        AlertDialog(
            onDismissRequest = onDismissSmokeAlert,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LocalFireDepartment, contentDescription = null, tint = StatusEmergency)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.smoke_alert_title), fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.smoke_alert_body),
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissSmokeAlert()
                        onEmergencyClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusEmergency)
                ) {
                    Text(text = stringResource(R.string.smoke_alert_sos), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissSmokeAlert) {
                    Text(text = stringResource(R.string.smoke_alert_false), color = TextPrimary)
                }
            },
            containerColor = Color(0xFF180A0A)
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
