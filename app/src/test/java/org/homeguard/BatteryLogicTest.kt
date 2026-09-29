package org.homeguard

import org.homeguard.core.hardware.BatteryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryLogicTest {

    @Test
    fun `test battery thresholds evaluation`() {
        // Normal battery (75%)
        val normal = evaluateBattery(level = 75, isCharging = false)
        assertFalse(normal.isLow)
        assertFalse(normal.isCritical)

        // Warning threshold (20%)
        val warning = evaluateBattery(level = 20, isCharging = false)
        assertTrue(warning.isLow)
        assertFalse(warning.isCritical)

        // Critical threshold (10%)
        val critical = evaluateBattery(level = 10, isCharging = false)
        assertFalse(critical.isLow)
        assertTrue(critical.isCritical)

        // Very low (5%)
        val extreme = evaluateBattery(level = 5, isCharging = true)
        assertTrue(extreme.isCritical)
        assertTrue(extreme.isCharging)
    }

    private fun evaluateBattery(level: Int, isCharging: Boolean): BatteryInfo {
        val isCritical = level <= 10
        val isLow = level in 11..20
        return BatteryInfo(
            level = level,
            isCharging = isCharging,
            isLow = isLow,
            isCritical = isCritical
        )
    }
}
