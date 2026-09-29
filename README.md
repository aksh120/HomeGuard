# HomeGuard

> **HomeGuard - Open-source home safety, made simple.**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-blue.svg)](https://developer.android.com/jetpack/compose)

---

## What is HomeGuard?

**HomeGuard** is a production-quality, open-source Android application engineered to provide a dependable, privacy-respecting safety layer for people staying at home.

Whether deployed for an elderly family member, someone living alone, parents, roommates, or individuals recovering from medical care, HomeGuard turns an existing or repurposed Android phone into a dedicated home safety companion.

Unlike invasive commercial trackers, **HomeGuard never performs GPS tracking, geofencing, or outdoor movement profiling**. It assumes the protected device is stationed at home—frequently operating on older or budget hardware, potentially without a cellular SIM card, relying strictly on Wi-Fi and offline resilience.

---

## Key Features

- **🚨 SOS Emergency Alerting**: Immediate emergency trigger with a 5-second cancelable countdown, high-priority local audio alarm, and automated dispatch.
- **🛡️ Guaranteed Zero SOS Loss**: Emergency events are atomically committed to a durable local Room database queue before any network dispatch. Failed sends automatically retry via WorkManager when connectivity returns.
- **✅ Scheduled Safety Check-Ins ("I'M OK")**: Configurable check-ins with clear, non-alarmist state transitions (`SAFE` → `UNKNOWN`). A missed check-in alerts trusted contacts to check in, without falsely triggering an active emergency.
- **📶 Wi-Fi & Connectivity Telemetry**: Event-driven `ConnectivityManager.NetworkCallback` monitors network availability without battery-draining polling.
- **🔋 Intelligent Battery Telemetry**: Native battery broadcast monitoring with actionable thresholds (20% warning, 10% critical) and charge-state visibility.
- **🔦 Flashlight & Power Cut Mode**: Instant torch access using Android's `CameraManager` (no camera preview or image capture), coupled with a dedicated Power Cut screen with battery telemetry and quick contacts.
- **🌙 Night Mode**: Low-distraction dark mode preserving quick emergency access while reducing ambient glare and visual clutter.
- **📋 Offline Emergency Information**: Offline-accessible emergency contacts, local emergency services numbers, optional medical overview, and home building notes.
- **📱 Dual-Interface Architecture**: Supports both **Protected Device** (large touch targets, 2-second visual comprehension) and **Monitoring Dashboard** (trusted contact overview of safety state, battery, and events).
- **🌐 Completely International & Neutral**: 100% localized through Android resources. Zero hardcoded personal names, family assumptions, or country-specific numbers.

---

## Safety Disclaimer

> **Important**: HomeGuard is an auxiliary safety companion and is **NOT** a replacement for professional emergency dispatch systems (such as 911, 112, 999), medical alarm equipment, or active personal supervision. Internet connectivity may drop, phone batteries may deplete, and background task execution may be delayed by operating system battery optimizations. Always maintain direct real-world emergency contacts.

---

## Technical Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin 2.x |
| **Minimum SDK** | Android 8.0 (API Level 26 - Oreo) |
| **Target SDK** | Android 15 (API Level 35) |
| **UI Framework** | Jetpack Compose + Material 3 |
| **State Management** | Kotlin Coroutines & `StateFlow` |
| **Local Storage** | Jetpack DataStore (Config) + Room (Event Queue) |
| **Background Scheduling** | AndroidX WorkManager |
| **Hardware APIs** | `CameraManager`, `BatteryManager`, `ConnectivityManager` |
| **Backend** | Supabase REST / Edge Functions (Pluggable with Offline Mock) |

---

## Architecture

HomeGuard adheres to clean, unidirectional architecture principles:

```
UI (Compose) ➔ ViewModel (StateFlow) ➔ Domain Use Cases ➔ Repositories ➔ Local DB (Room / DataStore) / Remote API
```

1. **Safety State Machine**: Tri-state logic (`SAFE`, `UNKNOWN`, `EMERGENCY`). Safety is never assumed simply because a device is online; it requires active confirmation.
2. **Offline-First Persistence**: All critical state changes and alerts are written to persistent storage before network I/O.
3. **Hardware Economy**: No continuous CPU wake-locks, zero continuous background polling, and lazy component initialization.

---

## Building and Running

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17 or JDK 21
- Android SDK 35 with Build Tools 35+

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Run Unit Tests
```bash
./gradlew test
```

### Build Release APK
```bash
./gradlew assembleRelease
```

---

## Contributing & Security

We welcome community contributions, translations, and security audits!
- **Contributing Guidelines**: [CONTRIBUTING.md](CONTRIBUTING.md)
- **Security Policy**: [SECURITY.md](SECURITY.md)
- **Code of Conduct**: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

---

## License

HomeGuard is open-source software licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.
