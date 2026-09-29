package org.homeguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.homeguard.R
import org.homeguard.core.designsystem.theme.ActionEmergency
import org.homeguard.core.designsystem.theme.StatusEmergency
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.model.AppConfig
import org.homeguard.ui.components.CyberBadge
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

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
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF060911),
                        Color(0xFF090D16),
                        Color(0xFF0D1322)
                    )
                )
            )
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = "SAFETY DOSSIER",
            subtitle = "OFFLINE VAULT • PERSISTED",
            onBackClick = onBackClick
        )

        // Quick Fire Evacuation Launcher
        CyberButton(
            text = "FIRE EVACUATION PROTOCOL",
            onClick = onOpenFireGuide,
            accentColor = StatusEmergency,
            icon = Icons.Default.LocalFireDepartment,
            minHeight = 58
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 1: EMERGENCY CONTACTS
        DossierSectionTitle(icon = Icons.Default.Phone, title = "EMERGENCY CONTACTS")
        CyberGlassCard {
            DossierRow(
                label = stringResource(R.string.info_primary_contact),
                value = formatContact(appConfig.primaryContact.name, appConfig.primaryContact.phoneOrHandle),
                accent = Color(0xFF38BDF8)
            )
            if (appConfig.secondaryContact.name.isNotBlank() || appConfig.secondaryContact.phoneOrHandle.isNotBlank()) {
                DossierDivider()
                DossierRow(
                    label = stringResource(R.string.info_secondary_contact),
                    value = formatContact(appConfig.secondaryContact.name, appConfig.secondaryContact.phoneOrHandle)
                )
            }
            if (appConfig.emergencyContact.name.isNotBlank() || appConfig.emergencyContact.phoneOrHandle.isNotBlank()) {
                DossierDivider()
                DossierRow(
                    label = stringResource(R.string.info_emergency_contact),
                    value = formatContact(appConfig.emergencyContact.name, appConfig.emergencyContact.phoneOrHandle),
                    accent = StatusEmergency
                )
            }
            if (appConfig.localEmergencyNumber.isNotBlank()) {
                DossierDivider()
                DossierRow(
                    label = stringResource(R.string.info_local_emergency_services),
                    value = appConfig.localEmergencyNumber,
                    accent = StatusEmergency
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: HOME DISPATCH DETAILS
        DossierSectionTitle(icon = Icons.Default.Home, title = "HOME DISPATCH & ACCESS")
        CyberGlassCard {
            DossierRow(
                label = stringResource(R.string.info_home_address),
                value = appConfig.homeInfo.address.ifBlank { "Address not configured" }
            )
            if (appConfig.homeInfo.buildingSecurityContact.isNotBlank()) {
                DossierDivider()
                DossierRow(
                    label = stringResource(R.string.info_building_security),
                    value = appConfig.homeInfo.buildingSecurityContact
                )
            }
            if (appConfig.homeInfo.accessInstructions.isNotBlank()) {
                DossierDivider()
                DossierRow(
                    label = stringResource(R.string.info_home_instructions),
                    value = appConfig.homeInfo.accessInstructions
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: MEDICAL DOSSIER
        DossierSectionTitle(icon = Icons.Default.MedicalServices, title = "MEDICAL & BIOMETRIC PROFILE")
        CyberGlassCard {
            val hasMedical = appConfig.medicalInfo.bloodGroup.isNotBlank() ||
                    appConfig.medicalInfo.allergies.isNotBlank() ||
                    appConfig.medicalInfo.medications.isNotBlank()

            if (hasMedical) {
                if (appConfig.medicalInfo.bloodGroup.isNotBlank()) {
                    DossierRow(label = stringResource(R.string.info_blood_group), value = appConfig.medicalInfo.bloodGroup, accent = Color(0xFFF43F5E))
                }
                if (appConfig.medicalInfo.allergies.isNotBlank()) {
                    DossierDivider()
                    DossierRow(label = stringResource(R.string.info_allergies), value = appConfig.medicalInfo.allergies)
                }
                if (appConfig.medicalInfo.medications.isNotBlank()) {
                    DossierDivider()
                    DossierRow(label = stringResource(R.string.info_medications), value = appConfig.medicalInfo.medications)
                }
                if (appConfig.medicalInfo.doctorContact.isNotBlank()) {
                    DossierDivider()
                    DossierRow(label = stringResource(R.string.info_doctor), value = appConfig.medicalInfo.doctorContact)
                }
            } else {
                Text(
                    text = "No medical profile recorded. Configure in Settings if relevant for first responders.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E0707),
                        Color(0xFF0F0404),
                        Color(0xFF060911)
                    )
                )
            )
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = "FIRE EVACUATION",
            subtitle = "CRITICAL SAFETY PLAYBOOK",
            onBackClick = onBackClick
        )

        // Emergency Hot-Dial Banner
        if (localEmergencyNumber.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2B0A0A),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, StatusEmergency)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LOCAL EMERGENCY DISPATCH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = StatusEmergency
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = localEmergencyNumber,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(StatusEmergency),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        val steps = listOf(
            stringResource(R.string.guide_fire_step1),
            stringResource(R.string.guide_fire_step2),
            stringResource(R.string.guide_fire_step3),
            stringResource(R.string.guide_fire_step4),
            stringResource(R.string.guide_fire_step5),
            stringResource(R.string.guide_fire_step6)
        )

        Text(
            text = "EVACUATION STEPS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        steps.forEachIndexed { index, step ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2B0E0E))
                            .border(1.dp, StatusEmergency.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = StatusEmergency,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = step,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DossierSectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun DossierRow(label: String, value: String, accent: Color = Color.White) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DossierDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(Color(0xFF1E293B))
    )
}

private fun formatContact(name: String, phoneOrHandle: String): String {
    return if (name.isNotBlank() && phoneOrHandle.isNotBlank()) {
        "$name  •  $phoneOrHandle"
    } else {
        name.ifBlank { phoneOrHandle }.ifBlank { "Not set" }
    }
}
