package org.homeguard.ui

import org.homeguard.core.model.AppProfile

/**
 * Every destination the app can show.
 *
 * Extracted from HomeViewModel so that the routing rules in ProfileRouting are plain Kotlin and can
 * be unit tested on the JVM. It previously lived inside a file that declared an AndroidViewModel,
 * which made the rules untestable without instantiating an Android component.
 */
sealed class NavigationDestination {
    data object Home : NavigationDestination()
    data object EmergencyCountdown : NavigationDestination()
    data object EmergencyActive : NavigationDestination()
    data object NightMode : NavigationDestination()
    data object PowerCut : NavigationDestination()
    data object EmergencyInfo : NavigationDestination()
    data object FireGuide : NavigationDestination()
    data object MonitoringDashboard : NavigationDestination()
    data object Settings : NavigationDestination()
    data object Setup : NavigationDestination()

    /** Phase 1: the first-run fork that chooses between the two profiles. */
    data object ProfileSelection : NavigationDestination()
}

/**
 * Which destinations each profile is allowed to reach.
 *
 * The two experiences are meant to be genuinely different, not the same screens with some buttons
 * hidden. Enforcing that here rather than by hiding UI means a monitoring install cannot reach a
 * protected-only destination even if something calls navigateTo with one, and the rule itself is
 * covered by ProfileRoutingTest.
 *
 * Phase 5 will add the caregiver-raised alarm destination for the monitoring profile. It is not
 * here yet: remote SOS does not exist, and listing it would be a route to nowhere.
 */
object ProfileRouting {

    /** Destinations that only make sense on the phone belonging to someone at home. */
    private val PROTECTED_ONLY = setOf(
        NavigationDestination.Setup,
        NavigationDestination.EmergencyCountdown,
        NavigationDestination.EmergencyActive,
        NavigationDestination.NightMode,
        NavigationDestination.PowerCut,
        NavigationDestination.EmergencyInfo,
        NavigationDestination.FireGuide,
        NavigationDestination.MonitoringDashboard
    )

    /**
     * True when [destination] is available to [profile].
     *
     * A null profile has not chosen yet, so only the selection screen itself is reachable. That is
     * what stops a fresh install from landing in protected setup.
     */
    fun isReachable(profile: AppProfile?, destination: NavigationDestination): Boolean =
        when (profile) {
            null -> destination == NavigationDestination.ProfileSelection
            AppProfile.PROTECTED -> true
            // Settings stays reachable so the caregiver can see sync health and leave a household.
            AppProfile.MONITORING -> destination !in PROTECTED_ONLY
        }

    /**
     * Where an install should open, given what it has chosen so far.
     *
     * Pure so the entry rule is testable. The caregiver dashboard and join screen are not modelled
     * as destinations because a monitoring install renders one of them in place of the shared
     * destination tree; only the protected profile walks [NavigationDestination].
     */
    fun entryDestination(
        profile: AppProfile?,
        isSetupComplete: Boolean
    ): NavigationDestination = when {
        profile == null -> NavigationDestination.ProfileSelection
        profile == AppProfile.MONITORING -> NavigationDestination.Home
        !isSetupComplete -> NavigationDestination.Setup
        else -> NavigationDestination.Home
    }
}