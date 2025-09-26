package com.marusys.upgradefirmware

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
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
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "onAvailable: =======> isReconnected = ${isReconnected.get()}")
            if (isReconnected.get()) {
                listener.onNetworkAvailable()
                isReconnected.set(false)
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