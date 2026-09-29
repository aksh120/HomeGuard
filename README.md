<div align="center">

  <img src="art/logo.svg" alt="HomeGuard Kinetic Aegis Logo" width="160" height="160" />

  # HomeGuard
  ### Open-Source Home Safety, Made Simple.

  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
  [![Build](https://img.shields.io/badge/Build-Passing-success.svg?style=for-the-badge)](https://github.com/aksh120/homeguard)
  [![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-brightgreen.svg?style=for-the-badge)](https://github.com/aksh120/homeguard)

  <p align="center">
    <strong>A production-grade, privacy-respecting Android safety layer built for people staying at home.</strong><br>
    Engineered for elderly family members, people living alone, parents, roommates, and recovering patients.<br>
    Repurposes any budget or spare Android device into an ultra-reliable, SIM-less home sentinel.
  </p>

  <p align="center">
    <a href="#-why-homeguard">Why HomeGuard</a> •
    <a href="#-core-pillars">Core Pillars</a> •
    <a href="#-system-architecture">Architecture</a> •
    <a href="#-screens--interface-modes">Interface Tour</a> •
    <a href="#-cloud-backend-supabase">Cloud Backend</a> •
    <a href="#-build--installation">Build &amp; Install</a> •
    <a href="#-safety-disclaimer">Disclaimer</a>
  </p>

</div>

---

## 🌟 Why HomeGuard?

Commercial safety devices and mobile trackers are broken in two fundamental ways:
1. **Predatory Subscriptions**: Proprietary medical alert pendants charge \$30–\$60/month for basic cellular hardware with lock-in contracts.
2. **Invasive Surveillance**: Consumer family-tracking apps constantly harvest outdoor GPS coordinates, geofences, and background movements—draining battery in hours while selling private location history.

**HomeGuard is built on a different philosophy**:
When an individual is at home, **GPS tracking is useless, invasive, and wasteful**. What actually matters is instant emergency signaling, guaranteed zero alert loss, routine safety confirmations, battery resilience, and connectivity monitoring.

HomeGuard runs effortlessly on older, low-cost Android hardware (even without a cellular SIM card), connecting over domestic Wi-Fi with guaranteed offline durability.

### Comparison Matrix

| Feature / Attribute | HomeGuard (Open Source) | Commercial Alert Pendants | Typical Family Tracker Apps |
|---|:---:|:---:|:---:|
| **Cost** | **100% Free & Open Source** | \$30–\$60 / month | Freemium / Ad-supported |
| **Hardware Required** | **Any Android phone (Android 8+)** | Proprietary hardware | Modern flagship smartphone |
| **Cellular SIM Required?** | **No (Wi-Fi Native + Offline)** | Mandatory cellular | Mandatory mobile data |
| **GPS / Location Tracking** | **None (Zero Tracking)** | Cell tower / GPS | Continuous background GPS |
| **Battery Impact** | **Ultra-Low (<1% / day)** | Dedicated battery | High battery drain (GPS wake-locks) |
| **SOS Queue Persistence** | **Guaranteed (Durable Room Queue)** | Proprietary protocol | Volatile (drops if disconnected) |
| **Safety State Logic** | **Tri-State (`SAFE`, `UNKNOWN`, `EMERGENCY`)** | Binary Panic Button | Inferred from geofencing |
| **Cloud Independence** | **Works 100% Offline + Pluggable Supabase** | Proprietary cloud only | Proprietary cloud only |
| **Privacy & Telemetry** | **Strict Zero-Knowledge Data Model** | Voice recordings stored | Location data monetized |

---

## 🛡️ Core Pillars

### 1. Guaranteed Zero SOS Loss (Offline-First Queue)
In an emergency, network connectivity is never guaranteed. HomeGuard commits every emergency trigger atomically to an encrypted local **Room database queue (`pending_events`)** before any network I/O is attempted.
- **Online**: Dispatched instantly via Supabase PostgREST with unique idempotency tokens (`UUIDv4`).
- **Offline**: Safely preserved across device reboots and process terminations.
- **Restoration**: Automatically detected by `ConnectivityManager.NetworkCallback` and drained via `WorkManager`. Duplicate alerts are mathematically impossible.

### 2. Tri-State Safety Intelligence
Never assume safety simply because a phone is plugged in or connected to Wi-Fi. HomeGuard enforces an honest, non-alarmist safety state machine:
- 🟢 **`SAFE`**: Active, recent safety confirmation exists (via routine check-in).
- 🟡 **`UNKNOWN`**: Check-in window elapsed or heartbeat stale. Alerts trusted contacts to check in, without causing panic.
- 🔴 **`EMERGENCY`**: An explicit SOS has been confirmed through the 5-second countdown.

```
       [ App Boot / Launch ]
                 │
                 ▼
          ┌─────────────┐
          │   UNKNOWN   │ ◄─────────────────────────┐
          └──────┬──────┘                           │
                 │                                  │ Check-In Window
                 │ User confirms "I'M OK"           │ Elapses (Grace Expired)
                 ▼                                  │
          ┌─────────────┐                           │
          │    SAFE     ├───────────────────────────┘
          └──────┬──────┘
                 │
                 │ Explicit Confirmed SOS
                 ▼
          ┌─────────────┐
          │  EMERGENCY  │ ──( Stop Alarm / Resolve )──> [ UNKNOWN / SAFE ]
          └─────────────┘
```

### 3. Hardware & Battery Economy
Engineered to run 24/7 on repurposed budget phones without overheating or degrading the battery:
- **Zero continuous polling loops**: Uses sticky broadcasts (`ACTION_BATTERY_CHANGED`) and event-driven network callbacks.
- **Threshold Alerts**: Intelligent rate-limited notifications at 20% (Warning) and 10% (Critical).
- **Fast Torch Engine**: Activates the camera flash instantly via `CameraManager.setTorchMode()` with zero camera preview or viewfinder overhead.
- **Acoustic & Haptic Alarm**: AudioTrack alarm stream accompanied by tactile international Morse code SOS vibration pulses (`... --- ...`).

### 4. Modular Communication Abstraction
Because the protected phone may not have an active cellular SIM card, HomeGuard abstracts communication behind a modular provider pipeline (`CommunicationManager`):
- **System Dialer (`ACTION_DIAL`)**: Opens phone dialer directly without requiring dangerous runtime permissions.
- **Internet Communication**: Universal VoIP/Web links or custom messaging URIs for SIM-less Wi-Fi calling.
- **Honest Feedback**: Never fakes a call state—if calling is unavailable, the user is immediately informed and the SOS event is prioritized.

### 5. Complete Neutrality & Localization
HomeGuard is built for international adoption:
- 100% of user-facing strings live in [`res/values/strings.xml`](app/src/main/res/values/strings.xml).
- Zero hardcoded family names (no *Mom*, *Dad*, *Son*, or personal identifiers).
- Fully configurable local emergency numbers (supports 911, 112, 999, 100, etc.).

---

## 🏛️ System Architecture

HomeGuard adheres strictly to Clean Architecture and Android Jetpack guidelines:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI (Material 3)                      │
│   ProtectedHomeScreen • EmergencyScreens • UtilityScreens • Dashboard   │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ StateFlow / UI Events
┌───────────────────────────────────┴────────────────────────────────────┐
│                             HomeViewModel                              │
│         Manages UI State, Countdown Timers, Navigation Routing         │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ Domain Use Cases
┌───────────────────────────────────┴────────────────────────────────────┐
│                           Repository Layer                             │
│     SafetyRepository • EventRepository • SyncRepository • Config       │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
        ┌───────────▼───────────┐        ┌───────────▼───────────┐
        │   Local Data Store    │        │   Remote Data Sync    │
        │ - Room DB (Queue)     │        │ - Supabase PostgREST  │
        │ - Jetpack DataStore   │        │ - Offline Mock Client │
        │ - Hardware Managers   │        │ - WorkManager Worker  │
        └───────────────────────┘        └───────────────────────┘
```

### Module Blueprint

| Component | Class | Purpose |
|---|---|---|
| **Database** | [`HomeGuardDatabase`](app/src/main/java/org/homeguard/core/database/HomeGuardDatabase.kt) | Room database containing `pending_events` and `event_history` tables. |
| **Preferences** | [`PreferencesManager`](app/src/main/java/org/homeguard/core/datastore/PreferencesManager.kt) | Jetpack DataStore handling device modes, contact info, and schedules. |
| **Safety Engine** | [`SafetyRepository`](app/src/main/java/org/homeguard/core/data/SafetyRepository.kt) | Evaluates safety state flow based on check-in timestamps and SOS triggers. |
| **Sync Manager** | [`SyncRepository`](app/src/main/java/org/homeguard/core/data/SyncRepository.kt) | Coordinates idempotent event dispatch with deduplication tokens. |
| **Background Sync** | [`SyncWorker`](app/src/main/java/org/homeguard/core/sync/SyncWorker.kt) | WorkManager coroutine worker with `NetworkType.CONNECTED` constraint. |
| **Flashlight** | [`TorchManager`](app/src/main/java/org/homeguard/core/hardware/TorchManager.kt) | High-speed torch toggle using Android `CameraManager`. |
| **Alarm & Haptics** | [`EmergencyAlarmManager`](app/src/main/java/org/homeguard/core/hardware/EmergencyAlarmManager.kt) | Looping alarm sound generator and tactile SOS vibration pattern. |
| **Battery Monitor** | [`BatteryMonitor`](app/src/main/java/org/homeguard/core/hardware/BatteryMonitor.kt) | Event-driven sticky receiver for battery levels and charging states. |
| **Network Monitor** | [`ConnectivityMonitor`](app/src/main/java/org/homeguard/core/hardware/ConnectivityMonitor.kt) | `ConnectivityManager.NetworkCallback` observer with auto-reconnect hooks. |

---

## 📱 Screens & Interface Tour

The user interface is designed for **2-second visual comprehension** with large, high-contrast touch targets (>64dp) and zero visual noise.

### 1. Protected Device Screen (`PROTECTED_DEVICE`)
Stationed at home. Displays immediate safety status, real-time connectivity and battery telemetry, and large primary action triggers.

```text
┌──────────────────────────────────────────────┐
│  [▲] HOMEGUARD                               │
│      Open-source home safety, made simple.   │
├──────────────────────────────────────────────┤
│  ┌────────────────────────────────────────┐  │
│  │  🟢 SAFE                               │  │
│  │  Safety confirmed by recent check-in   │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Wi-Fi connected   •   Battery 84% (Charging)│
│  Last check-in: 10:30 AM, Today              │
├──────────────────────────────────────────────┤
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │            [✓]  I'M OK                 │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │           [⚠]  EMERGENCY               │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │       [📞]  CALL PRIMARY CONTACT       │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │           [⚡]  FLASHLIGHT             │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  [ Night Mode ]   [ Power Cut ]   [ Info ]   │
└──────────────────────────────────────────────┘
```

### 2. Emergency Countdown & Active Alert
When **EMERGENCY** is pressed:
- A **5-second cancelable countdown** prevents accidental triggers with a prominent `CANCEL` button.
- Upon completion, the alarm sounds, the SOS event is persisted to Room, and the screen transitions to high-contrast alert mode with `STOP ALARM`.
- If offline, an explicit notification reassures the user: *"Your emergency alert has been saved locally and will be sent automatically when the connection returns."*

### 3. Dedicated Offline Utilities
- **Power Cut Mode**: Extra-large flashlight switch, high-visibility battery percentage, quick-dial primary contact, and offline outage guidelines.
- **Night Mode**: Pure black OLED interface that reduces sensory distractions while keeping emergency triggers immediately accessible.
- **Offline Emergency Directory & Fire Guide**: Contact cards, home entrance notes, physician info, optional medical notes (allergies/blood group), and calm fire evacuation procedures.

### 4. Remote Monitoring Dashboard (`MONITORING_DEVICE`)
Viewable on a caregiver or trusted contact's phone:
- Real-time safety status (`SAFE`, `UNKNOWN`, `EMERGENCY`).
- Protected device connectivity (`ONLINE` / `OFFLINE`), battery level, and last seen timestamp.
- Audit timeline of recent check-ins and emergency alerts.

---

## ☁️ Cloud Backend (Supabase)

HomeGuard works completely offline out-of-the-box. When connected to a backend, it integrates seamlessly with **PostgreSQL / Supabase** using lightweight PostgREST endpoints.

### Database Schema Overview ([`backend/schema.sql`](backend/schema.sql))
- **`devices`**: Registered protected and monitoring devices.
- **`device_status`**: Telemetry heartbeat, battery %, charging state, and online presence.
- **`emergency_events`**: Immutable emergency event audit logs with unique UUIDs.
- **`check_ins`**: Time-stamped "I'M OK" safety confirmations.
- **Row Level Security (RLS)**: Policies enabled across all tables.

### Quick Setup
1. Create a free project at [supabase.com](https://supabase.com).
2. In your Supabase Dashboard, open **SQL Editor**.
3. Paste the contents of [`backend/schema.sql`](backend/schema.sql) and click **Run**.
4. In your project, add your Supabase credentials to `local.properties`:
   ```properties
   supabase.url=https://YOUR_PROJECT_REF.supabase.co
   supabase.anon.key=YOUR_ANON_PUBLIC_KEY
   ```

---

## 🛠️ Build & Installation

### Prerequisites
- Android Studio Ladybug (2024.2+) or command-line Android SDK tools.
- JDK 21 (bundled with modern Android Studio).
- Android SDK 35 with Build Tools 35.0.0+.

### Command Line Build

```bash
# Clone the repository
git clone https://github.com/aksh120/homeguard.git
cd homeguard

# Run automated unit tests
./gradlew test

# Build Debug APK
./gradlew assembleDebug

# Build Production Release APK
./gradlew assembleRelease

# Install directly to an ADB-connected Android device
./gradlew installDebug
```

Compiled APK outputs are generated at:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 🔒 Security & Privacy

HomeGuard is safety-critical software designed around zero-knowledge principles:
- **No Background GPS Tracking**: We deliberately do not request location permissions (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`).
- **No Credential Exposure**: Sensitive keys and local configurations are read from `local.properties` (strictly ignored by Git) and compiled into `BuildConfig`.
- **Sandbox Storage**: All local data is isolated in Android application-sandboxed Room databases and DataStore preferences.
- **Strict Permission Footprint**: Only requests `CAMERA` (optional feature for torch flash) and `POST_NOTIFICATIONS`. No SMS, microphone, or storage reading permissions are requested.

---

## ⚠️ Safety Disclaimer

> **IMPORTANT NOTICE**: HomeGuard is an auxiliary home safety companion designed to provide an additional layer of communication and safety awareness. **It is NOT a certified emergency dispatch system, medical alarm device, or replacement for emergency services (such as 911, 112, 999).**
> 
> Operating system battery savers, Wi-Fi router power outages, device battery depletion, or network drops may cause notifications or check-in synchronization to be delayed. Users should always maintain active real-world emergency contacts and professional medical emergency resources.

---

## 🤝 Open Source & Contributing

We welcome contributions, translations, and security audits!
- **Contributing**: Please review [CONTRIBUTING.md](CONTRIBUTING.md) for code style and pull request guidelines.
- **Code of Conduct**: See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
- **Security Inquiries**: See [SECURITY.md](SECURITY.md).
- **Changelog**: All releases are documented in [CHANGELOG.md](CHANGELOG.md).

---

## 📄 License

HomeGuard is open-source software licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.

<div align="center">
  <sub>HomeGuard • Open-source home safety, made simple.</sub>
</div>
