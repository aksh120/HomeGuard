# HomeGuard - Actionable Task Tracker

- [x] **Phase 1: Project Foundation**
  - [x] Initialize Android project structure with Gradle Kotlin DSL
  - [x] Set up Version Catalog (`gradle/libs.versions.toml`)
  - [x] Configure `app/build.gradle.kts` with Compose, Coroutines, DataStore, Room, WorkManager
  - [x] Configure `AndroidManifest.xml` with required permissions and application class
  - [x] Verify clean compilation and project sync

- [x] **Phase 2: UI Foundation & Design System**
  - [x] Define Theme, Typography, and Color Palette (Material 3)
  - [x] Create `strings.xml` with comprehensive, neutral strings
  - [x] Implement `ProtectedHomeScreen` with primary touch targets
  - [x] Add accessibility semantics and screen-reader support

- [x] **Phase 3: Personalization & Setup Wizard**
  - [x] Implement setup data models and validation
  - [x] Build Onboarding/Setup Compose screen
  - [x] Add editable Profile & Settings screen

- [x] **Phase 4: Local Persistence Layer**
  - [x] Implement `PreferencesDataStore` for app settings and contacts
  - [x] Implement Room Entities (`PendingEventEntity`, `EventAuditEntity`)
  - [x] Create Room DAOs and `HomeGuardDatabase`
  - [x] Write repository implementations with offline caching

- [x] **Phase 5: Safety Status State Machine**
  - [x] Implement `SafetyStatus` enum and state machine
  - [x] Create `SafetyRepository` exposing reactive `StateFlow<SafetyStatus>`
  - [x] Implement evaluation logic for SAFE, UNKNOWN, EMERGENCY

- [x] **Phase 6: SOS System**
  - [x] Build `EmergencyCountdownScreen` with 5-second cancelable timer
  - [x] Implement `EmergencyScreen` with alarm stop action
  - [x] Implement audio alarm manager via `AudioTrack`/`MediaPlayer`

- [x] **Phase 7: Offline SOS Queue & Sync**
  - [x] Create atomic persistence for SOS events in Room
  - [x] Implement WorkManager `SyncWorker` with network constraints
  - [x] Add deduplication and idempotency verification

- [x] **Phase 8: Check-In System**
  - [x] Create scheduled check-in trigger logic
  - [x] Build check-in prompt dialog ("Are you okay?")
  - [x] Implement check-in timeout transition (SAFE -> UNKNOWN)

- [x] **Phase 9: Wi-Fi & Connectivity Telemetry**
  - [x] Implement `ConnectivityManager.NetworkCallback` observer
  - [x] Expose real-time connectivity state in UI
  - [x] Trigger pending sync on network restoration

- [x] **Phase 10: Battery Monitoring**
  - [x] Implement battery receiver for charge status and level
  - [x] Implement 20% warning and 10% critical thresholds
  - [x] Add battery status indicators to home screen

- [x] **Phase 11: Emergency Information Card**
  - [x] Implement offline Emergency Info screen (contacts, address, medical info)
  - [x] Ensure non-mandatory medical fields and strict privacy

- [x] **Phase 12: Flashlight & Power Cut Mode**
  - [x] Implement `TorchManager` using `CameraManager`
  - [x] Build `PowerCutScreen` with torch, battery, and quick contacts

- [x] **Phase 13: Night Mode**
  - [x] Implement Night Mode screen and dark theme optimization
  - [x] Add schedule configuration and manual toggle

- [x] **Phase 14: Modular Communication Layer**
  - [x] Implement `CommunicationManager` with internet & dialer abstraction
  - [x] Provide graceful fallback and informative error dialogs

- [x] **Phase 15: Backend & Synchronization**
  - [x] Write SQL schema (`backend/schema.sql`) for Supabase/PostgreSQL
  - [x] Implement `RemoteSyncDataSource` with mock and REST implementations

- [x] **Phase 16: Monitoring Dashboard**
  - [x] Build `MonitoringDashboardScreen` for remote trusted contacts
  - [x] Display live telemetry (device status, battery, internet, last check-in)

- [x] **Phase 17: Android Notifications**
  - [x] Implement notification channels for Emergency, Missed Check-in, Battery, Status
  - [x] Configure high-priority emergency notifications

- [x] **Phase 18: Security Hardening**
  - [x] Audit repository for exposed secrets
  - [x] Configure ProGuard rules and secure local storage

- [x] **Phase 19: Performance Optimization**
  - [x] Optimize startup latency and lazy initialization
  - [x] Verify 60 FPS UI performance and minimal memory footprint

- [x] **Phase 20: Automated Testing**
  - [x] Write unit tests for Safety Status, SOS Queue, Check-in Timeout, and Battery
  - [x] Verify test suite execution and passing status

- [x] **Phase 21: Release Preparation & Open Source Packaging**
  - [x] Create `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`
  - [x] Compile and verify release APK
