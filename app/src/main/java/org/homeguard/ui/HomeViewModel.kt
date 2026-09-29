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
import org.homeguard.core.communication.CommunicationManager
import org.homeguard.core.communication.CommunicationResult
import org.homeguard.core.data.DefaultRemoteSyncDataSource
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.SafetyRepository
import org.homeguard.core.data.SyncRepository
import org.homeguard.core.database.HomeGuardDatabase
import org.homeguard.core.datastore.PreferencesManager
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.hardware.BatteryMonitor
import org.homeguard.core.hardware.ConnectivityInfo
import org.homeguard.core.hardware.ConnectivityMonitor
import org.homeguard.core.hardware.EmergencyAlarmManager
import org.homeguard.core.hardware.TorchManager
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.SafetyStatus
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

    private val db = HomeGuardDatabase.getInstance(application)
    private val preferencesManager = PreferencesManager(application)
    private val eventRepository = EventRepository(db.pendingEventDao(), db.eventAuditDao())
    val safetyRepository = SafetyRepository(preferencesManager, eventRepository)
    private val syncRepository = SyncRepository(eventRepository, org.homeguard.core.data.SupabaseRemoteSyncDataSource())

    private val batteryMonitor = BatteryMonitor(application)
    private val connectivityMonitor = ConnectivityMonitor(application) {
        // Trigger sync when internet is restored
        SyncWorker.triggerImmediateSync(getApplication())
    }
    private val torchManager = TorchManager(application)
    private val alarmManager = EmergencyAlarmManager(application)
    private val communicationManager = CommunicationManager()

    val safetyStatus: StateFlow<SafetyStatus> = safetyRepository.safetyStatusFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SafetyStatus.UNKNOWN)

    val batteryInfo: StateFlow<BatteryInfo> = batteryMonitor.batteryInfo
    val connectivityInfo: StateFlow<ConnectivityInfo> = connectivityMonitor.connectivityInfo
    val isTorchOn: StateFlow<Boolean> = torchManager.isTorchOn
    val isAlarmPlaying: StateFlow<Boolean> = alarmManager.isAlarmPlaying

    val appConfig: StateFlow<AppConfig> = preferencesManager.appConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    val lastCheckInFormatted: StateFlow<String> = safetyRepository.lastCheckInTimestampFlow.map { ts ->
        if (ts != null && ts > 0) {
            val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
            sdf.format(Date(ts))
        } else {
            "No recent check-in"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "No recent check-in")

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

    init {
        // Check if onboarding setup is complete
        viewModelScope.launch {
            preferencesManager.appConfigFlow.collect { config ->
                if (!config.isSetupComplete && _currentDestination.value == NavigationDestination.Home) {
                    _currentDestination.value = NavigationDestination.Setup
                }
            }
        }
        // Start periodic sync schedule
        SyncWorker.schedulePeriodicSync(application)
    }

    fun navigateTo(dest: NavigationDestination) {
        _currentDestination.value = dest
    }

    fun confirmCheckIn() {
        viewModelScope.launch {
            val config = appConfig.value
            safetyRepository.recordCheckIn(config.deviceId)
            SyncWorker.triggerImmediateSync(getApplication())
        }
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
            val config = appConfig.value
            safetyRepository.triggerEmergency(config.deviceId)
            alarmManager.startAlarm()
            _currentDestination.value = NavigationDestination.EmergencyActive
            SyncWorker.triggerImmediateSync(getApplication())
        }
    }

    fun stopAlarmAndResolve() {
        alarmManager.stopAlarm()
        viewModelScope.launch {
            safetyRepository.resolveEmergency()
            _currentDestination.value = NavigationDestination.Home
        }
    }

    fun toggleFlashlight() {
        torchManager.toggleTorch()
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
        if (result is CommunicationResult.Unavailable) {
            _communicationError.value = result.message
        }
    }

    fun dismissCommunicationError() {
        _communicationError.value = null
    }

    fun saveConfig(config: AppConfig) {
        viewModelScope.launch {
            preferencesManager.saveConfig(config)
        }
    }

    override fun onCleared() {
        super.onCleared()
        batteryMonitor.unregister()
        connectivityMonitor.unregister()
        torchManager.cleanUp()
        alarmManager.stopAlarm()
    }
}
