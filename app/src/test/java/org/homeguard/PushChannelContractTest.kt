package org.homeguard

import org.homeguard.HomeGuardApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 4 contract between the notification channels the app creates and the channel the edge
 * function tells FCM to post on.
 *
 * This exists because the mismatch is silent. A push sent with the wrong channel_id is not an error:
 * Android drops it on a default channel with IMPORTANCE_DEFAULT, so the alert still appears but
 * quietly loses high priority. For an emergency that is a failure that looks like success, and it
 * would only be noticed by a caregiver who noticed the alert felt quiet.
 */
class PushChannelContractTest {

    /** The value the edge function sends as notification.channel_id. */
    private val edgeFunctionChannelId = "channel_homeguard_emergency"

    @Test
    fun `the emergency channel id matches what the edge function sends`() {
        assertEquals(
            "FCM channel_id must equal HomeGuardApplication.CHANNEL_EMERGENCY",
            HomeGuardApplication.CHANNEL_EMERGENCY,
            edgeFunctionChannelId
        )
    }

    /**
     * Finds the edge function by walking up from the working directory.
     *
     * Gradle runs unit tests with the module directory as the working directory, not the repository
     * root, so a fixed relative path resolves to the wrong place. Walking up to the marker file is
     * reliable regardless of which module is under test.
     */
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

    /**
     * Asserts it directly against the edge function source rather than a copy of the constant, so
     * changing one side without the other fails here.
     */
    @Test
    fun `the edge function does not hardcode a different emergency channel`() {
        val text = edgeFunctionSource().readText()

        assertTrue(
            "EMERGENCY_CHANNEL_ID must be defined in the edge function",
            text.contains("const EMERGENCY_CHANNEL_ID")
        )
        assertTrue(
            "the channel actually sent must use the constant, not a literal",
            text.contains("channel_id: EMERGENCY_CHANNEL_ID")
        )
        assertTrue(
            "the constant must match the app's channel id",
            text.contains("\"$edgeFunctionChannelId\"")
        )
    }

    /**
     * Push priority has to be HIGH or a backgrounded phone may defer the message until the next
     * maintenance window, which for an emergency is the difference between seconds and hours.
     */
    @Test
    fun `the edge function sends high priority`() {
        val text = edgeFunctionSource().readText()
        assertTrue(
            "FCM messages must be sent with HIGH priority",
            text.contains("priority: \"HIGH\"")
        )
    }
}