<div align="center">

  <img src="art/logo.svg" alt="HomeGuard logo" width="160" height="160" />

  # HomeGuard
  ### Open-Source Home Safety, Made Simple.

  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

  <p align="center">
    <strong>A privacy-respecting Android safety layer for people staying at home.</strong><br>
    Built for elderly family members, people living alone, parents, roommates, and anyone who
    wants a dependable extra layer of safety.<br>
    Runs on any spare Android 8+ phone, with or without a SIM card.
  </p>

  <p align="center">
    <a href="#-what-it-does">What it does</a> •
    <a href="#-what-it-does-not-do">What it does not do</a> •
    <a href="#-architecture">Architecture</a> •
    <a href="#-backend-setup">Backend setup</a> •
    <a href="#-building">Building</a> •
    <a href="#-limitations">Limitations</a>
  </p>

</div>

---

## What HomeGuard is for

When someone is at home, GPS tracking is useless, invasive, and a drain on the battery. What
matters instead is: **can I ask for help quickly, can my contacts know I am okay, and does the
device still work?**

HomeGuard turns a spare Android phone into a home safety device: a large emergency trigger with a
hold-to-confirm guard, scheduled safety checks, offline alert queuing, battery and connectivity
monitoring, an offline emergency reference card, a fire guide, night mode, and a power-cut mode.

| | HomeGuard | Commercial alert pendant | Family tracker app |
|---|:---:|:---:|:---:|
| Cost | Free, open source | \$30–\$60 / month | Freemium / ads |
| Hardware | Any Android 8+ phone | Proprietary | Modern flagship |
| SIM required | No | Yes | Yes |
| Location tracking | **None** | Cell tower / GPS | Continuous GPS |
| Offline durability | Alert queue on device | Proprietary | Usually lost |
| Safety states | `SAFE` / `UNKNOWN` / `EMERGENCY` | Binary panic button | Inferred from geofences |

---

## What HomeGuard does

**Emergency (SOS)**
- Large press-and-hold trigger with a 2.5-second guard, so a pocket bump cannot fire it
- A 5-second countdown with cancel before anything is sent
- Persisted to a local queue **before** any network call, so an alert cannot be lost
- Survives app restart, device reboot, Wi-Fi loss, and server failure
- Retried on every sync pass, up to 10 attempts. A failed alert is never deleted
- Loud local alarm plus Morse-code vibration

**Safety checks**
- A configurable schedule (up to twice a day) raises "Are you okay?"
- Answering records a check-in; not answering within the timeout downgrades the state to `UNKNOWN`
- A missed check-in is explicitly **not** treated as an emergency
- Significant device movement counts as a passive check-in

**Device health**
- Battery level, charging state, and configurable warning and critical thresholds
- Wi-Fi and internet state via `ConnectivityManager` callbacks, not polling
- A heartbeat so a caregiver can tell an offline device from a healthy one
- Low-battery alerts at the configured thresholds, fired once per discharge

**Offline reference**
- Emergency contacts, local emergency number, home address and access instructions
- Optional medical details: allergies, blood group, medications, doctor
- A fire evacuation guide with an explicit "this is not professional advice" caveat

**Other**
- Night mode with a configurable window and a dimmed, animation-free screen
- Power-cut mode with flashlight and battery guidance
- Flashlight via `CameraManager`, no camera preview

---

## What HomeGuard does **not** do

Stated plainly, because a safety app that overstates itself is worse than one that does less.

- **No location tracking of any kind.** No GPS permission exists in the manifest.
- **No caregiver app or remote dashboard.** The "Recent activity" screen shows what *this* phone
  has recorded. Watching a device from a second phone is not implemented. `MONITORING` device
  mode is stored but currently changes no behaviour.
- **No SMS or phone delivery.** Caregiver alerts are delivered by **email** only, via a Supabase
  webhook and edge function. A contact with a phone number but no email will not be reached.
- **No push notifications.** Email is not real-time in the way FCM would be, and it will not
  arrive if the caregiver's phone is offline.
- **The emergency alarm is not backed by a foreground service.** It stops if the process is
  killed or the app is swiped away, and it stops when the screen turns off on some devices.
- **Local data is not encrypted at rest.** It is sandboxed and excluded from backup, which is not
  the same thing.
- **No photo upload.** `ProtectedPerson.photoUri` is modelled but unused.
- **Performance has not been profiled on low-end hardware.** Section 40 of the specification asks
  for this; it has not been done.

---

## Architecture

```
UI (Compose)
  ↓ StateFlow
HomeViewModel
  ↓
AppContainer (composition root — no DI framework)
  ↓
Repositories:  SafetyRepository · EventRepository · SyncRepository
  ↓                      ↓                      ↓
Room queue            DataStore            Supabase REST
+ audit log           preferences          (+ anonymous auth session)
```

No Hilt, Dagger, or Koin. `CONTRIBUTING.md` principle 1 rules out dependency bloat, and the graph
is small enough to read in one screen, so it is wired by hand in
[`AppContainer`](app/src/main/java/org/homeguard/core/di/AppContainer.kt).

**Safety state machine** — a pure function, unit tested:

| State | Meaning |
|---|---|
| `SAFE` | A check-in was confirmed inside the configured window |
| `UNKNOWN` | No check-in yet, or the window elapsed |
| `EMERGENCY` | An explicit SOS was confirmed through the countdown |

A missed check-in moves `SAFE → UNKNOWN` and never further. Only the SOS button produces
`EMERGENCY`.

**Permissions.** `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
`VIBRATE`, and an optional `CAMERA` for the flashlight. No location, no contacts, no microphone, no
storage.

**Local data.** DataStore for preferences and settings; Room only for the event queue and audit
trail. Backup is disabled with explicit extraction rules, because this app stores medical details
and home addresses.

Full detail in [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Backend setup

HomeGuard works fully offline. The backend is optional and only adds caregiver email alerts and
remote visibility of queued events.

### 1. Create a Supabase project

### 2. Apply the schema

In the Supabase SQL editor, run in order:

| File | Purpose |
|---|---|
| [`backend/schema.sql`](backend/schema.sql) | Tables, indexes, RLS policies |
| [`backend/migrations/001_rls_ownership.sql`](backend/migrations/001_rls_ownership.sql) | Scopes RLS to `auth.uid()`. For an empty database. |
| [`backend/migrations/001b_backfill_existing_rows.sql`](backend/migrations/001b_backfill_existing_rows.sql) | Only if you already have rows |
| [`backend/migrations/002_monitoring_contacts.sql`](backend/migrations/002_monitoring_contacts.sql) | Caregiver contacts and status history |

### 3. Enable Anonymous sign-ins

**Authentication → Providers → Anonymous Sign-Ins → ON**

This is required. The RLS policies are scoped to `auth.uid()`, and the app bootstraps a per-install
anonymous session. Without it every write is rejected.

### 4. Deploy the caregiver notification function

```bash
supabase functions deploy notify-caregiver --no-verify-jwt
supabase secrets set RESEND_API_KEY=re_...
```

### 5. Create the webhook

**Database → Webhooks → Create**:

- Table `emergency_events`, event `INSERT`
- Type: *Supabase Database Webhook*
- URL: `https://<project-ref>.supabase.co/functions/v1/notify-caregiver`
- Add header `Authorization: service_role`

The function is
[`supabase/functions/notify-caregiver/index.ts`](supabase/functions/notify-caregiver/index.ts). It
reads `RESEND_API_KEY` from a function secret, so no credential is ever shipped in the app.

There is deliberately **no** webhook on `check_ins`: a caregiver does not need an email every
morning saying the device is fine.

### 6. Point the app at it

Add to `local.properties` (debug builds only; release uses CI secrets):

```properties
supabase.url=https://YOUR_PROJECT_REF.supabase.co
supabase.anon.key=YOUR_ANON_KEY
```

---

## Building

Requires JDK 21 and the Android SDK.

```bash
# Debug APK
./gradlew :app:assembleDebug

# Tests
./gradlew :app:testDebugUnitTest

# Release (R8 minified, unsigned)
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/`. Release is minified and shrinks from ~18 MB to ~1.5 MB.

Release credentials come only from `SUPABASE_URL` / `SUPABASE_ANON_KEY` environment variables or
`-P` properties. `local.properties` feeds debug alone, so a developer's key cannot reach a shipped
APK.

---

## Testing

60 unit tests covering the safety state machine, the offline queue and its retry semantics, JSON
payload serialisation, battery thresholds, the check-in schedule and timeout rules, and the night
mode window.

```bash
./gradlew :app:testDebugUnitTest
```

**Not covered:** instrumented and UI tests. The hold-to-emergency gesture and the flashlight
permission path are verified by inspection only.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Four principles are enforced by review:

1. Safety first — no bloat or non-essential background work
2. Strict neutrality — no personal names, no country-specific emergency numbers
3. Privacy first — no location or behavioural tracking
4. 100% localized — never hardcode user-facing strings in Kotlin

## License

[Apache 2.0](LICENSE). Contributions welcome.

---

## Safety disclaimer

HomeGuard is an **additional** safety layer. It is **not** a guaranteed emergency response service.

Internet may be unavailable. The phone may lose power. Android may restrict background activity, so
alerts can be delayed. Email alerts will not arrive if the caregiver's phone is offline.

**Keep real emergency contacts you can reach by other means.** Do not rely on HomeGuard as your
only safety net.