package org.homeguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.homeguard.core.designsystem.theme.ActionPrimary
import org.homeguard.core.designsystem.theme.BorderSubtle
import org.homeguard.core.designsystem.theme.DarkBackground
import org.homeguard.core.designsystem.theme.DarkSurface
import org.homeguard.core.designsystem.theme.TextPrimary
import org.homeguard.core.designsystem.theme.TextSecondary
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.ContactInfo
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.HomeInfo
import org.homeguard.core.model.MedicalInfo
import org.homeguard.ui.components.LargeActionButton

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
            Column {
                Text(
                    text = if (isInitialSetup) stringResource(R.string.setup_title) else stringResource(R.string.action_settings),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (isInitialSetup) stringResource(R.string.setup_subtitle) else "Edit device configuration and emergency details.",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            if (!isInitialSetup) {
                OutlinedButton(onClick = onBackClick) {
                    Text(text = "Back", color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device Mode Selector
        SectionHeader(text = stringResource(R.string.setup_mode_label))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = deviceMode == DeviceMode.PROTECTED_DEVICE,
                        onClick = { deviceMode = DeviceMode.PROTECTED_DEVICE },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionPrimary)
                    )
                    Text(
                        text = stringResource(R.string.setup_mode_protected),
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = deviceMode == DeviceMode.MONITORING_DEVICE,
                        onClick = { deviceMode = DeviceMode.MONITORING_DEVICE },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionPrimary)
                    )
                    Text(
                        text = stringResource(R.string.setup_mode_monitoring),
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Protected Person
        SectionHeader(text = stringResource(R.string.setup_protected_person_header))
        StyledTextField(
            label = stringResource(R.string.setup_person_name_label),
            value = personName,
            onValueChange = { personName = it },
            placeholder = "e.g. Alex"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.setup_relationship_label),
            value = personRole,
            onValueChange = { personRole = it },
            placeholder = "e.g. Primary Resident, Resident, etc."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Contact
        SectionHeader(text = stringResource(R.string.info_primary_contact))
        StyledTextField(
            label = "Primary Contact Name",
            value = primaryContactName,
            onValueChange = { primaryContactName = it },
            placeholder = "e.g. Jordan"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = "Phone Number / Communication Link",
            value = primaryContactPhone,
            onValueChange = { primaryContactPhone = it },
            placeholder = "+1 555 0199 or https://..."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Secondary & Emergency Contacts
        SectionHeader(text = "Secondary & Emergency Contacts")
        StyledTextField(
            label = "Secondary Contact (Name & Phone)",
            value = secondaryContactPhone,
            onValueChange = { secondaryContactPhone = it },
            placeholder = "+1 555 0188"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.setup_emergency_number_label),
            value = localEmergencyNumber,
            onValueChange = { localEmergencyNumber = it },
            placeholder = "e.g. 911, 112, 999, etc."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Home Information
        SectionHeader(text = stringResource(R.string.info_home_header))
        StyledTextField(
            label = stringResource(R.string.info_home_address),
            value = homeAddress,
            onValueChange = { homeAddress = it },
            placeholder = "Street address, Apartment / Unit"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.info_building_security),
            value = buildingSecurity,
            onValueChange = { buildingSecurity = it },
            placeholder = "Security desk or building manager contact"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.info_home_instructions),
            value = accessInstructions,
            onValueChange = { accessInstructions = it },
            placeholder = "Gate code, key safe location, access instructions"
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Optional Medical
        SectionHeader(text = stringResource(R.string.info_medical_header))
        StyledTextField(
            label = stringResource(R.string.info_blood_group),
            value = bloodGroup,
            onValueChange = { bloodGroup = it },
            placeholder = "e.g. O+, A-, etc."
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.info_allergies),
            value = allergies,
            onValueChange = { allergies = it },
            placeholder = "e.g. Penicillin, Latex, None"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.info_medications),
            value = medications,
            onValueChange = { medications = it },
            placeholder = "Daily medications or critical prescriptions"
        )
        Spacer(modifier = Modifier.height(10.dp))
        StyledTextField(
            label = stringResource(R.string.info_doctor),
            value = doctorContact,
            onValueChange = { doctorContact = it },
            placeholder = "Dr. Smith / Community Clinic (+1 555...)"
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Save / Complete Setup Button
        LargeActionButton(
            text = if (isInitialSetup) stringResource(R.string.setup_complete_button) else stringResource(R.string.action_save),
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
            backgroundColor = ActionPrimary,
            borderColor = ActionPrimary,
            textColor = androidx.compose.ui.graphics.Color.White,
            minHeight = 68
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun StyledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder, color = TextSecondary) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface,
            focusedBorderColor = ActionPrimary,
            unfocusedBorderColor = BorderSubtle,
            focusedLabelColor = ActionPrimary,
            unfocusedLabelColor = TextSecondary
        ),
        singleLine = true
    )
}
