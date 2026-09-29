package org.homeguard.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.model.SafetyStatus

/**
 * Hero Sentinel Aegis Orb with breathing glow and radar orbit ticks.
 */
@Composable
fun CyberAegisHeroOrb(
    status: SafetyStatus,
    onTapConfirmOk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "AegisOrbit")

    // Pulsing aura scale
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraPulse"
    )

    // Slow radar orbit rotation
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSpin"
    )

    val (primaryGlowColor, coreBgColor, statusLabel, icon) = when (status) {
        SafetyStatus.SAFE -> Quadruple(
            Color(0xFF10B981),
            Color(0xFF062E22),
            "SAFE",
            Icons.Default.Check
        )
        SafetyStatus.UNKNOWN -> Quadruple(
            Color(0xFFF59E0B),
            Color(0xFF381A05),
            "UNKNOWN",
            Icons.AutoMirrored.Filled.Help
        )
        SafetyStatus.EMERGENCY -> Quadruple(
            Color(0xFFEF4444),
            Color(0xFF3B0B0B),
            "EMERGENCY",
            Icons.Default.Warning
        )
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Radar Orbit Ticks (Canvas)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(orbitRotation)
        ) {
            val strokeWidth = 2.dp.toPx()
            val radius = (size.minDimension / 2) - 8.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)

            // Outer subtle boundary ring
            drawCircle(
                color = primaryGlowColor.copy(alpha = 0.25f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Radar arc segment
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        primaryGlowColor.copy(alpha = 0.0f),
                        primaryGlowColor.copy(alpha = 0.8f),
                        Color.White
                    )
                ),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Ambient Aura Glow Circle
        Box(
            modifier = Modifier
                .size((190 * auraScale).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryGlowColor.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Core Touch Orb (Interactive)
        Surface(
            modifier = Modifier
                .size(170.dp)
                .clip(CircleShape)
                .border(2.5.dp, primaryGlowColor, CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onTapConfirmOk()
                },
            color = coreBgColor,
            shape = CircleShape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primaryGlowColor,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusLabel,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (status == SafetyStatus.SAFE) "TAP TO VERIFY" else "TAP TO CONFIRM",
                    color = primaryGlowColor.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Tactical Hold-to-Charge Radial Emergency Arc.
 * Requires continuous press for 2.5 seconds with escalating haptic feedback to prevent false alarms.
 */
@Composable
fun TactileHoldSosRadial(
    onEmergencyTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isPressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = if (isPressing) 2500 else 200, easing = LinearEasing),
        label = "SosCharge"
    )

    // Trigger emergency when progress completes
    LaunchedEffect(animatedProgress) {
        if (animatedProgress >= 0.99f && isPressing) {
            isPressing = false
            progress = 0f
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onEmergencyTriggered()
        }
    }

    // Escalating haptic feedback during hold
    LaunchedEffect(isPressing) {
        if (isPressing) {
            while (isPressing) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                delay(300L)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFFDC2626).copy(alpha = 0.6f),
                        Color(0xFFEF4444),
                        Color(0xFFDC2626).copy(alpha = 0.6f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressing = true
                        progress = 1.0f
                        tryAwaitRelease()
                        isPressing = false
                        progress = 0f
                    }
                )
            },
        color = Color(0xFF140707),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Radial Progress Sweep Fill
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fillWidth = size.width * animatedProgress
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF991B1B).copy(alpha = 0.4f),
                            Color(0xFFDC2626).copy(alpha = 0.85f)
                        )
                    ),
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, size.height)
                )
            }

            // Foreground Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF7F1D1D))
                            .border(2.dp, Color(0xFFEF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = if (isPressing) "HOLDING FOR SOS…" else "HOLD FOR EMERGENCY",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isPressing) "${(animatedProgress * 100).toInt()}% • RELEASE TO CANCEL" else "Press & hold 2.5s to trigger sentinel SOS",
                            fontSize = 12.sp,
                            color = if (isPressing) Color(0xFFFCA5A5) else Color(0xFF9CA3AF)
                        )
                    }
                }

                // Percent readout badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF280808),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F1D1D))
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * High-density Telemetry Cockpit Strip with futuristic live gauges.
 */
@Composable
fun TelemetryCockpitHUD(
    batteryLevel: Int,
    isCharging: Boolean,
    wifiConnected: Boolean,
    isAcousticListening: Boolean,
    lastMotionAgoFormatted: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B101B))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SENTINEL TELEMETRY ARRAY",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = Color(0xFF64748B)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (wifiConnected) StatusSafe else StatusUnknown)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (wifiConnected) "ONLINE" else "OFFLINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (wifiConnected) StatusSafe else StatusUnknown
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4 Cockpit Gauge Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 1: Battery Power Grid
            CockpitTile(
                icon = if (isCharging) Icons.Default.Bolt else Icons.Default.Bolt,
                title = "POWER",
                value = "$batteryLevel%",
                sub = if (isCharging) "Charging" else "Battery",
                accentColor = if (batteryLevel <= 20) StatusEmergency else StatusSafe,
                modifier = Modifier.weight(1f)
            )

            // Tile 2: Signal Array
            CockpitTile(
                icon = if (wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                title = "NETWORK",
                value = if (wifiConnected) "Wi-Fi" else "No Net",
                sub = if (wifiConnected) "5 GHz" else "Saved Local",
                accentColor = if (wifiConnected) Color(0xFF38BDF8) else StatusUnknown,
                modifier = Modifier.weight(1f)
            )

            // Tile 3: Passive Presence (Motion)
            CockpitTile(
                icon = Icons.Default.Sensors,
                title = "PRESENCE",
                value = "Active",
                sub = lastMotionAgoFormatted,
                accentColor = Color(0xFFA855F7),
                modifier = Modifier.weight(1f)
            )

            // Tile 4: Acoustic Ear (Smoke Detector)
            CockpitTile(
                icon = Icons.Default.Hearing,
                title = "ACOUSTIC",
                value = if (isAcousticListening) "Guard" else "Idle",
                sub = "3.2kHz Ear",
                accentColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CockpitTile(
    icon: ImageVector,
    title: String,
    value: String,
    sub: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF111827),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                fontSize = 9.5.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CyberTopAppBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (onBackClick != null) {
                Surface(
                    onClick = onBackClick,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun CyberGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0xFF1E293B),
    backgroundColor: Color = Color(0xFF0B101B),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF10B981),
    icon: ImageVector? = null,
    isSecondary: Boolean = false,
    minHeight: Int = 54
) {
    val bg = if (isSecondary) Color(0xFF0F172A) else accentColor.copy(alpha = 0.16f)
    val border = if (isSecondary) Color(0xFF1E293B) else accentColor.copy(alpha = 0.6f)
    val textColor = if (isSecondary) Color(0xFFCBD5E1) else accentColor

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(minHeight.dp),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CyberBadge(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CyberActionTile(
    icon: ImageVector,
    title: String,
    status: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
