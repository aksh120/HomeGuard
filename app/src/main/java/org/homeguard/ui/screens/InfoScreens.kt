package org.homeguard.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.ActionPrimary
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.model.AppConfig
import org.homeguard.ui.components.LargeActionButton

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
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.info_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            OutlinedButton(onClick = onBackClick) {
                Text(text = "Back", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Fire Guide Button
        LargeActionButton(
            text = stringResource(R.string.guide_fire_title),
            onClick = onOpenFireGuide,
            backgroundColor = DarkSurface,
            borderColor = ActionEmergency,
            textColor = ActionEmergency,
            icon = Icons.Default.LocalFireDepartment,
            minHeight = 60
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Emergency Contacts
        SectionHeader(text = stringResource(R.string.info_contacts_header))
        InfoCard {
            InfoRow(label = stringResource(R.string.info_primary_contact), value = formatContact(appConfig.primaryContact.name, appConfig.primaryContact.phoneOrHandle))
            if (appConfig.secondaryContact.name.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_secondary_contact), value = formatContact(appConfig.secondaryContact.name, appConfig.secondaryContact.phoneOrHandle))
            }
            if (appConfig.emergencyContact.name.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_emergency_contact), value = formatContact(appConfig.emergencyContact.name, appConfig.emergencyContact.phoneOrHandle))
            }
            if (appConfig.localEmergencyNumber.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_local_emergency_services), value = appConfig.localEmergencyNumber)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Home Information
        SectionHeader(text = stringResource(R.string.info_home_header))
        InfoCard {
            InfoRow(label = stringResource(R.string.info_home_address), value = appConfig.homeInfo.address.ifBlank { "Not configured" })
            if (appConfig.homeInfo.buildingSecurityContact.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_building_security), value = appConfig.homeInfo.buildingSecurityContact)
            }
            if (appConfig.homeInfo.accessInstructions.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_home_instructions), value = appConfig.homeInfo.accessInstructions)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Medical Information (Optional)
        SectionHeader(text = stringResource(R.string.info_medical_header))
        InfoCard {
            if (appConfig.medicalInfo.bloodGroup.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_blood_group), value = appConfig.medicalInfo.bloodGroup)
            }
            if (appConfig.medicalInfo.allergies.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_allergies), value = appConfig.medicalInfo.allergies)
            }
            if (appConfig.medicalInfo.medications.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_medications), value = appConfig.medicalInfo.medications)
            }
            if (appConfig.medicalInfo.doctorContact.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_doctor), value = appConfig.medicalInfo.doctorContact)
            }
            if (appConfig.medicalInfo.notes.isNotBlank()) {
                InfoRow(label = stringResource(R.string.info_medical_notes), value = appConfig.medicalInfo.notes)
            }
            if (appConfig.medicalInfo.bloodGroup.isBlank() &&
                appConfig.medicalInfo.allergies.isBlank() &&
                appConfig.medicalInfo.medications.isBlank()) {
                Text(
                    text = "No medical information recorded (optional).",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun FireGuideScreen(
    localEmergencyNumber: String,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = ActionEmergency,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.guide_fire_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            OutlinedButton(onClick = onBackClick) {
                Text(text = "Back", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val steps = listOf(
            stringResource(R.string.guide_fire_step1),
            stringResource(R.string.guide_fire_step2),
            stringResource(R.string.guide_fire_step3),
            stringResource(R.string.guide_fire_step4),
            stringResource(R.string.guide_fire_step5),
            stringResource(R.string.guide_fire_step6)
        )

        steps.forEachIndexed { index, step ->
            FireStepCard(number = index + 1, instruction = step)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (localEmergencyNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, ActionEmergency)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Emergency Services",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionEmergency
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = localEmergencyNumber,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(text = value, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FireStepCard(number: Int, instruction: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(18.dp),
                color = ActionEmergency
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$number",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = instruction,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp
            )
        }
    }
}

private fun formatContact(name: String, phoneOrHandle: String): String {
    return if (name.isNotBlank() && phoneOrHandle.isNotBlank()) {
        "$name ($phoneOrHandle)"
    } else {
        name.ifBlank { phoneOrHandle }.ifBlank { "Not set" }
    }
}
