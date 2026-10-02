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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.model.AppConfig
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

/**
 * Offline emergency reference (section 21).
 *
 * Everything here is available with no internet and no network. Empty fields are shown as
 * "Not set" rather than blank rows, so the resident can tell the difference between "nothing
 * recorded" and "nothing to record".
 */
@Composable
fun EmergencyInfoScreen(
    appConfig: AppConfig,
    onOpenFireGuide: () -> Unit,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.info_title),
            subtitle = stringResource(R.string.tile_offline_vault),
            onBackClick = onBackClick
        )

        SectionHeader(Icons.Default.Phone, stringResource(R.string.info_contacts_header))

        CyberGlassCard {
            ContactRow(
                stringResource(R.string.info_primary_contact),
                appConfig.primaryContact.name,
                appConfig.primaryContact.phoneOrHandle
            )
            ContactRow(
                stringResource(R.string.info_secondary_contact),
                appConfig.secondaryContact.name,
                appConfig.secondaryContact.phoneOrHandle
            )
            ContactRow(
                stringResource(R.string.info_emergency_contact),
                appConfig.emergencyContact.name,
                appConfig.emergencyContact.phoneOrHandle
            )
            ContactRow(
                stringResource(R.string.info_local_emergency_services),
                "",
                appConfig.localEmergencyNumber
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(Icons.Default.Home, stringResource(R.string.info_home_header))

        CyberGlassCard {
            DetailRow(stringResource(R.string.info_home_address), appConfig.homeInfo.address)
            DetailRow(
                stringResource(R.string.info_building_security),
                appConfig.homeInfo.buildingSecurityContact
            )
            DetailRow(
                stringResource(R.string.info_home_instructions),
                appConfig.homeInfo.accessInstructions
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader(
            Icons.Default.MedicalServices,
            stringResource(R.string.info_medical_header)
        )

        val medical = appConfig.medicalInfo
        val hasMedical = listOf(
            medical.allergies, medical.bloodGroup, medical.medications,
            medical.doctorContact, medical.notes
        ).any { it.isNotBlank() }

        CyberGlassCard {
            if (!hasMedical) {
                Text(
                    text = stringResource(R.string.info_no_medical),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 23.sp
                )
            } else {
                DetailRow(stringResource(R.string.info_allergies), medical.allergies)
                DetailRow(stringResource(R.string.info_blood_group), medical.bloodGroup)
                DetailRow(stringResource(R.string.info_medications), medical.medications)
                DetailRow(stringResource(R.string.info_doctor), medical.doctorContact)
                DetailRow(stringResource(R.string.info_medical_notes), medical.notes)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        CyberButton(
            text = stringResource(R.string.guide_fire_title),
            onClick = onOpenFireGuide,
            accentColor = ActionEmergency,
            icon = Icons.Default.Call
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Offline fire guide (section 22).
 *
 * Deliberately general. Section 22 forbids presenting unsafe instructions as professional advice,
 * so the screen carries an explicit caveat and defers to local emergency services.
 */
@Composable
fun FireGuideScreen(
    localEmergencyNumber: String,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = stringResource(R.string.guide_fire_title),
            onBackClick = onBackClick
        )

        if (localEmergencyNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            CyberGlassCard {
                Text(
                    text = stringResource(R.string.info_local_emergency_services),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = localEmergencyNumber,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        CyberGlassCard {
            StepRow(1, stringResource(R.string.guide_fire_step1))
            StepRow(2, stringResource(R.string.guide_fire_step2))
            StepRow(3, stringResource(R.string.guide_fire_step3))
            StepRow(4, stringResource(R.string.guide_fire_step4))
            StepRow(5, stringResource(R.string.guide_fire_step5))
            StepRow(6, stringResource(R.string.guide_fire_step6))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.guide_fire_caveat),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ContactRow(label: String, name: String, handle: String) {
    val notSet = stringResource(R.string.info_not_set)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = name.ifBlank { notSet },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (handle.isNotBlank()) {
                Text(
                    text = handle,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "$number",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = text,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 25.sp,
            modifier = Modifier.weight(1f)
        )
    }
}