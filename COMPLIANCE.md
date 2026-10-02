# HomeGuard — Compliance Status

Audit of the implementation against the master development specification. Section numbers refer
to that document.

**Overall: substantially complete on the offline-first safety core.** Every section is either
implemented or explicitly recorded as a known limitation in the README.

---

## Implemented

| § | Requirement | Notes |
|---|---|---|
| 1, 50 | Neutral branding, no personal defaults | Verified: no personal names in source. Every field blank by default |
| 2 | No GPS / geofencing / travel tracking | No location permission exists |
| 3, 4 | Native stack, Kotlin, Compose, version catalog | AGP 8.12, Kotlin 2.0.21, Compose BOM 2024.10.01, `libs.versions.toml` |
| 5, 38 | Performance-first, event-driven, no polling | AlarmManager for check-ins, `ConnectivityManager` callbacks, `WorkManager` 15-min sync, sticky battery broadcast. No wake locks, no foreground service |
| 6A | Protected device screen | Status, check-in, emergency, telemetry, action list |
| 7 | Two-second comprehension | Ordered: status → check-in → emergency → device health → actions |
| 8 | Restrained, non-SaaS UI | **Reversed in `feat: align UI...`.** Removed glassmorphism, gradients, neon, and decorative animation. Flat surfaces, 16sp+ text, 64dp+ targets |
| 9 | Configurable personalization | Protected person, three contact tiers, home info. All editable later |
| 10 | Full localization | All display strings in `strings.xml`. Audit trail summaries stored as stable constants and mapped to localized text at render time |
| 11 | SOS with configurable countdown | 5-second countdown, cancel, then persist → alert → alarm → screen |
| 12 | SOS must never be lost | Queue written before any network call. Retried 10×. Failed events never deleted. Survives restart, reboot, Wi-Fi loss |
| 13 | Tri-state safety | `SAFE` / `UNKNOWN` / `EMERGENCY` as a pure, unit-tested function |
| 14 | Configurable scheduled check-ins | `CheckInSchedule` (up to twice daily), AlarmManager-driven prompt, configurable timeout, unanswered prompt notifies |
| 15 | Missed check-in never implies emergency | `CheckInTimeoutEvaluator` returns only a boolean; it cannot produce `EMERGENCY` |
| 16 | Night mode with start/end times | `NightModeSchedule` handles windows wrapping past midnight. Dimmed, animation-free |
| 17 | Configurable battery thresholds | `BatteryThresholds`, default 20/10, validated and persisted |
| 18 | Wi-Fi / internet via callbacks | `ConnectivityManager.NetworkCallback`. No server pinging |
| 19 | Lightweight heartbeat | Every 15 min via WorkManager, with battery and connectivity state |
| 20 | Offline-first | Queue + idempotent upserts (`Prefer: resolution=merge-duplicates`) |
| 21 | Offline emergency information | Contacts, local emergency number, home info, optional medical |
| 22 | Fire guide with disclaimer | Six steps plus an explicit "not professional advice" caveat |
| 23 | Power cut mode | Flashlight, battery, contacts, offline guidance |
| 24 | Flashlight | `CameraManager`, no preview, runtime `CAMERA` requested only on toggle |
| 25 | Emergency alarm | Alarm ringtone + SOS vibration pattern. **Foreground service not implemented** |
| 26 | Communication abstraction | `CommunicationProvider` interface: dialer and internet providers. Reports failure honestly |
| 27 | Monitoring interface | Activity log implemented. **Reads local state, not remote** |
| 28 | Backend | Supabase: auth, RLS, device registration, events, check-ins, status, contacts, webhook notification |
| 29, 43 | Security | HTTPS, per-install anonymous sessions, RLS scoped to `auth.uid()`, release secrets from CI only, R8 enabled |
| 30 | Minimal permissions | No location, contacts, SMS, microphone, storage. **Microphone feature deleted, not merely gated** |
| 31 | Accessibility | `contentDescription` and `Role.Button` on interactive controls, 16sp+ text, 64dp+ targets, status shown as text and not colour alone |
| 32 | No technical errors shown | All user-facing failures are plain language; technical detail goes to logcat |
| 33 | Lightweight architecture | Compose → ViewModel → `AppContainer` → repositories. No giant composables, no static context |
| 34 | Data model | `ProtectedPerson`, `ContactInfo`, `EmergencyInformation`, `HomeInfo`, `MedicalInfo`, `CheckInSchedule`, `NightModeSchedule`, `BatteryThresholds`, `AppConfig` |
| 35 | DataStore vs Room | DataStore for preferences; Room only for the queue and audit trail |
| 36 | Notification channels | Four channels. Battery, offline-SOS, check-in, missed check-in. Rate-limited |
| 37 | Fast startup | Lazy DI. First frame renders before sync work begins |
| 39 | Testing | 60 unit tests, all against production code |
| 41, 43 | Git hygiene | Conventional commits, small increments, no secrets committed |
| 46, 48 | Documentation and open-source files | `PLAN.md`, `ARCHITECTURE.md`, `PHASES.md`, `TASKS.md`, `README.md`, `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md` |
| 49 | Configurable emergency numbers | User-entered. No country assumption |
| 51 | Privacy | No analytics, no ads, no location, no behavioural tracking. Medical fields optional |
| 52 | Safety disclaimer | Shown once after setup and acknowledged. Documented in README |
| 53 | Four-question UX | Status, help, contact, device health — in that order |

---

## Known limitations

Recorded here and in the README rather than papered over.

| § | Gap | Impact |
|---|---|---|
| 27 | **No caregiver app or remote dashboard.** The activity screen shows this phone's own log. `MONITORING_DEVICE` mode is stored but inert | A caregiver cannot watch the device remotely in-app |
| 25 | **Emergency alarm has no foreground service** | Stops if the process is killed. Cannot be relied on as the primary alarm |
| 28 | **Email-only notification.** No push, no SMS | Not real-time; will not arrive if the caregiver's phone is offline |
| 39, 40 | **No instrumented or UI tests.** No profiling on low-end hardware | Gesture and permission paths verified by inspection only |
| — | **Local data not encrypted at rest** | Sandboxed and backup-excluded, but not SQLCipher |
| 9 | **Photo not implemented** | `ProtectedPerson.photoUri` modelled but unused |
| 19 | `device_readings` table exists; **the app does not yet write history rows** | Upsert-only `device_status` holds current values |

---

## Deliberate deviations

| Decision | Reasoning |
|---|---|
| No DI framework | §33 says do not overengineer and §5 forbids unnecessary dependencies. `AppContainer` is ~50 lines. Hilt would add an annotation processor for a graph this small |
| No use-case layer | §33's suggested diagram includes one, but it would be a pass-through indirection over three repositories. `ARCHITECTURE.md` documents the actual shape rather than an aspirational one |
| Check-ins limited to twice daily | The schedule UI permits two times. A comfort prompt that fires six times a day is noise, and §54 asks whether each feature earns its complexity |
| Webhook not wired to `check_ins` | §36 warns against notification spam. "The device is fine" emails are exactly that |
| `MONITORING_DEVICE` retained but inert | Removing it would be cleaner; implementing a remote dashboard is a feature, not a fix, and is recorded as a limitation instead |