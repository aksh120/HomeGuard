# HomeGuard Architecture Specification

## 1. Architectural Philosophy

HomeGuard is architected as an **offline-first, event-driven, reactive system** designed for extreme reliability on constrained hardware.

```
┌────────────────────────────────────────────────────────┐
│               Jetpack Compose UI Layer                 │
│  (Protected Home, Emergency Screen, Monitoring Dash)   │
└──────────────────────────▲─────────────────────────────┘
                           │ UI State (StateFlow) / Events
┌──────────────────────────┴─────────────────────────────┐
│                   ViewModel Layer                      │
│     (HomeViewModel, EmergencyViewModel, SetupVM)       │
└──────────────────────────▲─────────────────────────────┘
                           │ Domain Models / Use Cases
┌──────────────────────────┴─────────────────────────────┐
│                 Domain / Use Cases                     │
│    TriggerSosUseCase, SubmitCheckInUseCase, SyncEvents │
└──────────────────────────▲─────────────────────────────┘
                           │ Repositories
┌──────────────────────────┴─────────────────────────────┐
│                  Repository Layer                      │
│  SafetyRepo, SyncRepo, BatteryRepo, NetworkRepo, Config│
└────────────┬─────────────────────────────┬─────────────┘
             │                             │
    ┌────────▼────────┐           ┌────────▼────────┐
    │   Local Store   │           │   Remote / API  │
    │  - Room Queue   │           │ - Supabase REST │
    │  - DataStore    │           │ - Realtime      │
    │  - Hardware API │           │ - Mock Client   │
    └─────────────────┘           └─────────────────┘
```

---

## 2. Safety State Machine

HomeGuard defines three immutable safety states:

```
              ┌──────────────────────────────────────┐
              │                                      │
              │         [App Launch / Boot]          │
              │                                      │
              └──────────────────┬───────────────────┘
                                 │
                                 ▼
                     ┌───────────────────────┐
                     │                       │
           ┌─────────┤       UNKNOWN         ├─────────┐
           │         │                       │         │
           │         └───────────┬───────────┘         │
           │                     │                     │
           │ Check-In Timeout    │ Successful Check-In │ Explicit SOS Trigger
           │                     ▼                     │
           │         ┌───────────────────────┐         │
           │         │                       │         │
           └─────────┤         SAFE          ├─────────┤
                     │                       │         │
                     └───────────────────────┘         │
                                                       │
                                                       ▼
                                           ┌───────────────────────┐
                                           │                       │
                                           │       EMERGENCY       │
                                           │                       │
                                           └───────────────────────┘
```

* **`SAFE`**: A successful, unexpired check-in exists, and no active emergency has been initiated.
* **`UNKNOWN`**: A scheduled check-in window has elapsed without response, heartbeat telemetry is stale (>30 min), or initialization is incomplete. *Crucial rule: UNKNOWN is never treated as an emergency alert.*
* **`EMERGENCY`**: An explicit SOS has been confirmed through the countdown mechanism. Can only be cancelled/cleared by explicit user interaction (`STOP ALARM` / `DEACTIVATE`).

---

## 3. SOS Lifecycle & Resilience Pipeline

The SOS system guarantees **zero event loss** even under sudden power loss, network dropouts, or process termination:

```
[ User Presses EMERGENCY ]
             │
             ▼
[ 5-Second Configurable Countdown ] ──( User presses CANCEL )──> [ Return to Prior State ]
             │
             ▼ ( Countdown Reaches 0 )
[ Step 1: Create UUID & Emergency Event ]
             │
             ▼
[ Step 2: Persist into Room 'pending_events' table ] (ACID transaction)
             │
             ▼
[ Step 3: Trigger High-Priority Emergency Notification & Audio Alarm ]
             │
             ▼
[ Step 4: Dispatch via Network Repository ]
             │
     ┌───────┴───────┐
     │ Network OK    │ Offline / Network Error
     ▼               ▼
[ Send to Server ] [ Mark 'NEEDS_SYNC' in Room ]
     │               │
     │               ▼
     │         [ WorkManager Constraints: Connected ]
     │               │
     ▼               ▼ ( Connectivity Restored )
[ Mark Synced ] <────┘
```

---

## 4. Hardware Integrations

1. **Flashlight (`CameraManager`)**:
   - Uses `CameraManager.setTorchMode(cameraId, enabled)`.
   - Zero camera previews, zero camera capture sessions, zero permission prompt on API 23+ (uses system torch ID).
2. **Battery Telemetry (`BatteryManager`)**:
   - Listens to sticky intent `Intent.ACTION_BATTERY_CHANGED`.
   - Monitors `BatteryManager.EXTRA_LEVEL`, `BatteryManager.EXTRA_SCALE`, and `BatteryManager.EXTRA_STATUS` (charging vs unplugged).
   - Warning fired at `≤ 20%`, critical alert triggered at `≤ 10%`.
3. **Connectivity Monitoring (`ConnectivityManager.NetworkCallback`)**:
   - Registers callback via `NetworkRequest.Builder().addCapability(NET_CAPABILITY_INTERNET)`.
   - Purely event-driven; no socket polling, zero recurring battery-draining HTTP ping loops.
4. **Alarm (`AudioTrack` / `MediaPlayer` / `RingtoneManager`)**:
   - Sounds high-visibility alarm tone at alarm audio stream.
   - Non-blocking asynchronous playback with immediate cancellation hook via `STOP ALARM`.

---

## 5. Storage Architecture

### A. Preferences DataStore
Key-value configuration including:
- Protected person profile: Name, relationship.
- Primary, secondary, emergency contact details.
- Home address & emergency instructions.
- Check-in configuration: Schedules, grace period, timeout duration.
- Night Mode configuration: Enabled, start time, end time.
- Application mode: `PROTECTED_DEVICE` vs `MONITORING_DEVICE`.

### B. Room Database (`HomeGuardDatabase`)
Relational audit and queue tables:
- `pending_events`: Queue of unsynced events (SOS, check-ins, status transitions) with retry count and idempotency token.
- `event_history`: Local audit trail of all safety checks and alerts for display on monitoring timeline.

---

## 6. Remote Synchronization & Backend API

HomeGuard supports a pluggable backend interface (`RemoteSyncDataSource`):
- **Local / Mock Implementation**: Allows testing and full standalone utility without any cloud account.
- **Supabase / REST Implementation**:
  - Tables: `devices`, `emergency_events`, `check_ins`, `device_status`.
  - Row Level Security (RLS) protects device records by user identity.
  - Idempotent API endpoints using unique event UUIDs prevent duplicate alerts.
