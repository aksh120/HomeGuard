package org.homeguard.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

/**
 * Caregiver onboarding: enter the invite code shown on the device at home.
 *
 * This is the first screen a monitoring install sees. It is deliberately not the protected-device
 * setup flow: there is no protected person to describe and no emergency contact to configure,
 * because this phone will never raise an alert.
 */
@Composable
fun MonitorJoinScreen(
    isJoining: Boolean,
    errorMessage: String?,
    requestedHouseholdName: String? = null,
    approvalsRequired: Int = 0,
    onJoin: (String) -> Unit,
    onDismissError: () -> Unit,
    onSwitchToProtected: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.monitor_setup_title),
            subtitle = stringResource(R.string.caregiver_default_household)
        )

        Spacer(modifier = Modifier.height(16.dp))

        CyberGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.monitor_setup_body),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 23.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        androidx.compose.material3.OutlinedTextField(
            value = code,
            onValueChange = { input ->
                // Invite codes avoid ambiguous characters; accept only those.
                code = input.uppercase().filter { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }.take(8)
                if (errorMessage != null) onDismissError()
            },
            label = { Text(text = stringResource(R.string.monitor_setup_code_label), fontSize = 16.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.monitor_setup_code_hint),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            CyberGlassCard(borderColor = StatusEmergency) {
                Text(
                    text = errorMessage,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp
                )
            }
        }

        // The request was filed but grants nothing yet, so this is the only place the caregiver learns
        // what happens next. Without it the screen looks unchanged and a successful request is
        // indistinguishable from a failed one.
        if (requestedHouseholdName != null) {
            Spacer(modifier = Modifier.height(12.dp))
            CyberGlassCard {
                Text(
                    text = stringResource(
                        R.string.monitor_setup_ask_sent,
                        requestedHouseholdName,
                        approvalsRequired
                    ),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = stringResource(R.string.monitor_setup_ask),
            onClick = { onJoin(code) },
            enabled = code.length == 8 && !isJoining
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.approvals_caregiver_note),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )

        if (isJoining) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.caregiver_loading),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        TextButton(
            onClick = onSwitchToProtected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.monitor_switch_to_protected),
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}