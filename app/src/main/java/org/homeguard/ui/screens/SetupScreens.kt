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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
    onSaveConfig: (AppConfig) -> Unit,
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
            title = if (isInitialSetup) "INITIALIZATION" else "SENTINEL CONFIG",
            subtitle = if (isInitialSetup) "AEGIS ARCHITECTURE SETUP" else "SECURITY & HARDWARE PREFERENCES",
            onBackClick = if (!isInitialSetup) onBackClick else null
        )

        // SECTION 1: OPERATIONAL MODE
        CyberFormSectionHeader(icon = Icons.Default.Shield, title = "OPERATIONAL ROLE")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeSelectorTile(
                title = "PROTECTED",
                subtitle = "Companion HUD",
                icon = Icons.Default.Shield,
                isSelected = deviceMode == DeviceMode.PROTECTED_DEVICE,
                onSelect = { deviceMode = DeviceMode.PROTECTED_DEVICE },
                modifier = Modifier.weight(1f)
            )
            ModeSelectorTile(
                title = "MONITORING",
                subtitle = "Caregiver Array",
                icon = Icons.Default.Sensors,
                isSelected = deviceMode == DeviceMode.MONITORING_DEVICE,
                onSelect = { deviceMode = DeviceMode.MONITORING_DEVICE },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: IDENTITY
        CyberFormSectionHeader(icon = Icons.Default.Person, title = "RESIDENT & SENTINEL PROFILE")
        CyberGlassCard {
            CyberTextField(
                label = "Protected Person Name",
                value = personName,
                onValueChange = { personName = it },
                placeholder = "e.g. Alex"
            )
            Spacer(modifier = Modifier.height(12.dp))
            CyberTextField(
                label = "Role / Home Status",
                value = personRole,
                onValueChange = { personRole = it },
                placeholder = "e.g. Primary Resident, Resident, Solo"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: EMERGENCY DISPATCH CONTACTS
        CyberFormSectionHeader(icon = Icons.Default.Phone, title = "COMMUNICATION DISPATCH LINKS")
        CyberGlassCard {
            CyberTextField(
                label = "Primary Contact Name",
                value = primaryContactName,
                onValueChange = { primaryContactName = it },
                placeholder = "e.g. Jordan"
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = "Primary Phone / Webhook URL",
                value = primaryContactPhone,
                onValueChange = { primaryContactPhone = it },
                placeholder = "+1 555 0199 or https://..."
            )
            Spacer(modifier = Modifier.height(14.dp))
            CyberTextField(
                label = "Secondary Contact (Optional)",
                value = secondaryContactPhone,
                onValueChange = { secondaryContactPhone = it },
                placeholder = "+1 555 0188"
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = "Local Emergency Dispatch Number",
                value = localEmergencyNumber,
                onValueChange = { localEmergencyNumber = it },
                placeholder = "e.g. 911, 112, 999"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 4: PHYSICAL ADDRESS & ACCESS
        CyberFormSectionHeader(icon = Icons.Default.Home, title = "LOCATION & PHYSICAL ACCESS")
        CyberGlassCard {
            CyberTextField(
                label = "Physical Address / Apartment",
                value = homeAddress,
                onValueChange = { homeAddress = it },
                placeholder = "Street address, Apartment / Unit"
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = "Building Security Contact",
                value = buildingSecurity,
                onValueChange = { buildingSecurity = it },
                placeholder = "Security desk or building manager"
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = "Access Instructions / Gate Code",
                value = accessInstructions,
                onValueChange = { accessInstructions = it },
                placeholder = "Gate pin, keybox safe, instructions"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 5: BIOMETRIC / MEDICAL (OPTIONAL)
        CyberFormSectionHeader(icon = Icons.Default.MedicalServices, title = "BIOMETRICS & MEDICAL (OPTIONAL)")
        CyberGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CyberTextField(
                    label = "Blood Group",
                    value = bloodGroup,
                    onValueChange = { bloodGroup = it },
                    placeholder = "e.g. O+, A-",
                    modifier = Modifier.weight(1f)
                )
                CyberTextField(
                    label = "Known Allergies",
                    value = allergies,
                    onValueChange = { allergies = it },
                    placeholder = "e.g. Penicillin",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = "Critical Medications",
                value = medications,
                onValueChange = { medications = it },
                placeholder = "Daily medications or insulin prescriptions"
            )
            Spacer(modifier = Modifier.height(10.dp))
            CyberTextField(
                label = "Primary Physician Contact",
                value = doctorContact,
                onValueChange = { doctorContact = it },
                placeholder = "Dr. Miller / Clinic (+1 555...)"
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Commit Config Button
        CyberButton(
            text = if (isInitialSetup) "INITIALIZE SENTINEL CORE" else "COMMIT CONFIGURATION",
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
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
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
            fontSize = 11.sp,
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
