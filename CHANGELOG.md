# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-29

### Added
- **Core Design System & UI**:
  - Calm, trustworthy Material 3 theme in `Color.kt`, `Type.kt`, and `Theme.kt`.
  - Accessible touch targets (>64dp) and high-contrast status banners (`SafetyStatusBanner`).
  - 100% localized strings across all screens (`strings.xml`), with neutral naming and zero hardcoded family assumptions.
- **Protected Device Home Screen**:
  - 2-second visual comprehension displaying Safety Status, Wi-Fi, Battery, and Monitoring state.
  - Primary touch actions: `I'M OK`, `EMERGENCY`, `CALL PRIMARY CONTACT`, and `FLASHLIGHT`.
- **Reliable SOS Emergency System**:
  - Prominent EMERGENCY button with a 5-second cancelable countdown timer.
  - Continuous emergency audio alarm and haptic SOS vibration pattern.
  - Active emergency screen with `STOP ALARM` action.
  - Offline resilience: alerts are committed locally to Room before network dispatch with automatic background retry when connectivity returns.
- **Scheduled Safety Check-Ins**:
  - "Are you okay?" interactive prompt dialog.
  - Non-alarmist tri-state logic: missed check-in transitions state to `UNKNOWN`, never prematurely `EMERGENCY`.
- **Hardware & Telemetry Monitors**:
  - `BatteryMonitor`: event-driven broadcast receiver with 20% warning and 10% critical thresholds.
  - `ConnectivityMonitor`: `ConnectivityManager.NetworkCallback` observer with auto-sync trigger on reconnection.
  - `TorchManager`: camera flash torch control via `CameraManager` without camera preview overhead.
  - `EmergencyAlarmManager`: continuous looping alarm with graceful release.
- **Offline Utilities**:
  - `NightModeScreen`: low-distraction dark mode preserving quick emergency access.
  - `PowerCutScreen`: power outage utility with flashlight toggle, battery gauge, and emergency contacts.
  - `EmergencyInfoScreen`: offline contacts, home entry instructions, and optional medical info.
  - `FireGuideScreen`: calm, structured offline fire safety instructions.
- **Modular Communication Layer**:
  - `CommunicationManager` with telephony dialer intent (`ACTION_DIAL`) and internet link fallback.
- **Local Persistence & Background Sync**:
  - Room database (`HomeGuardDatabase`) with `PendingEventDao` and `EventAuditDao`.
  - Jetpack DataStore (`PreferencesManager`) for configuration, contacts, and state.
  - AndroidX `WorkManager` (`SyncWorker`) for periodic and network-constrained event synchronization.
- **Monitoring Dashboard**:
  - Remote monitoring screen showing device online status, battery, internet, last check-in, and recent event history.
- **Backend Schema**:
  - PostgreSQL / Supabase database schema (`backend/schema.sql`) with Row Level Security.
- **Automated Test Suite**:
  - Comprehensive unit tests covering safety state machine, offline queue, duplicate prevention, and battery evaluation.
