package org.homeguard.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.model.AppProfile
import org.homeguard.ui.components.CyberTopAppBar

/**
 * First-run fork between the two profiles.
 *
 * This screen exists because "At home" and "Monitoring" are different products, not two settings on
 * one product. Previously the choice was a two-tile toggle inside the protected setup form, which
 * meant both options produced the same form — the exact symptom that made the two profiles feel
 * interchangeable.
 *
 * There is no back button and no way past this screen: an unchosen install can reach nothing else.
 * Once a profile is chosen it is persisted, and Phase 3 governs any later change (a resident phone
 * converting to a monitoring phone requires caregiver approval).
 */
@Composable
fun RoleSelectionScreen(
    onSelectProfile: (AppProfile) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            CyberTopAppBar(
                title = stringResource(R.string.profile_choice_title),
                subtitle = stringResource(R.string.profile_choice_subtitle)
            )

            Spacer(modifier = Modifier.height(24.dp))

            ProfileOptionCard(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.profile_choice_protected_title),
                body = stringResource(R.string.profile_choice_protected_body),
                onClick = { onSelectProfile(AppProfile.PROTECTED) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProfileOptionCard(
                icon = Icons.Default.PersonSearch,
                title = stringResource(R.string.profile_choice_monitoring_title),
                body = stringResource(R.string.profile_choice_monitoring_body),
                onClick = { onSelectProfile(AppProfile.MONITORING) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.profile_choice_note),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * One large tappable card.
 *
 * Sized for a resident to hit reliably and labelled with a single spoken description, because the
 * first thing a screen reader reads on this screen is the choice itself, not the heading.
 */
@Composable
private fun ProfileOptionCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit
) {
    val spoken = "$title. $body"
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                // The whole card already announces title and body; a second label would duplicate it.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clearAndSetSemantics { }
            )
        }
    }
}