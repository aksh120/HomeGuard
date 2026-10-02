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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.homeguard.core.designsystem.theme.StatusSafe
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.ContactInfo
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.HomeInfo
import org.homeguard.core.model.MedicalInfo
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

@Composable
fun SetupAndSettingsScreen(
    currentConfig: AppConfig,
    isInitialSetup: Boolean,
    isAcousticEnabled: Boolean = false,
    onSaveConfig: (AppConfig) -> Unit,
    onToggleAcoustic: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    var deviceMode by remember { mutableStateOf(currentConfig.deviceMode) }
    var personName by remember { mutableStateOf(currentConfig.personName) }
    var personRole by remember { mutableStateOf(currentConfig.personRole) }

    var primaryContactName by remember { mutableStateOf(currentConfig.primaryContact.name) }
    var primaryContactPhone by remember { mutableStateOf(currentConfig.primaryContact.phoneOrHandle) }

    var secondaryContactName by remember { mutableStateOf(currentConfig.secondaryContact.name) }
    var secondaryContactPhone by remember { mutableStateOf(currentConfig.secondaryContact.phoneOrHandle) }

    var emergencyContactName by remember { mutableStateOf(currentConfig.emergencyContact.name) }
    var emergencyContactPhone by remember { mutableStateOf(currentConfig.emergencyContact.phoneOrHandle) }

    var localEmergencyNumber by remember { mutableStateOf(currentConfig.localEmergencyNumber) }

    var homeAddress by remember { mutableStateOf(currentConfig.homeInfo.address) }
    var buildingSecurity by remember { mutableStateOf(currentConfig.homeInfo.buildingSecurityContact) }
    var accessInstructions by remember { mutableStateOf(currentConfig.homeInfo.accessInstructions) }

    var bloodGroup by remember { mutableStateOf(currentConfig.medicalInfo.bloodGroup) }
    var allergies by remember { mutableStateOf(currentConfig.medicalInfo.allergies) }
    var medications by remember { mutableStateOf(currentConfig.medicalInfo.medications) }
    var doctorContact by remember { mutableStateOf(currentConfig.medicalInfo.doctorContact) }

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
            title = if (isInitialSetup) stringResource(R.string.setup_initialization)
            else stringResource(R.string.setup_sentinel_config),
            subtitle = if (isInitialSetup) stringResource(R.string.setup_architecture)
            else stringResource(R.string.setup_security_prefs),
            onBackClick = if (!isInitialSetup) onBackClick else null
        )

        // SECTION 1: OPERATIONAL MODE
        CyberFormSectionHeader(icon = Icons.Default.Shield, title = stringResource(R.string.setup_operational_role))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeSelectorTile(
                title = stringResource(R.string.setup_mode_protected_title),
                subtitle = stringResource(R.string.setup_mode_protected_sub),
                icon = Icons.Default.Shield,
                isSelected = deviceMode == DeviceMode.PROTECTED_DEVICE,
                onSelect = { deviceMode = DeviceMode.PROTECTED_DEVICE },
                modifier = Modifier.weight(1f)
            )
            ModeSelectorTile(
                title = stringResource(R.string.setup_mode_monitoring_title),
                subtitle = stringResource(R.string.setup_mode_monitoring_sub),
                icon = Icons.Default.Sensors,
                isSelected = deviceMode == DeviceMode.MONITORING_DEVICE,
                onSelect = { deviceMode = DeviceMode.MONITORING_DEVICE },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: IDENTITY
        CyberFormSectionHeader(icon = Icons.Default.Person, title = stringResource(R.string.setup_resident_profile))
        CyberGlassCard {
            CyberTextField(
                label = stringResource(R.string.setup_person_name_label),
                value = personName,
                onValueChange = { personName = it },
                placeholder = stringResource(R.string.setup_person_name_hint)
            )
            Spacer(modifier = Modifier.height(12.dp))
            CyberTextField(
                label = stringResource(R.string.setup_role_label),
                value = personRole,
                onValueChange = { personRole = it },
                placeholder = stringResource(R.string.setup_role_hint)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: EMERGENCY DISPATCH CONTACTS
        CyberFormSectionHeader(icon = Icons.Default.Phone, title = stringResource(R.string.setup_dispatch_links))
        CyberGlassCard {
            CyberTextField(
                label = stringResource(R.string.setup_primary_contact_label),
                value = primaryContactName,
                onValueChange = { primaryContactName = it },
                placeholder = stringResource(R.string.setup_primary_contact_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_primary_phone_label),
                value = primaryContactPhone,
                onValueChange = { primaryContactPhone = it },
                placeholder = stringResource(R.string.setup_primary_phone_hint)
            )
            Spacer(modifier = Modifier.height(14.dp))
            CyberTextField(
                label = stringResource(R.string.setup_secondary_contact_label),
                value = secondaryContactName,
                onValueChange = { secondaryContactName = it },
                placeholder = stringResource(R.string.setup_secondary_contact_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_secondary_phone_label),
                value = secondaryContactPhone,
                onValueChange = { secondaryContactPhone = it },
                placeholder = stringResource(R.string.setup_secondary_phone_hint)
            )
            Spacer(modifier = Modifier.height(14.dp))
            CyberTextField(
                label = stringResource(R.string.setup_emergency_contact_label),
                value = emergencyContactName,
                onValueChange = { emergencyContactName = it },
                placeholder = stringResource(R.string.setup_emergency_contact_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_emergency_contact_phone_label),
                value = emergencyContactPhone,
                onValueChange = { emergencyContactPhone = it },
                placeholder = stringResource(R.string.setup_primary_phone_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_emergency_number_label),
                value = localEmergencyNumber,
                onValueChange = { localEmergencyNumber = it },
                placeholder = stringResource(R.string.setup_emergency_number_hint)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 4: PHYSICAL ADDRESS & ACCESS
        CyberFormSectionHeader(icon = Icons.Default.Home, title = stringResource(R.string.setup_location_access))
        CyberGlassCard {
            CyberTextField(
                label = stringResource(R.string.setup_address_label),
                value = homeAddress,
                onValueChange = { homeAddress = it },
                placeholder = stringResource(R.string.setup_address_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_security_contact_label),
                value = buildingSecurity,
                onValueChange = { buildingSecurity = it },
                placeholder = stringResource(R.string.setup_security_contact_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_access_label),
                value = accessInstructions,
                onValueChange = { accessInstructions = it },
                placeholder = stringResource(R.string.setup_access_hint)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 5: BIOMETRIC / MEDICAL (OPTIONAL)
        CyberFormSectionHeader(icon = Icons.Default.MedicalServices, title = stringResource(R.string.setup_medical_header))
        CyberGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberTextField(
                    label = stringResource(R.string.setup_blood_group_label),
                    value = bloodGroup,
                    onValueChange = { bloodGroup = it },
                    placeholder = stringResource(R.string.setup_blood_group_hint),
                    modifier = Modifier.weight(1f)
                )
                CyberTextField(
                    label = stringResource(R.string.setup_allergies_label),
                    value = allergies,
                    onValueChange = { allergies = it },
                    placeholder = stringResource(R.string.setup_allergies_hint),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_medications_label),
                value = medications,
                onValueChange = { medications = it },
                placeholder = stringResource(R.string.setup_medications_hint)
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = stringResource(R.string.setup_doctor_label),
                value = doctorContact,
                onValueChange = { doctorContact = it },
                placeholder = stringResource(R.string.setup_doctor_hint)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Acoustic detection requires the microphone, so it is opt-in and explained in place.
        CyberFormSectionHeader(icon = Icons.Default.Mic, title = stringResource(R.string.setup_acoustic_header))
        CyberGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.acoustic_toggle_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.acoustic_toggle_desc),
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isAcousticEnabled,
                    onCheckedChange = { onToggleAcoustic() }
                )
            }
            if (isAcousticEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.acoustic_active_notice),
                    fontSize = 13.sp,
                    color = StatusSafe,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Commit Config Button
        CyberButton(
            text = if (isInitialSetup) stringResource(R.string.setup_initialize)
            else stringResource(R.string.setup_commit),
            onClick = {
                val updatedConfig = currentConfig.copy(
                    isSetupComplete = true,
                    deviceMode = deviceMode,
                    personName = personName,
                    personRole = personRole,
                    primaryContact = ContactInfo(name = primaryContactName, phoneOrHandle = primaryContactPhone),
                    secondaryContact = ContactInfo(name = secondaryContactName, phoneOrHandle = secondaryContactPhone),
                    emergencyContact = ContactInfo(name = emergencyContactName, phoneOrHandle = emergencyContactPhone),
                    localEmergencyNumber = localEmergencyNumber,
                    homeInfo = HomeInfo(
                        address = homeAddress,
                        buildingSecurityContact = buildingSecurity,
                        accessInstructions = accessInstructions
                    ),
                    medicalInfo = MedicalInfo(
                        allergies = allergies,
                        bloodGroup = bloodGroup,
                        medications = medications,
                        doctorContact = doctorContact
                    )
                )
                onSaveConfig(updatedConfig)
                onBackClick()
            },
            accentColor = Color(0xFF10B981),
            icon = Icons.Default.Check,
            minHeight = 64
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun ModeSelectorTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B)
    val bgColor = if (isSelected) Color(0xFF0A2218) else Color(0xFF0B101B)
    val iconColor = if (isSelected) Color(0xFF10B981) else Color(0xFF64748B)

    Surface(
        onClick = onSelect,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun CyberFormSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun CyberTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = 13.sp) },
        placeholder = { Text(text = placeholder, color = Color(0xFF475569), fontSize = 13.sp) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
            unfocusedContainerColor = Color(0xFF0B101B),
            focusedBorderColor = Color(0xFF10B981),
            unfocusedBorderColor = Color(0xFF1E293B),
            focusedLabelColor = Color(0xFF10B981),
            unfocusedLabelColor = Color(0xFF64748B)
        ),
        singleLine = true
    )
}
