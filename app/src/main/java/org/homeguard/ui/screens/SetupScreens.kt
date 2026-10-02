package org.homeguard.ui.screens

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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import org.homeguard.R
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.BatteryThresholds
import org.homeguard.core.model.CheckInSchedule
import org.homeguard.core.model.ContactInfo
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.HomeInfo
import org.homeguard.core.model.MedicalInfo
import org.homeguard.core.model.NightModeSchedule
import org.homeguard.ui.components.CyberButton
import org.homeguard.ui.components.CyberGlassCard
import org.homeguard.ui.components.CyberTopAppBar

/**
 * Setup and settings.
 *
 * Serves both roles: first-run configuration (section 9) and later editing. Every field is optional
 * and nothing is pre-filled, per section 50.
 */
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

    var checkInEnabled by remember { mutableStateOf(currentConfig.checkInSchedule.enabled) }
    var checkInTimes by remember {
        mutableStateOf(currentConfig.checkInSchedule.dailyTimes)
    }
    var checkInTimeout by remember { mutableStateOf(currentConfig.checkInSchedule.timeoutMinutes) }
    var showTimePicker by remember { mutableStateOf(false) }

    var nightEnabled by remember { mutableStateOf(currentConfig.nightModeSchedule.enabled) }
    var nightStart by remember { mutableStateOf(currentConfig.nightModeSchedule.startMinute) }
    var nightEnd by remember { mutableStateOf(currentConfig.nightModeSchedule.endMinute) }

    var batteryLow by remember { mutableStateOf(currentConfig.batteryThresholds.lowPercent.toString()) }
    var batteryCritical by remember {
        mutableStateOf(currentConfig.batteryThresholds.criticalPercent.toString())
    }

    val lowValue = batteryLow.toIntOrNull() ?: 0
    val criticalValue = batteryCritical.toIntOrNull() ?: 0
    val batteryValid = BatteryThresholds(lowValue, criticalValue).isValid

    val nightSchedule = NightModeSchedule(nightEnabled, nightStart, nightEnd)
    val checkInSchedule = CheckInSchedule(checkInEnabled, checkInTimes, checkInTimeout)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        CyberTopAppBar(
            title = if (isInitialSetup) {
                stringResource(R.string.setup_initialization)
            } else {
                stringResource(R.string.setup_sentinel_config)
            },
            subtitle = if (isInitialSetup) {
                stringResource(R.string.setup_architecture)
            } else {
                stringResource(R.string.setup_security_prefs)
            },
            onBackClick = if (!isInitialSetup) onBackClick else null
        )

        FormSection(icon = Icons.Default.Shield, title = stringResource(R.string.setup_operational_role))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeSelectorTile(
                title = stringResource(R.string.setup_mode_protected_title),
                subtitle = stringResource(R.string.setup_mode_protected_sub),
                isSelected = deviceMode == DeviceMode.PROTECTED_DEVICE,
                onSelect = { deviceMode = DeviceMode.PROTECTED_DEVICE },
                modifier = Modifier.weight(1f)
            )
            ModeSelectorTile(
                title = stringResource(R.string.setup_mode_monitoring_title),
                subtitle = stringResource(R.string.setup_mode_monitoring_sub),
                isSelected = deviceMode == DeviceMode.MONITORING_DEVICE,
                onSelect = { deviceMode = DeviceMode.MONITORING_DEVICE },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FormSection(icon = Icons.Default.Person, title = stringResource(R.string.setup_resident_profile))
        CyberGlassCard {
            LabeledField(
                label = stringResource(R.string.setup_person_name_label),
                value = personName,
                onValueChange = { personName = it }
            )
            Spacer(modifier = Modifier.height(12.dp))
            LabeledField(
                label = stringResource(R.string.setup_role_label),
                value = personRole,
                onValueChange = { personRole = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FormSection(icon = Icons.Default.Phone, title = stringResource(R.string.setup_dispatch_links))
        CyberGlassCard {
            LabeledField(
                label = stringResource(R.string.setup_primary_contact_label),
                value = primaryContactName,
                onValueChange = { primaryContactName = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_primary_phone_label),
                value = primaryContactPhone,
                onValueChange = { primaryContactPhone = it },
                keyboardType = KeyboardType.Text
            )
            Spacer(modifier = Modifier.height(14.dp))
            LabeledField(
                label = stringResource(R.string.setup_secondary_contact_label),
                value = secondaryContactName,
                onValueChange = { secondaryContactName = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_secondary_phone_label),
                value = secondaryContactPhone,
                onValueChange = { secondaryContactPhone = it },
                keyboardType = KeyboardType.Text
            )
            Spacer(modifier = Modifier.height(14.dp))
            LabeledField(
                label = stringResource(R.string.setup_emergency_contact_label),
                value = emergencyContactName,
                onValueChange = { emergencyContactName = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_emergency_contact_phone_label),
                value = emergencyContactPhone,
                onValueChange = { emergencyContactPhone = it },
                keyboardType = KeyboardType.Phone
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_emergency_number_label),
                value = localEmergencyNumber,
                onValueChange = { localEmergencyNumber = it },
                keyboardType = KeyboardType.Phone
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FormSection(icon = Icons.Default.Place, title = stringResource(R.string.setup_location_access))
        CyberGlassCard {
            LabeledField(
                label = stringResource(R.string.setup_address_label),
                value = homeAddress,
                onValueChange = { homeAddress = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_security_contact_label),
                value = buildingSecurity,
                onValueChange = { buildingSecurity = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_access_label),
                value = accessInstructions,
                onValueChange = { accessInstructions = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        FormSection(icon = Icons.Default.MedicalServices, title = stringResource(R.string.setup_medical_header))
        CyberGlassCard {
            Text(
                text = stringResource(R.string.setup_medical_caveat),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField(
                    label = stringResource(R.string.setup_blood_group_label),
                    value = bloodGroup,
                    onValueChange = { bloodGroup = it },
                    modifier = Modifier.weight(1f)
                )
                LabeledField(
                    label = stringResource(R.string.setup_allergies_label),
                    value = allergies,
                    onValueChange = { allergies = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_medications_label),
                value = medications,
                onValueChange = { medications = it }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LabeledField(
                label = stringResource(R.string.setup_doctor_label),
                value = doctorContact,
                onValueChange = { doctorContact = it },
                keyboardType = KeyboardType.Phone
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Scheduled safety checks (section 14).
        FormSection(
            icon = Icons.Default.NotificationsActive,
            title = stringResource(R.string.setup_checkin_header)
        )
        CyberGlassCard {
            Text(
                text = stringResource(R.string.setup_checkin_desc),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            ToggleRow(
                label = stringResource(R.string.setup_checkin_enabled),
                checked = checkInEnabled,
                onCheckedChange = { checkInEnabled = it }
            )

            if (checkInEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.setup_checkin_times),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                checkInTimes.sorted().forEach { minute ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d:%02d".format(minute / 60, minute % 60),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { checkInTimes = checkInTimes - minute },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setup_checkin_remove_time),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
                if (checkInTimes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.setup_checkin_no_times),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setup_checkin_add_time),
                        fontSize = 17.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LabeledField(
                    label = stringResource(R.string.setup_checkin_timeout_label),
                    value = checkInTimeout.toString(),
                    onValueChange = { checkInTimeout = it.toIntOrNull()?.coerceIn(5, 720) ?: 5 },
                    keyboardType = KeyboardType.Number
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Night mode window (section 16).
        FormSection(icon = Icons.Default.Bedtime, title = stringResource(R.string.setup_night_header))
        CyberGlassCard {
            Text(
                text = stringResource(R.string.setup_night_desc),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            ToggleRow(
                label = stringResource(R.string.setup_night_enabled),
                checked = nightEnabled,
                onCheckedChange = { nightEnabled = it }
            )
            if (nightEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                TimeStepper(
                    label = stringResource(R.string.setup_night_start),
                    minute = nightStart,
                    onChange = { nightStart = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                TimeStepper(
                    label = stringResource(R.string.setup_night_end),
                    minute = nightEnd,
                    onChange = { nightEnd = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Battery thresholds (section 17).
        FormSection(icon = Icons.Default.BatteryAlert, title = stringResource(R.string.setup_battery_header))
        CyberGlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField(
                    label = stringResource(R.string.setup_battery_low_label),
                    value = batteryLow,
                    onValueChange = { batteryLow = it.filter(Char::isDigit).take(3) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                LabeledField(
                    label = stringResource(R.string.setup_battery_critical_label),
                    value = batteryCritical,
                    onValueChange = { batteryCritical = it.filter(Char::isDigit).take(3) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!batteryValid) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.setup_battery_invalid),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        CyberButton(
            text = if (isInitialSetup) {
                stringResource(R.string.setup_initialize)
            } else {
                stringResource(R.string.setup_commit)
            },
            onClick = {
                onSaveConfig(
                    currentConfig.copy(
                        isSetupComplete = true,
                        deviceMode = deviceMode,
                        personName = personName,
                        personRole = personRole,
                        primaryContact = ContactInfo(
                            name = primaryContactName,
                            phoneOrHandle = primaryContactPhone
                        ),
                        secondaryContact = ContactInfo(
                            name = secondaryContactName,
                            phoneOrHandle = secondaryContactPhone
                        ),
                        emergencyContact = ContactInfo(
                            name = emergencyContactName,
                            phoneOrHandle = emergencyContactPhone
                        ),
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
                        ),
                        isNightModeEnabled = nightEnabled,
                        checkInSchedule = checkInSchedule,
                        nightModeSchedule = nightSchedule,
                        batteryThresholds = if (batteryValid) {
                            BatteryThresholds(lowValue, criticalValue)
                        } else {
                            currentConfig.batteryThresholds
                        }
                    )
                )
                onBackClick()
            },
            accentColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.Check
        )

        Spacer(modifier = Modifier.height(30.dp))
    }

    if (showTimePicker) {
        SimpleTimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { minute ->
                // Two checks a day is the practical maximum for a comfort prompt.
                if (checkInTimes.size < 2 && !checkInTimes.contains(minute)) {
                    checkInTimes = checkInTimes + minute
                }
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun FormSection(icon: ImageVector, title: String) {
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
private fun ModeSelectorTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            }
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Minute-of-day stepper.
 *
 * Deliberately not a dropdown or a clock widget: on the low-end hardware in the brief, a simple
 * +/- control is the fastest and most reliable thing to operate.
 */
@Composable
private fun TimeStepper(
    label: String,
    minute: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { onChange((minute + 1439) % 1440) },
            modifier = Modifier
                .size(56.dp)
        ) { Text(text = "−", fontSize = 22.sp) }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "%02d:%02d".format(minute / 60, minute % 60),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedButton(
            onClick = { onChange((minute + 1) % 1440) },
            modifier = Modifier.size(56.dp)
        ) { Text(text = "+", fontSize = 22.sp) }
    }
}

/**
 * Minimal time picker.
 *
 * A two-dial dialog would be more conventional, but a plain list of common times plus +/- fine
 * adjustment avoids a fragile nested-picker dialog and works with TalkBack.
 */
@Composable
private fun SimpleTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(
        7 * 60, 8 * 60, 9 * 60, 10 * 60, 12 * 60,
        14 * 60, 17 * 60, 18 * 60, 20 * 60, 21 * 60
    )
    var selected by remember { mutableStateOf(options.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.setup_checkin_times),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                options.forEach { minute ->
                    TextButton(
                        onClick = { selected = minute },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "%02d:%02d".format(minute / 60, minute % 60),
                            fontSize = 18.sp,
                            fontWeight = if (selected == minute) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TimeStepper(
                    label = stringResource(R.string.setup_checkin_times),
                    minute = selected,
                    onChange = { selected = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(text = stringResource(R.string.action_confirm), fontSize = 17.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), fontSize = 17.sp)
            }
        }
    )
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = 15.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}