/**
 * HomeGuard caregiver notification.
 *
 * Invoked by a Supabase Database Webhook when a row lands in `emergency_events`.
 * Looks up the primary monitoring contact for the device and emails them.
 *
 * Deployed with --no-verify-jwt: a Database Webhook posts with the service role,
 * not a user JWT, so the Authorization header is not a signed access token.
 *
 * Section 29 requires no hardcoded secrets, so the API key arrives as a function
 * secret. Section 36 warns against notification spam, so this only fires on
 * emergency_events inserts and never on routine check-ins.
 */

const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY") ?? "";
const RESEND_FROM = Deno.env.get("RESEND_FROM") ?? "HomeGuard <alerts@example.com>";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

interface EmergencyEventRow {
  id: string;
  device_id: string;
  triggered_at: string;
  details?: string | null;
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
 */
async function loadRecipients(
  deviceId: string
): Promise<{ device: DeviceRow | null; contacts: ContactRow[] }> {
  const headers = {
    apikey: SERVICE_ROLE_KEY,
    Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
  };

  const [deviceRes, contactRes] = await Promise.all([
    fetch(
      `${SUPABASE_URL}/rest/v1/devices?device_id=eq.${encodeURIComponent(deviceId)}&select=device_id,person_name,person_role&limit=1`,
      { headers }
    ),
    fetch(
      `${SUPABASE_URL}/rest/v1/monitoring_contacts?device_id=eq.${encodeURIComponent(
        deviceId
      )}&select=name,email,phone&order=is_primary.desc`,
      { headers }
    ),
  ]);

  const device = deviceRes.ok ? ((await deviceRes.json()) as DeviceRow[])[0] ?? null : null;
  const contacts = contactRes.ok ? ((await contactRes.json()) as ContactRow[]) : [];

  return { device, contacts };
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
    const { device, contacts } = await loadRecipients(record.device_id);

    const who = device?.person_name?.trim() || "Someone";
    const when = new Date(record.triggered_at).toLocaleString("en-GB", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "UTC",
    });

    const subject = `HomeGuard emergency alert - ${who}`;
    const body = [
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

    const withEmail = contacts.filter((c) => c.email);
    const results = await Promise.all(
      withEmail.map((c) => sendEmail(c.email as string, subject, body))
    );
    const sent = results.filter(Boolean).length;

    console.log(
      `Notified ${sent}/${withEmail.length} contact(s) for device ${record.device_id}`
    );

    // Contacts with a phone but no email are logged rather than silently dropped, so the
    // limitation is visible to whoever is debugging.
    const smsOnly = contacts.filter((c) => !c.email && c.phone);
    if (smsOnly.length > 0) {
      console.log(
        `${smsOnly.length} contact(s) have a phone number but no email. ` +
          `SMS delivery is not implemented.`
      );
    }

    return json({ sent, total: withEmail.length, smsPending: smsOnly.length });
  } catch (err) {
    console.error("notify-caregiver failed:", err);
    // 500 makes Supabase retry the webhook, which is what we want for a transient fault.
    return json({ error: "Notification failed" }, 500);
  }
});