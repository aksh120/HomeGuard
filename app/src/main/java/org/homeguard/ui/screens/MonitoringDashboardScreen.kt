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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.localizedSummaryText
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.components.CyberBadge
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar
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
    pendingSyncCount: Int,
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
            title = stringResource(R.string.dashboard_sentinel_audit),
            subtitle = stringResource(R.string.dashboard_live_stream),
            onBackClick = onBackClick
        )

        // Monitored Person Sentinel Dossier Card
        val personName = appConfig.personName.ifBlank {
            stringResource(R.string.dashboard_protected_person)
        }
        val personRole = appConfig.personRole.ifBlank {
            stringResource(R.string.dashboard_default_role)
        }

        CyberGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = personName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.dashboard_role, personRole),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                val (statusText, statusColor) = when (safetyStatus) {
                    SafetyStatus.SAFE -> stringResource(R.string.dashboard_safe) to StatusSafe
                    SafetyStatus.UNKNOWN -> stringResource(R.string.dashboard_pending) to StatusUnknown
                    SafetyStatus.EMERGENCY -> stringResource(R.string.dashboard_alert) to StatusEmergency
                }
                CyberBadge(text = statusText, color = statusColor)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Telemetry Array Cards
        Text(
            text = stringResource(R.string.dashboard_hardware_telemetry),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AuditTelemetryTile(
                title = stringResource(R.string.common_network),
                value = if (connectivityInfo.isInternetAvailable) {
                    stringResource(R.string.dashboard_online)
                } else {
                    stringResource(R.string.dashboard_offline)
                },
                sub = if (connectivityInfo.isWifiConnected) {
                    stringResource(R.string.wifi_connected)
                } else {
                    stringResource(R.string.dashboard_cellular)
                },
                color = if (connectivityInfo.isInternetAvailable) Color(0xFF38BDF8) else StatusUnknown,
                icon = Icons.Default.Wifi,
                modifier = Modifier.weight(1f)
            )

            AuditTelemetryTile(
                title = stringResource(R.string.common_battery),
                value = stringResource(R.string.battery_percent, batteryInfo.level),
                sub = if (batteryInfo.isCharging) {
                    stringResource(R.string.battery_charging)
                } else {
                    stringResource(R.string.battery_unplugged)
                },
                color = if (batteryInfo.level <= 20) StatusEmergency else StatusSafe,
                icon = Icons.Default.Bolt,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AuditTelemetryTile(
            title = stringResource(R.string.dashboard_last_checkin),
            value = lastCheckInFormatted,
            sub = stringResource(R.string.dashboard_local_log),
            color = Color(0xFF10B981),
            icon = Icons.Default.Sensors,
            modifier = Modifier.fillMaxWidth()
        )

        // Surfaces the offline queue so a user can see events that have not reached the backend.
        if (pendingSyncCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            AuditTelemetryTile(
                title = stringResource(R.string.dashboard_pending_sync),
                value = pendingSyncCount.toString(),
                sub = stringResource(R.string.dashboard_pending_sync_detail),
                color = StatusUnknown,
                icon = Icons.Default.CloudQueue,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Audit Events Timeline
        Text(
            text = stringResource(R.string.dashboard_audit_trail),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        if (recentEvents.isEmpty()) {
            CyberGlassCard {
                Text(
                    text = stringResource(R.string.dashboard_empty_events),
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            }
        } else {
            recentEvents.take(12).forEach { event ->
                CyberEventRow(event = event)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun AuditTelemetryTile(
    title: String,
    value: String,
    sub: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0B101B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF94A3B8)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Text(
                text = sub,
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun CyberEventRow(event: EventAuditEntity) {
    val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
    val formattedTime = sdf.format(Date(event.timestamp))

    val isAlert = event.eventType == "EMERGENCY_SOS" || event.eventType.contains("ALERT")
    val accentColor = if (isAlert) StatusEmergency else Color(0xFF10B981)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAlert) StatusEmergency.copy(alpha = 0.4f) else Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = event.localizedSummaryText(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlert) StatusEmergency else Color.White
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }
                if (!event.details.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.details,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}
