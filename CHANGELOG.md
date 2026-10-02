# Changelog

All notable changes to HomeGuard are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Scheduled safety checks** (spec §14). Configurable daily schedule, driven by `AlarmManager`
  rather than polling. Re-arms itself from the receiver so the schedule survives the app being
  closed. An unanswered prompt downgrades the state to `UNKNOWN` and notifies — never to
  `EMERGENCY`, per §15.
- **Configurable night mode window** (§16), including windows that wrap past midnight.
- **Configurable battery thresholds** (§17), replacing hardcoded 20/10 values.
- **Missed check-in notification** (§36), rate-limited to one per missed check-in.
- **Safety notice shown in the app** (§52). The disclaimer string existed but was never rendered.
- **Caregiver notification path** (§28). New `monitoring_contacts` and `device_readings` tables, a
  `notify-caregiver` Edge Function, and a documented Database Webhook setup. Delivery is email;
  no credential is shipped in the app.
- **Caregiver email field** on the primary contact (§9), published to the backend on save.
- Domain models from §34: `ProtectedPerson`, `EmergencyInformation`, `CheckInSchedule`,
  `NightModeSchedule`, `BatteryThresholds`.
- 30 new unit tests covering check-in timeout rules, schedule windows, night-mode windows, and
  battery threshold validation.

### Changed

- **Reversed the "Cyber-Aegis Sentinel HUD" redesign** (§8). The previous UI introduced exactly what
  the specification forbids: glassmorphism, multi-stop gradients on every screen, neon accents on
  near-black, and a pulsing radar orb. Replaced with flat surfaces, hairline borders, a neutral
  slate palette, a static status card, and a flat progress bar on the emergency trigger. Bright red
  is now reserved for the emergency trigger alone. Body text raised to 16sp minimum and touch
  targets to 64dp minimum.
- Sci-fi wording replaced with plain language: "SENTINEL ACTIVE • HOME" → "Safe",
  "TACTICAL UTILITY COCKPIT" → "Actions", and similar across all screens.
- Safety state machine extracted into a pure `calculateSafetyStatus` function so it is testable
  without a `Context`.
- Battery threshold evaluation extracted into `evaluateBatteryState`.

### Fixed

- **Failed events were stranded permanently.** `getPendingEvents` filtered on `PENDING` while
  `markEventFailed` wrote `FAILED`, so one transient network error removed an event from the only
  query that could ever select it again. Retries now work, up to 10 attempts.
- **`SyncRepository` reported success even when every send failed**, so `SyncWorker` never requested
  a retry.
- **Blank Supabase credentials deleted the queue.** Missing credentials returned
  `Result.success`, which made `markEventSynced` remove queued events without sending them. Now
  raises `RemoteNotConfiguredException` and keeps events queued.
- **`deviceId` was a fresh UUID on every DataStore emission** until setup completed, orphaning any
  event recorded first. Identity is now minted once and persisted.
- **`registerDevice()` had no callers**, so the foreign key into `devices` always failed and no
  event ever reached the backend.
- **All five RLS policies were `USING (true)`**, so the public anon key could read every device's
  medical details. Now scoped to `auth.uid()` with per-install anonymous sessions and a
  back-filling migration.
- **Hold-to-emergency could fire on an early release.** Completion was judged by an animated value
  against a threshold; releasing at ~99% still triggered. Now measured with wall-clock time.
  Haptic escalation was moved into the gesture coroutine, where it can actually run.
- **Payload JSON was built by string interpolation.** A quote in the details produced malformed
  JSON, which the consumer silently discarded, sending events with no device ID and no details.
  Now built with `JSONObject`.
- **Event enqueue was two independent inserts.** Now a Room `@Transaction`.
- **Resolving an emergency set `UNKNOWN` while the UI claimed `SAFE`.** It now records a
  deliberate check-in so `SAFE` is earned.
- **Flashlight called `setTorchMode` without a runtime `CAMERA` grant**, so it failed silently.
- **`BootReceiver` was a log-only stub.** Now re-schedules periodic sync.
- **Check-in timeout off-by-one.** Reaching the deadline exactly was treated as missed; it now
  matches `SafetyRepository`'s strictly-greater-than expiry test.
- **Coroutine scopes in both sensors were never cancelled**, only their jobs.
- **`allowBackup` was enabled** on an app storing medical details. Disabled, with explicit
  extraction rules.
- **Release builds had `isMinifyEnabled = false`**, leaving an ~18 MB APK unminified. R8 enabled
  with keep rules for Room and WorkManager; release is now ~1.5 MB.
- **~98 user-facing strings were hardcoded** in Kotlin despite §10.
- **`secondaryContactName` had no input field** in setup, so it could never be set.

### Removed

- **Microphone access and acoustic smoke-alarm detection** (§30). The specification excludes the
  microphone unless a future feature explicitly requires it, and none is specified. The detector
  was deleted rather than made opt-in, `RECORD_AUDIO` was removed from the manifest, and the
  acoustic column was removed from telemetry.

### Security

- Row Level Security scoped to `auth.uid()` across all tables, with per-install Supabase anonymous
  sessions as the identity.
- Release credentials read only from `SUPABASE_URL` / `SUPABASE_ANON_KEY` environment variables or
  `-P` properties. `local.properties` now feeds debug builds alone.
- Cloud backup and device-transfer extraction disabled for all domains.
- Added `backend/migrations/001_rls_ownership.sql` and
  `backend/migrations/002_monitoring_contacts.sql`.
- Added [`COMPLIANCE.md`](COMPLIANCE.md), a section-by-section audit against the specification.

### Documentation

- `README.md`, `ARCHITECTURE.md`, and `SECURITY.md` rewritten to describe only what the code does.
- Removed false claims: encrypted local storage, a caregiver-viewable dashboard, a heartbeat that
  did not exist, and a use-case layer that was never built.
- Documented what HomeGuard does **not** do, so the limits are visible before someone relies on it.

## [1.0.0] - 2026-09-29

### Added

- Initial Android project foundation (Gradle Kotlin DSL, version catalog, Compose).
- Safety state machine, event queue, offline persistence, and Supabase sync layer.
- Home screen, emergency countdown, emergency information, fire guide, and power-cut mode.
- Battery, connectivity, and flashlight integration.