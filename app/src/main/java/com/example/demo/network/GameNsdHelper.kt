package com.example.demo.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Android NSD 解析出的原始房间广播信息。
 */
data class NsdRoom(
    val ip: String,
    val port: Int,
    val roomName: String,
    val hostName: String,
    val currentPlayers: Int,
    val maxPlayers: Int
)

/**
 * 封装 Android 网络服务发现，用于房间广播和扫描。
 */
class GameNsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_boardgame._tcp."
    private val TAG = "GameNsdHelper"

    private val _discoveredRooms = MutableStateFlow<Map<String, NsdRoom>>(emptyMap())
    val discoveredRooms: StateFlow<Map<String, NsdRoom>> = _discoveredRooms.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * 将房主房间注册为可发现的 DNS-SD 服务。
     */
    fun startBroadcasting(roomName: String, hostName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = roomName
            serviceType = SERVICE_TYPE
            this.port = port

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

    /**
     * 扫描局域网，并通过 discoveredRooms 输出已解析房间。
     */
    fun startScanning() {
        _discoveredRooms.value = emptyMap()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "开始扫描局域网...")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "发现服务: ${service.serviceName}")

                if (service.serviceType.contains("_boardgame._tcp")) {

                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                            val ip = resolvedService.host.hostAddress ?: return
                            val port = resolvedService.port

                            val attributes = resolvedService.attributes
                            val hostName = attributes["hostName"]?.let { String(it, StandardCharsets.UTF_8) } ?: "Unknown"
                            val currentPlayers = attributes["currentPlayers"]?.let { String(it, StandardCharsets.UTF_8).toIntOrNull() } ?: 1
                            val maxPlayers = attributes["maxPlayers"]?.let { String(it, StandardCharsets.UTF_8).toIntOrNull() } ?: 6

                            val newRoom = NsdRoom(ip, port, resolvedService.serviceName, hostName, currentPlayers, maxPlayers)

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

    /**
     * 停止房间扫描和房间广播监听器。
     */
    fun stopEverything() {
        try { registrationListener?.let { nsdManager.unregisterService(it) } } catch (e: Exception) {}
        try { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } } catch (e: Exception) {}
    }
}
