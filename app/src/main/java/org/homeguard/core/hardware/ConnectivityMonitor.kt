package org.homeguard.core.hardware

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectivityInfo(
    val isWifiConnected: Boolean = true,
    val isInternetAvailable: Boolean = true
)

class ConnectivityMonitor(
    context: Context,
    private val onNetworkRestored: (() -> Unit)? = null
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _connectivityInfo = MutableStateFlow(checkInitialConnectivity())
    val connectivityInfo: StateFlow<ConnectivityInfo> = _connectivityInfo.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !_connectivityInfo.value.isInternetAvailable
            updateState()
            if (wasOffline) {
                onNetworkRestored?.invoke()
            }
        }

        override fun onLost(network: Network) {
            updateState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateState()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            // Fallback for restricted environments
        }
    }

    private fun checkInitialConnectivity(): ConnectivityInfo {
        val activeNetwork = connectivityManager.activeNetwork ?: return ConnectivityInfo(
            isWifiConnected = false,
            isInternetAvailable = false
        )
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return ConnectivityInfo(
            isWifiConnected = false,
            isInternetAvailable = false
        )

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return ConnectivityInfo(
            isWifiConnected = isWifi,
            isInternetAvailable = hasInternet
        )
    }

    private fun updateState() {
        _connectivityInfo.value = checkInitialConnectivity()
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }
}
