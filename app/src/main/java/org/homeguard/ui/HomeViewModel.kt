package org.homeguard.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.SafetyRepository
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.SafetyStatus
import org.homeguard.core.notifications.SafetyNotifier
import org.homeguard.core.sensors.AcousticAlarmDetector
import org.homeguard.core.sensors.FallAndMotionDetector
import org.homeguard.core.sync.SyncWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class NavigationDestination {
    data object Home : NavigationDestination()
    data object EmergencyCountdown : NavigationDestination()
    data object EmergencyActive : NavigationDestination()
    data object NightMode : NavigationDestination()
    data object PowerCut : NavigationDestination()
    data object EmergencyInfo : NavigationDestination()
    data object FireGuide : NavigationDestination()
    data object MonitoringDashboard : NavigationDestination()
    data object Settings : NavigationDestination()
    data object Setup : NavigationDestination()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as org.homeguard.HomeGuardApplication
    private val container = app.container
    private val preferencesManager = container.preferencesManager
    private val eventRepository: EventRepository = container.eventRepository
    private val safetyRepository: SafetyRepository = container.safetyRepository

    private val batteryMonitor = container.batteryMonitor
    private val connectivityMonitor = container.connectivityMonitor
    private val torchManager = container.torchManager
    private val alarmManager = container.alarmManager
    private val communicationManager = container.communicationManager
    private val caregiverBeaconManager = container.caregiverBeaconManager
    private val notifier = SafetyNotifier(application)

    private val _fallDetectedAlert = MutableStateFlow(false)
    val fallDetectedAlert: StateFlow<Boolean> = _fallDetectedAlert.asStateFlow()

    private val _smokeAlarmDetectedAlert = MutableStateFlow(false)
    val smokeAlarmDetectedAlert: StateFlow<Boolean> = _smokeAlarmDetectedAlert.asStateFlow()

    private val _isAcousticEnabled = MutableStateFlow(false)
    val isAcousticEnabled: StateFlow<Boolean> = _isAcousticEnabled.asStateFlow()

    private val acousticAlarmDetector = AcousticAlarmDetector(
        context = application,
        onAlarmSoundDetected = {
            _smokeAlarmDetectedAlert.value = true
        }
    )

    val isAcousticListening: StateFlow<Boolean> = acousticAlarmDetector.isListening

    private val fallAndMotionDetector = FallAndMotionDetector(
        context = application,
        onFallDetected = {
            _fallDetectedAlert.value = true
        },
        onSignificantMovement = {
            // Implicit passive check-in when user moves/handles phone at home
            viewModelScope.launch {
                val deviceId = preferencesManager.ensureDeviceId()
                safetyRepository.recordCheckIn(deviceId)
            }
        }
    )

    val safetyStatus: StateFlow<SafetyStatus> = safetyRepository.safetyStatusFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SafetyStatus.UNKNOWN)

    val batteryInfo: StateFlow<BatteryInfo> = batteryMonitor.batteryInfo
    val connectivityInfo = connectivityMonitor.connectivityInfo
    val isTorchOn: StateFlow<Boolean> = torchManager.isTorchOn
    val isAlarmPlaying: StateFlow<Boolean> = alarmManager.isAlarmPlaying

    val appConfig: StateFlow<AppConfig> = preferencesManager.appConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    val lastCheckInFormatted: StateFlow<String> = safetyRepository.lastCheckInTimestampFlow.map { ts ->
        if (ts != null && ts > 0) {
            val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
            sdf.format(Date(ts))
        } else {
            getApplication<Application>().getString(org.homeguard.R.string.last_checkin_none)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastMotionAgoFormatted: StateFlow<String> = fallAndMotionDetector.telemetry.map { telemetry ->
        val elapsedMinutes = (System.currentTimeMillis() - telemetry.lastMovementTimestamp) / 60_000L
        when {
            elapsedMinutes <= 0 -> getApplication<Application>()
                .getString(org.homeguard.R.string.last_motion_just_now)
            elapsedMinutes < 60 -> getApplication<Application>()
                .getString(org.homeguard.R.string.last_motion_minutes, elapsedMinutes)
            else -> getApplication<Application>()
                .getString(org.homeguard.R.string.last_motion_hours, elapsedMinutes / 60)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val recentEvents = eventRepository.recentAuditsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingSyncCount = eventRepository.pendingCountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _currentDestination = MutableStateFlow<NavigationDestination>(NavigationDestination.Home)
    val currentDestination: StateFlow<NavigationDestination> = _currentDestination.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(5)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _communicationError = MutableStateFlow<String?>(null)
    val communicationError: StateFlow<String?> = _communicationError.asStateFlow()

    private var countdownJob: Job? = null

    /** Tracks which battery thresholds have already alerted, so each fires once per discharge. */
    private var lowBatteryNotified = false
    private var criticalBatteryNotified = false

    init {
        viewModelScope.launch {
            preferencesManager.appConfigFlow.collect { config ->
                if (!config.isSetupComplete && _currentDestination.value == NavigationDestination.Home) {
                    _currentDestination.value = NavigationDestination.Setup
                }
            }
        }

        viewModelScope.launch {
            // P0-4: identity must exist before any event is recorded.
            val deviceId = preferencesManager.ensureDeviceId()
            // P0-5: the remote schema has NOT NULL foreign keys into `devices`, so registration
            // must happen before the first event or every insert fails on the constraint.
            val config = appConfig.value
            container.remoteSyncDataSource.registerDevice(
                deviceId = deviceId,
                deviceMode = config.deviceMode.name,
                personName = config.personName,
                personRole = config.personRole
            ).onFailure {
                android.util.Log.w(
                    "HomeGuard",
                    "Device registration failed; events will stay queued until it succeeds",
                    it
                )
            }
        }

        SyncWorker.schedulePeriodicSync(application)

        fallAndMotionDetector.start()

        // P0-6: the microphone is opt-in only. Never started automatically.
        observeBatteryForNotifications()
    }

    private fun observeBatteryForNotifications() {
        viewModelScope.launch {
            batteryMonitor.batteryInfo.collect { info ->
                if (info.isCharging) {
                    lowBatteryNotified = false
                    criticalBatteryNotified = false
                    notifier.clearBatteryAlerts()
                    return@collect
                }
                if (info.isCritical) {
                    if (!criticalBatteryNotified) {
                        criticalBatteryNotified = true
                        lowBatteryNotified = true
                        notifier.notifyBatteryCritical(info.level)
                    }
                } else if (info.isLow) {
                    if (!lowBatteryNotified) {
                        lowBatteryNotified = true
                        notifier.notifyBatteryLow(info.level)
                    }
                }
            }
        }
    }

    /** Called only after the user explicitly enables acoustic detection and grants the mic. */
    fun onAcousticPermissionGranted() {
        _isAcousticEnabled.value = true
        acousticAlarmDetector.startListening()
    }

    fun onAcousticPermissionDenied() {
        _isAcousticEnabled.value = false
        acousticAlarmDetector.stopListening()
    }

    fun setAcousticEnabled(enabled: Boolean) {
        if (enabled) {
            onAcousticPermissionGranted()
        } else {
            onAcousticPermissionDenied()
        }
    }

    fun navigateTo(dest: NavigationDestination) {
        _currentDestination.value = dest
    }

    fun confirmCheckIn() {
        viewModelScope.launch {
            val deviceId = preferencesManager.ensureDeviceId()
            safetyRepository.recordCheckIn(deviceId)
            caregiverBeaconManager.playRadarChirp()
            notifier.notifyCheckInRecorded()
            SyncWorker.triggerImmediateSync(getApplication())
        }
    }

    fun pingCaregiverBeacon() {
        viewModelScope.launch {
            caregiverBeaconManager.playRadarChirp()
        }
    }

    fun dismissFallAlert() {
        _fallDetectedAlert.value = false
    }

    fun dismissSmokeAlert() {
        _smokeAlarmDetectedAlert.value = false
    }

    fun startEmergencyCountdown() {
        _countdownSeconds.value = 5
        _currentDestination.value = NavigationDestination.EmergencyCountdown
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _countdownSeconds.value = i
                delay(1000)
            }
            triggerEmergencyNow()
        }
    }

    fun cancelEmergencyCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _currentDestination.value = NavigationDestination.Home
    }

    fun triggerEmergencyNow() {
        countdownJob?.cancel()
        viewModelScope.launch {
            val deviceId = preferencesManager.ensureDeviceId()
            safetyRepository.triggerEmergency(deviceId)
            alarmManager.startAlarm()
            _currentDestination.value = NavigationDestination.EmergencyActive
            if (!connectivityMonitor.connectivityInfo.value.isInternetAvailable) {
                notifier.notifyEmergencyQueuedOffline()
            }
            SyncWorker.triggerImmediateSync(getApplication())
        }
    }

    fun stopAlarmAndResolve() {
        alarmManager.stopAlarm()
        viewModelScope.launch {
            // P1-3: resolving records a deliberate check-in so SAFE is earned, not assumed.
            val deviceId = preferencesManager.ensureDeviceId()
            safetyRepository.resolveEmergency(deviceId)
            _currentDestination.value = NavigationDestination.Home
        }
    }

    fun toggleFlashlight() {
        torchManager.toggleTorch()
    }

    fun onTorchPermissionDenied() {
        torchManager.setTorch(false)
    }

    fun toggleNightMode() {
        val current = appConfig.value.isNightModeEnabled
        viewModelScope.launch {
            preferencesManager.setNightMode(!current)
            if (!current) {
                _currentDestination.value = NavigationDestination.NightMode
            } else {
                _currentDestination.value = NavigationDestination.Home
            }
        }
    }

    fun initiateCallToPrimary(context: Context) {
        val contact = appConfig.value.primaryContact
        val result = communicationManager.initiateCall(context, contact)
        if (result is org.homeguard.core.communication.CommunicationResult.Unavailable) {
            _communicationError.value = result.message
        }
    }

    fun dismissCommunicationError() {
        _communicationError.value = null
    }

    fun saveConfig(config: AppConfig) {
        viewModelScope.launch {
            val deviceId = preferencesManager.ensureDeviceId()
            preferencesManager.saveConfig(config.copy(deviceId = deviceId))
            // Keep the remote device row in step with local profile changes.
            container.remoteSyncDataSource.registerDevice(
                deviceId = deviceId,
                deviceMode = config.deviceMode.name,
                personName = config.personName,
                personRole = config.personRole
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        fallAndMotionDetector.stop()
        acousticAlarmDetector.stopListening()
        batteryMonitor.unregister()
        connectivityMonitor.unregister()
        torchManager.cleanUp()
        alarmManager.stopAlarm()
    }
}