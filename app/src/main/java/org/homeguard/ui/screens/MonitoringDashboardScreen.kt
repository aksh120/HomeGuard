package org.homeguard.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.components.SafetyStatusBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitoringDashboardScreen(
    safetyStatus: SafetyStatus,
    batteryInfo: BatteryInfo,
    connectivityInfo: ConnectivityInfo,
    lastCheckInFormatted: String,
    appConfig: AppConfig,
    recentEvents: List<EventAuditEntity>,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monitored Person Header
        val personName = appConfig.personName.ifBlank { "Protected Person" }
        val personRole = appConfig.personRole.ifBlank { "Home" }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = personName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Role: $personRole",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety Status Banner
        SafetyStatusBanner(status = safetyStatus)

        Spacer(modifier = Modifier.height(16.dp))

        // Device Telemetry Cards Grid
        TelemetryCard(
            title = stringResource(R.string.dashboard_device_state),
            value = if (connectivityInfo.isInternetAvailable) stringResource(R.string.dashboard_online) else stringResource(R.string.dashboard_offline),
            color = if (connectivityInfo.isInternetAvailable) StatusSafe else StatusUnknown
        )

        Spacer(modifier = Modifier.height(10.dp))

        TelemetryCard(
            title = "Last Safety Check-in",
            value = lastCheckInFormatted,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        TelemetryCard(
            title = "Battery Telemetry",
            value = "${batteryInfo.level}% (${if (batteryInfo.isCharging) "Charging" else "Unplugged"})",
            color = if (batteryInfo.level <= 20) StatusEmergency else TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        TelemetryCard(
            title = "Network Connectivity",
            value = if (connectivityInfo.isWifiConnected) "Wi-Fi Connected" else "Wi-Fi Disconnected",
            color = if (connectivityInfo.isWifiConnected) StatusSafe else StatusUnknown
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Audit Events
        Text(
            text = stringResource(R.string.dashboard_recent_events),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (recentEvents.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = DarkSurface
            ) {
                Text(
                    text = stringResource(R.string.dashboard_no_events),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            recentEvents.take(10).forEach { event ->
                EventAuditRow(event = event)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun TelemetryCard(title: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 15.sp, color = TextSecondary)
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun EventAuditRow(event: EventAuditEntity) {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = sdf.format(Date(event.timestamp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedTime,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.width(68.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.summary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (event.eventType == "EMERGENCY_SOS") StatusEmergency else TextPrimary
                )
                if (!event.details.isNullOrBlank()) {
                    Text(
                        text = event.details,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
