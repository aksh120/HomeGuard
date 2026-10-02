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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberTopAppBar

/**
 * Emergency countdown (section 11).
 *
 * Plain, high-contrast, and static. The number is the only thing moving; there is no radar sweep
 * or pulsing ornament, both of which the design brief rules out.
 */
@Composable
fun EmergencyCountdownScreen(
    countdownSeconds: Int,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.emergency_alert_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = StatusEmergency,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.emergency_countdown_initiating),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center) {
            Text(
                text = countdownSeconds.toString(),
                fontSize = 108.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        CyberButton(
            text = stringResource(R.string.emergency_abort),
            onClick = onCancelClick,
            accentColor = MaterialTheme.colorScheme.surfaceVariant,
            isSecondary = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.emergency_abort_hint),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Active emergency screen (section 11).
 *
 * States plainly whether the alert actually went out. Section 32 forbids implying success when the
 * message has not left the device, so the offline case is worded differently from the sent case.
 */
@Composable
fun EmergencyActiveScreen(
    isInternetAvailable: Boolean,
    pendingSyncCount: Int,
    onStopAlarmClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.emergency_sent_title),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = StatusEmergency,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.emergency_sent_message),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .flatSurface(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isInternetAvailable) {
                            stringResource(R.string.emergency_broadcasted)
                        } else {
                            stringResource(R.string.emergency_offline_armed)
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isInternetAvailable) {
                        stringResource(R.string.emergency_broadcasted_detail)
                    } else {
                        stringResource(R.string.emergency_offline_detail)
                    },
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 25.sp
                )
                if (!isInternetAvailable && pendingSyncCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.sync_pending_count, pendingSyncCount),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        CyberButton(
            text = stringResource(R.string.action_stop_alarm),
            onClick = onStopAlarmClick,
            accentColor = ActionEmergency
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.emergency_resolve_caption),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Flat surface colour, kept as a helper so screen code stays readable. */
private fun Modifier.flatSurface(color: androidx.compose.ui.graphics.Color): Modifier =
    this.background(color)