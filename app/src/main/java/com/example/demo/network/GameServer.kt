package com.example.demo.network

import android.util.Log
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

class GameServer(
    private val port: Int = 9999,
    private val onMessageReceived: (NetworkMessage) -> Unit // 当收到任何玩家消息时的回调
) {
    private val TAG = "GameServer"
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    // 保存所有连进来的玩家的输出流，方便群发消息
    private val clientWriters = mutableListOf<PrintWriter>()

    // 配置 JSON 解析器（忽略未知字段，避免崩溃）
    private val jsonConfig = Json { ignoreUnknownKeys = true }

    // 启动服务器（必须在协程的 IO 线程调用）
    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true

        try {
            serverSocket = ServerSocket(port)
            Log.d(TAG, "服务器已启动，正在监听端口 $port...")

            while (isRunning) {
                val clientSocket = serverSocket?.accept() ?: break
                Log.d(TAG, "新玩家已连接")

                // 【关键修复】：为每个连进来的玩家启动独立协程，不要阻塞主循环
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    handleClient(clientSocket)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "服务器异常: ${e.message}")
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val writer = PrintWriter(socket.getOutputStream(), true)
            
            // 将新玩家的输出流加入集合
            synchronized(clientWriters) { clientWriters.add(writer) }

            // 死循环监听该玩家发来的消息
            while (isRunning) {
                val jsonString = reader.readLine() ?: break // 如果读到 null，说明玩家断开了
                Log.d(TAG, "收到消息: $jsonString")
                
                // 将 JSON 字符串反序列化为 NetworkMessage 对象，并传给 ViewModel
                try {
                    val message = jsonConfig.decodeFromString<NetworkMessage>(jsonString)
                    onMessageReceived(message)
                } catch (e: Exception) {
                    Log.e(TAG, "消息解析失败: $jsonString", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "玩家连接断开")
        } finally {
            socket.close()
        }
    }

    // 房主专用：将消息广播给房间里的所有人（包括自己）
    suspend fun broadcast(message: NetworkMessage) = withContext(Dispatchers.IO) {
        try {
            val jsonString = jsonConfig.encodeToString(message)
            synchronized(clientWriters) {
                clientWriters.forEach { writer ->
                    writer.println(jsonString)
                }
            }
            Log.d(TAG, "广播消息: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "广播失败", e)
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        clientWriters.clear()
    }
}