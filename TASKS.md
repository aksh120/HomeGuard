# HomeGuard - Actionable Task Tracker

- [x] **Phase 1: Project Foundation**
  - [x] Initialize Android project structure with Gradle Kotlin DSL
  - [x] Set up Version Catalog (`gradle/libs.versions.toml`)
  - [x] Configure `app/build.gradle.kts` with Compose, Coroutines, DataStore, Room, WorkManager
  - [x] Configure `AndroidManifest.xml` with required permissions and application class
  - [x] Verify clean compilation and project sync

- [ ] **Phase 2: UI Foundation & Design System**
  - [ ] Define Theme, Typography, and Color Palette (Material 3)
  - [ ] Create `strings.xml` with comprehensive, neutral strings
  - [ ] Implement `ProtectedHomeScreen` with primary touch targets
  - [ ] Add accessibility semantics and screen-reader support

- [ ] **Phase 3: Personalization & Setup Wizard**
  - [ ] Implement setup data models and validation
  - [ ] Build Onboarding/Setup Compose screen
  - [ ] Add editable Profile & Settings screen

- [ ] **Phase 4: Local Persistence Layer**
  - [ ] Implement `PreferencesDataStore` for app settings and contacts
  - [ ] Implement Room Entities (`PendingEventEntity`, `EventAuditEntity`)
  - [ ] Create Room DAOs and `HomeGuardDatabase`
  - [ ] Write repository implementations with offline caching

- [ ] **Phase 5: Safety Status State Machine**
  - [ ] Implement `SafetyStatus` enum and state machine
  - [ ] Create `SafetyRepository` exposing reactive `StateFlow<SafetyStatus>`
  - [ ] Implement evaluation logic for SAFE, UNKNOWN, EMERGENCY

- [ ] **Phase 6: SOS System**
  - [ ] Build `EmergencyCountdownScreen` with 5-second cancelable timer
  - [ ] Implement `EmergencyScreen` with alarm stop action
  - [ ] Implement audio alarm manager via `AudioTrack`/`MediaPlayer`

- [ ] **Phase 7: Offline SOS Queue & Sync**
  - [ ] Create atomic persistence for SOS events in Room
  - [ ] Implement WorkManager `SyncWorker` with network constraints
  - [ ] Add deduplication and idempotency verification

- [ ] **Phase 8: Check-In System**
  - [ ] Create scheduled check-in trigger logic
  - [ ] Build check-in prompt dialog ("Are you okay?")
  - [ ] Implement check-in timeout transition (SAFE -> UNKNOWN)

- [ ] **Phase 9: Wi-Fi & Connectivity Telemetry**
  - [ ] Implement `ConnectivityManager.NetworkCallback` observer
  - [ ] Expose real-time connectivity state in UI
  - [ ] Trigger pending sync on network restoration

- [ ] **Phase 10: Battery Monitoring**
  - [ ] Implement battery receiver for charge status and level
  - [ ] Implement 20% warning and 10% critical thresholds
  - [ ] Add battery status indicators to home screen

- [ ] **Phase 11: Emergency Information Card**
  - [ ] Implement offline Emergency Info screen (contacts, address, medical info)
  - [ ] Ensure non-mandatory medical fields and strict privacy

- [ ] **Phase 12: Flashlight & Power Cut Mode**
  - [ ] Implement `TorchManager` using `CameraManager`
  - [ ] Build `PowerCutScreen` with torch, battery, and quick contacts

- [ ] **Phase 13: Night Mode**
  - [ ] Implement Night Mode screen and dark theme optimization
  - [ ] Add schedule configuration and manual toggle

- [ ] **Phase 14: Modular Communication Layer**
  - [ ] Implement `CommunicationManager` with internet & dialer abstraction
  - [ ] Provide graceful fallback and informative error dialogs

- [ ] **Phase 15: Backend & Synchronization**
  - [ ] Write SQL schema (`backend/schema.sql`) for Supabase/PostgreSQL
  - [ ] Implement `RemoteSyncDataSource` with mock and REST implementations

- [ ] **Phase 16: Monitoring Dashboard**
  - [ ] Build `MonitoringDashboardScreen` for remote trusted contacts
  - [ ] Display live telemetry (device status, battery, internet, last check-in)

- [ ] **Phase 17: Android Notifications**
  - [ ] Implement notification channels for Emergency, Missed Check-in, Battery, Status
  - [ ] Configure high-priority emergency notifications

- [ ] **Phase 18: Security Hardening**
  - [ ] Audit repository for exposed secrets
  - [ ] Configure ProGuard rules and secure local storage

- [ ] **Phase 19: Performance Optimization**
  - [ ] Optimize startup latency and lazy initialization
  - [ ] Verify 60 FPS UI performance and minimal memory footprint

- [ ] **Phase 20: Automated Testing**
  - [ ] Write unit tests for Safety Status, SOS Queue, Check-in Timeout, and Battery
  - [ ] Verify test suite execution and passing status

- [ ] **Phase 21: Release Preparation & Open Source Packaging**
  - [ ] Create `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`
  - [ ] Compile and verify release APK
