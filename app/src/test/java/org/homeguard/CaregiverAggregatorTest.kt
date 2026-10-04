package org.homeguard

import org.homeguard.core.model.CaregiverActivity
import org.homeguard.core.model.CaregiverAggregator
import org.homeguard.core.model.HouseholdRef
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.RemoteCheckIn
import org.homeguard.core.model.RemoteEmergencyEvent
import org.homeguard.core.model.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phase 2 / decision D2: one code covers a whole house, so the dashboard must show everyone in it.
 *
 * These rules are the difference between a plural dashboard and a correct one. The previous version
 * took the first device and the whole check-in list, so with two people in a household it showed one
 * person's status next to the other person's last check-in.
 */
class CaregiverAggregatorTest {

    private val now = 1_700_000_000_000L

    private fun isoAt(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))

    private fun minutesAgo(minutes: Long): String = isoAt(now - minutes * 60_000L)

    private fun protectedDevice(
        id: String,
        name: String = "",
        safety: SafetyStatus = SafetyStatus.SAFE,
        lastSeen: String = minutesAgo(5)
    ) = MonitoredDevice(
        deviceId = id,
        personName = name,
        deviceMode = "PROTECTED_DEVICE",
        safetyStatus = safety.name,
        lastSeenAt = lastSeen,
        batteryLevel = 80,
        isInternetAvailable = true
    )

    // --- who counts as a person -----------------------------------------------------------

    @Test
    fun `every protected device in the household appears`() {
        val people = CaregiverAggregator.watchedPeople(
            listOf(
                protectedDevice("d1", "Asmita"),
                protectedDevice("d2", "Ramesh"),
                protectedDevice("d3", "Priya")
            )
        )

        assertEquals(3, people.size)
        assertEquals(
            listOf("Asmita", "Priya", "Ramesh"),
            people.map { it.device.displayName }
        )
    }

    /**
     * The caregiver's own phone is a household member. Listing it as somebody to watch would be
     * nonsense, so monitoring devices are excluded from the people list.
     */
    @Test
    fun `the caregiver's own monitoring device is not listed as a person`() {
        val people = CaregiverAggregator.watchedPeople(
            listOf(
                protectedDevice("d1", "Asmita"),
                MonitoredDevice(deviceId = "mine", deviceMode = "MONITORING_DEVICE")
            )
        )

        assertEquals(listOf("d1"), people.map { it.deviceId })
    }

    @Test
    fun `a household with no protected devices yields nobody`() {
        assertTrue(
            CaregiverAggregator.watchedPeople(
                listOf(MonitoredDevice(deviceId = "mine", deviceMode = "MONITORING_DEVICE"))
            ).isEmpty()
        )
    }

    @Test
    fun `a blank device id is not treated as a person`() {
        val people = CaregiverAggregator.watchedPeople(
            listOf(MonitoredDevice(deviceId = "", personName = "Ghost", deviceMode = "PROTECTED_DEVICE"))
        )
        assertTrue(people.isEmpty())
    }

    // --- check-ins belong to a person -------------------------------------------------------

    @Test
    fun `each person gets their own latest check-in`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"), protectedDevice("d2", "Ramesh")),
            checkIns = listOf(
                RemoteCheckIn(id = "c1", deviceId = "d1", confirmedAt = minutesAgo(600)),
                RemoteCheckIn(id = "c2", deviceId = "d2", confirmedAt = minutesAgo(20)),
                RemoteCheckIn(id = "c3", deviceId = "d1", confirmedAt = minutesAgo(30))
            )
        )

        val byName = people.associateBy { it.device.displayName }
        // d1 checked in 10h ago and again 30m ago, so 30m is the one to show.
        assertEquals(minutesAgo(30), byName.getValue("Asmita").lastCheckInAt)
        assertEquals(minutesAgo(20), byName.getValue("Ramesh").lastCheckInAt)
    }

    /** The bug this replaces: with two people, one person's check-in was shown under the other. */
    @Test
    fun `a check-in is never attributed to the wrong person`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"), protectedDevice("d2", "Ramesh")),
            checkIns = listOf(RemoteCheckIn(id = "c1", deviceId = "d2", confirmedAt = minutesAgo(5)))
        )

        val byName = people.associateBy { it.device.displayName }
        assertNull(byName.getValue("Asmita").lastCheckInAt)
        assertEquals(minutesAgo(5), byName.getValue("Ramesh").lastCheckInAt)
    }

    @Test
    fun `a person with no check-ins shows none rather than someone else's`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita")),
            checkIns = listOf(RemoteCheckIn(id = "c1", deviceId = "d2", confirmedAt = minutesAgo(5)))
        )

        assertNull(people.single().lastCheckInAt)
    }

    // --- emergencies -----------------------------------------------------------------------

    @Test
    fun `an unresolved emergency is attached to its person`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"), protectedDevice("d2", "Ramesh")),
            emergencies = listOf(
                RemoteEmergencyEvent(id = "e1", deviceId = "d2", triggeredAt = minutesAgo(2))
            )
        )

        val byName = people.associateBy { it.device.displayName }
        assertFalse(byName.getValue("Asmita").hasOpenEmergency)
        assertTrue(byName.getValue("Ramesh").hasOpenEmergency)
        assertEquals("e1", byName.getValue("Ramesh").openEmergency?.id)
    }

    @Test
    fun `a resolved emergency is not treated as open`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita")),
            emergencies = listOf(
                RemoteEmergencyEvent(
                    id = "e1",
                    deviceId = "d1",
                    triggeredAt = minutesAgo(60),
                    resolvedAt = minutesAgo(30)
                )
            )
        )

        assertFalse(people.single().hasOpenEmergency)
    }

    /** Urgency order, so a caregiver scanning the list sees the person who needs them first. */
    @Test
    fun `a person with an open emergency sorts above everyone else`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(
                protectedDevice("d1", "Asmita"),
                protectedDevice("d2", "Ramesh"),
                protectedDevice("d3", "Priya")
            ),
            emergencies = listOf(
                RemoteEmergencyEvent(id = "e1", deviceId = "d3", triggeredAt = minutesAgo(2))
            )
        )

        assertEquals("Priya", people.first().device.displayName)
    }

    @Test
    fun `a reported emergency outranks a stale device`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(
                protectedDevice("d1", "Silent", lastSeen = minutesAgo(600)),
                protectedDevice("d2", "Reported", safety = SafetyStatus.EMERGENCY)
            ),
            nowMillis = now
        )

        assertEquals("Reported", people.first().device.displayName)
    }

    @Test
    fun `a stale device is surfaced before a chatty one`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(
                protectedDevice("d1", "Chatty", lastSeen = minutesAgo(5)),
                protectedDevice("d2", "Silent", lastSeen = minutesAgo(600))
            ),
            nowMillis = now
        )

        assertEquals("Silent", people.first().device.displayName)
    }

    // --- activity feed ----------------------------------------------------------------------

    @Test
    fun `the feed merges every person and is newest first`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"), protectedDevice("d2", "Ramesh"))
        )
        val feed = CaregiverAggregator.activityFeed(
            people = people,
            checkIns = listOf(
                RemoteCheckIn(id = "c1", deviceId = "d1", confirmedAt = minutesAgo(100)),
                RemoteCheckIn(id = "c2", deviceId = "d2", confirmedAt = minutesAgo(50))
            ),
            emergencies = listOf(
                RemoteEmergencyEvent(id = "e1", deviceId = "d1", triggeredAt = minutesAgo(10))
            )
        )

        assertEquals(3, feed.size)
        assertEquals("e1", (feed[0] as CaregiverActivity.Emergency).event.id)
        assertEquals("c2", (feed[1] as CaregiverActivity.CheckIn).checkIn.id)
        assertEquals("c1", (feed[2] as CaregiverActivity.CheckIn).checkIn.id)
    }

    @Test
    fun `feed entries name the person they belong to`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"), protectedDevice("d2", "Ramesh"))
        )
        val feed = CaregiverAggregator.activityFeed(
            people = people,
            checkIns = listOf(RemoteCheckIn(id = "c1", deviceId = "d2", confirmedAt = minutesAgo(5)))
        )

        assertEquals("Ramesh", feed.single().personName)
    }

    /**
     * An unattributed activity entry is worse than no entry, because the caregiver cannot act on
     * it. Rows for devices nobody watches are dropped.
     */
    @Test
    fun `rows for unwatched devices are dropped rather than shown unattributed`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"))
        )
        val feed = CaregiverAggregator.activityFeed(
            people = people,
            checkIns = listOf(RemoteCheckIn(id = "c1", deviceId = "stranger", confirmedAt = minutesAgo(5)))
        )

        assertTrue(feed.isEmpty())
    }

    @Test
    fun `the feed respects its limit`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"))
        )
        val checkIns = (1..30).map {
            RemoteCheckIn(id = "c$it", deviceId = "d1", confirmedAt = minutesAgo(it.toLong()))
        }

        assertEquals(5, CaregiverAggregator.activityFeed(people, checkIns, limit = 5).size)
    }

    @Test
    fun `an unparseable timestamp sinks to the bottom rather than pretending to be new`() {
        val people = CaregiverAggregator.watchedPeople(
            devices = listOf(protectedDevice("d1", "Asmita"))
        )
        val feed = CaregiverAggregator.activityFeed(
            people = people,
            checkIns = listOf(
                RemoteCheckIn(id = "good", deviceId = "d1", confirmedAt = minutesAgo(5)),
                RemoteCheckIn(id = "garbage", deviceId = "d1", confirmedAt = "not-a-date")
            )
        )

        assertEquals("good", (feed.first() as CaregiverActivity.CheckIn).checkIn.id)
    }

    @Test
    fun `parsing a backend timestamp yields the expected instant`() {
        assertEquals(0, CaregiverAggregator.parseIsoMillis(""))
        assertEquals(0, CaregiverAggregator.parseIsoMillis("not-a-date"))
        assertTrue(CaregiverAggregator.parseIsoMillis("${isoAt(now)}Z") == now)
    }

    // --- households -------------------------------------------------------------------------

    @Test
    fun `a household reports whether it has a usable name`() {
        assertTrue(HouseholdRef(id = "h1", name = "Home").hasName)
        assertFalse(HouseholdRef(id = "h1", name = "", inviteCode = "ABCD2345").hasName)
    }
}