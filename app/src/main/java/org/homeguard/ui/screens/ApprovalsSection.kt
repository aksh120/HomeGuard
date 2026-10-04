package org.homeguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionWarning
import org.homeguard.core.model.ApprovalKind
import org.homeguard.core.model.ApprovalPolicy
import org.homeguard.core.model.ApprovalRequest
import org.homeguard.core.model.HouseholdMember
import org.homeguard.ui.components.CyberGlassCard

/**
 * Approval requests awaiting a decision, and who is currently watching.
 *
 * Two things share this screen because they answer the same question from opposite sides: who has
 * access to this household, and who is asking for it. Decision Q4 requires the first to be always
 * visible; D4 requires the second to require a code.
 *
 * A request here has already been filed by someone. It is inert until a code is typed, so the
 * resident's options are to hand the code over, or decline. There is no one-tap removal (D6).
 */
@Composable
fun ApprovalsSection(
    approvals: List<ApprovalRequest>,
    watchers: List<HouseholdMember>,
    onDecline: (ApprovalRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val pending = remember(approvals) { approvals.filter { ApprovalPolicy.isAwaitingAction(it) } }

    Column(modifier = modifier) {
        WatchersCard(watchers)

        if (pending.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.approvals_heading),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            pending.forEachIndexed { index, request ->
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                ApprovalRequestCard(request = request, onDecline = { onDecline(request) })
            }
        }
    }
}

/**
 * Who is watching, always visible.
 *
 * A household with unnameable watchers is the exact failure the approval codes exist to prevent, so
 * this renders even when there are no pending requests.
 */
@Composable
private fun WatchersCard(watchers: List<HouseholdMember>) {
    CyberGlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.approvals_watched_by),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (watchers.isEmpty()) {
            Text(
                text = stringResource(R.string.approvals_no_watchers),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 23.sp
            )
        } else {
            watchers.forEach { watcher ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToReg,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.approvals_watcher_row),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * One pending request, showing the code the requester needs.
 *
 * The code is large and spaced because it is read aloud or copied into a message, which is the whole
 * consent step. No button here grants access: the resident cannot approve from their own phone.
 */
@Composable
private fun ApprovalRequestCard(
    request: ApprovalRequest,
    onDecline: () -> Unit
) {
    CyberGlassCard(borderColor = ActionWarning) {
        Text(
            text = stringResource(request.kind.titleStringRes()),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(request.kind.bodyStringRes()),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 23.sp
        )

        if (request.hasDisplayableCode) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.approvals_code_label),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = request.approvalCode.orEmpty().chunked(3).joinToString(" "),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.approvals_code_instructions),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDecline) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.approvals_decline),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Where a caregiver enters a code shown on the resident's phone.
 *
 * Deliberately separate from the join screen: joining a household and being approved to watch it are
 * different actions with different consent requirements.
 */
@Composable
fun ApprovalCodeEntry(
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (String) -> Unit,
    onDismissError: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    CyberGlassCard {
        Text(
            text = stringResource(R.string.approvals_enter_code_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.approvals_enter_code_body),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                // Same alphabet the server generator uses, bounded as it is typed.
                code = ApprovalPolicy.normaliseCode(input).take(ApprovalPolicy.CODE_LENGTH)
                if (errorMessage != null) onDismissError()
            },
            label = {
                Text(
                    text = stringResource(R.string.approvals_code_label),
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error,
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onSubmit(code) },
                enabled = ApprovalPolicy.isComplete(code) && !isSubmitting
            ) {
                Text(
                    text = stringResource(R.string.approvals_submit_code),
                    fontSize = 16.sp
                )
            }
            if (isSubmitting) {
                Spacer(modifier = Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun ApprovalKind.titleStringRes(): Int = when (this) {
    ApprovalKind.APPROVE_JOIN -> R.string.approvals_request_join_title
    ApprovalKind.APPROVE_REMOVE -> R.string.approvals_request_remove_title
    ApprovalKind.APPROVE_MODE_SWITCH -> R.string.approvals_request_switch_title
}

private fun ApprovalKind.bodyStringRes(): Int = when (this) {
    ApprovalKind.APPROVE_JOIN -> R.string.approvals_request_join_body
    ApprovalKind.APPROVE_REMOVE -> R.string.approvals_request_remove_body
    ApprovalKind.APPROVE_MODE_SWITCH -> R.string.approvals_request_switch_body
}