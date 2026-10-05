/**
 * HomeGuard caregiver notification.
 *
 * Invoked by a Supabase Database Webhook when a row lands in `emergency_events`.
 * Looks up who should be told, and sends both push and email in parallel.
 *
 * Deployed with --no-verify-jwt: a Database Webhook posts with the service role,
 * not a user JWT, so the Authorization header is not a signed access token.
 *
 * Section 29 requires no hardcoded secrets, so credentials arrive as function
 * secrets. Section 36 warns against notification spam, so this only fires on
 * emergency_events inserts and never on routine check-ins.
 */

const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY") ?? "";
const RESEND_FROM = Deno.env.get("RESEND_FROM") ?? "HomeGuard <alerts@example.com>";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
// Phase 4: a Firebase service account JSON, stored as a function secret rather than committed.
// Absent means push is unavailable and email still carries the alert, so a missing secret degrades
// instead of breaking delivery.
const FCM_SERVICE_ACCOUNT = Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "";
const FCM_PROJECT_ID = Deno.env.get("FCM_PROJECT_ID") ?? "";
const FCM_ENDPOINT = "https://fcm.googleapis.com/v1/projects";

/**
 * Must match HomeGuardApplication.CHANNEL_EMERGENCY exactly.
 *
 * A mismatch is not a crash: Android falls back to a default channel with IMPORTANCE_DEFAULT, so the
 * notification still appears but silently at low priority. For an emergency alert that is a failure
 * that looks like it worked, so the value is asserted against the app constant in a test.
 */
const EMERGENCY_CHANNEL_ID = "channel_homeguard_emergency";

interface EmergencyEventRow {
  id: string;
  device_id: string;
  triggered_at: string;
  details?: string | null;
  /** DEVICE for a resident-raised alarm, CAREGIVER_REMOTE for one a caregiver raised. */
  source?: string | null;
  /** Which caregiver raised it. Present only when source is CAREGIVER_REMOTE. */
  triggered_by?: string | null;
  /** The remote_commands row to deliver on the resident's phone, if this is a remote alarm. */
  remote_command_id?: string | null;
}

interface DeviceRow {
  device_id: string;
  person_name?: string | null;
  person_role?: string | null;
}

interface ContactRow {
  name: string;
  email: string | null;
  phone: string | null;
  /** Decision D8: email is a per-contact opt-in, push always goes. */
  email_enabled?: boolean | null;
}

interface PushTokenRow {
  user_id: string;
  token: string;
  updated_at: string;
}

interface WebhookPayload {
  type: string;
  table: string;
  record: EmergencyEventRow;
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  };
}

/** Minimal JSON response helper. */
function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders(), "Content-Type": "application/json" },
  });
}

/**
 * Looks up who to notify.
 *
 * Uses the service role deliberately: the row was written by the device's own
 * session, but this function has no user JWT to filter on. It only ever reads the
 * single device named in the webhook payload.
 *
 * Throws on a PostgREST failure rather than returning an empty list. Swallowing
 * the error would make "the table does not exist" look exactly like "no contacts
 * are configured", which is a genuinely dangerous ambiguity for a safety alert.
 */
async function loadRecipients(
  deviceId: string
): Promise<{
  device: DeviceRow | null;
  contacts: ContactRow[];
  pushTokens: PushTokenRow[];
  deviceTokens: PushTokenRow[];
}> {
  const headers = {
    apikey: SERVICE_ROLE_KEY,
    Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
  };
  const encoded = encodeURIComponent(deviceId);

  const read = async (url: string, label: string): Promise<unknown[]> => {
    const res = await fetch(url, { headers });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`${label} query failed (HTTP ${res.status}): ${body}`);
    }
    return (await res.json()) as unknown[];
  };

  const [devices, contacts, tokens, deviceTokens] = await Promise.all([
    read(
      `${SUPABASE_URL}/rest/v1/devices?device_id=eq.${encoded}&select=device_id,person_name,person_role&limit=1`,
      "devices"
    ),
    read(
      `${SUPABASE_URL}/rest/v1/monitoring_contacts?device_id=eq.${encoded}&select=name,email,phone,is_primary&order=is_primary.desc`,
      "monitoring_contacts"
    ),
    // Resolved server-side so this function does not walk households -> members -> tokens.
    read(
      `${SUPABASE_URL}/rest/v1/rpc/caregiver_push_tokens?${new URLSearchParams({ target_device_id: deviceId }).toString()}`,
      "caregiver_push_tokens"
    ),
    // The alarmed phone's own tokens. Needed for a caregiver-raised alarm, where the recipient is
    // the resident's device rather than any household caregiver.
    read(
      `${SUPABASE_URL}/rest/v1/rpc/device_push_tokens?${new URLSearchParams({ target_device_id: deviceId }).toString()}`,
      "device_push_tokens"
    ),
  ]);

  return {
    device: (devices as DeviceRow[])[0] ?? null,
    contacts: contacts as ContactRow[],
    pushTokens: tokens as PushTokenRow[],
    deviceTokens: deviceTokens as PushTokenRow[],
  };
}

// --- push (FCM HTTP v1) -----------------------------------------------------------------

let cachedAccessToken: { value: string; expiresAt: number } | null = null;

/**
 * Mints a Google OAuth access token from the service account key.
 *
 * Implemented directly against Google's token endpoint with Web Crypto rather than pulling in an
 * SDK, because the signing requirement is RS256 and the payload is a standard JWT assertion. Cached
 * until shortly before expiry so a burst of alerts does not re-sign per notification.
 */
async function getGoogleAccessToken(): Promise<string | null> {
  if (!FCM_SERVICE_ACCOUNT) return null;

  const now = Date.now();
  if (cachedAccessToken && cachedAccessToken.expiresAt > now + 60_000) {
    return cachedAccessToken.value;
  }

  let key: { client_email: string; private_key: string };
  try {
    key = JSON.parse(FCM_SERVICE_ACCOUNT);
  } catch (e) {
    console.error("FCM_SERVICE_ACCOUNT is not valid JSON; push disabled.");
    return null;
  }

  const issued = Math.floor(now / 1000);
  const expiresIn = 3600;
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(
    JSON.stringify({
      iss: key.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: issued,
      exp: issued + expiresIn,
    })
  );
  const signature = await rs256(`${header}.${claims}`, key.private_key);
  const assertion = `${header}.${claims}.${signature}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString(),
  });

  if (!res.ok) {
    console.error("Google token exchange failed:", res.status, await res.text());
    return null;
  }

  const json = (await res.json()) as { access_token: string; expires_in: number };
  cachedAccessToken = {
    value: json.access_token,
    expiresAt: now + (json.expires_in ?? expiresIn) * 1000,
  };
  return json.access_token;
}

function base64Url(input: string): string {
  const bytes = new TextEncoder().encode(input);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** RS256 signature, as raw bytes, using the PKCS#8 key Firebase hands out. */
async function rs256(message: string, pem: string): Promise<string> {
  const der = pemToArrayBuffer(pem);
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(message)
  );
  return base64UrlFromBytes(new Uint8Array(signature));
}

function base64UrlFromBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const body = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

/**
 * Sends one push message.
 *
 * Returns "unregistered" when FCM reports the token is dead, so the caller can drop it rather than
 * retrying a token that will never work again.
 */
async function sendPush(
  accessToken: string,
  token: string,
  title: string,
  body: string
): Promise<"sent" | "failed" | "unregistered"> {
  const projectId = FCM_PROJECT_ID || "replace-me";
  const res = await fetch(`${FCM_ENDPOINT}/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    // High priority so the alert can wake the device. A safety alert that arrives when the caregiver
    // happens to open the app is not a safety alert.
    body: JSON.stringify({
      message: {
        token,
        notification: { title, body },
        android: {
          priority: "HIGH",
          notification: {
            channel_id: EMERGENCY_CHANNEL_ID,
            // Tapping the alert opens the caregiver dashboard.
            default_sound: true,
          },
        },
        data: { kind: "EMERGENCY" },
      },
    }),
  });

  if (res.ok) return "sent";

  const detail = await res.text();
  // 404 UNREGISTERED and 400 INVALID_ARGUMENT both mean the token is no longer deliverable.
  if (detail.includes("UNREGISTERED") || detail.includes("INVALID_ARGUMENT")) {
    console.warn("FCM reported a dead token; it will be removed.");
    return "unregistered";
  }
  console.error("FCM send failed:", res.status, detail);
  return "failed";
}

/**
 * Sends the alarm command to the phone being alarmed.
 *
 * Deliberately a data message with no notification payload. A payload would be rendered by the SDK
 * with a generic title, which would read as a resident-initiated emergency. The resident's own screen
 * has to say a caregiver raised it, so the app builds the notification itself.
 *
 * High priority so a backgrounded phone can act inside Android's short allowance for high-priority
 * FCM messages. Best effort: the contacts alert has already gone out by this point, so a failure here
 * degrades the alarm rather than the emergency.
 */
async function sendAlarmCommand(
  accessToken: string,
  token: string,
  commandId: string
): Promise<"sent" | "failed" | "unregistered"> {
  const projectId = FCM_PROJECT_ID || "replace-me";
  const res = await fetch(`${FCM_ENDPOINT}/${projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token,
        android: { priority: "HIGH" },
        data: { kind: "RAISE_ALARM", command_id: commandId },
      },
    }),
  });

  if (res.ok) return "sent";
  const detail = await res.text();
  if (detail.includes("UNREGISTERED") || detail.includes("INVALID_ARGUMENT")) {
    return "unregistered";
  }
  console.error("Alarm command send failed:", res.status, detail);
  return "failed";
}

/**
 * Removes tokens FCM has declared dead.
 *
 * Without this a token that failed once is retried forever and the caregiver silently stops getting
 * alerts while the row looks perfectly healthy.
 */
async function deleteDeadTokens(tokens: string[]): Promise<void> {
  if (tokens.length === 0) return;
  const headers = {
    apikey: SERVICE_ROLE_KEY,
    Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
    Prefer: "return=minimal",
  };
  const body = JSON.stringify(tokens);
  const res = await fetch(`${SUPABASE_URL}/rest/v1/push_tokens?token=in.(${tokens.join(",")})`, {
    method: "DELETE",
    headers: { ...headers, "Content-Type": "application/json" },
    body,
  });
  if (!res.ok) {
    console.error("Could not remove dead push tokens:", res.status, await res.text());
    return;
  }
  console.log(`Removed ${tokens.length} dead push token(s)`);
}

/** Sends the alert email. Swallows failures: a webhook must not retry forever. */
async function sendEmail(to: string, subject: string, body: string): Promise<boolean> {
  if (!RESEND_API_KEY) {
    console.error("RESEND_API_KEY is not set; cannot send notification.");
    return false;
  }
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ from: RESEND_FROM, to: [to], subject, text: body }),
  });
  if (!res.ok) {
    console.error("Email send failed:", res.status, await res.text());
    return false;
  }
  return true;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders() });
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  let payload: WebhookPayload;
  try {
    payload = await req.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const record = payload?.record;
  if (!record?.device_id) {
    // Not an emergency_events insert, or the row is malformed. Nothing to notify.
    return json({ skipped: true, reason: "no device_id" });
  }

    try {
      const { device, contacts, pushTokens, deviceTokens } = await loadRecipients(record.device_id);

      // A caregiver-raised alarm must never read as the resident panicking, so the wording changes.
      const isRemoteAlarm = record.source === "CAREGIVER_REMOTE";
      const who = device?.person_name?.trim() || "Someone";
      const when = new Date(record.triggered_at).toLocaleString("en-GB", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "UTC",
      });

      const subject = isRemoteAlarm
        ? `HomeGuard: a caregiver raised an alarm for ${who}`
        : `HomeGuard emergency alert - ${who}`;
      const body = isRemoteAlarm
        ? [
            `A caregiver asked for help from the device at home.`,
            ``,
            `Person:     ${who}${device?.person_role ? ` (${device.person_role})` : ""}`,
            `Raised at:  ${when} UTC`,
            ``,
            `This was NOT the resident pressing their own SOS button. A caregiver asked for`,
            `assistance on their behalf.`,
            ``,
            `If nobody at home asked for this, treat it as a mistake and check with them.`,
          ].join("\n")
        : [
            `HomeGuard sent an emergency alert from the device at home.`,
            ``,
            `Person:     ${who}${device?.person_role ? ` (${device.person_role})` : ""}`,
            `Triggered:  ${when} UTC`,
            `Details:    ${record.details || "None provided"}`,
            ``,
            `If you believe this is a mistake, the resident can stop the alarm from the`,
            `HomeGuard emergency screen. Otherwise please contact them or local`,
            `emergency services now.`,
          ].join("\n");

      // D8: email is opt-in per contact, push always goes to every registered caregiver device.
      const withEmail = contacts.filter((c) => c.email && c.email_enabled !== false);
      const accessToken = await getGoogleAccessToken();
      const pushTitle = isRemoteAlarm ? `Caregiver asked for help: ${who}` : `Emergency: ${who}`;
      const pushBody = isRemoteAlarm
        ? `A caregiver raised an alarm for this device. It was not the resident pressing SOS.`
        : record.details?.trim() || "Emergency alert from the device at home";

      const [emailResults, pushResults, alarmResults] = await Promise.all([
        Promise.all(withEmail.map((c) => sendEmail(c.email as string, subject, body))),
        accessToken && pushTokens.length > 0
          ? Promise.all(
              pushTokens.map((t) => sendPush(accessToken, t.token, pushTitle, pushBody))
            )
          : Promise.resolve([] as ("sent" | "failed" | "unregistered")[]),
        // The alarm itself goes to the resident's own phone. Requires both the command id and a
        // token; without either the contacts alert above is still the safety net.
        accessToken && isRemoteAlarm && record.remote_command_id && deviceTokens.length > 0
          ? Promise.all(
              deviceTokens.map((t) =>
                sendAlarmCommand(accessToken, t.token, record.remote_command_id as string)
              )
            )
          : Promise.resolve([] as ("sent" | "failed" | "unregistered")[]),
      ]);

      const sent = emailResults.filter(Boolean).length;
      const pushed = pushResults.filter((r) => r === "sent").length;
      const alarmsSent = alarmResults.filter((r) => r === "sent").length;
      const deadTokens = pushTokens
        .filter((_, i) => pushResults[i] === "unregistered")
        .map((t) => t.token);
      if (deadTokens.length > 0) await deleteDeadTokens(deadTokens);

      console.log(
        `Notified ${sent}/${withEmail.length} contact(s) by email, ` +
          `${pushed}/${pushTokens.length} caregiver device(s) by push, ` +
          `${alarmsSent}/${deviceTokens.length} alarmed device(s) ` +
          `for device ${record.device_id}`
      );

      // The alarm failing is worth an explicit warning: the resident's contacts were told, but
      // nobody at home heard anything, which is the case a caregiver would be alarmed to discover.
      if (isRemoteAlarm && alarmsSent === 0) {
        console.warn(
          `Caregiver-raised alarm did not reach the device itself ` +
            `(command=${record.remote_command_id ?? "none"}, tokens=${deviceTokens.length}). ` +
            `Contacts were notified; the phone alarm may rely on the poll fallback.`
        );
      }

      // Contacts with a phone but no email are logged rather than silently dropped, so the
      // limitation is visible to whoever is debugging.
      const smsOnly = contacts.filter((c) => !c.email && c.phone);
      if (smsOnly.length > 0) {
        console.log(
          `${smsOnly.length} contact(s) have a phone number but no email. ` +
            `SMS delivery is not implemented.`
        );
      }

      if (!device) {
        console.warn(
          `Device ${record.device_id} is not registered. Has the app completed setup and synced?`
        );
      }
      if (contacts.length === 0 && pushTokens.length === 0) {
        console.warn(
          `No monitoring contact or push token for device ${record.device_id}. ` +
            `The caregiver must save Settings with an email, or open the caregiver dashboard once ` +
            `so notifications can be enabled.`
        );
      }

      return json({
        sent,
        total: withEmail.length,
        pushed,
        pushTotal: pushTokens.length,
        alarmsSent,
        alarmTotal: deviceTokens.length,
        isRemoteAlarm,
        remoteCommandId: record.remote_command_id ?? null,
        pushConfigured: Boolean(accessToken),
        smsPending: smsOnly.length,
        deviceFound: device !== null,
        contactsFound: contacts.length,
        // Spells out the most likely cause, so an empty send is never a mystery.
        reason:
          sent > 0 || pushed > 0
            ? null
            : isRemoteAlarm
            ? "a caregiver alarm was recorded but no contact or token could be notified"
            : contacts.length === 0 && pushTokens.length === 0
            ? "no monitoring contact configured for this device"
            : accessToken
            ? "delivery attempted but no channel succeeded"
            : "FCM_SERVICE_ACCOUNT is not configured; only email is available",
      });
    } catch (err) {
    console.error("notify-caregiver failed:", err);
    // 500 makes Supabase retry the webhook, which is what we want for a transient fault.
    return json({ error: "Notification failed" }, 500);
  }
});