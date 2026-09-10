package com.shohan.hotlink.util

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getLocalIpAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore, return whatever was found so far
        }
        return result
    }
}
