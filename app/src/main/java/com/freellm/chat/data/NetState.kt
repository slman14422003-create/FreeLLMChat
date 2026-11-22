package com.freellm.chat.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class NetState(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Volatile var network: Network? = null
    @Volatile var vpn: Boolean = false
    @Volatile var validated: Boolean = false
    @Volatile var mode: String = "auto"

    private var registered = false

    fun start(onChange: () -> Unit) {
        if (registered) return
        registered = true
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    this@NetState.network = network
                    onChange()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val nowVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    val nowValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val changed = nowVpn != vpn || network != this@NetState.network
                    this@NetState.network = network
                    vpn = nowVpn
                    validated = nowValidated
                    if (changed) onChange()
                }

                override fun onLost(network: Network) {
                    this@NetState.network = null
                    vpn = false
                }
            })
        }
    }

    private inner class BoundSocketFactory : SocketFactory() {
        private fun base(): SocketFactory = network?.socketFactory ?: SocketFactory.getDefault()

        override fun createSocket(): Socket = base().createSocket()

        override fun createSocket(host: String, port: Int): Socket = base().createSocket(host, port)

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            base().createSocket(host, port, localHost, localPort)

        override fun createSocket(host: InetAddress, port: Int): Socket = base().createSocket(host, port)

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            base().createSocket(address, port, localAddress, localPort)
    }

    val socketFactory: SocketFactory = BoundSocketFactory()
}

class VpnGuard(private val net: NetState) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (net.mode == "vpn_only" && !net.vpn && !LanGuard.isPrivateHost(chain.request().url.host)) {
            throw IOException("vpn required")
        }
        return chain.proceed(chain.request())
    }
}
