package org.homeguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusEmergencyBackground
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusSafeBackground
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.StatusUnknownBackground
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.model.SafetyStatus

@Composable
fun SafetyStatusBanner(
    status: SafetyStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, icon, statusText, detailText) = when (status) {
        SafetyStatus.SAFE -> StatusConfig(
            StatusSafeBackground,
            StatusSafe,
            Icons.Default.CheckCircle,
            stringResource(R.string.status_safe),
            stringResource(R.string.status_safe_detail)
        )
        SafetyStatus.UNKNOWN -> StatusConfig(
            StatusUnknownBackground,
            StatusUnknown,
            Icons.Default.Help,
            stringResource(R.string.status_unknown),
            stringResource(R.string.status_unknown_detail)
        )
        SafetyStatus.EMERGENCY -> StatusConfig(
            StatusEmergencyBackground,
            StatusEmergency,
            Icons.Default.Warning,
            stringResource(R.string.status_emergency),
            stringResource(R.string.status_emergency_detail)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
            .semantics { contentDescription = "Safety Status: $statusText, $detailText" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = statusText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = detailText,
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

private data class StatusConfig(
    val bg: Color,
    val border: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

@Composable
fun LargeActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = DarkSurface,
    textColor: Color = TextPrimary,
    borderColor: Color = BorderSubtle,
    icon: ImageVector? = null,
    minHeight: Int = 68
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight.dp)
            .semantics {
                this.contentDescription = text
            },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TelemetryRow(
    wifiConnected: Boolean,
    batteryLevel: Int,
    isCharging: Boolean,
    isMonitoringActive: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (wifiConnected) stringResource(R.string.wifi_connected) else stringResource(R.string.wifi_disconnected),
                color = if (wifiConnected) StatusSafe else StatusUnknown,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${stringResource(R.string.battery_status_format, batteryLevel)} • ${if (isCharging) stringResource(R.string.battery_charging) else stringResource(R.string.battery_not_charging)}",
                color = if (batteryLevel <= 20) StatusEmergency else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isMonitoringActive) stringResource(R.string.monitoring_active) else stringResource(R.string.monitoring_paused),
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}
