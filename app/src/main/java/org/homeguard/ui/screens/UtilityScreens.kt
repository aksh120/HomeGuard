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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.designsystem.theme.TorchActive
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.ui.components.LargeActionButton

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
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Night Mode Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.night_mode_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            OutlinedButton(
                onClick = onExitNightMode,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Exit")
            }
        }

        // Minimal Telemetry Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.monitoring_active),
                fontSize = 20.sp,
                color = StatusSafe,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (connectivityInfo.isWifiConnected) stringResource(R.string.wifi_connected) else stringResource(R.string.wifi_disconnected),
                fontSize = 18.sp,
                color = if (connectivityInfo.isWifiConnected) TextSecondary else StatusUnknown
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.battery_status_format, batteryInfo.level),
                fontSize = 18.sp,
                color = TextSecondary
            )
        }

        // Prominent Emergency Access
        LargeActionButton(
            text = stringResource(R.string.action_emergency),
            onClick = onEmergencyClick,
            backgroundColor = ActionEmergency,
            borderColor = ActionEmergency,
            textColor = Color.White,
            icon = Icons.Default.Warning,
            minHeight = 84,
            modifier = Modifier.padding(bottom = 16.dp)
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
                Icon(
                    imageVector = Icons.Default.PowerOff,
                    contentDescription = null,
                    tint = StatusUnknown,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.power_cut_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            OutlinedButton(onClick = onBackClick) {
                Text(text = "Back", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.power_cut_desc),
            color = TextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Large Flashlight Button
        LargeActionButton(
            text = if (isTorchOn) stringResource(R.string.torch_on) else stringResource(R.string.action_flashlight),
            onClick = onToggleTorch,
            backgroundColor = if (isTorchOn) TorchActive else DarkSurface,
            borderColor = if (isTorchOn) TorchActive else BorderSubtle,
            textColor = if (isTorchOn) DarkBackground else TextPrimary,
            icon = Icons.Default.FlashOn,
            minHeight = 88
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Battery Status Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Battery Status",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.battery_status_format, batteryInfo.level)} • ${if (batteryInfo.isCharging) stringResource(R.string.battery_charging) else stringResource(R.string.battery_not_charging)}",
                    fontSize = 16.sp,
                    color = if (batteryInfo.level <= 20) ActionEmergency else TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Call Primary Contact
        if (appConfig.primaryContact.name.isNotBlank()) {
            LargeActionButton(
                text = "CALL ${appConfig.primaryContact.name.uppercase()}",
                onClick = onCallPrimary,
                backgroundColor = DarkSurface,
                borderColor = BorderSubtle,
                textColor = TextPrimary,
                icon = Icons.Default.Call,
                minHeight = 64
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Offline Advice
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Power Cut Safety Guidelines",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Keep refrigerator and freezer doors closed.\n• Unplug sensitive electronic equipment to avoid surge damage.\n• Use phone flashlight sparingly to preserve battery.\n• Remain indoors unless evacuation is necessary.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}
