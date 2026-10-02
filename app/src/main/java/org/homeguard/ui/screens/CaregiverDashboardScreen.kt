package org.homeguard.ui.screens

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionWarning
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.model.HouseholdMember
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.RemoteCheckIn
import org.homeguard.core.model.RemoteEmergencyEvent
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.components.CyberBadge
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Caregiver dashboard (sections 6B and 27).
 *
 * Deliberately a different screen from the protected-device home, not a variant of it. A caregiver
 * cannot press "I'M OK", cannot trigger an SOS, and has no flashlight to control. They read, they
 * refresh, and they leave. Every action here is observational.
 */
@Composable
fun CaregiverDashboardScreen(
    householdName: String,
    device: MonitoredDevice?,
    members: List<HouseholdMember>,
    emergencyEvents: List<RemoteEmergencyEvent>,
    checkIns: List<RemoteCheckIn>,
    isRefreshing: Boolean,
    loadState: CaregiverLoadState,
    lastRefreshedAt: Long?,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.caregiver_title),
            subtitle = householdName.ifBlank { stringResource(R.string.caregiver_default_household) },
            onBackClick = null
        )

        // Unresolved emergency takes the top slot. A caregiver scanning this screen should not
        // have to read past telemetry to see it.
        val openEmergency = emergencyEvents.firstOrNull { !it.isResolved }
        if (openEmergency != null) {
            EmergencyBanner(openEmergency)
            Spacer(modifier = Modifier.height(16.dp))
        }

        when {
            isRefreshing && device == null -> {
                Text(
                    text = stringResource(R.string.caregiver_loading),
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            loadState is CaregiverLoadState.Error && device == null -> {
                CyberGlassCard(borderColor = StatusEmergency) {
                    Text(
                        text = stringResource(R.string.caregiver_load_failed),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = loadState.message,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 21.sp
                    )
                }
            }

            device == null -> {
                CyberGlassCard {
                    Text(
                        text = stringResource(R.string.caregiver_no_device_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.caregiver_no_device_body),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 23.sp
                    )
                }
            }

            else -> {
                DeviceStatusCard(device)
                Spacer(modifier = Modifier.height(16.dp))
                TelemetryCard(device, checkIns)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        CyberButton(
            text = stringResource(R.string.caregiver_refresh),
            onClick = onRefresh,
            accentColor = MaterialTheme.colorScheme.surfaceVariant,
            isSecondary = true,
            icon = Icons.Default.Refresh
        )

        if (lastRefreshedAt != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.caregiver_last_refreshed,
                    formatRemoteTime(lastRefreshedAt)
                ),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section 27: recent events, emergencies first.
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.caregiver_recent_activity),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (emergencyEvents.isEmpty() && checkIns.isEmpty()) {
            CyberGlassCard {
                Text(
                    text = stringResource(R.string.caregiver_no_activity),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            CyberGlassCard {
                emergencyEvents.take(6).forEach { event ->
                    RemoteEventRow(
                        headline = stringResource(R.string.caregiver_emergency_row),
                        detail = event.details.ifBlank { formatRemoteTimestamp(event.triggeredAt) },
                        timestamp = formatRemoteTimestamp(event.triggeredAt),
                        isAlert = true,
                        resolved = event.isResolved
                    )
                }
                checkIns.take(6).forEach { checkIn ->
                    RemoteEventRow(
                        headline = stringResource(R.string.caregiver_checkin_row),
                        detail = formatRemoteTimestamp(checkIn.confirmedAt),
                        timestamp = formatRemoteTimestamp(checkIn.confirmedAt),
                        isAlert = false,
                        resolved = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.caregiver_who_can_see),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        CyberGlassCard {
            if (members.isEmpty()) {
                Text(
                    text = stringResource(R.string.caregiver_no_members),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (member.role == "PROTECTED") {
                                stringResource(R.string.caregiver_role_protected)
                            } else {
                                stringResource(R.string.caregiver_role_monitoring)
                            },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Prominent, unmissable banner for an emergency nobody has resolved yet. */
@Composable
private fun EmergencyBanner(event: RemoteEmergencyEvent) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.caregiver_emergency_alert),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = formatRemoteTimestamp(event.triggeredAt),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onError
            )
            if (event.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = event.details,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onError,
                    lineHeight = 23.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.caregiver_emergency_advice),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onError,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun DeviceStatusCard(device: MonitoredDevice) {
    val now = System.currentTimeMillis()
    val stale = device.isStale(now)

    // Section 19: a silent device is UNKNOWN, never EMERGENCY.
    val effective = when {
        device.safety == SafetyStatus.EMERGENCY -> SafetyStatus.EMERGENCY
        stale -> SafetyStatus.UNKNOWN
        else -> device.safety
    }
    val (label, color) = when (effective) {
        SafetyStatus.SAFE -> stringResource(R.string.status_safe) to StatusSafe
        SafetyStatus.UNKNOWN -> stringResource(R.string.status_unknown) to StatusUnknown
        SafetyStatus.EMERGENCY -> stringResource(R.string.status_emergency) to StatusEmergency
    }

    CyberGlassCard(borderColor = color) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (device.personRole.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.personRole,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CyberBadge(text = label, color = color)
        }

        if (stale) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusUnknown,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.caregiver_stale_notice),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    device: MonitoredDevice,
    checkIns: List<RemoteCheckIn>
) {
    CyberGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.common_battery),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (device.batteryLevel >= 0) {
                        stringResource(R.string.battery_percent, device.batteryLevel)
                    } else {
                        stringResource(R.string.dash)
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (device.batteryLevel in 0..20) {
                        StatusEmergency
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = if (device.isCharging) {
                        stringResource(R.string.battery_charging)
                    } else {
                        stringResource(R.string.battery_not_charging)
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.isInternetAvailable) {
                    Icons.Default.Wifi
                } else {
                    Icons.Default.WifiOff
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.common_network),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (device.isInternetAvailable) {
                    stringResource(R.string.dashboard_online)
                } else {
                    stringResource(R.string.dashboard_offline)
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (device.isInternetAvailable) {
                    StatusSafe
                } else {
                    StatusUnknown
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.caregiver_last_seen),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (device.hasReported) {
                    formatRemoteTimestamp(device.lastSeenAt)
                } else {
                    stringResource(R.string.dash)
                },
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.caregiver_last_checkin),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = checkIns.firstOrNull()?.let { formatRemoteTimestamp(it.confirmedAt) }
                    ?: stringResource(R.string.dash),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RemoteEventRow(
    headline: String,
    detail: String,
    timestamp: String,
    isAlert: Boolean,
    resolved: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isAlert) ActionEmergency else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (resolved) stringResource(R.string.caregiver_resolved) else timestamp,
            fontSize = 14.sp,
            color = if (resolved) StatusSafe else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Loading outcome for the caregiver view, so failures are explained rather than hidden. */
sealed interface CaregiverLoadState {
    data object Idle : CaregiverLoadState
    data object Loading : CaregiverLoadState
    data class Loaded(val at: Long) : CaregiverLoadState
    data class Error(val message: String) : CaregiverLoadState
}

/**
 * Parses an ISO-8601 instant from the backend.
 *
 * Returns the raw text when parsing fails rather than hiding it: an unparseable timestamp is
 * itself worth a caregiver seeing, and silently substituting "now" would misrepresent when an
 * emergency happened.
 */
fun formatRemoteTimestamp(iso: String): String {
    if (iso.isBlank()) return ""
    val normalised = iso.replace("Z", "").substringBefore("+").trim()
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss"
    )
    for (pattern in patterns) {
        val parsed: Date? = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(normalised)
        }.getOrNull()
        // An unparseable timestamp is shown verbatim: substituting "now" would misreport when an
        // emergency actually happened.
        if (parsed != null) {
            return SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(parsed)
        }
    }
    return iso
}

private fun formatRemoteTime(epochMillis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))