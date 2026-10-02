package org.homeguard

import org.homeguard.core.hardware.evaluateBatteryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the production threshold function. The previous version of this test reimplemented
 * the thresholds in a private helper and asserted against that copy, so it could not fail.
 */
class BatteryLogicTest {

    @Test
    fun `healthy battery is neither low nor critical`() {
        val info = evaluateBatteryState(percentage = 75, isCharging = false)
        assertFalse(info.isLow)
        assertFalse(info.isCritical)
        assertEquals(75, info.level)
    }

    @Test
    fun `twenty percent is low but not critical`() {
        val info = evaluateBatteryState(percentage = 20, isCharging = false)
        assertTrue(info.isLow)
        assertFalse(info.isCritical)
    }

    @Test
    fun `ten percent is critical`() {
        val info = evaluateBatteryState(percentage = 10, isCharging = false)
        assertTrue(info.isCritical)
        assertFalse(info.isLow)
    }

    @Test
    fun `five percent while charging is still critical`() {
        val info = evaluateBatteryState(percentage = 5, isCharging = true)
        assertTrue(info.isCritical)
        assertTrue(info.isCharging)
    }

    @Test
    fun `twenty one percent is above the low threshold`() {
        val info = evaluateBatteryState(percentage = 21, isCharging = false)
        assertFalse(info.isLow)
        assertFalse(info.isCritical)
    }

    @Test
    fun `eleven percent is the low band floor`() {
        val info = evaluateBatteryState(percentage = 11, isCharging = false)
        assertTrue(info.isLow)
        assertFalse(info.isCritical)
    }

    @Test
    fun `charging flag is preserved`() {
        assertTrue(evaluateBatteryState(percentage = 50, isCharging = true).isCharging)
        assertFalse(evaluateBatteryState(percentage = 50, isCharging = false).isCharging)
    }
}