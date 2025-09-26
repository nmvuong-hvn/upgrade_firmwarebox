package com.marusys.downloadsdk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.concurrent.atomic.AtomicBoolean

class NetworkManager(private val context: Context, private val listener: NetworkConnectionState) {
    interface NetworkConnectionState {
        fun onConnected()
        fun onDisconnected()
    }
    private val TAG = "NetworkManager"

    private var isReconnected = AtomicBoolean(false)
    private var isRegistered = false

    @SuppressLint("MissingPermission")
    fun isConnected(): Boolean {
        val network = connectManager.activeNetwork ?: return false
        val capabilities = connectManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val connectManager = context.getSystemService(ConnectivityManager::class.java) as ConnectivityManager
    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            Log.d(TAG, "onAvailable: ======>")
            super.onAvailable(network)
            if (isReconnected.get()) {
                listener.onConnected()
                isReconnected.set(false)
            }
        }

        override fun onLost(network: android.net.Network) {
            super.onLost(network)
            Log.d(TAG, "onLost: ======>")
            listener.onDisconnected()
            isReconnected.set(true)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun registerNetworkCallback() {
        if (!isRegistered) {
            connectManager.registerNetworkCallback(networkRequest, networkCallback)
            isRegistered = true
            Log.d(TAG, "Network callback registered")
        }
    }

    fun unregisterNetworkCallback() {
        if (isRegistered) {
            try {
                connectManager.unregisterNetworkCallback(networkCallback)
                isRegistered = false
                Log.d(TAG, "Network callback unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            }
        }
    }

    fun cleanup() {
        unregisterNetworkCallback()
    }
}