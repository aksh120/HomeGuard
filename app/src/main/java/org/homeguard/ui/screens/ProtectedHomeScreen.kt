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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.ApprovalRequest
import org.homeguard.core.model.HouseholdMember
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.NavigationDestination
import org.homeguard.ui.components.CyberActionTile
import org.homeguard.ui.components.CyberAegisHeroOrb
import org.homeguard.ui.components.TactileHoldSosRadial
import org.homeguard.ui.components.TelemetryCockpitHUD
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionPrimary
import org.homeguard.core.designsystem.theme.ActionWarning
import org.homeguard.core.designsystem.theme.StatusEmergency

/**
 * Protected-device home screen.
 *
 * Section 7 asks that the screen be understandable in about two seconds, and section 53 reduces it
 * to four questions: Am I okay? Can I ask for help? Can I contact someone? Is the device working?
 * The layout answers them in that order: status, check-in, emergency, telemetry, then actions.
 */
@Composable
fun ProtectedHomeScreen(
    safetyStatus: SafetyStatus,
    batteryInfo: BatteryInfo,
    connectivityInfo: ConnectivityInfo,
    appConfig: AppConfig,
    lastCheckInFormatted: String,
    lastMotionAgoFormatted: String,
    syncHealth: org.homeguard.core.model.SyncHealth,
    pendingSyncCount: Int,
    communicationError: String?,
    fallDetectedAlert: Boolean,
    checkInPromptVisible: Boolean,
    safetyNoticeVisible: Boolean,
    approvals: List<ApprovalRequest> = emptyList(),
    watchers: List<HouseholdMember> = emptyList(),
    onDeclineApproval: (ApprovalRequest) -> Unit = {},
    onCheckInClick: () -> Unit,
    onEmergencyClick: () -> Unit,
    onCallPrimaryClick: () -> Unit,
    onFlashlightClick: () -> Unit,
    onPingBeaconClick: () -> Unit,
    onDismissFallAlert: () -> Unit,
    onCheckInPromptDismiss: () -> Unit,
    onCheckInPromptHelp: () -> Unit,
    onSafetyNoticeDismiss: () -> Unit,
    onNavigate: (NavigationDestination) -> Unit,
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        HomeHeaderRow(onSettingsClick = { onNavigate(NavigationDestination.Settings) })

        // 1. Status and the primary check-in action.
        CyberAegisHeroOrb(status = safetyStatus, onTapConfirmOk = onCheckInClick)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.last_checkin_format, lastCheckInFormatted),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Emergency. Placed high and visually distinct; this is the reason the app exists.
        TactileHoldSosRadial(onEmergencyTriggered = onEmergencyClick)

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Is the device itself working? Includes whether alerts can actually leave the phone.
        TelemetryCockpitHUD(
            batteryLevel = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            wifiConnected = connectivityInfo.isInternetAvailable,
            lastMotionAgoFormatted = lastMotionAgoFormatted
        )

        Spacer(modifier = Modifier.height(12.dp))

        SyncStatusCard(syncHealth = syncHealth, pendingSyncCount = pendingSyncCount)

        Spacer(modifier = Modifier.height(12.dp))

        // Q4: who is watching, and any request waiting on this resident. Sits directly under the
        // sync card because both answer "is this phone actually reporting what it says".
        ApprovalsSection(
            approvals = approvals,
            watchers = watchers,
            onDecline = onDeclineApproval
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.home_utility_cockpit),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // 4. Can I contact someone? Full-width rows read in a predictable order and let labels
        //    wrap, which matters when a contact name is long.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CyberActionTile(
                icon = Icons.Default.Call,
                title = stringResource(R.string.action_call_primary),
                status = appConfig.primaryContact.name.ifBlank {
                    stringResource(R.string.call_not_set)
                },
                accentColor = ActionPrimary,
                onClick = onCallPrimaryClick
            )

            CyberActionTile(
                icon = Icons.Default.FlashOn,
                title = stringResource(R.string.action_flashlight),
                status = if (batteryInfo.level <= 20) {
                    stringResource(R.string.home_torch_low_battery)
                } else {
                    stringResource(R.string.tile_standby)
                },
                accentColor = ActionWarning,
                onClick = onFlashlightClick
            )

            CyberActionTile(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.home_radar_beacon),
                status = stringResource(R.string.tile_ping_chirp),
                accentColor = ActionPrimary,
                onClick = onPingBeaconClick
            )

            CyberActionTile(
                icon = Icons.Default.Info,
                title = stringResource(R.string.home_safety_dossier),
                status = stringResource(R.string.tile_offline_vault),
                accentColor = ActionPrimary,
                onClick = { onNavigate(NavigationDestination.EmergencyInfo) }
            )

            CyberActionTile(
                icon = Icons.Default.LocalFireDepartment,
                title = stringResource(R.string.home_fire_evacuation),
                status = stringResource(R.string.tile_offline_guide),
                accentColor = ActionEmergency,
                onClick = { onNavigate(NavigationDestination.FireGuide) }
            )

            CyberActionTile(
                icon = Icons.Default.Bedtime,
                title = stringResource(R.string.home_night_aegis),
                status = appConfig.nightModeSchedule.displayWindow(),
                accentColor = ActionWarning,
                onClick = { onNavigate(NavigationDestination.NightMode) }
            )

            CyberActionTile(
                icon = Icons.Default.PowerOff,
                title = stringResource(R.string.home_power_outage),
                status = stringResource(R.string.tile_battery_guard),
                accentColor = ActionWarning,
                onClick = { onNavigate(NavigationDestination.PowerCut) }
            )

            CyberActionTile(
                icon = Icons.Default.Sensors,
                title = stringResource(R.string.home_sentinel_audit),
                status = stringResource(R.string.tile_event_stream),
                accentColor = ActionPrimary,
                onClick = { onNavigate(NavigationDestination.MonitoringDashboard) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Scheduled check-in prompt (section 14).
    if (checkInPromptVisible) {
        AlertDialog(
            onDismissRequest = onCheckInPromptDismiss,
            title = {
                Text(
                    text = stringResource(R.string.checkin_dialog_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.checkin_dialog_message),
                    fontSize = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onCheckInClick,
                    modifier = Modifier.height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_im_ok),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onCheckInPromptHelp,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_i_need_help),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    // Safety notice (section 52). Shown once, until acknowledged.
    if (safetyNoticeVisible) {
        AlertDialog(
            onDismissRequest = onSafetyNoticeDismiss,
            title = {
                Text(
                    text = stringResource(R.string.disclaimer_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.disclaimer_text),
                    fontSize = 17.sp,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onSafetyNoticeDismiss,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = stringResource(R.string.disclaimer_acknowledge),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    // Fall detection alert.
    if (fallDetectedAlert) {
        AlertDialog(
            onDismissRequest = onDismissFallAlert,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StatusEmergency
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.fall_alert_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(text = stringResource(R.string.fall_alert_body), fontSize = 17.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissFallAlert()
                        onCheckInClick()
                    },
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fall_alert_confirm),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onDismissFallAlert()
                        onEmergencyClick()
                    },
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fall_alert_sos),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    // Communication failure.
    if (communicationError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = {
                Text(
                    text = stringResource(R.string.call_failed_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = communicationError, fontSize = 17.sp, lineHeight = 24.sp)
            },
            confirmButton = {
                Button(
                    onClick = onDismissError,
                    modifier = Modifier.height(64.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        fontSize = 18.sp
                    )
                }
            }
        )
    }
}

/**
 * Whether alerts can actually leave this phone.
 *
 * A safety app that cannot say "my alert is stuck" is worse than one that has no backend at all,
 * because the resident reasonably assumes the emergency was delivered. Local-only operation is a
 * legitimate state, so it is stated plainly rather than shown as a failure.
 */
@Composable
private fun SyncStatusCard(
    syncHealth: org.homeguard.core.model.SyncHealth,
    pendingSyncCount: Int
) {
    val (label, color) = when (syncHealth.status) {
        org.homeguard.core.model.SyncStatus.HEALTHY ->
            stringResource(R.string.sync_ok) to org.homeguard.core.designsystem.theme.StatusSafe
        org.homeguard.core.model.SyncStatus.PENDING ->
            stringResource(R.string.sync_pending) to MaterialTheme.colorScheme.onSurfaceVariant
        org.homeguard.core.model.SyncStatus.NOT_CONFIGURED ->
            stringResource(R.string.sync_local_only) to MaterialTheme.colorScheme.onSurfaceVariant
        org.homeguard.core.model.SyncStatus.NOT_AUTHENTICATED ->
            stringResource(R.string.sync_not_signed_in) to org.homeguard.core.designsystem.theme.StatusUnknown
        // Deliberately not an alarm colour: nothing is wrong and nothing is lost, so colouring this
        // red would train the resident to ignore the card that matters.
        org.homeguard.core.model.SyncStatus.RATE_LIMITED ->
            stringResource(R.string.sync_rate_limited) to org.homeguard.core.designsystem.theme.StatusUnknown
        org.homeguard.core.model.SyncStatus.ERROR ->
            stringResource(R.string.sync_error) to org.homeguard.core.designsystem.theme.StatusEmergency
    }

    val detail = when (syncHealth.status) {
        org.homeguard.core.model.SyncStatus.NOT_CONFIGURED ->
            stringResource(R.string.sync_local_only_detail)
        org.homeguard.core.model.SyncStatus.NOT_AUTHENTICATED ->
            stringResource(R.string.sync_not_signed_in_detail)
        org.homeguard.core.model.SyncStatus.RATE_LIMITED ->
            stringResource(R.string.sync_rate_limited_detail)
        org.homeguard.core.model.SyncStatus.ERROR ->
            stringResource(R.string.sync_error_detail)
        org.homeguard.core.model.SyncStatus.PENDING ->
            stringResource(R.string.sync_pending_detail)
        org.homeguard.core.model.SyncStatus.HEALTHY ->
            stringResource(R.string.sync_ok_detail)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
            if (pendingSyncCount > 0) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = pendingSyncCount.toString(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

/** Header row with the app identity and settings affordance. */
@Composable
fun HomeHeaderRow(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_app_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSettingsClick, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.a11y_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}