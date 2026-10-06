package org.homeguard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R

/**
 * Asks to watch another home using its invite code (decisions D2 and D4).
 *
 * Deliberately only a code field. One house is one code and every person in it is already listed,
 * so the only thing a caregiver can legitimately add is another house's code. Offering anything
 * else here would suggest per-person sharing exists, and it does not.
 *
 * The wording says "ask", not "connect", because that is what it now does. Somebody at home has to
 * read out an approval code before anything is shared, and a button labelled Connect would promise
 * access this dialog cannot deliver.
 *
 * Dismisses without a callback on cancel, so an abandoned dialog cannot clear an existing error.
 */
@Composable
fun AddPersonDialog(
    isJoining: Boolean,
    errorMessage: String?,
    onJoin: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.caregiver_add_person),
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.caregiver_add_person_body),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 23.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        // Same alphabet the generator uses, so a code that cannot be typed cannot
                        // have been generated either.
                        code = input.uppercase()
                            .filter { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }
                            .take(8)
                        if (errorMessage != null) onDismissError()
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.monitor_setup_code_label),
                            fontSize = 16.sp
                        )
                    },
                    singleLine = true,
                    enabled = !isJoining,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 21.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(code) },
                enabled = code.length == 8 && !isJoining
            ) {
                Text(
                    text = stringResource(R.string.monitor_setup_ask),
                    fontSize = 16.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isJoining) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    fontSize = 16.sp
                )
            }
        }
    )
}