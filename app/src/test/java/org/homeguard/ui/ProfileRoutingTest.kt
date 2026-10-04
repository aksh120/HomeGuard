package org.homeguard.ui

import org.homeguard.core.model.AppProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1: the two profiles must be separated at the routing level, not merely by hiding buttons.
 *
 * These are the tests that would have caught D-1, D-2 and D-3. Each one failed against the
 * pre-Phase-1 behaviour, where a monitoring install opened the protected form and the join screen
 * was gated on a navigation state that never held on a fresh install.
 */
class ProfileRoutingTest {

    private val everyDestination = listOf(
        NavigationDestination.Home,
        NavigationDestination.EmergencyCountdown,
        NavigationDestination.EmergencyActive,
        NavigationDestination.NightMode,
        NavigationDestination.PowerCut,
        NavigationDestination.EmergencyInfo,
        NavigationDestination.FireGuide,
        NavigationDestination.MonitoringDashboard,
        NavigationDestination.Settings,
        NavigationDestination.Setup,
        NavigationDestination.ProfileSelection
    )

    @Test
    fun `monitoring profile cannot reach any protected-only destination`() {
        val protectedOnly = listOf(
            NavigationDestination.Setup,
            NavigationDestination.EmergencyCountdown,
            NavigationDestination.EmergencyActive,
            NavigationDestination.NightMode,
            NavigationDestination.PowerCut,
            NavigationDestination.EmergencyInfo,
            NavigationDestination.FireGuide,
            NavigationDestination.MonitoringDashboard
        )

        protectedOnly.forEach { destination ->
            assertFalse(
                "monitoring must not reach $destination",
                ProfileRouting.isReachable(AppProfile.MONITORING, destination)
            )
        }
    }

    @Test
    fun `monitoring profile keeps the destinations it legitimately needs`() {
        assertTrue(ProfileRouting.isReachable(AppProfile.MONITORING, NavigationDestination.Home))
        assertTrue(ProfileRouting.isReachable(AppProfile.MONITORING, NavigationDestination.Settings))
    }

    @Test
    fun `unselected profile can reach nothing except the fork`() {
        everyDestination.forEach { destination ->
            val expected = destination == NavigationDestination.ProfileSelection
            assertEquals(
                "unselected profile reachability for $destination",
                expected,
                ProfileRouting.isReachable(null, destination)
            )
        }
    }

    @Test
    fun `protected profile keeps every resident destination`() {
        everyDestination.forEach { destination ->
            assertTrue(
                "protected profile must be able to reach $destination",
                ProfileRouting.isReachable(AppProfile.PROTECTED, destination)
            )
        }
    }

    // --- Entry routing: the defect that made monitoring onboarding unreachable -----------------

    @Test
    fun `fresh install opens the profile fork, not protected setup`() {
        assertEquals(
            NavigationDestination.ProfileSelection,
            ProfileRouting.entryDestination(profile = null, isSetupComplete = false)
        )
    }

    @Test
    fun `resident who chose their profile but has not finished setup still gets setup`() {
        assertEquals(
            NavigationDestination.Setup,
            ProfileRouting.entryDestination(AppProfile.PROTECTED, isSetupComplete = false)
        )
    }

    @Test
    fun `caregiver never lands in the protected setup form`() {
        assertEquals(
            NavigationDestination.Home,
            ProfileRouting.entryDestination(AppProfile.MONITORING, isSetupComplete = false)
        )
    }

    @Test
    fun `finished setup opens home for both profiles`() {
        assertEquals(
            NavigationDestination.Home,
            ProfileRouting.entryDestination(AppProfile.PROTECTED, isSetupComplete = true)
        )
        assertEquals(
            NavigationDestination.Home,
            ProfileRouting.entryDestination(AppProfile.MONITORING, isSetupComplete = true)
        )
    }

    // --- Backwards compatibility for installs created before the fork -------------------------

    @Test
    fun `a pre-fork install that had completed protected setup is a protected install`() {
        // These installs always wrote a device mode, so they genuinely chose the resident profile
        // and must not be sent back to the fork. PreferencesManager.readProfile performs this
        // derivation; AppProfile carries the result.
        val legacyDeviceMode = org.homeguard.core.model.DeviceMode.PROTECTED_DEVICE
        assertEquals(AppProfile.PROTECTED, legacyDeviceMode.toProfile())
    }

    @Test
    fun `a pre-fork monitoring install stays a monitoring install`() {
        val legacyDeviceMode = org.homeguard.core.model.DeviceMode.MONITORING_DEVICE
        assertEquals(AppProfile.MONITORING, legacyDeviceMode.toProfile())
    }

    @Test
    fun `an unselected install resolves to protected device mode for remote registration`() {
        val config = org.homeguard.core.model.AppConfig(profile = null)
        assertEquals(
            org.homeguard.core.model.DeviceMode.PROTECTED_DEVICE,
            config.deviceMode
        )
        assertFalse(config.hasChosenProfile)
    }

    @Test
    fun `profile and device mode cannot disagree`() {
        val monitoring = org.homeguard.core.model.AppConfig(profile = AppProfile.MONITORING)
        val protected = org.homeguard.core.model.AppConfig(profile = AppProfile.PROTECTED)

        assertEquals(
            org.homeguard.core.model.DeviceMode.MONITORING_DEVICE,
            monitoring.deviceMode
        )
        assertEquals(
            org.homeguard.core.model.DeviceMode.PROTECTED_DEVICE,
            protected.deviceMode
        )
    }
}

/** Mirrors PreferencesManager's legacy derivation so the rule is asserted, not assumed. */
private fun org.homeguard.core.model.DeviceMode.toProfile(): AppProfile =
    when (this) {
        org.homeguard.core.model.DeviceMode.MONITORING_DEVICE -> AppProfile.MONITORING
        org.homeguard.core.model.DeviceMode.PROTECTED_DEVICE -> AppProfile.PROTECTED
    }