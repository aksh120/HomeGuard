# HomeGuard - Master Implementation Plan

> **HomeGuard - Open-source home safety, made simple.**

## Executive Summary

HomeGuard is a production-quality, open-source Android home safety companion designed to provide an ultra-reliable, privacy-first safety layer for individuals staying at home (parents, elderly, living alone, roommates, caregivers). It operates under the strict assumption that the protected device is stationed at home—frequently on older or budget hardware, possibly without a SIM card or cellular subscription, relying solely on Wi-Fi and offline resilience.

HomeGuard rejects GPS surveillance, geofencing, route tracking, and bloated cloud dependencies. Instead, it focuses on what truly matters for home safety: **Emergency SOS alerts, scheduled "I'm OK" check-ins, network & battery telemetry, local emergency guides & information, flashlight/power-cut utilities, and an integrated monitoring dashboard.**

---

## Core Tenets & Architectural Philosophy

1. **Safety Over Novelty**: A missed check-in transitions state to `UNKNOWN`, never prematurely `SAFE` or `EMERGENCY`. Only an explicit SOS triggers an `EMERGENCY`.
2. **Offline-First & Zero Data Loss**: Every emergency event and check-in is persisted locally to an encrypted/durable Room queue before network dispatch. Failed transmissions automatically retry upon connectivity restoration.
3. **Hardware & Battery Economy**: No continuous CPU wake-locks, zero continuous polling, no heavy background animation loops. We leverage Android's native `ConnectivityManager.NetworkCallback`, `BatteryManager` broadcast receivers, and `WorkManager` for idempotent, battery-friendly synchronization.
4. **Accessible, Calm, Trustworthy UX**: Built with Jetpack Compose following native Android Material 3 design guidelines. High-contrast typography, large tactile touch targets (>48dp min, >72dp for emergency triggers), screen-reader semantics, and instant 2-second visual comprehension.
5. **No Personal or Hardcoded Assumptions**: Complete localization via `res/values/strings.xml`. Zero hardcoded names, phone numbers, or country-specific emergency lines. Everything is configurable via an onboarding setup wizard and settings suite.

---

## Technical Specifications

| Parameter | Specification |
|---|---|
| **Platform** | Native Android (Kotlin 2.x) |
| **Minimum SDK** | Android 8.0 (API Level 26 - Oreo) |
| **Target SDK** | Android 15 (API Level 35) |
| **UI Toolkit** | Jetpack Compose + Material 3 |
| **Concurrency** | Kotlin Coroutines + StateFlow / SharedFlow |
| **Local Storage** | Jetpack DataStore (Preferences/Config) + Room (Pending Events & Audit Log) |
| **Background Work** | AndroidX WorkManager (Constraints: NetworkType.CONNECTED) |
| **Hardware APIs** | CameraManager (Flashlight), BatteryManager, AudioTrack / MediaPlayer |
| **Networking** | Ktor / OkHttp + Supabase REST / Edge Functions (with Offline Mock Mode) |
| **Testing** | JUnit 5 / 4, AndroidX Test, Robolectric, Kotlinx Coroutines Test |

---

## Module Breakdown

1. **`app` (Core Android Module)**:
   - `core/designsystem`: Typography, Color Palette, Components (Touch targets, emergency buttons, status chips).
   - `core/model`: Domain models (`SafetyStatus`, `EmergencyEvent`, `CheckIn`, `DeviceStatus`, `ContactInfo`, `HomeInfo`).
   - `core/data`: Repositories (`SafetyRepository`, `CheckInRepository`, `BatteryRepository`, `ConnectivityRepository`, `SettingsRepository`).
   - `core/database`: Room DB (`PendingEventEntity`, `EventAuditEntity`, DAOs, migrations).
   - `core/datastore`: Preferences DataStore for configuration, personalization, contact details.
   - `core/hardware`: Flashlight controller, alarm tone generator, battery broadcast receiver, network callback monitor.
   - `core/sync`: WorkManager sync workers, idempotency handlers, deduplication.
   - `feature/home`: Protected device main dashboard, status banner, I'M OK, EMERGENCY, Flashlight, Night mode.
   - `feature/emergency`: SOS countdown screen, active emergency alert screen with alarm silence, offline confirmation.
   - `feature/checkin`: Check-in modal prompt, custom schedule configuration.
   - `feature/powercut`: Dedicated power-cut mode (torch, battery gauge, emergency quick contacts).
   - `feature/info`: Emergency information (offline contacts, optional medical summary, home address/notes).
   - `feature/guides`: Fire safety and emergency offline procedure guides.
   - `feature/monitoring`: Dashboard view for remote caregiver / trusted contact to monitor protected device status.
   - `feature/setup`: Wizard for initial configuration and personalization.

---

## Verification & Quality Gates

Each phase executes according to:
`IMPLEMENT` -> `LOCAL VERIFICATION (Build & Unit Tests)` -> `CODE REVIEW & LINT` -> `GIT COMMIT` -> `GIT PUSH` -> `VERIFY CLEAN TREE`
