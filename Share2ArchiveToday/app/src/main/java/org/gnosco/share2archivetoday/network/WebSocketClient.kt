package org.gnosco.share2archivetoday.network

import org.gnosco.share2archivetoday.network.*

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.WebSocket
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket client implementation inspired by Signal Android's approach
 * 
 * This class provides robust WebSocket connectivity for yt-dlp's WebSocket-based
 * downloads, similar to how Signal handles real-time communication.
 * 
 * ## Features
 * - ✅ Automatic reconnection with exponential backoff
 * - ✅ Heartbeat/ping-pong mechanism
 * - ✅ Message queuing during disconnection
 * - ✅ Binary and text message support
 * - ✅ Connection state management
 * - ✅ Error handling and recovery
 * 
 * ## Usage Example
 * ```kotlin
 * val client = WebSocketClient(
 *     url = "wss://example.com/ws",
 *     listener = object : WebSocketListener {
 *         override fun onMessage(text: String) { /* handle text */ }
 *         override fun onMessage(bytes: ByteArray) { /* handle binary */ }
 *         override fun onConnected() { /* connection established */ }
 *         override fun onDisconnected() { /* connection lost */ }
 *     }
 * )
 * 
 * client.connect()
 * client.send("Hello WebSocket!")
 * client.disconnect()
 * ```
 */
class WebSocketClient(
    private val url: String,
    private val listener: WebSocketListener,
    private val reconnectInterval: Long = 5000L,
    private val maxReconnectAttempts: Int = 10,
    private val heartbeatInterval: Long = 30000L
) {
    
    companion object {
        private const val TAG = "WebSocketClient"
        private const val PING_MESSAGE = "ping"
        private const val PONG_MESSAGE = "pong"
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No read timeout for WebSocket
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(heartbeatInterval, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val messageQueue = mutableListOf<Message>()
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    
    /**
     * Connect to WebSocket server
     */
    fun connect() {
        if (isConnecting.get() || isConnected.get()) {
            Log.d(TAG, "Already connecting or connected")
            return
        }
        
        clientScope.launch {
            performConnection()
        }
    }
    
    /**
     * Disconnect from WebSocket server
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        
        isConnected.set(false)
        isConnecting.set(false)
        
        listener.onDisconnected()
    }
    
    /**
     * Send text message
     */
    fun send(text: String) {
        if (isConnected.get()) {
            webSocket?.send(text)
            Log.d(TAG, "Sent text message: $text")
        } else {
            Log.w(TAG, "Not connected, queuing message: $text")
            messageQueue.add(Message.Text(text))
        }
    }
    
    /**
     * Send binary message
     */
    fun send(bytes: ByteArray) {
        if (isConnected.get()) {
            webSocket?.send(ByteString.of(*bytes))
            Log.d(TAG, "Sent binary message: ${bytes.size} bytes")
        } else {
            Log.w(TAG, "Not connected, queuing binary message: ${bytes.size} bytes")
            messageQueue.add(Message.Binary(bytes))
        }
    }
    
    /**
     * Check if WebSocket is connected
     */
    fun isConnected(): Boolean = isConnected.get()
    
    /**
     * Get connection state
     */
    fun getConnectionState(): ConnectionState {
        return when {
            isConnected.get() -> ConnectionState.CONNECTED
            isConnecting.get() -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
    }
    
    /**
     * Perform the actual connection
     */
    private suspend fun performConnection() {
        if (isConnecting.get()) return
        
        isConnecting.set(true)
        reconnectAttempts.incrementAndGet()
        
        try {
            Log.d(TAG, "Connecting to WebSocket: $url (attempt ${reconnectAttempts.get()})")
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Share2ArchiveToday/1.0")
                .addHeader("Accept", "*/*")
                .addHeader("Connection", "Upgrade")
                .addHeader("Upgrade", "websocket")
                .addHeader("Sec-WebSocket-Version", "13")
                .build()
            
            webSocket = httpClient.newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected successfully")
                    isConnected.set(true)
                    isConnecting.set(false)
                    reconnectAttempts.set(0)
                    
                    // Start heartbeat
                    startHeartbeat()
                    
                    // Process queued messages
                    processQueuedMessages()
                    
                    listener.onConnected()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received text message: $text")
                    listener.onMessage(text)
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                    listener.onMessage(bytes.toByteArray())
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code - $reason")
                    isConnected.set(false)
                    listener.onDisconnected()
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code - $reason")
                    isConnected.set(false)
                    isConnecting.set(false)
                    listener.onDisconnected()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket connection failed", t)
                    isConnected.set(false)
                    isConnecting.set(false)
                    
                    // Attempt reconnection if not manually disconnected
                    if (reconnectAttempts.get() < maxReconnectAttempts) {
                        scheduleReconnect()
                    } else {
                        Log.e(TAG, "Max reconnection attempts reached")
                        listener.onError(t)
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating WebSocket connection", e)
            isConnecting.set(false)
            listener.onError(e)
        }
    }
    
    /**
     * Schedule reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        
        val delay = minOf(
            reconnectInterval * (1L shl minOf(reconnectAttempts.get() - 1, 5)),
            60000L // Max 60 seconds
        )
        
        Log.d(TAG, "Scheduling reconnection in ${delay}ms")
        
        reconnectJob = clientScope.launch {
            delay(delay)
            if (!isConnected.get() && !isConnecting.get()) {
                performConnection()
            }
        }
    }
    
    /**
     * Start heartbeat mechanism
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        
        heartbeatJob = clientScope.launch {
            while (isConnected.get()) {
                delay(heartbeatInterval)
                if (isConnected.get()) {
                    try {
                        webSocket?.send(PING_MESSAGE)
                        Log.d(TAG, "Sent heartbeat ping")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending heartbeat", e)
                        break
                    }
                }
            }
        }
    }
    
    /**
     * Process queued messages
     */
    private fun processQueuedMessages() {
        if (messageQueue.isEmpty()) return
        
        Log.d(TAG, "Processing ${messageQueue.size} queued messages")
        
        val messages = messageQueue.toList()
        messageQueue.clear()
        
        messages.forEach { message ->
            when (message) {
                is Message.Text -> send(message.content)
                is Message.Binary -> send(message.content)
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        disconnect()
        clientScope.cancel()
    }
    
    /**
     * WebSocket listener interface
     */
    interface WebSocketListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(text: String)
        fun onMessage(bytes: ByteArray)
        fun onError(error: Throwable)
    }
    
    /**
     * Connection state enum
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
    
    /**
     * Message types for queuing
     */
    private sealed class Message {
        data class Text(val content: String) : Message()
        data class Binary(val content: ByteArray) : Message()
    }
}

/**
 * WebSocket manager for handling multiple WebSocket connections
 * Similar to Signal's approach for managing multiple connections
 */
class WebSocketManager {
    
    companion object {
        private const val TAG = "WebSocketManager"
    }
    
    private val connections = mutableMapOf<String, WebSocketClient>()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Create or get existing WebSocket connection
     */
    fun getConnection(
        url: String,
        listener: WebSocketClient.WebSocketListener,
        reconnectInterval: Long = 5000L,
        maxReconnectAttempts: Int = 10
    ): WebSocketClient {
        return connections.getOrPut(url) {
            WebSocketClient(url, listener, reconnectInterval, maxReconnectAttempts)
        }
    }
    
    /**
     * Connect to WebSocket
     */
    fun connect(url: String, listener: WebSocketClient.WebSocketListener) {
        val client = getConnection(url, listener)
        client.connect()
    }
    
    /**
     * Disconnect specific WebSocket
     */
    fun disconnect(url: String) {
        connections[url]?.disconnect()
        connections.remove(url)
    }
    
    /**
     * Disconnect all WebSockets
     */
    fun disconnectAll() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
    }
    
    /**
     * Get connection status
     */
    fun getConnectionStatus(url: String): WebSocketClient.ConnectionState? {
        return connections[url]?.getConnectionState()
    }
    
    /**
     * Send message to specific WebSocket
     */
    fun sendMessage(url: String, message: String) {
        connections[url]?.send(message)
    }
    
    /**
     * Send binary message to specific WebSocket
     */
    fun sendMessage(url: String, message: ByteArray) {
        connections[url]?.send(message)
    }
    
    /**
     * Clean up all connections
     */
    fun cleanup() {
        disconnectAll()
        managerScope.cancel()
    }
}
