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
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.model.CaregiverActivity
import org.homeguard.core.model.CaregiverAggregator
import org.homeguard.core.model.ApprovalRequest
import org.homeguard.core.model.HouseholdMember
import org.homeguard.core.model.RemoteCheckIn
import org.homeguard.core.model.RemoteEmergencyEvent
import org.homeguard.core.model.SafetyStatus
import org.homeguard.core.model.WatchedPerson
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
 *
 * Phase 2: built around a list of people rather than one device, per decision D2. One code covers a
 * whole house, so the dashboard shows everyone in it and offers to add more by code.
 */
@Composable
fun CaregiverDashboardScreen(
    householdName: String,
    people: List<WatchedPerson>,
    members: List<HouseholdMember>,
    approvals: List<ApprovalRequest> = emptyList(),
    approvalError: String? = null,
    isSubmittingApproval: Boolean = false,
    emergencyEvents: List<RemoteEmergencyEvent>,
    checkIns: List<RemoteCheckIn>,
    isRefreshing: Boolean,
    loadState: CaregiverLoadState,
    lastRefreshedAt: Long?,
    onRefresh: () -> Unit,
    onAddPerson: () -> Unit,
    onSubmitApprovalCode: (String) -> Unit = {},
    onDismissApprovalError: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val openEmergencies = remember(emergencyEvents) {
        emergencyEvents.filterNot { it.isResolved }
    }
    val feed = remember(people, checkIns, emergencyEvents) {
        CaregiverAggregator.activityFeed(people, checkIns, emergencyEvents)
    }

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

        // Unresolved emergencies take the top slots, full width, naming who they belong to. A
        // caregiver scanning this screen must not read past telemetry to see one, and must never
        // have to guess which person it concerns.
        openEmergencies.forEach { event ->
            val person = people.firstOrNull { it.deviceId == event.deviceId }
            EmergencyBanner(event, person?.device?.displayName.orEmpty())
            Spacer(modifier = Modifier.height(12.dp))
        }

        when {
            isRefreshing && people.isEmpty() -> {
                Text(
                    text = stringResource(R.string.caregiver_loading),
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            loadState is CaregiverLoadState.Error && people.isEmpty() -> {
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

            people.isEmpty() -> {
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.caregiver_people_heading),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                people.forEachIndexed { index, person ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    PersonCard(person)
                }
            }
        }

        // Adding a person is one code. Keeping this prominent is the affordance for D2: a caregiver
        // watching a second relative should never need to hunt through settings for it.
        Spacer(modifier = Modifier.height(16.dp))
        CyberButton(
            text = stringResource(R.string.caregiver_add_person),
            onClick = onAddPerson,
            accentColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.PersonAdd
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        if (feed.isEmpty()) {
            CyberGlassCard {
                Text(
                    text = stringResource(R.string.caregiver_no_activity),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            CyberGlassCard {
                feed.forEach { item ->
                    when (item) {
                        is CaregiverActivity.Emergency -> RemoteEventRow(
                            headline = stringResource(
                                R.string.caregiver_emergency_row,
                                item.personName
                            ),
                            detail = item.event.details.ifBlank {
                                formatRemoteTimestamp(item.event.triggeredAt)
                            },
                            timestamp = formatRemoteTimestamp(item.event.triggeredAt),
                            isAlert = true,
                            resolved = item.event.isResolved
                        )

                        is CaregiverActivity.CheckIn -> RemoteEventRow(
                            headline = stringResource(
                                R.string.caregiver_checkin_row,
                                item.personName
                            ),
                            detail = formatRemoteTimestamp(item.checkIn.confirmedAt),
                            timestamp = formatRemoteTimestamp(item.checkIn.confirmedAt),
                            isAlert = false,
                            resolved = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Completing a request is a separate action from joining a household, so it gets its own
        // panel rather than sharing the join flow.
        ApprovalCodeEntry(
            isSubmitting = isSubmittingApproval,
            errorMessage = approvalError,
            onSubmit = onSubmitApprovalCode,
            onDismissError = onDismissApprovalError
        )

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
private fun EmergencyBanner(event: RemoteEmergencyEvent, personName: String) {
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
            // Named explicitly. In a multi-person household an unattributed alarm is unactionable.
            if (personName.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = personName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
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

/**
 * One watched person: status, battery, network, last seen, and their own last check-in.
 *
 * Per-person rather than household-wide. The previous single card showed whichever device happened
 * to be first alongside every check-in in the household, so with two people the readings belonged
 * to different devices.
 */
@Composable
private fun PersonCard(person: WatchedPerson) {
    val device = person.device
    val now = System.currentTimeMillis()
    val stale = device.isStale(now)

    // Section 19: a silent device is UNKNOWN, never EMERGENCY.
    val effective = when {
        person.hasOpenEmergency || device.safety == SafetyStatus.EMERGENCY -> SafetyStatus.EMERGENCY
        stale -> SafetyStatus.UNKNOWN
        else -> device.safety
    }
    val (label, color) = when (effective) {
        SafetyStatus.SAFE -> stringResource(R.string.status_safe) to StatusSafe
        SafetyStatus.UNKNOWN -> stringResource(R.string.status_unknown) to StatusUnknown
        SafetyStatus.EMERGENCY -> stringResource(R.string.status_emergency) to StatusEmergency
    }

    val spoken = buildString {
        append(device.displayName)
        append(". ")
        append(label)
        append(". ")
        append(
            if (device.batteryLevel >= 0) {
                stringResource(R.string.battery_percent, device.batteryLevel)
            } else {
                stringResource(R.string.dash)
            }
        )
        append(". ")
        append(
            if (device.isInternetAvailable) {
                stringResource(R.string.dashboard_online)
            } else {
                stringResource(R.string.dashboard_offline)
            }
        )
    }

    CyberGlassCard(borderColor = color) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = spoken },
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

        Spacer(modifier = Modifier.height(12.dp))

        PersonMetric(
            icon = Icons.Default.Bolt,
            label = stringResource(R.string.common_battery),
            value = if (device.batteryLevel >= 0) {
                stringResource(R.string.battery_percent, device.batteryLevel)
            } else {
                stringResource(R.string.dash)
            },
            valueColor = if (device.batteryLevel in 0..20) StatusEmergency else null,
            footnote = if (device.isCharging) {
                stringResource(R.string.battery_charging)
            } else {
                stringResource(R.string.battery_not_charging)
            }
        )

        PersonMetric(
            icon = if (device.isInternetAvailable) Icons.Default.Wifi else Icons.Default.WifiOff,
            label = stringResource(R.string.common_network),
            value = if (device.isInternetAvailable) {
                stringResource(R.string.dashboard_online)
            } else {
                stringResource(R.string.dashboard_offline)
            },
            valueColor = if (device.isInternetAvailable) StatusSafe else StatusUnknown
        )

        PersonMetric(
            icon = Icons.Default.Sensors,
            label = stringResource(R.string.caregiver_last_seen),
            value = if (device.hasReported) {
                formatRemoteTimestamp(device.lastSeenAt)
            } else {
                stringResource(R.string.dash)
            }
        )

        PersonMetric(
            icon = Icons.Default.Warning,
            label = stringResource(R.string.caregiver_last_checkin),
            value = person.lastCheckInAt?.let { formatRemoteTimestamp(it) }
                ?: stringResource(R.string.dash)
        )

        if (stale) {
            Spacer(modifier = Modifier.height(8.dp))
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

/** One labelled reading inside a person card. */
@Composable
private fun PersonMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    footnote: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
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
            fontSize = 17.sp,
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
            if (footnote != null) {
                Text(
                    text = footnote,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                color = if (isAlert) StatusEmergency else MaterialTheme.colorScheme.onSurface
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