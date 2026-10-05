package org.homeguard

import org.homeguard.core.data.RemoteAlarmDataSource
import org.homeguard.core.model.ApprovalKind
import org.homeguard.core.model.CaregiverAggregator
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.RemoteAlarm
import org.homeguard.core.model.WatchedPerson
import org.homeguard.core.notifications.RemoteAlarmService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 5 contract for the caregiver-raised alarm.
 *
 * Two halves are checked. The first is what this app must not do: a caregiver alarm must never be
 * mistaken for the resident's own emergency, and it must never appear where a resident would read it
 * as a request for access to their home. Both failures are silent — the alarm still works, it is
 * simply described wrongly, which is the kind of defect that erodes trust in a safety app and is only
 * noticed by the person it misleads.
 *
 * The second is the wire contract with the edge function, which cannot be unit tested directly and
 * would otherwise drift silently in both directions.
 */
class RemoteAlarmContractTest {

    // --- what must never happen ---------------------------------------------------------------

    /**
     * A caregiver alarm carries no approval code.
     *
     * If it did, the resident would be shown a code and asked to type it in, which would make an
     * emergency alarm look like somebody asking to be let into their home. The server enforces this
     * with a constraint; this pins the client-side model so the two cannot drift apart.
     */
    @Test
    fun `an alarm is never modelled as an approval request`() {
        val alarm = RemoteAlarm(id = "command-1")

        // RemoteAlarm is a separate type from ApprovalRequest precisely so that it cannot acquire an
        // approvalCode field, be listed in the approvals UI, or be cancelled by the target device.
        assertNull(
            "an alarm must not carry an approval code",
            alarm.javaClass.declaredFields.firstOrNull { it.name == "approvalCode" }
        )
    }

    /**
     * An alarm is not a consent-bearing kind.
     *
     * D4 requires a code for join, remove and mode switch. If RAISE_ALARM joined that enum it would sit
     * in the same list a resident reads as "people asking for access to my home", which is exactly the
     * confusion the consent model exists to prevent. Asserting the enum has no alarm member keeps the
     * separation from being undone by a later convenience refactor.
     */
    @Test
    fun `an alarm is not a consent bearing approval kind`() {
        assertNull(
            "an alarm must not parse as an approval kind",
            ApprovalKind.fromWire(RemoteAlarmDataSource.KIND_RAISE_ALARM)
        )
        assertFalse(
            "no approval kind may be the alarm kind",
            ApprovalKind.entries.any { it.name == RemoteAlarmDataSource.KIND_RAISE_ALARM }
        )
    }

    /**
     * The alarm data message must stay data-only.
     *
     * A notification payload would be rendered by the FCM SDK using its own title, which cannot say
     * who asked. The resident would then read "Emergency" on a phone that nobody at home pressed,
     * which is both alarming and wrong. This is the single most important assertion in this file.
     */
    @Test
    fun `the edge function sends the alarm without a notification payload`() {
        val text = edgeFunctionSource().readText()
        val start = text.indexOf("async function sendAlarmCommand")
        assertTrue("sendAlarmCommand must exist", start >= 0)

        val body = text.substring(start, text.indexOf("async function deleteDeadTokens", start))
        assertFalse(
            "the alarm message must not carry a notification payload; the app builds it itself",
            body.contains("notification:")
        )
        assertTrue(
            "the alarm message must be sent at HIGH priority or a locked phone may defer it",
            body.contains("priority: \"HIGH\"")
        )
        assertTrue(
            "the alarm message must identify itself so the app can route it",
            body.contains("kind: \"RAISE_ALARM\"")
        )
    }

    /**
     * The caregiver's own alarm button must not appear without a target.
     *
     * The alarm API is keyed by auth user id, not device id. A device row with no user id cannot be
     * alarmed, and offering the button anyway would produce a guaranteed failure at the moment
     * somebody most needs it to work.
     */
    @Test
    fun `the raise alarm control is hidden when the device cannot be alarmed`() {
        assertFalse(
            "a device with no auth user id must not be alarmable",
            MonitoredDevice(deviceId = "dev-1", userId = "").canRaiseAlarm
        )
        assertTrue(
            "a device with an auth user id is alarmable",
            MonitoredDevice(deviceId = "dev-1", userId = "user-1").canRaiseAlarm
        )
    }

    /**
     * A caregiver's own monitoring device must never be offered as someone to alarm.
     *
     * The aggregator already excludes monitoring devices from the watched list. This asserts the
     * alarm path inherits that exclusion, so a future change to the dashboard cannot make it possible
     * to alarm oneself and receive the result.
     */
    @Test
    fun `a monitoring device is never listed as someone to alarm`() {
        val people = CaregiverAggregator.watchedPeople(
            listOf(
                MonitoredDevice(
                    deviceId = "monitoring",
                    userId = "caregiver-1",
                    deviceMode = "MONITORING_DEVICE"
                ),
                MonitoredDevice(
                    deviceId = "home-1",
                    personName = "Mum",
                    userId = "resident-1"
                )
            )
        )

        assertEquals("only the protected device is watchable", 1, people.size)
        assertEquals("Mum", people.first().device.displayName)
        assertTrue(people.first().device.canRaiseAlarm)
    }

    /**
     * A stale device is not an emergency, so it must not be presented as though it were.
     *
     * The dashboard already words a silent device as UNKNOWN. This asserts the device is still
     * alarmable, because a caregiver must be able to raise an alarm for a person they cannot reach
     * precisely when the phone has gone quiet.
     */
    @Test
    fun `a stale device is still alarmable`() {
        val person = WatchedPerson(
            device = MonitoredDevice(
                deviceId = "home-1",
                userId = "resident-1",
                lastSeenAt = "2020-01-01T00:00:00"
            )
        )

        assertTrue("a stale device is alarmable", person.device.canRaiseAlarm)
        assertTrue(
            "a device that has not reported is stale",
            person.device.isStale(System.currentTimeMillis())
        )
    }

    /**
 * A raw auth UUID must never reach the resident's lock screen.
 *
 * remote_commands.requested_by is a UUID, and the natural-looking implementation — pass it through and
 * interpolate it — puts "Asked by 7f3a91c4-…" on the screen. That reads as a malfunction rather than as
 * information about who asked, and the resident cannot act on it.
 *
 * Tested as a pure function rather than by inspecting the Intent, because this project has no
 * Robolectric and `isReturnDefaultValues` makes Intent a stub whose getStringExtra always returns
 * null. Asserting on the stub would have passed while proving nothing.
 */
@Test
fun `a uuid is never shown as the caregiver's name`() {
    val uuid = "7f3a91c4-1d2e-4a55-9b3c-0e6f8a2d4c11"

    assertEquals(
        "an identifier must be rejected, not displayed",
        "",
        RemoteAlarmService.usableDisplayName(uuid)
    )
    assertEquals(
        "an uppercase uuid is still an identifier",
        "",
        RemoteAlarmService.usableDisplayName(uuid.uppercase())
    )
}

@Test
fun `a real name survives the display name check`() {
    assertEquals("Sarah", RemoteAlarmService.usableDisplayName("Sarah"))
    assertEquals("Alex from next door", RemoteAlarmService.usableDisplayName("Alex from next door"))
    assertEquals("a name is trimmed, not rejected", "Jo", RemoteAlarmService.usableDisplayName("  Jo  "))
    assertEquals("blank means unknown, not an error", "", RemoteAlarmService.usableDisplayName("   "))
}

private fun edgeFunctionSource(): File {
        var dir: File? = File(".").absoluteFile
        while (dir?.parentFile != null) {
            val candidate = File(dir, "supabase/functions/notify-caregiver/index.ts")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError(
            "Could not locate supabase/functions/notify-caregiver/index.ts above ${File(".").absolutePath}"
        )
    }
}