package org.homeguard.core.di

import android.content.Context
import org.homeguard.core.communication.CommunicationManager
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.SafetyRepository
import org.homeguard.core.data.SupabaseRemoteSyncDataSource
import org.homeguard.core.data.SyncRepository
import org.homeguard.core.database.HomeGuardDatabase
import org.homeguard.core.datastore.PreferencesManager
import org.homeguard.core.hardware.BatteryMonitor
import org.homeguard.core.hardware.ConnectivityMonitor
import org.homeguard.core.hardware.EmergencyAlarmManager
import org.homeguard.core.hardware.TorchManager
import org.homeguard.core.sensors.CaregiverBeaconManager
import org.homeguard.core.sync.SyncWorker

/**
 * Single composition root. Previously HomeViewModel and SyncWorker each built their own graph,
 * which meant two divergent dependency trees. Plain lazy properties rather than a DI framework,
 * per CONTRIBUTING.md principle 1 (no bloat).
 */
class AppContainer(private val context: Context) {

    val database: HomeGuardDatabase by lazy { HomeGuardDatabase.getInstance(context) }

    val preferencesManager: PreferencesManager by lazy { PreferencesManager(context) }

    val eventRepository: EventRepository by lazy {
        EventRepository(
            pendingEventDao = database.pendingEventDao(),
            eventAuditDao = database.eventAuditDao(),
            database = database
        )
    }

    val safetyRepository: SafetyRepository by lazy {
        SafetyRepository(preferencesManager, eventRepository)
    }

    val remoteSyncDataSource: SupabaseRemoteSyncDataSource by lazy {
        SupabaseRemoteSyncDataSource()
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(eventRepository, remoteSyncDataSource)
    }

    val batteryMonitor: BatteryMonitor by lazy { BatteryMonitor(context) }

    val connectivityMonitor: ConnectivityMonitor by lazy {
        ConnectivityMonitor(context) { SyncWorker.triggerImmediateSync(context) }
    }

    val torchManager: TorchManager by lazy { TorchManager(context) }
    val alarmManager: EmergencyAlarmManager by lazy { EmergencyAlarmManager(context) }
    val communicationManager: CommunicationManager by lazy { CommunicationManager() }
    val caregiverBeaconManager: CaregiverBeaconManager by lazy { CaregiverBeaconManager(context) }
}