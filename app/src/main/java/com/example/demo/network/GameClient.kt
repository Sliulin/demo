package com.example.demo.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class GameClient(
    private val onMessageReceived: (NetworkMessage) -> Unit,
    private val onDisconnected: () -> Unit = {}
) {
    private val tag = "GameClient"
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var listenJob: Job? = null
    @Volatile private var isConnected = false

    private val jsonConfig = Json { ignoreUnknownKeys = true }

    suspend fun connect(scope: CoroutineScope, ip: String, port: Int = 9999): Boolean = withContext(Dispatchers.IO) {
        disconnect(notify = false)

        try {
            val socketAddress = InetSocketAddress(ip, port)
            val newSocket = Socket().apply {
                tcpNoDelay = true
                connect(socketAddress, 5000)
            }

            socket = newSocket
            writer = PrintWriter(newSocket.getOutputStream(), true)
            isConnected = true

            listenJob = scope.launch(Dispatchers.IO) {
                listenToServer(newSocket)
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Socket connect failed: ${e.message}", e)
            disconnect(notify = false)
            false
        }
    }

    private suspend fun listenToServer(activeSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(activeSocket.getInputStream(), Charsets.UTF_8))
            while (isConnected && socket === activeSocket) {
                val jsonString = reader.readLine() ?: break
                Log.d(tag, "Received host broadcast: $jsonString")

                try {
                    val message = jsonConfig.decodeFromString<NetworkMessage>(jsonString)
                    onMessageReceived(message)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse client message", e)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Disconnected from host: ${e.message}")
        } finally {
            if (socket === activeSocket) {
                disconnect(notify = true)
            }
        }
    }

    suspend fun sendMessage(message: NetworkMessage): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected || writer == null) {
            Log.w(tag, "Not connected, unable to send message")
            return@withContext false
        }

        try {
            val jsonString = jsonConfig.encodeToString(message)
            val activeWriter = writer ?: return@withContext false
            activeWriter.println(jsonString)
            if (activeWriter.checkError()) {
                disconnect(notify = true)
                return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Send message failed", e)
            disconnect(notify = true)
            false
        }
    }

    fun disconnect(notify: Boolean = false) {
        val wasConnected = isConnected
        isConnected = false
        listenJob?.cancel()
        listenJob = null

        try {
            writer?.close()
        } catch (_: Exception) {
        }
        writer = null

        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null

        if (notify && wasConnected) {
            onDisconnected()
        }
    }

    fun isConnected(): Boolean = isConnected
}
