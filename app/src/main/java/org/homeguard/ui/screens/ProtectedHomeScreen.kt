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
                        contentDescription = "HomeGuard Sentinel",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "HOMEGUARD",
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
                        text = "SENTINEL ACTIVE • HOME",
                        fontSize = 11.sp,
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
                        contentDescription = "Ping Beacon",
                        tint = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = { onNavigate(NavigationDestination.Settings) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
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
            text = "TACTICAL UTILITY COCKPIT",
            fontSize = 11.sp,
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
                title = "FLASHLIGHT",
                status = if (isTorchOn) "ACTIVE" else "STANDBY",
                accentColor = if (isTorchOn) TorchActive else Color(0xFF64748B),
                onClick = onFlashlightClick,
                modifier = Modifier.weight(1f)
            )

            // Primary Contact Call Tile
            CyberActionTile(
                icon = Icons.Default.Call,
                title = "CALL PRIMARY",
                status = appConfig.primaryContact.name.ifBlank { "DIRECT" },
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
                title = "NIGHT AEGIS",
                status = "DARK HUD",
                accentColor = Color(0xFF818CF8),
                onClick = { onNavigate(NavigationDestination.NightMode) },
                modifier = Modifier.weight(1f)
            )

            // Power Cut Defense Tile
            CyberActionTile(
                icon = Icons.Default.PowerOff,
                title = "POWER OUTAGE",
                status = "BATTERY GUARD",
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
                title = "SAFETY DOSSIER",
                status = "OFFLINE VAULT",
                accentColor = Color(0xFF2DD4BF),
                onClick = { onNavigate(NavigationDestination.EmergencyInfo) },
                modifier = Modifier.weight(1f)
            )

            // Fire Evacuation Guide Tile
            CyberActionTile(
                icon = Icons.Default.LocalFireDepartment,
                title = "FIRE EVACUATION",
                status = "OFFLINE GUIDE",
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
                title = "RADAR BEACON",
                status = "PING CHIRP",
                accentColor = Color(0xFF38BDF8),
                onClick = onPingBeaconClick,
                modifier = Modifier.weight(1f)
            )

            // Sentinel Audit Dashboard Tile
            CyberActionTile(
                icon = Icons.Default.Check,
                title = "SENTINEL AUDIT",
                status = "EVENT STREAM",
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
                    Text(text = "Sudden Fall Impact Detected", fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            text = {
                Text(
                    text = "A sudden impact and stillness were detected on this device. Are you okay or do you need assistance?",
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
                    Text(text = "I'M OK", fontWeight = FontWeight.Bold)
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
                    Text(text = "SEND EMERGENCY SOS", fontWeight = FontWeight.Bold)
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
                    Text(text = "Smoke Alarm Pattern Detected", fontWeight = FontWeight.Black, color = Color.White)
                }
            },
            text = {
                Text(
                    text = "The acoustic listener detected a high-pitch smoke alarm pattern (T3/T4) nearby. Please verify your safety immediately.",
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
                    Text(text = "TRIGGER SOS ALARM", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissSmokeAlert) {
                    Text(text = "I'M SAFE / FALSE ALARM", color = TextPrimary)
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

@Composable
private fun CyberActionTile(
    icon: ImageVector,
    title: String,
    status: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
