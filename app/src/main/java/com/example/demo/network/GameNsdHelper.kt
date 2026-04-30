package com.example.demo.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

data class NsdRoom(
    val ip: String,
    val port: Int,
    val roomName: String,
    val hostName: String,
    val currentPlayers: Int,
    val maxPlayers: Int
)

class GameNsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_boardgame._tcp." // 自定义的服务类型
    private val TAG = "GameNsdHelper"

    // 将发现的房间暴露为数据流，供 ViewModel 监听
    private val _discoveredRooms = MutableStateFlow<Map<String, NsdRoom>>(emptyMap())
    val discoveredRooms: StateFlow<Map<String, NsdRoom>> = _discoveredRooms.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // ================= 1. 作为房主：广播房间 =================
    fun startBroadcasting(roomName: String, hostName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = roomName
            serviceType = SERVICE_TYPE
            this.port = port
            // 利用 Attributes 直接把房间信息广播出去 (API 21+)
            setAttribute("hostName", hostName)
            setAttribute("currentPlayers", "1")
            setAttribute("maxPlayers", "6")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "成功广播房间: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "广播失败: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    // ================= 2. 作为玩家：扫描房间 =================
    fun startScanning() {
        _discoveredRooms.value = emptyMap() // 每次扫描前清空历史
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "开始扫描局域网...")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "发现服务: ${service.serviceName}")
                // 只解析我们自己类型的服务
                if (service.serviceType.contains("_boardgame._tcp")) {
                    // 解析服务以获取 IP 和 Attributes
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                            val ip = resolvedService.host.hostAddress ?: return
                            val port = resolvedService.port
                            
                            // 解析附加属性
                            val attributes = resolvedService.attributes
                            val hostName = attributes["hostName"]?.let { String(it, StandardCharsets.UTF_8) } ?: "Unknown"
                            val currentPlayers = attributes["currentPlayers"]?.let { String(it, StandardCharsets.UTF_8).toIntOrNull() } ?: 1
                            val maxPlayers = attributes["maxPlayers"]?.let { String(it, StandardCharsets.UTF_8).toIntOrNull() } ?: 6

                            val newRoom = NsdRoom(ip, port, resolvedService.serviceName, hostName, currentPlayers, maxPlayers)
                            
                            // 更新房间列表 (用 IP+Port 作为唯一 Key)
                            val key = "$ip:$port"
                            val updatedMap = _discoveredRooms.value.toMutableMap()
                            updatedMap[key] = newRoom
                            _discoveredRooms.value = updatedMap
                        }
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "解析服务失败: $errorCode")
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // 当房主关闭房间时，从列表中移除
                val updatedMap = _discoveredRooms.value.toMutableMap().apply {
                    val keyToRemove = entries.find { it.value.roomName == service.serviceName }?.key
                    if (keyToRemove != null) remove(keyToRemove)
                }
                _discoveredRooms.value = updatedMap
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopEverything() {
        try { registrationListener?.let { nsdManager.unregisterService(it) } } catch (e: Exception) {}
        try { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } } catch (e: Exception) {}
    }
}