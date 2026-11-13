package me.floow.shared.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.floow.domain.network.NetworkMonitor
import me.floow.shared.auth.AndroidTokenContext

actual fun createNetworkMonitor(): NetworkMonitor {
    val context = AndroidTokenContext.get()
    return if (context != null) {
        AndroidNetworkMonitor(context)
    } else {
        object : NetworkMonitor {
            private val _online = MutableStateFlow(true)
            override val isOnline: StateFlow<Boolean> = _online.asStateFlow()
        }
    }
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        connectivityManager?.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = checkOnline()
                }
            }
        )
    }

    private fun checkOnline(): Boolean {
        val manager = connectivityManager ?: return true
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
