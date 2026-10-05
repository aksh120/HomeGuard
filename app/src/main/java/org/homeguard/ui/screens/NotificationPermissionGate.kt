package org.homeguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.ui.components.CyberButton

/**
 * Blocking gate shown until notifications are allowed (decision D9).
 *
 * On API 33+ a denied POST_NOTIFICATIONS permission makes every push vanish with no error anywhere.
 * The caregiver would conclude alerts are not working, with nothing to diagnose. So for the caregiver
 * profile this is not a dismissible dialog but a gate: push is the only channel that reaches a locked
 * phone, and leaving it disabled silently removes the feature the product exists for.
 *
 * Still dismissible, because an OS-level permission request can be permanently denied and trapping
 * someone in a loop they cannot escape is worse. What is not acceptable is a caregiver believing
 * they are protected when they are not.
 */
@Composable
fun NotificationPermissionGate(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    permanentlyDenied: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.push_gate_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.push_gate_body_denied else R.string.push_gate_body
            ),
            fontSize = 17.sp,
            lineHeight = 25.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        CyberButton(
            text = stringResource(
                if (permanentlyDenied) R.string.push_gate_open_settings else R.string.push_gate_allow
            ),
            onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text(
                text = stringResource(R.string.push_gate_settings_link),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
