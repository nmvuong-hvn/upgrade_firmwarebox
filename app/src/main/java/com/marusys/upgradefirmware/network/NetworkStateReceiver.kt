package com.marusys.upgradefirmware.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

class NetworkStateReceiver : BroadcastReceiver() {
    
    private val TAG = "NetworkStateReceiver"
    
    interface NetworkStateListener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }
    
    companion object {
        var listeners = mutableSetOf<NetworkStateListener>()
        
        fun addListener(listener: NetworkStateListener) {
            listeners.add(listener)
        }
        
        fun removeListener(listener: NetworkStateListener) {
            listeners.remove(listener)
        }
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isConnected = isNetworkAvailable(connectivityManager)
        
        Log.d(TAG, "Network state changed: connected = $isConnected")
        
        if (isConnected) {
            listeners.forEach { it.onNetworkAvailable() }
        } else {
            listeners.forEach { it.onNetworkLost() }
        }
    }
    
    private fun isNetworkAvailable(connectivityManager: ConnectivityManager): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}