package com.example.customizedownloadandroid.customizedownloading

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class NetworkConnectionManager (context : Context, listener: NetworkStateListener) {

    interface NetworkStateListener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }
    val TAG = "NetworkConnectionManager"
    private var isReconnected = AtomicBoolean(false)
    private val connectionManager = context.getSystemService(ConnectivityManager::class.java) as ConnectivityManager
    private val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)         // Wi-Fi
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            try {
                Log.d(TAG, "onAvailable: =======> isReconnected = ${isReconnected.get()}")
                val capabilities: NetworkCapabilities? = connectionManager.getNetworkCapabilities(network)
                Log.d(TAG, "onAvailable: ====> capabilities = $capabilities")
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        Log.d(TAG, "Connected via Ethernet")
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        Log.d(TAG, "Connected via Wi-Fi")
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        // Here it can be 3G/4G/5G
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

    fun isConnected() : Boolean {
        val network = connectionManager.activeNetwork ?: return false
        val caps = connectionManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}