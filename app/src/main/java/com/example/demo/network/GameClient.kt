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
import java.net.Socket

class GameClient(
    private val onMessageReceived: (NetworkMessage) -> Unit // 当收到房主广播时的回调
) {
    private val TAG = "GameClient"
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var isConnected = false

    private val jsonConfig = Json { ignoreUnknownKeys = true }

    // 连接到房主的 IP 和端口
    suspend fun connect(ip: String, port: Int = 9999): Boolean = withContext(Dispatchers.IO) {
        try {
            // 增加 5 秒超时限制，防止一直卡死
            val socketAddress = java.net.InetSocketAddress(ip, port)
            val newSocket = java.net.Socket()
            newSocket.connect(socketAddress, 5000)

            socket = newSocket
            writer = java.io.PrintWriter(newSocket.getOutputStream(), true)
            isConnected = true

            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                listenToServer()
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Socket 连接发生致命错误: ${e.message}")
            return@withContext false
        }
    }

    private suspend fun listenToServer() = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"))
            while (isConnected) {
                val jsonString = reader.readLine() ?: break
                Log.d(TAG, "收到房主广播: $jsonString")
                
                try {
                    val message = jsonConfig.decodeFromString<NetworkMessage>(jsonString)
                    onMessageReceived(message)
                } catch (e: Exception) {
                    Log.e(TAG, "客户端消息解析失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "与房主连接断开")
        } finally {
            disconnect()
        }
    }

    // 玩家用来向房主发送请求（比如提交暗杀、投票）
    suspend fun sendMessage(message: NetworkMessage) = withContext(Dispatchers.IO) {
        if (!isConnected || writer == null) {
            Log.w(TAG, "尚未连接，无法发送消息")
            return@withContext
        }
        try {
            val jsonString = jsonConfig.encodeToString(message)
            writer?.println(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败", e)
        }
    }

    fun disconnect() {
        isConnected = false
        writer?.close()
        socket?.close()
    }
}