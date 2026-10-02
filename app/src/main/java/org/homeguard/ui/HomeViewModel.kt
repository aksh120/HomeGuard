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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.homeguard.HomeGuardApplication
import org.homeguard.R
import org.homeguard.core.checkin.CheckInScheduler
import org.homeguard.core.checkin.CheckInTimeoutEvaluator
import org.homeguard.core.communication.CommunicationManager
import org.homeguard.core.communication.CommunicationResult
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.SafetyRepository
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.SafetyStatus
import org.homeguard.core.notifications.SafetyNotifier
import org.homeguard.core.sensors.FallAndMotionDetector
import org.homeguard.core.sync.SyncWorker
import java.text.SimpleDateFormat
import java.util.Calendar
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

    private val app = application as HomeGuardApplication
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
    private val checkInScheduler = CheckInScheduler(application)

    private val _fallDetectedAlert = MutableStateFlow(false)
    val fallDetectedAlert: StateFlow<Boolean> = _fallDetectedAlert.asStateFlow()

    private val fallAndMotionDetector = FallAndMotionDetector(
        context = application,
        onFallDetected = {
            _fallDetectedAlert.value = true
        },
        onSignificantMovement = {
            // Passive confirmation: handling the phone at home is evidence the resident is up.
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
            SimpleDateFormat("h:mm a, MMM d", Locale.getDefault()).format(Date(ts))
        } else {
            getApplication<Application>().getString(R.string.last_checkin_none)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastMotionAgoFormatted: StateFlow<String> = fallAndMotionDetector.telemetry.map { telemetry ->
        val elapsedMinutes = (System.currentTimeMillis() - telemetry.lastMovementTimestamp) / 60_000L
        when {
            elapsedMinutes <= 0 -> getString(R.string.last_motion_just_now)
            elapsedMinutes < 60 -> getString(R.string.last_motion_minutes, elapsedMinutes)
            else -> getString(R.string.last_motion_hours, elapsedMinutes / 60)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val recentEvents = eventRepository.recentAuditsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingSyncCount = eventRepository.pendingCountFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _currentDestination =
        MutableStateFlow<NavigationDestination>(NavigationDestination.Home)
    val currentDestination: StateFlow<NavigationDestination> = _currentDestination.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(5)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _communicationError = MutableStateFlow<String?>(null)
    val communicationError: StateFlow<String?> = _communicationError.asStateFlow()

    /**
     * Whether the scheduled "Are you okay?" prompt should be showing.
     *
     * Driven by an intent extra when the user taps the notification, and otherwise false.
     */
    private val _checkInPromptVisible = MutableStateFlow(false)
    val checkInPromptVisible: StateFlow<Boolean> = _checkInPromptVisible.asStateFlow()

    /** Section 52: shown once, until the resident acknowledges it. */
    private val _safetyNoticeVisible = MutableStateFlow(false)
    val safetyNoticeVisible: StateFlow<Boolean> = _safetyNoticeVisible.asStateFlow()

    private var countdownJob: Job? = null

    /** Tracks which battery thresholds have already alerted, so each fires once per discharge. */
    private var lowBatteryNotified = false
    private var criticalBatteryNotified = false

    init {
        viewModelScope.launch {
            preferencesManager.appConfigFlow.collect { config ->
                if (!config.isSetupComplete &&
                    _currentDestination.value == NavigationDestination.Home
                ) {
                    _currentDestination.value = NavigationDestination.Setup
                }
                if (!config.hasAcknowledgedSafetyNotice && config.isSetupComplete) {
                    _safetyNoticeVisible.value = true
                }
            }
        }

        viewModelScope.launch {
            // Identity must exist before any event is recorded.
            val deviceId = preferencesManager.ensureDeviceId()
            // The remote schema has NOT NULL foreign keys into `devices`, so registration must
            // happen before the first event or every insert fails on the constraint.
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
        observeBatteryForNotifications()
        observeCheckInSchedule()
        observeMissedCheckIn()
    }

    /** Applies the configured schedule whenever the user changes it. */
    private fun observeCheckInSchedule() {
        viewModelScope.launch {
            preferencesManager.appConfigFlow
                .map { it.checkInSchedule }
                .distinctUntilChanged()
                .collect { checkInScheduler.applySchedule(it) }
        }
    }

    /**
     * Downgrades an unanswered scheduled check-in to UNKNOWN and notifies once.
     *
     * Deliberately never produces EMERGENCY: section 15 reserves that for an explicit SOS.
     */
    private fun observeMissedCheckIn() {
        viewModelScope.launch {
            combine(
                appConfig,
                safetyRepository.lastCheckInTimestampFlow
            ) { config, lastCheckIn -> config to lastCheckIn }
                .collect { (config, lastCheckIn) ->
                    if (!config.isSetupComplete || !config.checkInSchedule.enabled) return@collect

                    val now = System.currentTimeMillis()
                    val promptAt = lastPromptAt
                    val missed = CheckInTimeoutEvaluator.isMissed(
                        schedule = config.checkInSchedule,
                        lastCheckInAt = lastCheckIn,
                        lastPromptAt = promptAt,
                        now = now
                    )
                    if (missed && !missedNotified) {
                        missedNotified = true
                        // Record the miss so it reaches the monitoring contact.
                        viewModelScope.launch {
                            val deviceId = preferencesManager.ensureDeviceId()
                            eventRepository.recordAudit(
                                eventType = org.homeguard.core.model.EventType.CHECK_IN,
                                summary = org.homeguard.core.database.entity.EventSummaries
                                    .CHECK_IN_MISSED,
                                details = "Scheduled check-in was not answered within the timeout."
                            )
                            SyncWorker.triggerImmediateSync(getApplication())
                        }
                        notifier.notifyCheckInMissed()
                    }
                    if (!missed) {
                        missedNotified = false
                    }
                }
        }
    }

    /** Timestamp of the most recent scheduled prompt raised on this device. */
    private var lastPromptAt: Long? = null

    /** Suppresses repeat notifications for the same missed check-in. */
    private var missedNotified = false

    private fun observeBatteryForNotifications() {
        viewModelScope.launch {
            batteryMonitor.batteryInfo.collect { info ->
                val thresholds = appConfig.value.batteryThresholds
                if (info.isCharging) {
                    lowBatteryNotified = false
                    criticalBatteryNotified = false
                    notifier.clearBatteryAlerts()
                    return@collect
                }
                if (info.level <= thresholds.criticalPercent) {
                    if (!criticalBatteryNotified) {
                        criticalBatteryNotified = true
                        lowBatteryNotified = true
                        notifier.notifyBatteryCritical(info.level)
                    }
                } else if (info.level <= thresholds.lowPercent) {
                    if (!lowBatteryNotified) {
                        lowBatteryNotified = true
                        notifier.notifyBatteryLow(info.level)
                    }
                }
            }
        }
    }

    fun onCheckInNotificationOpened() {
        lastPromptAt = System.currentTimeMillis()
        _checkInPromptVisible.value = true
    }

    fun dismissCheckInPrompt() {
        _checkInPromptVisible.value = false
    }

    fun dismissSafetyNotice() {
        _safetyNoticeVisible.value = false
        viewModelScope.launch { preferencesManager.acknowledgeSafetyNotice() }
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
            missedNotified = false
            _checkInPromptVisible.value = false
            SyncWorker.triggerImmediateSync(getApplication())
        }
    }

    fun requestHelpFromPrompt() {
        _checkInPromptVisible.value = false
        startEmergencyCountdown()
    }

    fun pingCaregiverBeacon() {
        viewModelScope.launch { caregiverBeaconManager.playRadarChirp() }
    }

    fun dismissFallAlert() {
        _fallDetectedAlert.value = false
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
            // Resolving records a deliberate check-in so SAFE is earned, not assumed.
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

    /** True when the current wall-clock time falls inside the configured night window. */
    fun isNightWindowActive(): Boolean {
        val calendar = Calendar.getInstance()
        val nowMinute =
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return appConfig.value.nightModeSchedule.isActiveAt(nowMinute)
    }

    fun toggleNightMode() {
        val current = appConfig.value.nightModeSchedule.enabled
        viewModelScope.launch {
            preferencesManager.setNightMode(!current)
            _currentDestination.value =
                if (!current) NavigationDestination.NightMode
                else NavigationDestination.Home
        }
    }

    fun initiateCallToPrimary(context: Context) {
        val contact = appConfig.value.primaryContact
        val result = communicationManager.initiateCall(context, contact)
        if (result is CommunicationResult.Unavailable) {
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
            // Re-arm immediately so a schedule change takes effect without waiting for a restart.
            checkInScheduler.applySchedule(config.checkInSchedule)
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
        batteryMonitor.unregister()
        connectivityMonitor.unregister()
        torchManager.cleanUp()
        alarmManager.stopAlarm()
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}