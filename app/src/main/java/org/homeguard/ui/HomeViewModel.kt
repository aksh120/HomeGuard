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
import kotlinx.coroutines.flow.first
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
import org.homeguard.core.data.MonitoringRemoteDataSource
import org.homeguard.core.data.SafetyRepository
import org.homeguard.core.hardware.BatteryInfo
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.AppProfile
import org.homeguard.core.model.CaregiverAggregator
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.Household
import org.homeguard.core.model.HouseholdMember
import org.homeguard.core.model.HouseholdRef
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.RemoteCheckIn
import org.homeguard.core.model.RemoteEmergencyEvent
import org.homeguard.core.model.SafetyStatus
import org.homeguard.core.model.SyncHealth
import org.homeguard.core.model.WatchedPerson
import org.homeguard.core.notifications.SafetyNotifier
import org.homeguard.core.sensors.FallAndMotionDetector
import org.homeguard.core.sync.SyncWorker
import org.homeguard.ui.screens.CaregiverLoadState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    /**
     * Remote connection health, so the app can answer section 53's fourth question honestly.
     *
     * Without this a build that cannot reach Supabase looks identical to a working one, and
     * alerts quietly queue forever.
     */
    val syncHealth: StateFlow<SyncHealth> = preferencesManager.syncHealthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncHealth())

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
                val entry = ProfileRouting.entryDestination(config.profile, config.isSetupComplete)
                // Only steer on first arrival. Once the user has moved, leaving their choice alone
                // matters more than re-asserting the entry rule on every config emission.
                val stillAtEntry = _currentDestination.value == NavigationDestination.Home ||
                    _currentDestination.value == NavigationDestination.Setup ||
                    _currentDestination.value == NavigationDestination.ProfileSelection
                if (!ProfileRouting.isReachable(config.profile, _currentDestination.value) || stillAtEntry) {
                    _currentDestination.value = entry
                }
                if (!config.hasAcknowledgedSafetyNotice && config.isSetupComplete &&
                    config.profile == AppProfile.PROTECTED
                ) {
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

        viewModelScope.launch {
            val stored = preferencesManager.householdsFlow.first()
            _households.value = stored
            _householdId.value = stored.firstOrNull()?.id
            _householdInviteCode.value = stored.firstOrNull()?.inviteCode
            if (stored.isNotEmpty()) {
                refreshMonitoring()
            }
        }
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

    /**
     * Monitoring to protected, offered from the join screen.
     *
 * Decision D4: this direction needs no approval, because there is no counterparty in the household
 * being disarmed by it. The reverse direction is deliberately absent here — it requires caregiver
 * approval and lands in Phase 3.
     *
     * Setup is re-run rather than skipped, because the new resident's own contacts and medical
     * details have to be entered. The household is left in place for now; offering to leave it is
     * Phase 2 work, once the dashboard can show membership.
 */
fun switchToProtectedProfile() {
        viewModelScope.launch {
            preferencesManager.saveConfig(
                appConfig.value.copy(
                    profile = AppProfile.PROTECTED,
                    isSetupComplete = false
                )
            )
            val deviceId = preferencesManager.ensureDeviceId()
            container.remoteSyncDataSource.registerDevice(
                deviceId = deviceId,
                deviceMode = DeviceMode.PROTECTED_DEVICE.name,
                personName = appConfig.value.personName,
                personRole = appConfig.value.personRole
            )
            _currentDestination.value = NavigationDestination.Setup
        }
    }

    /**
     * Moves to [dest] only if the current profile is allowed there.
     *
     * Rejecting rather than silently correcting keeps the destination flow honest: a caller that
     * asks for a screen this profile must not have is a bug, and swallowing it would hide that.
     */
    fun navigateTo(dest: NavigationDestination) {
        val profile = appConfig.value.profile
        if (!ProfileRouting.isReachable(profile, dest)) {
            android.util.Log.w(
                "HomeGuard",
                "Blocked ${profile ?: "unselected"} profile from reaching $dest"
            )
            return
        }
        _currentDestination.value = dest
    }

    /**
     * Records the first-run profile choice and routes accordingly.
     *
     * Choosing the monitoring profile completes setup immediately: there is no protected form to
     * fill, because this phone will never raise an alert. The caregiver goes straight to the join
     * screen. Choosing the resident profile opens the protected form.
     */
    fun selectProfile(profile: AppProfile) {
        viewModelScope.launch {
            val config = appConfig.value
            val isMonitoring = profile == AppProfile.MONITORING
            preferencesManager.saveConfig(
                config.copy(
                    profile = profile,
                    isSetupComplete = isMonitoring || config.isSetupComplete
                )
            )
            if (isMonitoring) {
                // Backends see the device as a monitoring device immediately, so a caregiver
                // reading the device list does not briefly see it as an unconfigured resident phone.
                val deviceId = preferencesManager.ensureDeviceId()
                container.remoteSyncDataSource.registerDevice(
                    deviceId = deviceId,
                    deviceMode = DeviceMode.MONITORING_DEVICE.name,
                    personName = config.personName,
                    personRole = config.personRole
                )
            }
            _currentDestination.value = if (isMonitoring) {
                NavigationDestination.Home
            } else {
                NavigationDestination.Setup
            }
        }
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
            // Section 11/14: the monitoring contact must exist on the backend before an
            // emergency row can be turned into a notification for them.
            container.remoteSyncDataSource.syncMonitoringContact(
                deviceId = deviceId,
                name = config.primaryContact.name,
                email = config.primaryContact.email,
                phone = config.primaryContact.phoneOrHandle
            ).onFailure {
                android.util.Log.w(
                    "HomeGuard",
                    "Could not publish the monitoring contact; caregiver alerts will not send",
                    it
                )
            }
        }
    }

    // --- Household and monitoring (sections 6B, 27) ----------------------------------------

    private val _householdId = MutableStateFlow<String?>(null)
    private val _householdInviteCode = MutableStateFlow<String?>(null)
    val householdInviteCode: StateFlow<String?> = _householdInviteCode.asStateFlow()

    /**
     * Every household this install belongs to.
     *
     * Phase 2 / decision D2: a caregiver may watch more than one house, so this is a list. Empty
     * means the caregiver has not entered a code yet and should be shown onboarding rather than a
     * dashboard.
     */
    private val _households = MutableStateFlow<List<HouseholdRef>>(emptyList())
    val households: StateFlow<List<HouseholdRef>> = _households.asStateFlow()

    private val _monitoredDevice = MutableStateFlow<MonitoredDevice?>(null)
    val monitoredDevice: StateFlow<MonitoredDevice?> = _monitoredDevice.asStateFlow()

    /**
     * Everyone this caregiver watches, already grouped and ordered by CaregiverAggregator.
     *
     * The dashboard used to take a single device, which is why it could only ever show one person
     * no matter how many were in the household.
     */
    private val _watchedPeople = MutableStateFlow<List<WatchedPerson>>(emptyList())
    val watchedPeople: StateFlow<List<WatchedPerson>> = _watchedPeople.asStateFlow()

    private val _householdMembers = MutableStateFlow<List<HouseholdMember>>(emptyList())
    val householdMembers: StateFlow<List<HouseholdMember>> = _householdMembers.asStateFlow()

    private val _remoteEmergencies = MutableStateFlow<List<RemoteEmergencyEvent>>(emptyList())
    val remoteEmergencies: StateFlow<List<RemoteEmergencyEvent>> = _remoteEmergencies.asStateFlow()

    private val _remoteCheckIns = MutableStateFlow<List<RemoteCheckIn>>(emptyList())
    val remoteCheckIns: StateFlow<List<RemoteCheckIn>> = _remoteCheckIns.asStateFlow()

    private val _isRefreshingMonitoring = MutableStateFlow(false)
    val isRefreshingMonitoring: StateFlow<Boolean> = _isRefreshingMonitoring.asStateFlow()

    private val _caregiverLoadState = MutableStateFlow<CaregiverLoadState>(CaregiverLoadState.Idle)
    val caregiverLoadState: StateFlow<CaregiverLoadState> = _caregiverLoadState.asStateFlow()

    private val _lastMonitoringRefresh = MutableStateFlow<Long?>(null)
    val lastMonitoringRefresh: StateFlow<Long?> = _lastMonitoringRefresh.asStateFlow()

    private val _householdError = MutableStateFlow<String?>(null)
    val householdError: StateFlow<String?> = _householdError.asStateFlow()

    /**
     * Creates a household and links this device into it, publishing the invite code.
     *
     * Idempotent: if a household already exists it is not recreated, so a user who taps twice
     * does not end up orphaned from their own caregivers.
     */
    fun createHousehold(name: String) {
        viewModelScope.launch {
            _householdError.value = null
            val existing = _households.value.firstOrNull()
            if (existing != null) {
                _householdId.value = existing.id
                _householdInviteCode.value = existing.inviteCode
                return@launch
            }
            val remote = container.monitoringRemoteDataSource
            val deviceId = preferencesManager.ensureDeviceId()
            remote.createHousehold(name, deviceId)
                .onSuccess { household ->
                    // Creation now enrols this device as the household's first member atomically, so
                    // the separate membership insert that used to follow is gone. It could also fail
                    // on its own, leaving a household nobody could see or ever join.
                    remote.attachDeviceToHousehold(household.id, deviceId)
                    rememberHousehold(household)
                    // Publish the household id on future writes so caregivers can find the device.
                    publishHouseholdToEvents(household.id)
                }
                .onFailure {
                    _householdError.value = it.message ?: "Could not create a household"
                }
        }
    }

    /** Stamps the household id onto this device so caregivers can find it. */
    private suspend fun publishHouseholdToEvents(householdId: String) {
        val deviceId = preferencesManager.ensureDeviceId()
        container.remoteSyncDataSource.sendHouseholdId(householdId, deviceId)
    }

    /** Records a household locally and keeps the legacy single-value mirror in step. */
    private suspend fun rememberHousehold(household: Household) {
        val ref = HouseholdRef(id = household.id, name = household.name, inviteCode = household.inviteCode)
        preferencesManager.addHousehold(ref)
        _households.value = preferencesManager.householdsFlow.first()
        _householdId.value = _households.value.firstOrNull()?.id
        _householdInviteCode.value = _households.value.firstOrNull()?.inviteCode
    }

    /**
     * Joins an existing household using the code shown on the home device.
     *
     * Phase 2: also the "add another person" path. Joining a second code must add to the list
     * rather than replace the only one, which is the whole point of decision D2.
     */
    fun joinHousehold(inviteCode: String) {
        viewModelScope.launch {
            _householdError.value = null
            _isRefreshingMonitoring.value = true
            val remote = container.monitoringRemoteDataSource
            val normalised = MonitoringRemoteDataSource.normaliseInviteCode(inviteCode)
            if (normalised.length != 8) {
                _householdError.value =
                    getApplication<Application>().getString(R.string.monitor_setup_code_hint)
                _isRefreshingMonitoring.value = false
                return@launch
            }
            remote.findHouseholdByCode(normalised)
                .onSuccess { household ->
                    val deviceId = preferencesManager.ensureDeviceId()
                    remote.joinHousehold(household.id, deviceId, "MONITORING")
                        .onFailure {
                            // Already a member is fine; anything else is not.
                            if (!it.message.orEmpty().contains("duplicate", ignoreCase = true)) {
                                _householdError.value = it.message
                            }
                        }
                    rememberHousehold(household)
                    refreshMonitoring()
                }
                .onFailure {
                    _householdError.value = it.message ?: "Could not connect"
                }
            _isRefreshingMonitoring.value = false
        }
    }

    /**
     * Stops watching a household.
     *
     * Removing membership is a Phase 3 approval flow on the protected phone, so this only clears
     * local state. The remote membership row is left in place deliberately: unilaterally deleting
     * it here would bypass the consent check Phase 3 adds.
     */
    fun forgetHousehold(householdId: String) {
        viewModelScope.launch {
            preferencesManager.removeHousehold(householdId)
            _households.value = preferencesManager.householdsFlow.first()
            _householdId.value = _households.value.firstOrNull()?.id
            _householdInviteCode.value = _households.value.firstOrNull()?.inviteCode
            refreshMonitoring()
        }
    }

    /**
     * Reloads everything the caregiver dashboard shows, across every joined household.
     *
     * Section 27 wants current status, last check-in, online state, battery, internet, and recent
     * events. Each query is per household and each is read independently, so one household failing
     * does not blank the others and one failing query does not blank the screen.
     */
    fun refreshMonitoring() {
        val joined = _households.value
        if (joined.isEmpty()) {
            _caregiverLoadState.value = CaregiverLoadState.Error("No household connected")
            _watchedPeople.value = emptyList()
            _monitoredDevice.value = null
            return
        }
        viewModelScope.launch {
            _isRefreshingMonitoring.value = true
            val remote = container.monitoringRemoteDataSource

            var firstError: Throwable? = null
            val devices = mutableListOf<MonitoredDevice>()
            val emergencies = mutableListOf<RemoteEmergencyEvent>()
            val checkIns = mutableListOf<RemoteCheckIn>()
            val members = mutableListOf<HouseholdMember>()

            for (household in joined) {
                // Sequential rather than concurrent: every request shares one auth session, and
                // firing four per household at once is how the sign-up rate limit was reached in
                // the first place.
                remote.listDevices(household.id).fold(
                    onSuccess = { devices += it },
                    onFailure = { firstError = firstError ?: it }
                )
                remote.listEmergencyEvents(household.id).fold(
                    onSuccess = { emergencies += it },
                    onFailure = { firstError = firstError ?: it }
                )
                remote.listCheckIns(household.id).fold(
                    onSuccess = { checkIns += it },
                    onFailure = { firstError = firstError ?: it }
                )
                remote.listMembers(household.id).fold(
                    onSuccess = { members += it },
                    onFailure = { firstError = firstError ?: it }
                )
            }

            _remoteEmergencies.value = emergencies
            _remoteCheckIns.value = checkIns
            _householdMembers.value = members
            _watchedPeople.value = CaregiverAggregator.watchedPeople(devices, checkIns, emergencies)
            // Kept for the single-device call sites; the dashboard now uses watchedPeople.
            _monitoredDevice.value = _watchedPeople.value.firstOrNull()?.device
            _lastMonitoringRefresh.value = System.currentTimeMillis()

            _caregiverLoadState.value = if (firstError != null) {
                CaregiverLoadState.Error(firstError.message ?: "Load failed")
            } else {
                CaregiverLoadState.Loaded(System.currentTimeMillis())
            }
            _isRefreshingMonitoring.value = false
        }
    }

    fun dismissHouseholdError() {
        _householdError.value = null
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