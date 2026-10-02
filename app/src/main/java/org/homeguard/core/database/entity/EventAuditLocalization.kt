package org.homeguard.core.database.entity

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.homeguard.R

/**
 * Audit summaries are persisted in Room so the record survives reinstalls and language changes.
 * They are stored as stable English constants and mapped to a localized string at display time,
 * which keeps the historical log readable without storing resource ids that would drift.
 */
object EventSummaries {
    const val EMERGENCY_SOS = "Emergency SOS Triggered"
    const val CHECK_IN = "Safety Check-In Completed"
    const val EMERGENCY_RESOLVED = "Emergency resolved by resident"
}

/** Localized display text for an audit entry, falling back to the stored value. */
@Composable
fun EventAuditEntity.localizedSummaryText(): String = when (summary) {
    EventSummaries.EMERGENCY_SOS -> stringResource(R.string.audit_emergency_sos)
    EventSummaries.CHECK_IN -> stringResource(R.string.audit_check_in)
    EventSummaries.EMERGENCY_RESOLVED -> stringResource(R.string.audit_emergency_resolved)
    else -> summary
}