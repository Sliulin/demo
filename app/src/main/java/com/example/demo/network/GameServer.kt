package com.example.demo.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * 房主设备持有的权威 TCP 服务端。
 */
class GameServer(
    private val port: Int = 9999,
    private val onMessageReceived: (NetworkMessage) -> Unit
) {
    private val tag = "GameServer"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false

    private val clientWriters = mutableListOf<PrintWriter>()
    private val writerPlayerIds = mutableMapOf<PrintWriter, String>()
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    /**
     * 开始接收客机连接，直到服务端被停止。
     */
    suspend fun start(scope: CoroutineScope) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true

        try {
            serverSocket?.close()
            serverSocket = ServerSocket(port)
            Log.d(tag, "Server started on port $port")

            while (isRunning) {
                val clientSocket = serverSocket?.accept() ?: break
                Log.d(tag, "Client connected")

                scope.launch(Dispatchers.IO) {
                    handleClient(clientSocket)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(tag, "Server error: ${e.message}", e)
            }
        } finally {
            isRunning = false
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        var writer: PrintWriter? = null
        try {
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer = PrintWriter(socket.getOutputStream(), true)

            synchronized(clientWriters) {
                clientWriters.add(writer)
            }

            while (isRunning) {
                val jsonString = reader.readLine() ?: break
                Log.d(tag, "Received message: $jsonString")

                try {
                    val message = jsonConfig.decodeFromString<NetworkMessage>(jsonString)
                    if (message is MsgJoinRoom) {
                        synchronized(clientWriters) {
                            writerPlayerIds[writer] = message.playerId
                        }
                    }
                    onMessageReceived(message)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse message: $jsonString", e)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Client connection closed: ${e.message}")
        } finally {
            synchronized(clientWriters) {
                writer?.let {
                    clientWriters.remove(it)
                    writerPlayerIds.remove(it)
                }
            }
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(tag, "Failed to close client socket", e)
            }
        }
    }

    /**
     * 向所有已连接客机广播一条消息。
     */
    suspend fun broadcast(message: NetworkMessage) = withContext(Dispatchers.IO) {
        try {
            val jsonString = jsonConfig.encodeToString(message)
            synchronized(clientWriters) {
                val failedWriters = mutableListOf<PrintWriter>()
                clientWriters.forEach { writer ->
                    writer.println(jsonString)
                    if (writer.checkError()) {
                        failedWriters.add(writer)
                    }
                }
                clientWriters.removeAll(failedWriters)
            }
            Log.d(tag, "Broadcast message: $jsonString")
        } catch (e: Exception) {
            Log.e(tag, "Broadcast failed", e)
        }
    }

    /**
     * 只向指定玩家对应的客机发送一条消息。
     */
    suspend fun sendToPlayers(playerIds: Set<String>, message: NetworkMessage) = withContext(Dispatchers.IO) {
        try {
            val jsonString = jsonConfig.encodeToString(message)
            synchronized(clientWriters) {
                val failedWriters = mutableListOf<PrintWriter>()
                clientWriters.forEach { writer ->
                    if (writerPlayerIds[writer] in playerIds) {
                        writer.println(jsonString)
                        if (writer.checkError()) {
                            failedWriters.add(writer)
                        }
                    }
                }
                failedWriters.forEach {
                    clientWriters.remove(it)
                    writerPlayerIds.remove(it)
                }
            }
            Log.d(tag, "Send private message: $jsonString")
        } catch (e: Exception) {
            Log.e(tag, "Private send failed", e)
        }
    }

    /**
     * 停止接收连接，并关闭所有活跃输出流。
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Failed to close server socket", e)
        }
        synchronized(clientWriters) {
            clientWriters.forEach { it.close() }
            clientWriters.clear()
            writerPlayerIds.clear()
        }
    }

    /**
     * 返回房主 Socket 循环是否仍在运行。
     */
    fun isRunning(): Boolean = isRunning
}
