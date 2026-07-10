package com.touchlink.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int
)

class Discovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var activeDiscovery: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onDeviceFound: (DiscoveredDevice) -> Unit) {
        val listener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val device = DiscoveredDevice(
                            name = info.serviceName,
                            host = info.host?.hostAddress ?: return,
                            port = info.port
                        )
                        onDeviceFound(device)
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        // Silently ignore resolve failures
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        activeDiscovery = listener
        nsdManager.discoverServices(
            "_touchlink._tcp",
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )
    }

    fun stopDiscovery() {
        activeDiscovery?.let { nsdManager.stopServiceDiscovery(it) }
        activeDiscovery = null
    }
}
