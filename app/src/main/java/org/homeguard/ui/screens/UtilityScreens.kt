package org.homeguard.ui.screens

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.ui.components.CyberBadge
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

@Composable
fun NightModeScreen(
    batteryInfo: BatteryInfo,
    connectivityInfo: ConnectivityInfo,
    onEmergencyClick: () -> Unit,
    onExitNightMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030509))
            .statusBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimal Low-Luminance Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF818CF8))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.night_aegis_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.night_low_emission),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF818CF8)
                    )
                }
            }

            Surface(
                onClick = onExitNightMode,
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.a11y_exit_night_mode),
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.night_exit),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Low-Light Central Vigilance Visualizer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF090D18))
                    .border(1.5.dp, Color(0xFF818CF8).copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.night_vigilance_active),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.night_vigilance_desc),
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Telemetry Badges
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CyberBadge(
                    text = stringResource(R.string.battery_status_format, batteryInfo.level),
                    color = if (batteryInfo.level <= 20) StatusEmergency else StatusSafe
                )
                CyberBadge(
                    text = if (connectivityInfo.isWifiConnected) {
                        stringResource(R.string.wifi_connected)
                    } else {
                        stringResource(R.string.dashboard_offline)
                    },
                    color = if (connectivityInfo.isWifiConnected) Color(0xFF38BDF8) else StatusUnknown
                )
            }
        }

        // Direct SOS Trigger (Always Accessible)
        CyberButton(
            text = stringResource(R.string.night_trigger_sos),
            onClick = onEmergencyClick,
            accentColor = ActionEmergency,
            icon = Icons.Default.Warning,
            minHeight = 64
        )
    }
}

@Composable
fun PowerCutScreen(
    batteryInfo: BatteryInfo,
    isTorchOn: Boolean,
    appConfig: AppConfig,
    onToggleTorch: () -> Unit,
    onCallPrimary: () -> Unit,
    onBackClick: () -> Unit
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
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.power_outage_title),
            subtitle = stringResource(R.string.power_battery_guard),
            onBackClick = onBackClick
        )

        // Tactical Torch Card
        val torchBg = if (isTorchOn) Color(0xFFFBBF24).copy(alpha = 0.2f) else Color(0xFF0B101B)
        val torchBorder = if (isTorchOn) Color(0xFFFBBF24) else Color(0xFF1E293B)
        val torchText = if (isTorchOn) Color(0xFFFBBF24) else Color.White

        Surface(
            onClick = onToggleTorch,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = torchBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, torchBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.power_tactical_torch),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = torchText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isTorchOn) {
                            stringResource(R.string.torch_active_hint)
                        } else {
                            stringResource(R.string.torch_standby_hint)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTorchOn) Color(0xFFFBBF24) else Color(0xFF64748B)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isTorchOn) Color(0xFFFBBF24) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isTorchOn) Color.Black else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Power Telemetry Card
        CyberGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.power_battery_telemetry),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.power_reserved, batteryInfo.level),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (batteryInfo.level <= 20) StatusEmergency else Color.White
                    )
                }
                CyberBadge(
                    text = if (batteryInfo.isCharging) {
                        stringResource(R.string.battery_charging)
                    } else {
                        stringResource(R.string.power_on_battery)
                    },
                    color = if (batteryInfo.isCharging) StatusSafe else Color(0xFFF59E0B)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Call Primary Contact
        if (appConfig.primaryContact.name.isNotBlank()) {
            CyberButton(
                text = stringResource(R.string.power_call_contact, appConfig.primaryContact.name),
                onClick = onCallPrimary,
                accentColor = Color(0xFF38BDF8),
                icon = Icons.Default.Call,
                minHeight = 58
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Power Cut Protocol Card
        CyberGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.power_outage_protocol),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.power_outage_tips),
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
