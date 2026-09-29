# HomeGuard - Development Phases

This document details the 21 implementation phases for HomeGuard. Each phase follows the cycle:
`IMPLEMENT` → `TEST` → `REVIEW` → `COMMIT` → `PUSH` → `VERIFY CLEAN WORKING TREE`.

---

### Phase 1: Project Foundation
- Initialize Gradle Kotlin DSL project with Version Catalog (`gradle/libs.versions.toml`).
- Configure Android SDK 35 (compileSdk/targetSdk) and minSdk 26.
- Set up Jetpack Compose, Material 3, Coroutines, DataStore, Room, WorkManager.
- Configure ProGuard/R8 rules and strict Android linting.

### Phase 2: HomeGuard UI & Design System
- Establish calm, native Android design system (Color tokens, Typography, Tactile touch targets).
- Implement Protected Device Home Screen (Status banner, Telemetry chips, I'M OK, EMERGENCY, Flashlight, Call).
- Implement large tactile touch targets and high-contrast accessibility.

### Phase 3: Personalization & Setup
- Onboarding & Setup wizard for Protected Person, Primary/Secondary/Emergency contacts, and Home Info.
- Ensure strict neutrality: no hardcoded personal or family names.
- Configurable local emergency numbers (no country assumptions).

### Phase 4: Local Persistence
- Jetpack DataStore for user preferences and device profile.
- Room database for durable event queues and history audit logs.
- Type converters and database schemas with migration strategy.

### Phase 5: Safety Status State Machine
- Tri-state model: `SAFE`, `UNKNOWN`, `EMERGENCY`.
- Reactive StateFlow pipeline for safety evaluations.
- Strict rule enforcement: missed check-in transitions to `UNKNOWN`, never directly `EMERGENCY`.

### Phase 6: SOS System
- Tactile EMERGENCY button with 5-second cancelable countdown.
- Alarm sound generation using system audio streams with non-blocking audio controllers.
- Emergency screen with active alert feedback and STOP ALARM action.

### Phase 7: Offline SOS Queue & Reliability
- ACID persistence of emergency events into Room before any network dispatch.
- WorkManager synchronization worker triggered on network availability.
- Deduplication and idempotency keys (UUIDv4) to prevent duplicate emergency alerts.

### Phase 8: Check-In System ("I'M OK")
- Configurable check-in scheduler (daily, morning/evening, custom).
- "Safety Check: Are you okay?" interactive prompt.
- Timeout resolution logic transitioning `SAFE` → `UNKNOWN`.

### Phase 9: Wi-Fi & Connectivity Telemetry
- Event-driven `ConnectivityManager.NetworkCallback` implementation.
- Zero network polling; instant detection of Wi-Fi connect/disconnect.
- Automatic queue drain and sync trigger upon restoration.

### Phase 10: Battery Monitoring
- Sticky intent listener for `ACTION_BATTERY_CHANGED`.
- Warning threshold at 20%, Critical threshold at 10%.
- Charging state telemetry with rate-limited notification dispatch.

### Phase 11: Emergency Information
- Offline-first emergency card: Contacts, Home Address, Building security info.
- Optional medical emergency details (allergies, medications, blood group, doctor).

### Phase 12: Flashlight & Power Cut Mode
- `CameraManager` torch mode controller without camera preview overhead.
- Dedicated "Power Cut" screen: Torch toggle, battery meter, quick emergency contacts.

### Phase 13: Night Mode
- Simplified dark interface reducing ambient glow and sensory distraction.
- Configurable schedule (start time, end time) and manual toggle.
- Full access to emergency triggers retained.

### Phase 14: Modular Communication Layer
- Modular `CommunicationManager` abstraction supporting internet calls / dialer fallbacks.
- Clear error feedback when no SIM or connectivity is available.

### Phase 15: Backend & Synchronization
- Supabase / PostgreSQL schema definition (`schema.sql`) with Row Level Security.
- Pluggable `RemoteSyncDataSource` (Supabase REST + Mock Offline client).

### Phase 16: Monitoring Dashboard
- Remote dashboard interface for trusted contacts to monitor protected device.
- Live status indicators (Online/Offline, Battery, Wi-Fi, Safety status, Event log).

### Phase 17: Notification Infrastructure
- Android notification channels: 🚨 Emergency, ⚠️ Missed Check-In, 🔋 Low Battery, 📶 Connectivity.
- High-priority channels for safety events compliant with Android notification policies.

### Phase 18: Security Hardening
- Zero plaintext credential storage; no hardcoded API keys or secrets.
- Encrypted storage where appropriate; strict ProGuard rules.

### Phase 19: Performance Optimization
- Cold startup profiling: lazy initialization of non-critical components.
- Memory leak prevention; 60 FPS verified on low-end device profiles.

### Phase 20: Automated Testing
- Comprehensive Unit and Integration tests for SOS lifecycle, Check-in timeout, State transitions, and Battery/Network logic.

### Phase 21: Release Preparation & Open Source Packaging
- Open-source documents: README, LICENSE, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, CHANGELOG.
- Release APK build verification.
