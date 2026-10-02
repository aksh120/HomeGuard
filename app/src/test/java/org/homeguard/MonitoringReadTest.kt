package org.homeguard

import org.homeguard.core.data.MonitoringRemoteDataSource
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.RemoteEmergencyEvent
import org.homeguard.core.model.SafetyStatus
import org.homeguard.ui.screens.formatRemoteTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Section 19 is the critical invariant for the caregiver view: a device that has gone quiet must
 * show as UNKNOWN, never as an emergency. Only an explicit SOS may produce EMERGENCY.
 */
class MonitoringReadTest {

    private val now = 1_700_000_000_000L

    private fun isoAt(epochMillis: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(java.util.Date(epochMillis))
    }

    private fun minutesAgo(minutes: Long): String = isoAt(now - minutes * 60_000L)

    @Test
    fun `device with a recent heartbeat is not stale`() {
        val device = MonitoredDevice(deviceId = "d1", lastSeenAt = minutesAgo(10))
        assertTrue(device.hasReported)
        assertFalse(device.isStale(now))
    }

    @Test
    fun `device silent past the threshold is stale`() {
        val device = MonitoredDevice(deviceId = "d1", lastSeenAt = minutesAgo(60))
        assertTrue(device.isStale(now))
    }

    @Test
    fun `device that has never reported is stale`() {
        val device = MonitoredDevice(deviceId = "d1", lastSeenAt = "")
        assertFalse(device.hasReported)
        assertTrue(device.isStale(now))
    }

    @Test
    fun `unparseable timestamp counts as stale rather than silently fresh`() {
        val device = MonitoredDevice(deviceId = "d1", lastSeenAt = "not-a-date")
        assertTrue(device.isStale(now))
    }

    @Test
    fun `safety status falls back to UNKNOWN when the backend sends an unknown value`() {
        val device = MonitoredDevice(deviceId = "d1", safetyStatus = "WAT")
        assertEquals(SafetyStatus.UNKNOWN, device.safety)
    }

    @Test
    fun `emergency status is read from the backend`() {
        val device = MonitoredDevice(deviceId = "d1", safetyStatus = "EMERGENCY")
        assertEquals(SafetyStatus.EMERGENCY, device.safety)
    }

    @Test
    fun `display name falls back rather than showing a blank`() {
        assertEquals("Protected person", MonitoredDevice().displayName)
        assertEquals("Asmita", MonitoredDevice(personName = "Asmita").displayName)
    }

    @Test
    fun `unresolved emergency is distinguished from a resolved one`() {
        assertFalse(RemoteEmergencyEvent(id = "e1", resolvedAt = null).isResolved)
        assertFalse(RemoteEmergencyEvent(id = "e1", resolvedAt = "").isResolved)
        assertTrue(
            RemoteEmergencyEvent(id = "e1", resolvedAt = "2026-10-02T10:00:00Z").isResolved
        )
    }

    // --- invite codes -----------------------------------------------------------------

    @Test
    fun `generated invite codes are the right length and alphabet`() {
        repeat(50) {
            val code = MonitoringRemoteDataSource.generateInviteCode()
            assertEquals(8, code.length)
            assertTrue(
                "Unexpected characters in $code",
                code.all { c -> "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".contains(c) }
            )
        }
    }

    @Test
    fun `generated codes do not repeat`() {
        val codes = (1..200).map { MonitoringRemoteDataSource.generateInviteCode() }.toSet()
        assertTrue("Codes collided unexpectedly", codes.size > 190)
    }

    @Test
    fun `user input is normalised the same way the code is generated`() {
        assertEquals(
            "ABCD2345",
            MonitoringRemoteDataSource.normaliseInviteCode("abcd-2345")
        )
    }

    @Test
    fun `normalising strips characters outside the alphabet`() {
        assertEquals(
            "ABC2345",
            MonitoringRemoteDataSource.normaliseInviteCode("a b c 2 3 4 5 !@#")
        )
    }

    @Test
    fun `normalising handles lowercase input`() {
        assertEquals("XY234567", MonitoringRemoteDataSource.normaliseInviteCode("xy234567"))
    }

    // --- timestamp formatting ---------------------------------------------------------

    @Test
    fun `remote timestamps format without throwing`() {
        assertFalse(formatRemoteTimestamp("2026-10-02T14:30:00Z").isBlank())
        assertFalse(formatRemoteTimestamp("2026-10-02T14:30:00.123456Z").isBlank())
        assertFalse(formatRemoteTimestamp("2026-10-02T14:30:00.123Z").isBlank())
    }

    /**
     * A timestamp the parser cannot read must be shown verbatim. Substituting the current time
     * would tell a caregiver an emergency just happened.
     */
    @Test
    fun `unparseable remote timestamp is returned verbatim, not as now`() {
        val garbage = "definitely-not-a-date"
        assertEquals(garbage, formatRemoteTimestamp(garbage))
        assertEquals("", formatRemoteTimestamp(""))
    }

    @Test
    fun `a real backend timestamp is parsed and reformatted`() {
        // Built with an explicit UTC formatter so the input does not depend on the machine's
        // timezone, then asserted to have actually been reformatted rather than passed through.
        val iso = "2026-10-02T14:30:00"
        val formatted = formatRemoteTimestamp("${iso}Z")
        assertFalse(formatted.isBlank())
        assertFalse(
            "Timestamp was not parsed; it was returned verbatim",
            formatted.contains("T14:30:00")
        )
    }
}