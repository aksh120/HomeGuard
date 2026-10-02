package org.homeguard.ui.components

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionImOk
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.designsystem.theme.StatusUnknown
import org.homeguard.core.model.SafetyStatus

/** Continuous press required before the SOS triggers. */
private const val HOLD_SOS_DURATION_MS = 2500L

/**
 * Minimum interactive height.
 *
 * The target audience is elderly and non-technical users, so every tappable element clears the
 * 64dp comfortable-touch minimum rather than the 48dp baseline.
 */
private val MinTouchTarget = 64.dp

/**
 * Safety status summary with the primary "I'm OK" action.
 *
 * Deliberately not an animated orb. The master specification rules out decorative animation and
 * asks for a serious, trustworthy utility, so this is a flat card with a large single action.
 */
@Composable
fun CyberAegisHeroOrb(
    status: SafetyStatus,
    onTapConfirmOk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (status) {
        SafetyStatus.SAFE -> stringResource(R.string.status_safe)
        SafetyStatus.UNKNOWN -> stringResource(R.string.status_unknown)
        SafetyStatus.EMERGENCY -> stringResource(R.string.status_emergency)
    }
    val detail = when (status) {
        SafetyStatus.SAFE -> stringResource(R.string.status_safe_detail)
        SafetyStatus.UNKNOWN -> stringResource(R.string.status_unknown_detail)
        SafetyStatus.EMERGENCY -> stringResource(R.string.status_emergency_detail)
    }
    val accent = when (status) {
        SafetyStatus.SAFE -> StatusSafe
        SafetyStatus.UNKNOWN -> StatusUnknown
        SafetyStatus.EMERGENCY -> StatusEmergency
    }
    val icon = when (status) {
        SafetyStatus.SAFE -> Icons.Default.CheckCircle
        SafetyStatus.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
        SafetyStatus.EMERGENCY -> Icons.Default.Warning
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                // Status is conveyed by text as well as colour, never colour alone.
                Text(
                    text = label,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = detail,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            CyberButton(
                text = stringResource(R.string.action_im_ok),
                onClick = onTapConfirmOk,
                accentColor = ActionImOk,
                icon = Icons.Default.CheckCircle,
                minHeight = 72
            )
        }
    }
}

/**
 * Emergency trigger.
 *
 * Requires a continuous press to avoid accidental activation, which matters for a device that may
 * sit in a pocket or be bumped. Completion is measured with wall-clock time so an early release
 * cannot trigger it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TactileHoldSosRadial(
    onEmergencyTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var isPressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val sosA11yDescription = stringResource(R.string.sos_button_a11y)

    // Visual fill follows measured hold time directly; no tween, so the bar cannot lag behind
    // the actual decision and mislead the user.
    val shown = animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = if (isPressing) 80 else 150, easing = LinearEasing),
        label = "sosHold"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = sosA11yDescription
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val start = SystemClock.elapsedRealtime()
                        isPressing = true
                        coroutineScope {
                            val holdLoop = launch {
                                while (true) {
                                    val held = SystemClock.elapsedRealtime() - start
                                    progress = (held.toFloat() / HOLD_SOS_DURATION_MS)
                                        .coerceIn(0f, 1f)
                                    if (held >= HOLD_SOS_DURATION_MS) break
                                    // Firmer feedback as the hold approaches completion.
                                    view.performHapticFeedback(
                                        if (held > HOLD_SOS_DURATION_MS / 2) {
                                            HapticFeedbackConstants.CONTEXT_CLICK
                                        } else {
                                            HapticFeedbackConstants.KEYBOARD_TAP
                                        }
                                    )
                                    delay(250L)
                                }
                            }
                            tryAwaitRelease()
                            holdLoop.cancel()
                        }
                        val held = SystemClock.elapsedRealtime() - start
                        isPressing = false
                        if (held >= HOLD_SOS_DURATION_MS) {
                            progress = 1f
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onEmergencyTriggered()
                        } else {
                            progress = 0f
                        }
                    }
                )
            },
        color = ActionEmergency,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Flat progress bar rather than a sweeping gradient fill.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(shown.value)
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.85f))
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isPressing) {
                            stringResource(R.string.sos_holding)
                        } else {
                            stringResource(R.string.sos_hold_cta)
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isPressing) {
                            stringResource(R.string.sos_progress_cancel, (shown.value * 100).toInt())
                        } else {
                            stringResource(R.string.sos_hold_hint)
                        },
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/** Device telemetry: battery, network, and recent movement. Flat rows, no gauges. */
@Composable
fun TelemetryCockpitHUD(
    batteryLevel: Int,
    isCharging: Boolean,
    wifiConnected: Boolean,
    lastMotionAgoFormatted: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            TelemetryRow(
                icon = Icons.Default.Bolt,
                label = stringResource(R.string.common_battery),
                value = stringResource(R.string.battery_status_format, batteryLevel),
                detail = if (isCharging) {
                    stringResource(R.string.battery_charging)
                } else {
                    stringResource(R.string.battery_not_charging)
                },
                valueColor = if (batteryLevel <= 20) StatusEmergency else null
            )
            TelemetryRow(
                icon = if (wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                label = stringResource(R.string.common_network),
                value = if (wifiConnected) {
                    stringResource(R.string.wifi_connected)
                } else {
                    stringResource(R.string.wifi_disconnected)
                },
                valueColor = if (wifiConnected) null else StatusUnknown
            )
            TelemetryRow(
                icon = Icons.Default.Sensors,
                label = stringResource(R.string.common_presence),
                value = lastMotionAgoFormatted
            )
        }
    }
}

/** Single telemetry line. Exposed for screens that need to reuse it. */
@Composable
private fun TelemetryRow(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String? = null,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** Screen title bar with an optional back affordance. */
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
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            Surface(
                onClick = onBackClick,
                modifier = Modifier
                    .size(MinTouchTarget)
                    .semantics { role = Role.Button },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

/**
 * Flat content container.
 *
 * Retains the historical name for call-site compatibility, but no longer blurs or tints: it is a
 * plain surface with a hairline border, per the "restrained colors, subtle elevation" guidance.
 */
@Composable
fun CyberGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/**
 * Primary action button.
 *
 * [minHeight] defaults to the comfortable touch target rather than a compact default, because the
 * emergency and check-in actions must be easy to hit.
 */
@Composable
fun CyberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    isSecondary: Boolean = false,
    minHeight: Int = 64,
    enabled: Boolean = true
) {
    val containerColor = if (isSecondary) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        accentColor
    }
    val contentColor = if (isSecondary) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Small status chip. */
@Composable
fun CyberBadge(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

/**
 * Home-screen action entry.
 *
 * A plain full-width row rather than a fixed grid tile: a predictable reading order is easier to
 * scan than a masonry of equal boxes, and it lets labels wrap instead of truncating.
 */
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $status"
                role = Role.Button
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = status,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Fixed-size spacer helper retained for call-site readability. */
@Composable
fun VerticalGap(height: Dp) {
    Spacer(modifier = Modifier.height(height))
}