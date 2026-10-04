# HomeGuard — NEW_PHASES.md

**Status:** Planning complete. Phase 0 not yet started.
**Written:** 2026-10-04
**Supersedes:** nothing. Complements `PHASES.md` (the original 21 build phases) and `COMPLIANCE.md`.

This document is the authoritative roadmap for turning HomeGuard into **one APK containing two
genuinely distinct experiences**: the protected at-home device and the caregiver monitoring device.

It records decisions already made so they are not relitigated, the reasoning behind them, and the
exact work required per phase.

---

## Table of contents

1. [Product vision](#1-product-vision)
2. [Locked decisions](#2-locked-decisions-with-reasoning)
3. [Platform decision: Supabase + FCM](#3-platform-decision-supabase--fcm)
4. [Confirmed defects in current code](#4-confirmed-defects-in-current-code)
5. [Schema inventory](#5-schema-inventory-current-state)
6. [New backend design](#6-new-backend-design)
7. [Remote SOS — the hard part](#7-remote-sos--the-hard-part)
8. [Phase plan](#8-phase-plan)
9. [Actions only the developer can perform](#9-actions-only-the-developer-can-perform)
10. [Known constraints and accepted limitations](#10-known-constraints-and-accepted-limitations)
11. [Open questions](#11-open-questions)
12. [Definition of done](#12-definition-of-done)
13. [Working agreement](#13-working-agreement)

---

## 1. Product vision

One application, two roles, zero ambiguity between them.

```
┌─────────────────────────────┐         ┌─────────────────────────────┐
│  PROTECTED (at home)        │         │  MONITORING (caregiver)     │
├─────────────────────────────┤         ├─────────────────────────────┤
│  SOS / Emergency            │         │  People you watch           │
│  I'm OK                     │         │  Live status, battery, net  │
│  Flashlight                 │         │  Last check-in              │
│  Night mode                 │         │  Call                       │
│  Power-cut alert            │         │  Activity feed (all people) │
│  Personal + medical profile │         │  Remote SOS → a watched    │
│  Check-in schedule           │         │    person's alarm           │
│  Share this house's code    │         │  Add another person         │
└─────────────────────────────┘         └─────────────────────────────┘
         one APK, one binary, one install
```

The role is chosen **once, at first run**, and then governs which routes exist. Not a field inside
a shared form — a genuine fork. This is the single most important architectural change in the
roadmap, because everything else (routing, permissions, copy, capabilities) follows from it.

### Why this shape

An elderly resident needs a large, simple, high-contrast screen with very few things on it.
A caregiver needs dense, information-rich monitoring. Serving both from one shared UI is what
produced the current "same options in both modes" complaint. Forking at setup fixes it properly.

---

## 2. Locked decisions (with reasoning)

Every item below was decided in conversation. Rationale is recorded so the decision survives
without re-litigation.

### D1 — One APK, two profiles

**Decided.** The resident's phone and the caregiver's phone run the same binary. The role is a
first-run choice.

*Rationale:* one project, one Play Store listing, one update path, no duplicate maintenance. The
two experiences diverge at routing level, not at install level.

### D2 — One house, one code

**Decided.** A household holds multiple protected devices and multiple caregivers, all under a
single share code.

*Rationale:* a code you must read aloud or text to two different relatives is a code that will
get mistyped and abandoned. One code per house means the whole family watches the same code.

*Implementation consequence:* the schema already permits this (see §5). Zero migration needed for
multi-person support — the single-device limit lives entirely in the app layer.

### D3 — All-or-nothing medical sharing

**Decided.** If a caregiver can see the medical profile, they see all of it.

*Rationale:* simpler mental model for the resident, simpler UI, no per-section settings screen to
build or explain. The privacy cost is explicit and accepted: a caregiver with access learns
everything in the profile, not a chosen subset.

*Documentation obligation:* this is recorded in `SECURITY.md` and `COMPLIANCE.md` as a deliberate
design choice, not an oversight.

### D4 — Symmetric approval codes

**Decided.** Both of these require a code shown on the protected phone and typed by the caregiver:

| Transition | Approval required? | Reason |
|---|---|---|
| Caregiver joins a household | **Yes** | Existing caregiver consents to sharing |
| Caregiver removed from household | **Yes** | Same path, symmetric |
| Protected → Monitoring switch | **Yes** | See below |
| Monitoring → Protected switch | **No** | No existing owner to ask |

*Rationale for protected → monitoring:* without approval, anyone with brief physical access to an
elderly person's phone could silently convert it into a surveillance device feeding a stranger's
household. The consent of the existing caregiver is the control that prevents it.

*Rationale for monitoring → protected:* there is no counterparty in the household who is being
disarmed by the switch, so requiring approval would be theatre. The phone re-runs protected setup
and offers to leave the household it was watching.

### D5 — Unapproved switch stays pending indefinitely

**Decided.** The protected phone remains protected, keeps displaying the pending request, and
**never expires it**.

*Rationale:* a timeout on a consent prompt is a silent consent. If the caregiver never answers,
the correct behaviour is for nothing to change, permanently.

*Implementation consequence:* no expiry timestamp, no cleanup job, no auto-revert on
`remote_commands` of kind `APPROVE_MODE_SWITCH`.

### D6 — No one-tap "stop watching me"

**Decided — explicitly declined.** The protected phone does **not** get a one-tap caregiver-removal
button. Removal goes through the symmetric approval code (D4).

*Rationale accepted:* the resident cannot unilaterally revoke a caregiver's access. The flip side
is recorded honestly in §10 as an accepted risk.

### D7 — Remote SOS from the caregiver profile

**Decided.** The monitoring profile **has** an SOS button. It raises an alarm **on behalf of a
selected watched person** — never for the caregiver themself. It does both:

1. Immediately notifies the watched person's own registered contacts (email + push)
2. Makes the watched person's phone alarm — torch, siren, full-screen SOS

Full design in §7. This is the highest-risk item in the roadmap.

### D8 — Push always on, email optional

**Decided.** Every caregiver always receives push. Email is a per-contact opt-in toggle.

*Rationale:* push is the channel that actually reaches a phone. Email is the fallback and the
channel people trust for a record.

### D9 — Supabase stays; FCM only for push

**Decided.** See §3.

### D10 — Realtime for live dashboard, FCM for background alerts

**Decided.** Two mechanisms, each doing what it is good at:

| Concern | Mechanism |
|---|---|
| Dashboard updates while caregiver has app open | **Supabase Realtime** (private channel per household) |
| Alerts to a locked, backgrounded phone | **FCM** |

*Rationale:* Realtime is already available, free, and cheap for a handful of concurrent caregivers.
FCM is the only thing that can wake a closed app. Using Realtime for both would mean no alert
arrives when the caregiver is not looking at the app — unacceptable for a safety app.

---

## 3. Platform decision: Supabase + FCM

### The short version

**Keep Supabase for everything that is already built. Add FCM as a push transport only. Do not
migrate anything.**

### The framing error to avoid

Supabase and Firebase are not interchangeable backends for this app. The question "Supabase or
Firebase?" was asked and answered incorrectly until it was split by layer:

| Need | Chosen | Why |
|---|---|---|
| Relational database | **Supabase** | 6 related tables with foreign keys and role-based access |
| Row-level security | **Supabase** | "Caregivers see only their own household" is a few lines of Postgres RLS. The Firestore equivalent is hand-written security rules that are easy to get subtly wrong. |
| Auth (anonymous) | **Supabase** | Already built and working |
| Email alerts | **Supabase Edge Functions + Resend** | Already deployed and tested |
| Live dashboard | **Supabase Realtime** | Already available |
| Push to a locked phone | **FCM** | The only option that exists |

### Verified cost facts

Checked against vendor documentation, not memory:

- **FCM is free and unlimited on every Firebase plan, including the free Spark plan. No payment
  method required.** Default quota is 600,000 messages/minute, which is irrelevant at this app's
  scale. It is a permanently no-cost product, not a trial.
- **Supabase Free pauses a project after 7 days of low activity.** On restore, the first request
  takes 10–30 seconds to cold-start. This is the one genuine downside of the free tier for a
  safety app, and is recorded in §10.
- Supabase Free limits otherwise: 500 MB database, 5 GB/month egress, 50,000 auth MAU, 2 projects,
  200 concurrent Realtime connections. All far beyond this app's needs.

### Why not "run it all on Firebase to keep it in one place"

Technically possible and still free, and explicitly rejected. It would mean rewriting the data
layer, converting Postgres RLS into Firestore Security Rules, porting the edge function to Cloud
Functions, and losing relational integrity across 6 tables whose entire access model is
"who is a member of which household."

That is a large correctness regression for no practical gain. Firebase's only real advantage here
is that it never pauses — which is addressed by awareness (§10), not by a rewrite.

### Firebase's actual role in this project

One dependency, one config file, one token table, one branch in an existing edge function. Not a
backend choice.

---

## 4. Confirmed defects in current code

Each of these was verified by reading the source, not inferred.

### D-1 — Monitoring onboarding is unreachable

`app/src/main/java/org/homeguard/ui/HomeGuardApp.kt:88`

The monitoring branch only renders `MonitorJoinScreen` when
`currentDestination == Home`. A fresh install lands on `Setup` (see D-2), so the condition is
false and the join screen never appears. A monitoring-profile user cannot get past setup.

**Fix:** role determines the entry destination, not incidental navigation state.

### D-2 — Fresh install is forced into protected setup

`app/src/main/java/org/homeguard/ui/HomeViewModel.kt:166-169`

`if (!config.isSetupComplete)` redirects to `Setup` unconditionally. Combined with D-1 this is
why the monitoring profile appears broken.

### D-3 — Setup does not branch by role

`app/src/main/java/org/homeguard/ui/screens/SetupScreens.kt`

The role selector exists, but both branches render the same protected-person form — person name,
medical, contacts, schedule. There is no role-specific setup path at all. This is the direct
cause of the reported "same options in both modes" symptom.

### D-4 — Monitoring is hardwired to a single device

- `app/src/main/java/org/homeguard/ui/HomeViewModel.kt:580` —
  `devices.getOrNull()?.firstOrNull { ... }` takes one device
- `app/src/main/java/org/homeguard/core/data/PreferencesManager.kt` — single `KEY_HOUSEHOLD_ID`
- `app/src/main/java/org/homeguard/ui/screens/CaregiverDashboardScreen.kt` — single `device` param

The schema supports many (D2). The app layer does not.

### D-5 — Household invite code cannot be used to join

`app/src/main/java/org/homeguard/core/data/MonitoringRemoteDataSource.kt`

`findHouseholdByCode()` selects from `households` filtered by invite code, but the RLS policy is
`USING (public.is_household_member(id))` — which requires **existing membership**. A prospective
caregiver is therefore not permitted to see the one row they need in order to join.

**Fix:** a `SECURITY DEFINER` lookup function that resolves an invite code to a household ID
without exposing the row, so joining works without granting open reads.

### D-6 — Rows are written without `household_id`

`app/src/main/java/org/homeguard/core/data/SupabaseRemoteSyncDataSource.kt`

Emergency events, check-ins, heartbeat status, and monitoring contacts are all written without
`household_id`, while caregiver reads filter on it (D2). Caregiver dashboards would read empty.

### D-7 — Rows are written without `user_id`

`backend/schema.sql` and `supabase/migrations/20261002060630_backfill_existing_rows.sql` both set
`user_id UUID NOT NULL`. No ordinary sync payload supplies it. RLS policies require
`auth.uid() = user_id`, so these inserts fail the not-null constraint even once the 429 is fixed.

**Fix:** a `BEFORE INSERT` trigger assigning `user_id = auth.uid()` when null. Correct at the data
layer and non-spoofable, rather than trusting each client to send it.

### D-8 — Remote SOS is impossible by policy design

`supabase/migrations/20261002142224_households.sql:139-143`

```sql
CREATE POLICY "write_own_emergency_events" ON public.emergency_events
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
```

The comment immediately above it (lines 131-132) states the intent:

> Caregivers read the home device's data. They never write to it: an emergency row must come from
> the device that raised it, otherwise a caregiver could fabricate one.

D7 (remote SOS) deliberately overrules this. The overrule must be guarded rather than a loosened
policy — see §7.2.

### D-9 — Notification permission is never hard-required

`POST_NOTIFICATIONS` is declared in `AndroidManifest.xml:7` and defensively checked in
`SafetyNotifier.kt:29` and `CheckInAlarmReceiver.kt:90`, but nothing guarantees the resident
granted it.

**Why it matters now:** on API 33+ a denied permission makes FCM notifications silently vanish.
Push would appear broken with no error anywhere.

**Fix:** the monitoring profile gets a blocking "enable notifications" gate, and the protected
profile gets an equivalent gate before it can be remotely alarmed.

### D-10 — Release config is environment-injected, so Firebase config must match

`app/build.gradle.kts:40-55`

Debug reads `local.properties`; release reads CI secrets. Firebase's `google-services.json` is
per-build-type, so it needs copies at `app/src/debug/` and `app/src/release/`. Release also runs
R8 (`isMinifyEnabled = true`, `isShrinkResources = true`), so a release build must be verified
rather than assumed.

### D-11 — Offline queue contains unsent safety events

Measured on device `9D6PZ9KF8PWSFQKR`: 43 queued rows — **23 FAILED, 20 PENDING** — plus 44
`event_history` rows (43 check-ins, 1 SOS). Local reads and writes are healthy; remote delivery is
failing. This is Phase 0's real-world test case, and it is a genuine safety gap: a resident has
raised an SOS that no caregiver received.

---

## 5. Schema inventory (current state)

As of `4f9dba5`.

### Tables

| Table | Ownership column | RLS writes | Notes |
|---|---|---|---|
| `devices` | `user_id NOT NULL` | owner only | `household_id` nullable (003) |
| `device_status` | `user_id NOT NULL` | owner only | `household_id` nullable (003) |
| `emergency_events` | `user_id NOT NULL` | owner only | `household_id` nullable (003); no `source`, no `triggered_by` |
| `check_ins` | `user_id NOT NULL` | owner only | `household_id` nullable (003) |
| `device_readings` | `user_id NOT NULL` | owner only | 002 |
| `monitoring_contacts` | `user_id NOT NULL` | owner only | 002 |
| `households` | `created_by` | `created_by = auth.uid()` | 003; `invite_code` UNIQUE |
| `household_members` | `user_id NOT NULL` | self-insert | 003; `UNIQUE (household_id, user_id)` |

### What the schema already supports (no migration needed)

- **A caregiver in multiple households.** `UNIQUE (household_id, user_id)` is per-household, so the
  same `user_id` may appear in many households. A caregiver watching two families works at the
  schema level already.
- **Multiple devices per household.** `devices.household_id` is a plain nullable FK with no
  uniqueness constraint.

**Therefore D2 requires no migration.** The single-device restriction is app-layer only (D-4).

### Existing helper

`public.is_household_member(target_household UUID)` — `SECURITY DEFINER`, `search_path` pinned to
`public`, returns whether `auth.uid()` is a member. Exists to avoid RLS recursion. New policies
follow this established pattern.

### Migration order

Migrations live in `supabase/migrations/`, which is the only directory the Supabase CLI reads. They
were previously in `backend/migrations`, where `supabase db push` could not see them — the CLI
reported "Remote database is up to date" while pending changes sat unapplied. See L10.

```
20261002060629_rls_ownership                       APPLIED
20261002060630_backfill_existing_rows              APPLIED
20261002071530_monitoring_contacts                 APPLIED
20261002142224_households                          APPLIED
20261004130928_row_ownership_and_invite_lookup     APPLIED
20261004140000_household_ownership                 APPLIED  (superseded by 143000)
20261004143000_column_aware_ownership_trigger      APPLIED
20261004150000_household_policy_split              APPLIED
20261004160000_atomic_household_creation           APPLIED
20261004170000_reload_postgrest_schema             APPLIED  (see L12)
```

001–003 were applied through the dashboard SQL editor, so the CLI had no history of them and they
were baselined with `supabase migration repair` before 004 could be pushed on its own.

**Verification (11/11 checks passing)** run directly in SQL, because PostgREST could not route the
RPCs at the time (L12):

| Check | Result |
|---|---|
| Household created with its first member in one call | 1 |
| Membership row role is `PROTECTED` | ✅ |
| `households.created_by` filled from the JWT, not the client | 1 |
| `devices.user_id` filled by trigger | 1 |
| `check_ins.user_id` filled by trigger | 1 |
| **`check_ins.household_id` propagated from the device** (fixes D-6) | 1 |
| Invite code resolves to its household | 1 |
| Wrong invite code returns nothing | 0 |

RLS enforcement was verified separately over HTTP: an insert without `user_id` was rejected with
42501 before the trigger existed, and a temporary fully permissive policy did **not** make the
failing insert succeed, which is what proved the failure was the `RETURNING` clause rather than the
policy expression.

---

## 6. New backend design

### 6.1 `push_tokens` (Phase 4)

Renamed from the earlier `caregiver_push_tokens` sketch because **both** roles now register —
the protected device needs a token to receive remote alarm commands (§7).

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK | |
| `user_id` | `uuid` FK `auth.users` NOT NULL | assigned by trigger |
| `token` | `text` NOT NULL UNIQUE | FCM registration token |
| `platform` | `text` CHECK (`'ANDROID'`) | |
| `created_at` / `updated_at` | `timestamptz` | |

RLS: a user reads and writes only their own tokens.

Client obligation: handle `onNewToken`. A stale token silently kills push forever, which is the
classic way this feature dies after a working demo.

### 6.2 `remote_commands` (Phase 5)

One table serves both remote features — remote SOS and the mode-switch approval flow — so there is
a single delivery mechanism with a single set of guarantees rather than two bespoke ones.

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` PK | **idempotency key** |
| `device_id` | `uuid` FK `auth.users` NOT NULL | target device |
| `household_id` | `uuid` FK `households` | |
| `kind` | `text` CHECK (`'RAISE_ALARM'`, `'APPROVE_MODE_SWITCH'`) | |
| `payload` | `jsonb` | kind-specific |
| `requested_by` | `uuid` FK `auth.users` | caregiver who asked |
| `status` | `text` CHECK (`'PENDING'`,`'DELIVERED'`,`'EXECUTED'`,`'FAILED'`) | |
| `created_at` | `timestamptz` NOT NULL | |
| `delivered_at` / `executed_at` | `timestamptz` | |

**Delivery, two paths:**

1. **Instant** — insert fires the webhook → edge function sends an FCM high-priority data message
   carrying the command `id`
2. **Fallback** — the protected device's `SyncWorker` polls for `PENDING` rows, so alarms still
   land without Google Play Services or after a dropped push

The `id` as idempotency key is what makes these two paths safe to run together: a push plus a poll
of the same row must not double-fire the siren.

**RLS:**

- `INSERT` — requester must be a household member of the target device's household, via a
  `SECURITY DEFINER` helper (same pattern as `is_household_member`)
- `SELECT` — target device owner, or the requester
- `UPDATE` — **device owner only.** A caregiver cannot mark their own command executed.

### 6.3 `emergency_events` additions (Phase 5)

| Column | Type | Notes |
|---|---|---|
| `source` | `text` CHECK (`'DEVICE'`, `'CAREGIVER_REMOTE'`) | default `'DEVICE'` |
| `triggered_by` | `uuid` FK `auth.users` | null for `DEVICE` |

### 6.4 Ownership trigger (Phase 0)

```sql
-- Assign user_id and derive household_id from the device row, so clients cannot
-- spoof ownership and caregiver reads can find their rows.
```

Addresses D-6 and D-7 together: one `BEFORE INSERT` trigger over the five device tables sets
`user_id = auth.uid()` when null and resolves `household_id` by looking up the device.

---

## 7. Remote SOS — the hard part

### 7.1 What the caregiver presses

The monitoring profile has an SOS button. It targets a **selected watched person**. It never raises
an alarm about the caregiver's own device — the monitoring profile is not a safety device for its
owner.

Two effects, both required:

1. **Immediate** — an `emergency_events` row for the watched device → existing webhook → email and
   push to that person's registered `monitoring_contacts`. *(Works regardless of phone state.)*
2. **Alarm** — an `RAISE_ALARM` command reaches the watched person's phone: torch, siren,
   full-screen SOS, labelled clearly.

### 7.2 Why the security guard is mandatory

D-8 records that the schema deliberately forbids caregiver writes to `emergency_events`, for a
sound reason: any caregiver could fabricate an emergency on a resident's phone, spamming that
resident's real contacts and triggering a false alarm. D7 overrules this, so the overrule carries
its own controls:

| Control | Mechanism |
|---|---|
| Who may request | `SECURITY DEFINER` check that requester is in the target device's household |
| Abuse rate limit | **DB trigger**, one `RAISE_ALARM` per device per 15 minutes — enforced in Postgres so no client can bypass it |
| Attribution | `source` + `triggered_by` columns |
| No self-marking | `UPDATE` restricted to the device owner |
| Resident transparency | Phone shows **"Caregiver requested assistance"** with the requester's name — never styled as a resident-initiated SOS |
| Audit trail | Command row persists as the record |

The resident-never-mistakes-it point is the important one. A false alarm that looks genuine trains
people to ignore alarms.

### 7.3 Honest engineering risk

This is the single most likely part of the roadmap to need iteration on a real device.

Making a **silent, backgrounded** phone light up, siren, and flash a torch is exactly what
alarm-clock apps do, and it is genuinely hard:

- Requires a foreground service with an appropriate type
- Android grants only a ~10-second background execution window to high-priority FCM messages
- Doze mode and battery optimisation will fight it; a battery-optimisation exemption step is
  needed for the protected device
- Torch access depends on camera/torch permission state, already handled for the flashlight feature

**Mitigating factor:** the contacts-alert half is fully independent. If the alarm path proves
unreliable, emergency notifications still reach caregivers by email and push. The two halves are
built and shipped separately so the reliable one is never blocked by the difficult one.

---

## 8. Phase plan

Each phase ends with build + tests + commit + push, per the existing working agreement.

### Phase 0 — Unblock sync *(prerequisite for everything)*

**Problem:** anonymous signup is rate-limited. The device re-authenticates on every failed attempt,
so it never recovers. Measured failure:

```
HTTP 429
{"code":429,"error_code":"over_request_rate_limit","msg":"Request rate limit reached"}
```

**Work:**
- Persist the anonymous session; refresh on 401 only, never re-sign-up speculatively
- Exponential backoff with jitter on 429
- Separate "rate limited" from "not configured" — today both display as *Not signed in*
- Ownership + household trigger (D-6, D-7) so rows carry `user_id` and `household_id`
- Recovery path for the 43 queued rows (D-11), including rows whose retry count is exhausted
- Invite-code lookup (D-5) so joining is possible at all

**Done when:** the 43 offline rows drain to the server and the local queue empties.
**Risk:** low. **Needs the developer:** no.

### Phase 1 — Two real profiles

**Work:**
- Mode becomes a first-run fork (D-1)
- Role-specific setup: protected gets person / contacts / medical / schedule / share code;
  monitoring gets **only** an invite code — nothing else (D-3)
- Fix D-1 and D-2
- Strip protected-only routes from monitoring entirely — not merely hide buttons. No SOS (until
  Phase 5), no flashlight, no night mode, no power-cut, no check-in scheduler
- Tests asserting a monitoring-profile install **cannot reach** any protected-only destination

**Done when:** a monitoring install shows an invite field and nothing else.
**Risk:** low. **Needs the developer:** no.

### Phase 2 — Multi-person caregiver dashboard

**Status: done.** Committed on top of Phase 1.

**Work:**
- `households: List<HouseholdRef>` and `watchedPeople: List<WatchedPerson>` replace the single
  household id and single device (D-4). Household membership is now a persisted set, so a caregiver
  can watch more than one house
- `refreshMonitoring()` iterates every joined household and aggregates across them. Queries run
  **sequentially**, not concurrently: they share one auth session, and firing four per household at
  once is how the sign-up rate limit was reached originally
- `CaregiverAggregator` resolves each person's own latest check-in and open emergency. Previously the
  dashboard paired the first device with the household's whole check-in list, so with two people in
  a house it showed one person's status beside the other person's check-in
- Dashboard rebuilt around a people list: unresolved emergencies full-width and named, then one
  `PersonCard` per person with status, battery, network, last seen and last check-in
- Activity feed merged across devices, newest first, with each row naming its person
- "Add another person" — a prominent button opening `AddPersonDialog`, which is only a code field.
  That is the whole affordance D2 needs: one house is one code, so the only thing a caregiver can
  legitimately add is another house's code
- Household name shown instead of the invite code (Q1). The code is a credential that gets read
  aloud, screenshotted and texted; it was being used as a label

**Done when:** one code shows every watched person, and adding a second takes only a code. ✅

**Two items deliberately not built:**

- **Call action.** There is no phone number for a watched person anywhere in the schema. `devices`
  carries only `person_name` and `person_role`; `monitoring_contacts` holds the *resident's*
  registered contacts, which for a caregiver is their own details. A Call button would therefore
  place a call to the caregiver themselves. Shipping a dead button is worse than shipping none, so
  it is deferred pending a decision on how a person is reachable — see §11 Q5.
- **Realtime live updates.** No WebSocket client is present and adding one is a dependency this
  project has deliberately avoided so far. Manual refresh satisfies this phase's done-when. Still
  wanted, and still the right home for it (D10); deferred rather than dropped.

**Risk:** low. **Needs the developer:** no.

### Phase 3 — Approval codes

**Status: done**, with one gap recorded below.

**Work:**
- `remote_commands` table (migration 011) holding approval requests. Created here rather than in
  Phase 5 because approvals need it first
- **The code is minted and checked in the database**, never by the client. The code is the entire
  consent mechanism, so a client-side comparison would mean anything able to call PostgREST could
  satisfy an approval by guessing
- RLS: INSERT gated per kind, SELECT limited to the target device and the requester, UPDATE only the
  target — so a requester can never mark their own request satisfied
- `approve_remote_command(code)` performs the membership change itself. The client can only present
  a code
- `cancel_remote_command(id)` so a resident can refuse. Without it, "consent" would mean "no way to
  say no"
- `ApprovalsSection` on the resident's home screen: pending requests each showing the code, plus a
  permanently visible "people watching this home" list (Q4)
- `ApprovalCodeEntry` on the caregiver dashboard, separate from the join flow because joining a
  household and being approved to watch it are different actions with different consent requirements
- `ApprovalPolicy` holds the pure rules, with **no age parameter on `isAwaitingAction` by design**
  (D5). If a future change reintroduces a timeout, that signature makes it visible

**Done when:** no membership change is possible without a code. ✅

**Gap:** the protected → monitoring switch is *schema* ready (`APPROVE_MODE_SWITCH` kind, plus a
`cancel_remote_command` path and the device-side `resolve_own_remote_commands` policy that lets the
device mark it applied) but the client flow is not wired. There is currently no UI on the resident's
phone that *creates* a switch request, so the kind is unreachable. The reverse direction
(monitoring → protected) works and correctly needs no approval.

**Risk:** medium. Four bugs were found only by executing the functions, not reading them —
see the change log.

### Phase 4 — FCM push

**Work:**
- `push_tokens` table + RLS (§6.1)
- `google-services.json` at `app/src/debug/` and `app/src/release/`, gitignored
- `FirebaseMessagingService` → `SafetyNotifier` on the existing `CHANNEL_EMERGENCY`
- `onNewToken` handling
- Blocking notification-permission gate (D-9)
- Edge function: FCM HTTP v1 branch beside `sendEmail()`, `Promise.all`, push always / email
  optional (D8)
- Service account credential as function secret `FCM_SERVICE_ACCOUNT`

**Done when:** a real push arrives on a backgrounded phone within seconds.
**Risk:** medium — R8 and per-build-type config (D-10).
**Needs the developer:** **yes, ~15 minutes** (§9).

### Phase 5 — Remote SOS

**Work:**
- `remote_commands` + `emergency_events.source` / `triggered_by` (§6.2, §6.3)
- RLS and the 15-minute DB-trigger rate limit (§7.2)
- Remote SOS button on the monitoring profile, targeting a selected person (D7)
- `RAISE_ALARM` handling on the protected device: torch, siren, full-screen SOS, labelled
  "Caregiver requested assistance"
- FCM data message as the instant path + `SyncWorker` poll as the fallback, deduplicated by command
  `id`
- Battery-optimisation exemption step
- Resolve D-8 as a documented, guarded overrule

**Done when:** caregiver SOS alerts contacts instantly and alarms the resident's phone, and is rate
limited to one per 15 minutes.
**Risk:** **highest in the roadmap.** **Needs the developer:** no, but expect device testing.

### Phase 6 — Polish and verification

**Work:**
- Release build with R8 verified for FCM (D-10)
- Accessibility pass on **both** profiles — large targets, contrast, screen-reader labels
- Sync-health card reviewed on both roles
- Compliance and security documentation updated for D3 (all-or-nothing medical) and D6 (no
  one-tap revoke)
- R8/Release ProGuard verification, uninstall/reinstall behaviour with preserved data

**Risk:** low. **Needs the developer:** no.

---

## 9. Actions only the developer can perform

Everything else is agent-implementable. Total developer time across the whole roadmap is about
15 minutes, once, at $0 cost.

### 9.1 Firebase project and app config

1. Create a Firebase project at console.firebase.google.com (free, no card)
2. Add an Android app with package name **`org.homeguard`**
3. Download `google-services.json`
4. Place copies at `app/src/debug/google-services.json` and `app/src/release/google-services.json`

**Do not commit these files.** Firebase config is not a true secret, but the project already keeps
release credentials out of git by injecting them from CI, so these follow the same convention.

### 9.2 Service account for FCM HTTP v1

1. In the Firebase project → Project settings → Service accounts → Generate new private key
2. Download the JSON key
3. Store as a Supabase function secret:

```
supabase secrets set FCM_SERVICE_ACCOUNT='<paste json>' --project-ref rmiausgluaqqshszvged
```

FCM's HTTP v1 API requires an OAuth credential — the Supabase anon key will not work.

### 9.3 Migrations

All ten are applied. 001–003 were applied through the dashboard SQL editor, so the CLI had no history
of them; they were baselined with `supabase migration repair --status applied` before 004 could be
pushed on its own. Do not re-run the repair or push again without checking `supabase migration list`
first — local and remote should already agree.

```
npx supabase migration list --project-ref rmiausgluaqqshszvged
```

**One manual step remains (L12):** PostgREST's cached schema does not include the RPC functions,
because `NOTIFY pgrst` is not delivered through the pooler. Run this once in the **Supabase dashboard
SQL editor**, which uses a direct connection:

```sql
NOTIFY pgrst, 'reload schema';
```

Until that runs, `/rest/v1/rpc/*` returns `404 requested path is invalid`. Tables and writes are
unaffected.

### 9.4 One-time action from earlier work, still outstanding

The mail password and the Resend API key were pasted into chat and shell history during earlier
configuration. **Rotate both.** Do not commit either.

---

## 10. Known constraints and accepted limitations

Recorded so they are not rediscovered as bugs.

| # | Item | Status |
|---|---|---|
| L1 | **Supabase free tier pauses after 7 days of low activity**, with a 10–30 s cold start on restore | Accepted on the free plan. Regular check-in and heartbeat sync will normally keep it alive; a week with everyone away could delay the first alert. $25/month Pro removes pausing entirely. |
| L2 | **FCM requires Google Play Services** on the receiving phone | The caregiver's RMX3710 has it. A Huawei or other GMS-less device would receive no push at all. Email still works. |
| L3 | **No one-tap caregiver removal** (D6) | Accepted. A resident who wants to stop being watched has no unilateral in-app escape; removal requires the other party's code. |
| L4 | **All-or-nothing medical sharing** (D3) | Accepted. No per-section granularity. |
| L5 | **Background alarm is best-effort** on aggressive OEM battery management | Mitigated by the exemption step and the independent contacts-alert path. |
| L6 | **SMS is not implemented** | Contacts with a phone but no email are logged and dropped by the edge function. SMS would need a provider such as Twilio. |
| L7 | **Resend free tier is 100 emails/month on one domain** | Adequate for testing and small deployments. |
| L8 | **Supabase log retention is 1 day on the free plan** | A delayed delivery failure may not be diagnosable after a day. |
| L9 | `navigation-compose` is in the version catalog but unused; routing is hand-rolled | Noted. Phase 1 works within the existing routing approach. |
| L10 | **Migrations were in `backend/migrations/`, invisible to the Supabase CLI** | Fixed in Phase 0 by moving them to `supabase/migrations/`. `db push` had been reporting "Remote database is up to date" with nothing applied. This is the same class of mistake as the edge-function path bug fixed in `15c2c5b`. |
| L11 | **`local.properties` held a double-escaped Supabase URL** (`https\://…`) | Found in Phase 0. Every URL built from it was malformed, so debug builds could not reach Supabase at all. Corrected after confirming the host matches `project_id` in `supabase/config.toml`. The anon key was unaffected. Worth re-checking after any tool rewrites this file, since the corruption is silent. |
| L12 | **PostgREST's schema cache cannot be reloaded from the CLI** | Migrations 005–008 are applied and verified, but `supabase db push` and `supabase db query` both connect through the pooler, where `NOTIFY pgrst, 'reload schema'` is not delivered. Every `/rpc/` path returns `404 requested path is invalid` even though the functions exist in `pg_proc`. Tables resolve normally, so only the function schema is stale. **Needs one manual step:** run `NOTIFY pgrst, 'reload schema';` in the Supabase dashboard SQL editor (which uses a direct connection), or restart PostgREST. The caregiver join path cannot work over HTTP until this is done. Database logic was verified independently — see §8 Phase 0 verification note. |
| L13 | **`households` INSERT failed under `return=representation`** | Root cause was not the policy. `INSERT ... RETURNING` is evaluated against the table's SELECT policy, which requires existing membership, so the row a user had just created was invisible to them and PostgreSQL raised 42501. Confirmed by varying only the `Prefer` header: `return=minimal` → 201, `return=representation` → 403. Fixed by creating the household and its first member in one `SECURITY DEFINER` function, where RLS does not apply to `RETURNING`. |

---

## 11. Open questions

Everything is decided except these. Each carries a default that will be applied if no answer is
given, so none of them blocks starting Phase 0.

| # | Question | Default if unanswered |
|---|---|---|
| Q1 | What is the household's display name? Currently the dashboard shows the invite code where a name belongs. | Add a `name` column to `households`, default to a localised "My home", and never display the invite code as a label. |
| Q2 | Can a caregiver belong to more than one household in the app UI at once, or switch between them? | Support the list; default to showing all households' people in one feed. The schema already permits it. |
| Q3 | Should the caregiver be able to resolve/dismiss an emergency from the dashboard? | Yes — but only the device owner may mark a `remote_commands` row `EXECUTED`; a caregiver resolving an alert is a UI-side state, not a server write. **Done in Phase 3:** `resolve_own_remote_commands` restricts UPDATE to the target device. |
| Q4 | Does the protected phone need a visible "monitored by" indicator at all times? | Yes. Required by D4's transparency principle and already implied by Phase 3. **Done:** `ApprovalsSection` renders the watcher list unconditionally, including when it is empty. |
| Q5 | **How is a watched person reached by phone?** Phase 2's Call action was deferred because no phone number for a watched person exists anywhere in the schema, and `monitoring_contacts` holds the resident's contacts — which for a caregiver is their own details. | Needs a decision before it can be built: add `contact_phone` to `devices` and an input on the protected profile, or point Call at a shared contact list instead. |

---

## 12. Definition of done

The roadmap is complete when all of the following hold.

**Both profiles**
- [ ] A fresh install can choose either role and reaches a role-appropriate screen (D1)
- [ ] Monitoring profile exposes no protected-only route, verified by test
- [ ] The resident's existing home experience is preserved, not redesigned away

**Multi-person**
- [ ] One code shows every watched person in a household (D2)
- [ ] Adding a second person requires only a code
- [ ] A caregiver can belong to multiple households

**Consent**
- [ ] Joining, removal, and protected → monitoring all require a code (D4)
- [ ] Unapproved requests never expire (D5)
- [ ] The resident can always see who is watching (Q4)

**Remote SOS**
- [ ] Caregiver SOS alerts the watched person's contacts immediately
- [ ] Caregiver SOS alarms the watched person's phone, labelled as caregiver-requested
- [ ] Rate limited to one per device per 15 minutes in the database
- [ ] Duplicate delivery cannot double-fire the alarm

**Infrastructure**
- [ ] Local queue drains; no unsent safety events remain (D11)
- [ ] Push reaches a backgrounded phone within seconds (D8)
- [ ] Email is a per-contact opt-in
- [ ] Release build with R8 passes and is verified on device
- [ ] Both profiles pass an accessibility pass

**Quality**
- [ ] Unit tests green
- [ ] Debug and release builds green
- [ ] Every phase committed and pushed individually
- [ ] `COMPLIANCE.md` and `SECURITY.md` updated for D3 and D6

---

## 13. Working agreement

Per `CONTRIBUTING.md`, for each phase:

1. `IMPLEMENT` → 2. `TEST` → 3. `REVIEW` → 4. `COMMIT` → 5. `PUSH` → 6. verify clean working tree

Plus, specific to this roadmap:

- **Stop after Phase 3** and let the developer verify both profiles before any notification work
  begins. Verifying profiles is cheap; debugging notification failures is not.
- No secrets, keys, or `google-services.json` in git.
- No destructive schema changes. Migrations are additive; existing rows must survive.
- Every new migration is idempotent (`IF NOT EXISTS`) and includes a verification query, matching
  the style of migrations 001–003.
- Existing local data must survive reinstall. The 43 queued rows are the regression fixture.

---

## Change log

| Date | Change |
|---|---|
| 2026-10-04 | Initial roadmap. Recorded decisions D1–D10, platform decision Supabase+FCM, defects D-1…D-11, schema inventory, remote SOS design, seven-phase plan. |
| 2026-10-04 | Phase 1 and Phase 0 completed. Recorded the migration-directory bug (L10) and the corrupted `local.properties` URL (L11). Migration 004 still unapplied. |
| 2026-10-04 | Phase 2 completed: multi-person dashboard, per-person check-in resolution, household set, add-another-person by code, household name replacing the invite code. Recorded Q5 for the deferred Call action. |
| 2026-10-04 | Phase 3 completed: `remote_commands`, server-minted approval codes, database-executed approval and cancellation, resident-side approvals and watcher list, caregiver-side code entry. Recorded the unwired protected→monitoring switch as a Phase 3 gap. Four defects found only by executing the functions are listed below. |
| 2026-10-04 | Defects found by executing rather than reading the approval migrations, all now fixed: `approve_remote_command` declared an OUT parameter named `household_id` that shadowed the column (42702); `CREATE OR REPLACE` cannot change a return type (42P13); `can_request_for` collapsed to "requester is the target" so no real member could request; and it keyed on `devices.household_id` rather than membership, which also refused every first-time joiner. |
| 2026-10-04 | Corrected an earlier claim in migration 011 that approvals cannot be spam. A join request is created by someone who is by definition not yet a member, so a stranger holding a household id could flood a resident's approvals list. Fixed with a partial unique index plus a 60-minute per-target interval. |