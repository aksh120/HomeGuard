package org.homeguard.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.localizedSummaryText
import org.homeguard.core.designsystem.theme.ActionEmergency
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

/**
 * Activity log (section 27).
 *
 * Important limitation, stated plainly in the subtitle: this renders what *this* phone has
 * recorded. A caregiver watching a different phone is a separate feature and is not implemented;
 * claiming otherwise would be misleading in a safety context.
 */
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
    val notSetUp = !appConfig.isSetupComplete

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.dashboard_sentinel_audit),
            subtitle = stringResource(R.string.dashboard_live_stream),
            onBackClick = onBackClick
        )

        if (notSetUp) {
            Spacer(modifier = Modifier.height(8.dp))
            CyberGlassCard {
                Text(
                    text = stringResource(R.string.dashboard_not_set_up),
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val person = appConfig.protectedPerson
            CyberGlassCard {
                Text(
                    text = person.name.ifBlank {
                        stringResource(R.string.dashboard_protected_person)
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (person.relationship.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = person.relationship,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                val (label, color) = when (safetyStatus) {
                    SafetyStatus.SAFE -> stringResource(R.string.status_safe) to StatusSafe
                    SafetyStatus.UNKNOWN -> stringResource(R.string.status_unknown) to StatusUnknown
                    SafetyStatus.EMERGENCY -> stringResource(R.string.status_emergency) to StatusEmergency
                }
                CyberBadge(text = label, color = color)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.dashboard_hardware_telemetry),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            CyberGlassCard {
                TileRow(
                    icon = Icons.Default.Bolt,
                    label = stringResource(R.string.common_battery),
                    value = stringResource(R.string.battery_status_format, batteryInfo.level),
                    sub = if (batteryInfo.isCharging) {
                        stringResource(R.string.battery_charging)
                    } else {
                        stringResource(R.string.battery_not_charging)
                    },
                    valueColor = if (batteryInfo.level <= 20) StatusEmergency else null
                )
                TileRow(
                    icon = if (connectivityInfo.isInternetAvailable) {
                        Icons.Default.Wifi
                    } else {
                        Icons.Default.WifiOff
                    },
                    label = stringResource(R.string.common_network),
                    value = if (connectivityInfo.isInternetAvailable) {
                        stringResource(R.string.dashboard_online)
                    } else {
                        stringResource(R.string.dashboard_offline)
                    },
                    sub = null,
                    valueColor = if (connectivityInfo.isInternetAvailable) null else StatusUnknown
                )
                TileRow(
                    icon = Icons.Default.Sensors,
                    label = stringResource(R.string.dashboard_last_checkin),
                    value = lastCheckInFormatted,
                    sub = null,
                    valueColor = null
                )
            }

            if (pendingSyncCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                CyberGlassCard(borderColor = StatusUnknown) {
                    Text(
                        text = stringResource(R.string.dashboard_pending_sync),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_pending_sync_detail),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sync_pending_count, pendingSyncCount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusUnknown
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.dashboard_audit_trail),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (recentEvents.isEmpty()) {
            CyberGlassCard {
                Text(
                    text = stringResource(R.string.dashboard_empty_events),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            CyberGlassCard {
                recentEvents.take(12).forEach { event ->
                    EventRow(event)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TileRow(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String?,
    valueColor: Color?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EventRow(event: EventAuditEntity) {
    val isAlert = event.summary.contains("Emergency", ignoreCase = true)
    val time = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(event.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.localizedSummaryText(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (isAlert) ActionEmergency else MaterialTheme.colorScheme.onSurface,
                lineHeight = 23.sp
            )
            if (!event.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.details,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = time,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}