package com.example.downloadmanagerandroid

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.concurrent.atomic.AtomicBoolean

class NetworkConnectionManager (context: Context , listener: NetworkStateListener){
    interface NetworkStateListener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }
    val TAG = "NetworkMonitorManager"
    private var isReconnected = AtomicBoolean(false)
    private val connectionManager = context.getSystemService(ConnectivityManager::class.java) as ConnectivityManager
    private val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            try {
                Log.d(TAG, "onAvailable: =======> isReconnected = ${isReconnected.get()}")
                val capabilities: NetworkCapabilities? = connectionManager.getNetworkCapabilities(network)
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        Log.d(TAG, "Connected via Ethernet")
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        Log.d(TAG, "Connected via Wi-Fi")
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        Log.d(TAG, "Connected via Cellular (4G/5G)")
                    }
                }

                if (isReconnected.get()) {
                    listener.onNetworkAvailable()
                    isReconnected.set(false)
                }
            }catch (e : Exception){
                Log.d(TAG, "onAvailable: ====> e = ${e.message}")
            }

        }
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "onLost: =======>")
            isReconnected.set(true)
            listener.onNetworkLost()
        }
    }

    fun init() {
        connectionManager.requestNetwork(networkRequest, networkCallback)
    }
}