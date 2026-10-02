package org.homeguard.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard

@Composable
fun EmergencyCountdownScreen(
    countdownSeconds: Int,
    onCancelClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse_countdown")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_scale"
    )

    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "countdown_sweep"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E0707),
                        Color(0xFF120303),
                        Color(0xFF060911)
                    )
                )
            )
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Futuristic Top Alert Badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = StatusEmergency.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusEmergency.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StatusEmergency)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.emergency_countdown_initiating),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = StatusEmergency
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.emergency_alert_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.emergency_sending_in),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }

        // Cybernetic Countdown Radar Core
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Animated shockwave ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = center
                val radius = (size.minDimension / 2) * 0.82f

                // Outer pulsing shockwave
                drawCircle(
                    color = StatusEmergency.copy(alpha = 0.15f * (2f - pulseScale)),
                    radius = radius * pulseScale,
                    center = centerOffset,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Static outer aegis ring
                drawCircle(
                    color = Color(0xFF2E1010),
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Rotating sweep beam
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            StatusEmergency.copy(alpha = 0.6f),
                            StatusEmergency
                        )
                    ),
                    startAngle = sweepAngle,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx())
                )
            }

            // High-contrast inner dark core
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF140505))
                    .border(2.dp, StatusEmergency.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$countdownSeconds",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.emergency_seconds_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = StatusEmergency
                    )
                }
            }
        }

        // Abort Button Section
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CyberButton(
                text = stringResource(R.string.emergency_abort),
                onClick = onCancelClick,
                accentColor = Color(0xFFEF4444),
                isSecondary = true,
                minHeight = 64
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.emergency_abort_hint),
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun EmergencyActiveScreen(
    isInternetAvailable: Boolean,
    pendingSyncCount: Int,
    onStopAlarmClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse_active")
    val beaconAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF260808),
                        Color(0xFF150404),
                        Color(0xFF060911)
                    )
                )
            )
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            // Glowing Beacon Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(StatusEmergency.copy(alpha = beaconAlpha * 0.25f))
                    .border(2.dp, StatusEmergency.copy(alpha = beaconAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.emergency_sent_title),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.emergency_sent_message),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        // Live Offline / Sync Status Card
        CyberGlassCard(
            borderColor = if (isInternetAvailable) StatusSafe.copy(alpha = 0.3f) else StatusUnknown.copy(alpha = 0.4f),
            backgroundColor = Color(0xFF0F172A).copy(alpha = 0.8f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isInternetAvailable) Icons.Default.NotificationsActive else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isInternetAvailable) StatusSafe else StatusUnknown,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (isInternetAvailable) {
                            stringResource(R.string.emergency_broadcasted)
                        } else {
                            stringResource(R.string.emergency_offline_armed)
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isInternetAvailable) {
                            stringResource(R.string.emergency_broadcasted_detail)
                        } else {
                            stringResource(R.string.emergency_offline_detail)
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 19.sp
                    )
                    if (!isInternetAvailable && pendingSyncCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.sync_pending_count, pendingSyncCount),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusSafe
                        )
                    }
                }
            }
        }

        // Deactivate & Resolve Alarm Button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CyberButton(
                text = stringResource(R.string.action_stop_alarm),
                onClick = onStopAlarmClick,
                accentColor = Color(0xFF10B981),
                isSecondary = false,
                minHeight = 68
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.emergency_resolve_caption),
                fontSize = 13.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
