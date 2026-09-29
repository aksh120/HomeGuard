package org.homeguard.core.model

/**
 * Core tri-state safety representation for HomeGuard.
 * - SAFE: Recent successful check-in exists and no active emergency.
 * - UNKNOWN: Awaiting safety check-in confirmation or timeout expired.
 * - EMERGENCY: Active confirmed SOS alert in progress.
 */
enum class SafetyStatus {
    SAFE,
    UNKNOWN,
    EMERGENCY
}
