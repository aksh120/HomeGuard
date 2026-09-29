package org.homeguard.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusEmergencyBackground
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.ui.components.LargeActionButton

@Composable
fun EmergencyCountdownScreen(
    countdownSeconds: Int,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusEmergency,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.emergency_alert_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.emergency_sending_in),
                fontSize = 18.sp,
                color = TextSecondary
            )
        }

        // Tactile countdown circle
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(StatusEmergencyBackground)
                .border(4.dp, StatusEmergency, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$countdownSeconds",
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        // Cancel Button (prominent to prevent accidental triggers)
        LargeActionButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancelClick,
            backgroundColor = DarkSurface,
            borderColor = Color.White,
            textColor = Color.White,
            minHeight = 72,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
fun EmergencyActiveScreen(
    isInternetAvailable: Boolean,
    onStopAlarmClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StatusEmergencyBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.emergency_sent_title),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.emergency_sent_message),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        // Offline Notification if disconnected
        if (!isInternetAvailable) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBackground)
                    .border(2.dp, StatusUnknown, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.emergency_offline_notice),
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // STOP ALARM BUTTON
        LargeActionButton(
            text = stringResource(R.string.action_stop_alarm),
            onClick = onStopAlarmClick,
            backgroundColor = Color.White,
            borderColor = Color.White,
            textColor = ActionEmergency,
            minHeight = 80,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
