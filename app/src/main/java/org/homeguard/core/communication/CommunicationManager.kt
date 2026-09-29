package org.homeguard.core.communication

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.homeguard.core.model.ContactInfo

sealed class CommunicationResult {
    data object Initiated : CommunicationResult()
    data class Unavailable(val message: String) : CommunicationResult()
}

interface CommunicationProvider {
    val name: String
    fun isAvailable(context: Context): Boolean
    fun initiate(context: Context, contact: ContactInfo): CommunicationResult
}

/**
 * Standard Telephony/Dialer provider.
 * Uses ACTION_DIAL so no dangerous CALL_PHONE permission is strictly required,
 * allowing user verification before connection.
 */
class SystemDialerProvider : CommunicationProvider {
    override val name: String = "Dialer"

    override fun isAvailable(context: Context): Boolean {
        val hasTelephony = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val dummyIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
        val canResolve = dummyIntent.resolveActivity(context.packageManager) != null
        return hasTelephony || canResolve
    }

    override fun initiate(context: Context, contact: ContactInfo): CommunicationResult {
        val phone = contact.phoneOrHandle.trim()
        if (phone.isEmpty()) {
            return CommunicationResult.Unavailable("No contact number is configured.")
        }
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            CommunicationResult.Initiated
        } catch (e: Exception) {
            CommunicationResult.Unavailable("Unable to open phone dialer: ${e.message}")
        }
    }
}

/**
 * Internet Communication provider for SIM-less devices.
 * Supports web communication or universal messaging URIs.
 */
class InternetCommunicationProvider : CommunicationProvider {
    override val name: String = "Internet"

    override fun isAvailable(context: Context): Boolean = true

    override fun initiate(context: Context, contact: ContactInfo): CommunicationResult {
        val handle = contact.phoneOrHandle.trim()
        if (handle.isEmpty()) {
            return CommunicationResult.Unavailable("No contact address or handle is configured.")
        }

        // Check if handle is a valid web URL or custom scheme
        if (handle.startsWith("https://") || handle.startsWith("http://")) {
            return try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(handle)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                CommunicationResult.Initiated
            } catch (e: Exception) {
                CommunicationResult.Unavailable("Could not open internet communication link.")
            }
        }

        return CommunicationResult.Unavailable("Please configure a phone number or internet communication URL in settings.")
    }
}

class CommunicationManager(
    private val providers: List<CommunicationProvider> = listOf(
        SystemDialerProvider(),
        InternetCommunicationProvider()
    )
) {
    fun initiateCall(context: Context, contact: ContactInfo): CommunicationResult {
        if (contact.phoneOrHandle.isBlank()) {
            return CommunicationResult.Unavailable("Contact information is empty. Please set up a primary contact.")
        }

        for (provider in providers) {
            if (provider.isAvailable(context)) {
                val result = provider.initiate(context, contact)
                if (result is CommunicationResult.Initiated) {
                    return result
                }
            }
        }
        return CommunicationResult.Unavailable("No communication method is available on this device.")
    }
}
