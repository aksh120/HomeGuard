package org.homeguard.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette: a single dark scheme, deliberately close to neutral grey rather than black.
 *
 * The master specification forbids neon effects and asks for a restrained palette with strong
 * contrast. The previous palette used near-black backgrounds (#060911) with neon-green and
 * red accents, which read as an alerting console rather than a dependable home device.
 */
val DarkBackground = Color(0xFF14181F)     // neutral slate, not black
val DarkSurface = Color(0xFF1E242E)        // subtle elevation over background
val DarkSurfaceVariant = Color(0xFF2A313C) // one step further, for chips and dividers
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB6C0CC)      // legible secondary text, still high contrast
val BorderSubtle = Color(0xFF39424F)       // hairline borders

/**
 * Status colours are used for text, borders, and icons. They are never a large filled field
 * behind body text, so state is never communicated by colour alone.
 */
val StatusSafe = Color(0xFF3FB950)          // calm green
val StatusUnknown = Color(0xFFD29922)       // amber
val StatusEmergency = Color(0xFFE5484D)     // red, reserved for the SOS action only

val StatusSafeBackground = Color(0xFF16261C)
val StatusUnknownBackground = Color(0xFF2A2114)
val StatusEmergencyBackground = Color(0xFF2C1618)

/**
 * Action colours.
 *
 * Primary actions are calm. Bright red is reserved exclusively for the emergency trigger so it
 * remains the single most salient control on the screen.
 */
val ActionPrimary = Color(0xFF3D7EFF)      // calm blue for neutral primary actions
val ActionPrimaryPressed = Color(0xFF2F62CC)
val ActionImOk = Color(0xFF2F7D4F)         // deeper, calmer green for "I'm OK"
val ActionEmergency = Color(0xFFC62F32)    // strong but not neon red
val ActionWarning = Color(0xFFB5741B)
val TorchActive = Color(0xFFD29922)