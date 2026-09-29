package org.homeguard.core.model

enum class EventSyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

enum class EventType {
    EMERGENCY_SOS,
    CHECK_IN,
    BATTERY_ALERT,
    CONNECTIVITY_CHANGED,
    STATUS_CHANGED
}
