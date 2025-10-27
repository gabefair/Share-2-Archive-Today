package org.gnosco.share2archivetoday.network

import org.gnosco.share2archivetoday.network.*

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network monitoring utility for video downloads
 * Provides real-time network state updates and connection quality assessment
 */
class NetworkMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isWiFiConnected = MutableStateFlow(false)
    val isWiFiConnected: StateFlow<Boolean> = _isWiFiConnected.asStateFlow()
    
    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality: StateFlow<ConnectionQuality> = _connectionQuality.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * Start monitoring network state
     */
    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                    updateNetworkState()
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                    updateNetworkState()
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "Network capabilities changed: $networkCapabilities")
                    updateNetworkState()
                }
            }
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        }
        
        // Initial state check
        updateNetworkState()
    }
    
    /**
     * Stop monitoring network state
     */
    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
    }
    
    /**
     * Update network state based on current connectivity
     */
    private fun updateNetworkState() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        
        val connected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                       capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ||
                       capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        
        val wifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        
        val quality = when {
            !connected -> ConnectionQuality.NO_CONNECTION
            wifiConnected -> {
                when {
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true -> 
                        ConnectionQuality.WIFI_EXCELLENT
                    else -> ConnectionQuality.WIFI_POOR
                }
            }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                when {
                    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true -> 
                        ConnectionQuality.CELLULAR_GOOD
                    else -> ConnectionQuality.CELLULAR_POOR
                }
            }
            else -> ConnectionQuality.UNKNOWN
        }
        
        _isConnected.value = connected
        _isWiFiConnected.value = wifiConnected
        _connectionQuality.value = quality
        
        Log.d(TAG, "Network state updated: connected=$connected, wifi=$wifiConnected, quality=$quality")
    }
    
    /**
     * Check if device is currently connected to internet
     */
    fun isConnected(): Boolean {
        return _isConnected.value
    }
    
    /**
     * Check if device is connected to WiFi
     */
    fun isWiFiConnected(): Boolean {
        return _isWiFiConnected.value
    }
    
    /**
     * Get current connection quality
     */
    fun getConnectionQuality(): ConnectionQuality {
        return _connectionQuality.value
    }
    
    /**
     * Check if connection is suitable for large downloads
     */
    fun isSuitableForLargeDownloads(): Boolean {
        return when (_connectionQuality.value) {
            ConnectionQuality.WIFI_EXCELLENT,
            ConnectionQuality.WIFI_POOR -> true
            ConnectionQuality.CELLULAR_GOOD -> true
            else -> false
        }
    }
    
    /**
     * Get recommended quality based on connection
     */
    fun getRecommendedQuality(): String {
        return when (_connectionQuality.value) {
            ConnectionQuality.WIFI_EXCELLENT -> "best"
            ConnectionQuality.WIFI_POOR -> "720p"
            ConnectionQuality.CELLULAR_GOOD -> "480p"
            ConnectionQuality.CELLULAR_POOR -> "360p"
            else -> "360p"
        }
    }
    
    /**
     * Check if we should warn user about data usage
     */
    fun shouldWarnAboutDataUsage(): Boolean {
        return _connectionQuality.value in listOf(
            ConnectionQuality.CELLULAR_GOOD,
            ConnectionQuality.CELLULAR_POOR
        )
    }
    
    /**
     * Connection quality levels
     */
    enum class ConnectionQuality {
        NO_CONNECTION,
        WIFI_EXCELLENT,
        WIFI_POOR,
        CELLULAR_GOOD,
        CELLULAR_POOR,
        UNKNOWN
    }
}
