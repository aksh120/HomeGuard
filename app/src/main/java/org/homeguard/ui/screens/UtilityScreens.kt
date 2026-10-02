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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionWarning
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

/**
 * Night mode (section 16).
 *
 * Reduced visual activity: no animation, no radar, a flat surface, and the emergency action still
 * present and reachable. The schedule itself is configured in Settings.
 */
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
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.night_mode_title),
            subtitle = stringResource(R.string.night_low_emission),
            onBackClick = onExitNightMode
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .flatSurface(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.night_vigilance_active),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.night_vigilance_desc),
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        CyberGlassCard {
            InfoLine(
                stringResource(R.string.common_battery),
                stringResource(R.string.battery_status_format, batteryInfo.level)
            )
            InfoLine(
                stringResource(R.string.common_network),
                if (connectivityInfo.isInternetAvailable) {
                    stringResource(R.string.wifi_connected)
                } else {
                    stringResource(R.string.wifi_disconnected)
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        CyberButton(
            text = stringResource(R.string.night_trigger_sos),
            onClick = onEmergencyClick,
            accentColor = ActionEmergency,
            icon = Icons.Default.Info
        )
    }
}

/** Power cut mode (section 23). Everything here works with no internet. */
@Composable
fun PowerCutScreen(
    batteryInfo: BatteryInfo,
    isTorchOn: Boolean,
    appConfig: AppConfig,
    onToggleTorch: () -> Unit,
    onCallPrimary: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.power_cut_title),
            subtitle = stringResource(R.string.power_battery_guard),
            onBackClick = onBackClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        CyberGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.power_tactical_torch),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    onClick = onToggleTorch,
                    modifier = Modifier.height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = if (isTorchOn) {
                        ActionWarning
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = if (isTorchOn) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTorchOn) {
                                stringResource(R.string.tile_active)
                            } else {
                                stringResource(R.string.tile_standby)
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTorchOn) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CyberGlassCard {
            Text(
                text = stringResource(R.string.power_battery_telemetry),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.power_reserved, batteryInfo.level),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (batteryInfo.level <= 20) {
                    ActionEmergency
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (appConfig.primaryContact.phoneOrHandle.isNotBlank()) {
            CyberButton(
                text = stringResource(R.string.power_call_contact, appConfig.primaryContact.name),
                onClick = onCallPrimary,
                accentColor = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Call
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        CyberGlassCard {
            Text(
                text = stringResource(R.string.power_outage_protocol),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.power_outage_tips),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 26.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Flat surface colour, kept as a helper so screen code stays readable. */
private fun Modifier.flatSurface(color: androidx.compose.ui.graphics.Color): Modifier =
    this.background(color)