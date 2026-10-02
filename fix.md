# HomeGuard — Fix Plan

Status: proposed. Not yet applied. Ordered by blast radius, not by file location.

Every item cites `file:line` so it can be verified before touching it.

---

## Clarification on "the database does nothing"

The Room layer is **live and correct at the schema level**. Verified against generated code:

- `HomeGuardDatabase_Impl.java:43-44` — `CREATE TABLE IF NOT EXISTS pending_events` / `event_history`
- `PendingEventDao_Impl.java:159` — `SELECT * FROM pending_events WHERE syncStatus = 'PENDING' ...`
- Write path: `HomeViewModel.confirmCheckIn()` → `SafetyRepository.recordCheckIn()` → `EventRepository.enqueueCheckIn()` → **two inserts**
- Read path: `HomeViewModel.recentEvents` → `EventAuditDao.getRecentEventsFlow()` → `MonitoringDashboardScreen.kt:212`

An empty `pending_events` table therefore means one of:

1. No check-in / SOS has ever been recorded (no seed data exists — this is correct behaviour).
2. **Rows were deleted as if synced.** `EventRepository.markEventSynced()` is a `deleteById` (`EventRepository.kt:91`), so a successful sync empties the table by design.
3. Blank Supabase credentials → `SupabaseRemoteSyncDataSource.kt:24-27` returns `Result.success` without sending anything → the queue is **destroyed** and reported as synced. See **P0-3**.

`event_history` should accumulate and is never cleared in production (`clearHistory()` has no callers). If that table is empty, no event has ever been recorded on this device.

The real problems are below.

---

## P0 — Ship blockers

### P0-1 · RLS policies are `USING (true)` — total data exposure

`backend/schema.sql:67-72`. All five policies permit every operation to every caller, including the `anon` role whose key is compiled into `BuildConfig.SUPABASE_ANON_KEY`:

```sql
CREATE POLICY "Allow public device read access" ON public.devices FOR SELECT USING (true);
```

`ARCHITECTURE.md:156` claims "RLS protects device records by user identity". There is no `auth.uid()` scoping and no `user_id` column. **Anyone holding the public anon key can read every device's name, home address, medical allergies/medications, and emergency events.** The anon key is in gitignored `local.properties`, so it is not in git history — but it is baked into both the debug and release APKs.

Fix: introduce real ownership, then scope every policy to it.

```sql
alter table public.devices      add column user_id uuid not null references auth.users(id) on delete cascade;
alter table public.device_status add column user_id uuid not null references auth.users(id) on delete cascade;
alter table public.emergency_events add column user_id uuid not null references auth.users(id) on delete cascade;
alter table public.check_ins     add column user_id uuid not null references auth.users(id) on delete cascade;

drop policy if exists "Allow public device read access" on public.devices;
-- ...drop the other four...

create policy "own devices" on public.devices
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own device_status" on public.device_status
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own emergency_events" on public.emergency_events
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "own check_ins" on public.check_ins
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
```

Note `emergency_events.id` and `check_ins.id` are bare `UUID PRIMARY KEY` with no default — correct for the app's upsert, keep that.

### P0-2 · Failed events are stranded forever — the "zero event loss" guarantee is false

Two compounding bugs.

`Daos.kt:23` filters on `'PENDING'` only:

```sql
SELECT * FROM pending_events WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC
```

But `EventRepository.markEventFailed()` (`EventRepository.kt:94-104`) sets the row to `FAILED`. A single transient network error removes the event from the only query that can ever pick it up. `retryCount` is incremented once and then the row sits in Room permanently.

`SyncRepository.kt:28` then reports success regardless:

```kotlin
Result.success(syncedCount)   // syncedCount may be 0 while everything failed
```

which `SyncWorker.doWork()` (`SyncWorker.kt:33`) treats as done, so it never even asks for `Result.retry()`.

Fix both. Query PENDING *and* retryable FAILED, and fail loudly.

`Daos.kt`:

```kotlin
@Query("""
    SELECT * FROM pending_events
    WHERE syncStatus = 'PENDING'
       OR (syncStatus = 'FAILED' AND retryCount < :maxRetries)
    ORDER BY timestamp ASC
""")
suspend fun getPendingEvents(maxRetries: Int = MAX_RETRIES): List<PendingEventEntity>

companion object { const val MAX_RETRIES = 10 }
```

`SyncRepository`:

```kotlin
var failedCount = 0
for (event in pendingEvents) {
    val result = remoteSyncDataSource.sendEvent(event)
    if (result.isSuccess) {
        eventRepository.markEventSynced(event.id)
        syncedCount++
    } else {
        eventRepository.markEventFailed(event.id)
        failedCount++
    }
}
if (failedCount > 0) Result.failure(SyncIncompleteException(failedCount))
else Result.success(syncedCount)
```

`markEventFailed` must stop incrementing past the ceiling, and `SyncWorker` should honour `Result.retry()`.

### P0-3 · Blank credentials silently delete the entire queue

`SupabaseRemoteSyncDataSource.kt:24-27`:

```kotlin
if (supabaseUrl.isBlank() || anonKey.isBlank()) {
    Log.i("SupabaseSync", "Supabase credentials not configured. Operating in local mock mode.")
    return@withContext Result.success(Unit)   // <- reported as synced
}
```

A "success" here makes `markEventSynced` delete the row. Any build without credentials — a fresh clone, CI, a misconfigured release — **destroys pending SOS events and reports success.** Offline mode must not be modelled as successful delivery.

Fix — surface it so the caller keeps the event:

```kotlin
class RemoteNotConfiguredException : Exception("Supabase credentials not configured")

if (supabaseUrl.isBlank() || anonKey.isBlank()) {
    return@withContext Result.failure(RemoteNotConfiguredException())
}
```

Then have `SyncWorker` treat that specific failure as "stay queued, don't burn retries":

```kotlin
Result.failure(e) -> if (e is RemoteNotConfiguredException) Result.success()
                     else Result.retry()
```

Also make the missing-config case visible in the UI instead of silent.

### P0-4 · `deviceId` is a fresh UUID on every read — all events orphaned

`PreferencesManager.kt:69`:

```kotlin
deviceId = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString(),
```

This sits inside `dataStore.data.map { }`. `KEY_DEVICE_ID` is only written by `saveConfig()` (`:117`), so **until setup is completed every emission of `appConfigFlow` mints a new UUID.** `HomeViewModel.confirmCheckIn()` and `triggerEmergencyNow()` read `appConfig.value.deviceId`, so events recorded before setup carry a device ID that no longer exists. Combined with P0-5, every one of them fails the foreign key.

Fix — mint once, then persist:

```kotlin
val deviceId: Flow<String> = dataStore.data.map { prefs ->
    prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString()
}.distinctUntilChanged()
```

and make persistence happen on first read rather than at setup:

```kotlin
suspend fun ensureDeviceId(): String {
    var id = ""
    dataStore.edit { prefs ->
        id = prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[KEY_DEVICE_ID] = it }
    }
    return id
}
```

Call it from `HomeViewModel.init`. An app-wide stable identity should never depend on a user finishing a wizard.

### P0-5 · `registerDevice()` is never called — the foreign key always fails

`SupabaseRemoteSyncDataSource.kt:93` defines `registerDevice(...)`. Grep confirms it has **zero callers**. `backend/schema.sql` puts `NOT NULL` FKs from `emergency_events.device_id`, `check_ins.device_id`, and `device_status.device_id` to `devices.device_id`.

So even with valid credentials, the first `emergency_events` insert fails on the FK, `postToSupabase` throws (`SupabaseRemoteSyncDataSource.kt:137-140`), the event is marked FAILED, and P0-2 then strands it. **The remote backend has never received a single event from this app.** Combined with the unused `sendHeartbeat()` and P0-3, the sync feature is entirely non-functional.

Fix — register on device identity being established, and re-register defensively before the first event of a session:

```kotlin
// HomeViewModel.init
viewModelScope.launch {
    val config = appConfig.value
    val id = preferencesManager.ensureDeviceId()
    supabase.registerDevice(id, config.deviceMode.name, config.personName, config.personRole)
        .onFailure { Log.w("HomeGuard", "Device registration failed; events will queue", it) }
}
```

### P0-6 · `RECORD_AUDIO` is requested on launch and re-prompted on every `onResume`

`MainActivity.kt:34-40` prompts in `onCreate`; `:54-62` re-checks in `onResume`. `AcousticAlarmDetector.kt:42` then runs a continuous 16 kHz mic loop.

`README.md` and `SECURITY.md` both state the app requests only `CAMERA` and `POST_NOTIFICATIONS` and never the microphone. That is false. Beyond the doc mismatch, a mic prompt fired before the user sees a single screen of context is both a Play Store policy risk and directly at odds with the project's own stated privacy principle.

Fix — remove the launch-time prompt. Request contextually, from the acoustic-detection toggle, and default the feature **off**:

```kotlin
// In AcousticDetectionScreen / settings — on explicit user action only
val micLauncher = registerForActivityResult(RequestPermission()) { granted ->
    if (granted) viewModel.enableAcousticDetection() else viewModel.disableAcousticDetection()
}
```

```kotlin
// HomeViewModel — never auto-start
// delete: acousticAlarmDetector.startListening() from init
fun enableAcousticDetection() = acousticAlarmDetector.startListening()
fun disableAcousticDetection() = acousticAlarmDetector.stopListening()
```

Surface the always-on-mic state in the UI whenever listening is active. Then correct `README.md` and `SECURITY.md`.

---

## P1 — Correctness

### P1-1 · Hand-built JSON is injection-prone and silently unparseable

`EventRepository.kt:23`:

```kotlin
val payload = """{"deviceId":"$deviceId","timestamp":$timestamp,"type":"EMERGENCY_SOS","details":"${details ?: ""}"}"""
```

A `"` or `\` in `details` produces malformed JSON. The consumer swallows it — `SupabaseRemoteSyncDataSource.kt:30-34` catches the parse failure and substitutes `JSONObject()`, so the event is uploaded with **no device ID and no details** while still reporting success.

Fix — use the parser that is already imported:

```kotlin
val payload = JSONObject().apply {
    put("deviceId", deviceId)
    put("timestamp", timestamp)
    put("type", EventType.EMERGENCY_SOS.name)
    put("details", details ?: "")
}.toString()
```

### P1-2 · The "atomic" enqueue is two independent inserts

`EventRepository.kt:33-43` inserts into `pending_events`, then into `event_history`. `README.md` claims an ACID transaction. A crash between them leaves an event with no audit record. Room cannot wrap two DAO calls without a `@Transaction` method on the DAO.

Fix — add to `PendingEventDao`:

```kotlin
@Transaction
suspend fun insertEventWithAudit(event: PendingEventEntity, audit: EventAuditEntity) {
    insert(event)
    eventAuditDao.insert(audit)
}
```

Room supports cross-DAO `@Transaction` when both belong to the same `RoomDatabase`; otherwise expose it from `HomeGuardDatabase` as an abstract `@Transaction` method.

### P1-3 · `resolveEmergency()` sets `UNKNOWN`, but the UI says `SAFE`

`SafetyRepository.kt:54-56`:

```kotlin
suspend fun resolveEmergency() {
    preferencesManager.setSafetyStatus(SafetyStatus.UNKNOWN)
}
```

`EmergencyActiveScreen` captions this as "resets Sentinel state to SAFE". For an elderly user who just cancelled a real emergency, being told they are back to a known-safe state when the app actually believes the opposite is the wrong direction to be wrong in. Either set `SAFE` (and additionally record a check-in so the state is earned) or fix the caption. Prefer the former — resolving should record a deliberate check-in.

### P1-4 · Torch never requests `CAMERA`, so the flashlight fails silently

`TorchManager.kt` calls `CameraManager.setTorchMode`, which needs a runtime `CAMERA` grant on API 23+. The manifest declares `CAMERA` (`AndroidManifest.xml:16`) but nothing ever requests it. The fallback at `TorchManager.kt` logs "unavailable" when `cameraIdList[0]` is not a flash unit, so the user sees a toggle that does nothing.

Fix — request `CAMERA` at the moment the torch is first toggled, and reflect real state in the UI rather than optimistically flipping `isTorchOn`.

### P1-5 · No notification is ever posted

`HomeGuardApplication.kt:22-48` creates three channels (emergency, check-in, telemetry) and **nothing calls `notify()`** — grep for `.notify(` across `app/src/main` returns zero hits. `battery_low_warning` / `battery_critical_warning` are unused strings. `BatteryMonitor` (≤20% low, ≤10% critical) computes the levels and discards them.

`README.md` promises "intelligent rate-limited notifications at 20% / 10%" and an "explicit notification reassures the user" when an SOS is queued while offline. Neither exists. For a background-only safety app this is the single most user-visible missing feature.

Fix — post from the ViewModel on threshold edges (rising edge only, so it fires once per discharge), and post a reassurance notification on offline SOS enqueue. Use `WorkManager` or a foreground service if the alert must survive backgrounding; document the `FOREGROUND_SERVICE` permission and its type if you add the latter.

### P1-6 · `TactileHoldSosRadial` can fire on an early release, and haptics never fire

`CyberAegisComponents.kt`. Two bugs:

- `progress` jumps to `1.0f` in `onPress`, and `tryAwaitRelease()` blocks. On early release the state resets to `0f` and `animateFloatAsState` tweens back down over 200ms — but the completion check is `animatedProgress >= 0.99f`, so releasing at ~99.5% still triggers the SOS. Use an explicit `heldForMillis` accumulator and compare against the full duration.
- The escalation loop `while (isPressing) { haptic; delay(300) }` is launched as `LaunchedEffect(isPressing)`. Setting `isPressing = true` *inside* `onPress` does not restart the effect, so the captured value is stale and haptics do not run.

Hold-to-confirm is the primary emergency affordance. Both defects must be covered by a UI test.

### P1-7 · `BootReceiver` is a stub

`BootReceiver.kt:10-13` logs a line and contains a comment where the body should be. Harmless today because WorkManager restores its own periodic work, but it is registered in the manifest (`AndroidManifest.xml:43`) implying behaviour that does not exist. Either re-enqueue explicitly and verify, or delete the receiver and the manifest entry.

---

## P2 — Quality, a11y, and dead weight

### P2-1 · Localization contract is broken

`CONTRIBUTING.md` principle 4: "never hardcode user-facing strings in Kotlin". `README.md` claims 100% localization.

Reality: 38 `R.string.*` references in the whole main source set, **17 of them in `Components.kt` which is dead code.** The live screens hardcode English throughout — `"SENTINEL ACTIVE • HOME"`, `"TACTICAL UTILITY COCKPIT"`, `"FIRE EVACUATION PROTOCOL"`, every setup label and placeholder, plus `"No recent check-in"`, `"Just now"`, and the `"${elapsedMinutes}m ago"` format in `HomeViewModel.kt:110-122`. Roughly 70 of the 100 strings in `strings.xml` are unused.

Fix — extract every live hardcoded string, then delete the now-duplicate entries and the orphan families. Add a lint gate so this cannot regress:

```xml
<!-- app/build.gradle.kts -->
lint {
    warningsAsErrors = true
    error += "HardcodedText"
}
```

### P2-2 · Accessibility was orphaned by the HUD redesign

The last two commits replaced the Material 3 system with the "Cyber-Aegis Sentinel HUD". `Components.kt:62-208` has real `semantics` / `contentDescription` blocks — and **all three of its components are unreferenced.** The live `CyberAegisComponents.kt` has almost none.

For an app whose users are elderly, 8.5sp all-caps letterspaced neon-on-black telemetry labels are close to the opposite of the goal. `PLAN.md` commits to ">72dp targets", "screen-reader semantics", "high-contrast typography".

Fix — restore `semantics { contentDescription = ... }` and `Role.Button` on every interactive node in `CyberAegisComponents.kt`, raise body text to ≥14sp and labels to ≥12sp, and re-run TalkBack over all ten screens. Then either delete `Components.kt` or reuse its semantics blocks.

### P2-3 · No DI; dependency graph built twice

`HomeViewModel.kt:55-68` constructs DB → prefs → 3 repositories → 4 hardware managers → 3 sensors inline. `SyncWorker.kt:28-30` builds a second, independent graph. There is no Hilt/Dagger/Koin. Adding the work in P0-4/P0-5 deepens the problem.

Given `CONTRIBUTING.md` principle 1 ("no bloat"), do **not** add Hilt. A plain container is proportionate:

```kotlin
class AppContainer(context: Context) {
    val database by lazy { HomeGuardDatabase.getInstance(context) }
    val preferencesManager by lazy { PreferencesManager(context) }
    val eventRepository by lazy { EventRepository(database.pendingEventDao(), database.eventAuditDao()) }
    val safetyRepository by lazy { SafetyRepository(preferencesManager, eventRepository) }
    val syncRepository by lazy { SyncRepository(eventRepository, SupabaseRemoteSyncDataSource()) }
    // hardware managers + sensors
}
```

Hold one instance on `HomeGuardApplication`, have `HomeViewModel` take it, and have `SyncWorker` read the same one. One graph, one place where the shape is visible.

### P2-4 · Documented layers that do not exist

`ARCHITECTURE.md` described a `Domain / Use Cases` layer (`TriggerSosUseCase`, `SubmitCheckInUseCase`) and four repositories (`CheckInRepository`, `BatteryRepository`, `ConnectivityRepository`, `SettingsRepository`). There was no `usecase` package and none of those repositories existed.

**Fixed by correcting the document**, not by building the layer. `ARCHITECTURE.md` now shows the real shape (ViewModel → `AppContainer` → repositories) and says plainly that there is no DI framework and why. If a use-case layer is wanted later, it has an obvious home.

### P2-5 · Dead code

- `RemoteSyncDataSource.kt` → `DefaultRemoteSyncDataSource` is a simulated no-op, and `SyncWorker.kt:14` imports it without using it.
- `sendHeartbeat()` implemented twice, **never invoked**. Yet `device_status.last_seen_at` exists in the schema and the README promises a heartbeat. Implement it on a periodic worker or delete it and the column.
- `AppConfig.checkInIntervalHours` / `checkInGracePeriodMinutes` — `saveConfig` never writes them and `appConfigFlow` never reads them, so they are permanently 24/60 while appearing configurable.
- `DeviceStatus`, `EmergencyEvent`, `CheckIn` domain models — declared, never used.
- `DeviceMode.MONITORING_DEVICE` — settable in setup, **read by no UI or routing code.** The "caregiver dashboard" therefore renders *this* device's local state, contradicting `README.md`.
- `navigation-compose` in `app/build.gradle.kts` — unused; `HomeGuardApp.kt` hand-rolls routing over a `MutableStateFlow`.
- `ContactInfo.relationship`, `MedicalInfo.notes`, `HomeInfo` partial — never read into or out of DataStore.
- `app/proguard-rules.pro` — never applied; `isMinifyEnabled = false` in both build types (`:38`, `:45`). The 18MB debug / oversized release APK is the direct consequence.

### P2-6 · Unimplemented features promised in docs

None of these exist: scheduled check-ins (no `AlarmManager`, no scheduler, no prompt, no schedule UI — check-in happens only on the manual "I'm OK" tap or the movement heuristic at `HomeViewModel.kt:81-87`), night-mode start/end schedule (boolean only), remote caregiver monitoring, Room encryption (`exportSchema = false`, `fallbackToDestructiveMigration()`, no SQLCipher).

Decide per item: build it, or correct the docs. Shipping claims that are false is worse than shipping a smaller feature set — especially on a safety app where a user may trust a capability that is not there.

### P2-7 · Manifest and misc hardening

- `android:allowBackup="true"` (`AndroidManifest.xml`) on an app storing medical info, while `README.md` claims "sandboxed storage". Set `false` or define an explicit `dataExtractionRules` / `fullBackupContent` that excludes the DB and DataStore.
- `theme.xml` sets transparent system bars; `Theme.kt` immediately overwrites them with an opaque colour via `SideEffect`. Delete the dead declaration.
- `FallAndMotionDetector` and `AcousticAlarmDetector` each own a `CoroutineScope(Dispatchers.X)` that is never cancelled — only their jobs are. **Fixed:** scopes use `SupervisorJob()` and are cancelled in `stop()` / `stopListening()`.
- `EmergencyAlarmManager` loops the default alarm ringtone via `MediaPlayer`. **Correction to my earlier note:** `isLooping = true` does loop indefinitely, so the siren does *not* run out. The real gap is that the alarm is not backed by a foreground service, so it dies with the process and there is no way to stop it once the app is backgrounded. `README.md` also claimed "AudioTrack"; the only `AudioTrack` user is `CaregiverBeaconManager`. **Not fixed** — it needs a foreground service and a `FOREGROUND_SERVICE` permission, which is a product decision.
- `ConnectivityMonitor` swallows registration exceptions silently.

### P2-8 · Tests that assert nothing

`app/src/test` has 9 passing tests; two of the three headline safety guarantees have **zero real coverage.**

- `BatteryLogicTest` reimplements the thresholds in a private helper and asserts against that copy. `BatteryMonitor` is never exercised.
- `SafetyRepositoryTest` — the three state-machine tests (`:81`, `:99`, `:113`) reimplement the `combine` logic inline in the test body. `SafetyRepository` is imported but never actually driven.
- Only the two `EventRepository` enqueue tests and the three `SyncRepositoryTest` cases exercise production code.

Fix: extract the state machine into a pure function so it is testable without a `Context`, then assert against it.

```kotlin
// SafetyRepository.kt
internal fun calculateSafetyStatus(
    stored: SafetyStatus,
    lastCheckIn: Long?,
    intervalHours: Int,
    graceMinutes: Int,
    now: Long = System.currentTimeMillis()
): SafetyStatus = when {
    stored == SafetyStatus.EMERGENCY -> SafetyStatus.EMERGENCY
    lastCheckIn == null -> SafetyStatus.UNKNOWN
    now - lastCheckIn > TimeUnit.HOURS.toMillis(intervalHours.toLong()) +
        TimeUnit.MINUTES.toMillis(graceMinutes.toLong()) -> SafetyStatus.UNKNOWN
    else -> SafetyStatus.SAFE
}
```

Then the existing three tests call it, and add coverage for: exactly-at-the-boundary, P0-2 retry-then-succeed, P0-3 blank-credentials-must-not-delete, and P1-1 a `details` string containing `"` and `\`.

`androidTestImplementation` deps are already declared but there is no `androidTest/` source set. The P1-6 hold-to-SOS gesture and P1-4 torch need instrumented tests.

### P2-9 · Secrets handling

`local.properties` holds a live anon key in plaintext and it is correctly gitignored, but `app/build.gradle.kts:16-17` bakes it into `BuildConfig` for **release** too. Route release credentials through CI secrets, and rotate the current key since it has been sitting on disk and in built APKs.

---

## Suggested order

1. **P0-4, P0-5** — device identity + registration. Nothing remote works until these land.
2. **P0-2, P0-3** — retry semantics and no silent deletion. This is the actual "zero event loss" guarantee.
3. **P0-6** — remove the launch-time mic prompt. Small diff, real policy risk.
4. **P0-1** — RLS. Needs a migration; coordinate with the Supabase project.
5. **P1-1, P1-2** — JSON + transaction.
6. **P1-5** — notifications. The largest user-visible gap.
7. **P1-3, P1-4, P1-6, P1-7** — correctness and the emergency gesture.
8. **P2-3** then **P2-8** — container + real tests, so the rest has somewhere to live.
9. **P2-1, P2-2** — localization and accessibility.
10. **P2-4 … P2-7, P2-9** — docs reconciliation, dead code, hardening, secrets.

Steps 1-3 are small, mechanical, and independently verifiable. Step 4 needs the schema migration path agreed first.

---

## Definition of done

- `gradlew :app:testDebugUnitTest :app:assembleDebug` green.
- A new unit test proves an event that fails to send is retried and eventually deleted only on real success.
- A new unit test proves blank credentials leave the event queued.
- `registerDevice` has a caller; a real insert into `emergency_events` succeeds against a staging Supabase project with the new RLS policies.
- Device ID is stable across app restarts before setup completes.
- No microphone permission is requested until the user enables acoustic detection.
- `grep -c "USING (true)" backend/schema.sql` → `0`.
- Every live screen passes TalkBack; no body text below 13sp.
- `README.md`, `ARCHITECTURE.md`, and `SECURITY.md` each describe only what the code does.

> **`HardcodedText` lint gate was dropped.** Lint's `HardcodedText` check only inspects XML layout
> files, not Compose `Text()` calls, so `error += "HardcodedText"` would have been a no-op that
> looked like a safeguard. Localization is enforced by review instead. A custom lint rule or a
> `grep` in CI would be needed to actually gate it.

---

## Implementation status

Applied in the order above. `:app:testDebugUnitTest` and both `assembleDebug` / `assembleRelease`
pass; 30 unit tests, 0 failures.

### Shipped

| Item | What changed |
|---|---|
| P0-1 | `user_id` added to all four tables, policies scoped to `auth.uid()`. New [`backend/migrations/001_rls_ownership.sql`](backend/migrations/001_rls_ownership.sql) back-fills existing rows and refuses to run on orphans. Client signs in anonymously via `SupabaseAuthClient` and sends that JWT. |
| P0-2 | Queue selects PENDING **and** retryable FAILED; `markEventFailed` stops at `MAX_RETRIES = 10`; `SyncRepository` returns `SyncIncompleteException`; `SyncWorker` returns `Result.retry()`. |
| P0-3 | Blank credentials now return `RemoteNotConfiguredException`; the worker treats it as "stay queued" and does not consume retries. |
| P0-4 | `ensureDeviceId()` persists identity on first use; `AppConfig.deviceId` defaults to blank and `saveConfig` refuses to overwrite a real ID with an empty one. |
| P0-5 | `registerDevice()` called on startup and on every config save. |
| P0-6 | Mic prompt removed from `onCreate` and `onResume`; acoustic detection is opt-in via a Settings toggle; permission state is persisted. |
| P1-1 | Payloads built with `JSONObject`. Covered by a test using quotes, backslashes, `%`, `<`, `&`. |
| P1-2 | Dual insert moved into `@Transaction insertEventWithAudit` on `HomeGuardDatabase`. |
| P1-3 | `resolveEmergency(deviceId)` records a real check-in and sets SAFE; caption corrected. |
| P1-4 | Torch checks the `CAMERA` grant, dropped the `cameraIdList[0]` fallback, and resets state on failure. Permission requested on first toggle. |
| P1-5 | `SafetyNotifier` posts offline-SOS reassurance, check-in confirmation, and battery low/critical on rising edges only. `ic_stat_homeguard` + strings added. |
| P1-6 | Hold-to-SOS measures wall-clock time with `SystemClock.elapsedRealtime()`; haptics run in a child coroutine of the gesture scope. |
| P1-7 | `BootReceiver` re-schedules periodic sync. |
| P2-1 | ~98 hardcoded strings extracted. Audit summaries stored as stable constants and localized at render time. |
| P2-2 | `contentDescription` / `Role.Button` on the hero orb, SOS button, action tiles, and buttons. Sub-12sp text raised to ≥13sp. |
| P2-3 | `AppContainer` composition root shared by the ViewModel and `SyncWorker`. No DI framework, per `CONTRIBUTING.md` principle 1. |
| P2-5 | Deleted `Components.kt`, `DefaultRemoteSyncDataSource`, and three unused domain models. `navigation-compose` dropped. **Minify enabled** — release APK went 174MB → **1.5MB**. R8 keep rules written for Room/WorkManager. Check-in interval and acoustic flag now actually persist. |
| P2-7 | `allowBackup=false` + `data_extractionRules` excluding all domains; deprecated system-bar APIs replaced with edge-to-edge; coroutine scopes cancelled. |
| P2-8 | 30 tests, all against production code. The two tautological tests are gone. |
| P2-9 | Release credentials now read **only** from `SUPABASE_URL` / `SUPABASE_ANON_KEY` env or `-P` properties. `local.properties` feeds debug only, so a developer's key can never reach a shipped APK. |
| P2-4, P2-6 | `README.md`, `ARCHITECTURE.md`, `SECURITY.md` corrected. Encryption, scheduled check-ins, night-mode schedules, and remote caregiver monitoring are no longer claimed as shipped. |

### Not done

- **Rotate the existing Supabase anon key.** It was on disk and in built APKs. Rotation is manual and
  must be done in the Supabase dashboard.
- **Enable Anonymous Sign-Ins** in the Supabase dashboard before the new RLS policies will accept
  writes. Without it every request is rejected.
- **Apply `backend/migrations/001_rls_ownership.sql`** to any existing database.
- **Foreground-service the emergency alarm** (see the P2-7 correction).
- **Scheduled check-ins** (AlarmManager + prompt) and **night-mode schedules** remain unimplemented.
  Documented as such rather than faked.
- **`DeviceMode.MONITORING_DEVICE` is still inert.** It is settable in setup but changes no
  behaviour; the dashboard shows this device's own state. Building a real remote view is a feature,
  not a fix.
- **No instrumented tests.** The `androidTest/` source set is still empty, so the hold-to-SOS
  gesture and the torch permission path are verified by inspection only.
- **Night-mode schedule, medical-notes field**, and `ContactInfo.relationship` remain unused model
  surface.