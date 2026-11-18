package com.privateplayer.data.webupload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.TetheringInterface
import android.net.TetheringManager
import android.os.Build
import android.system.OsConstants
import androidx.annotation.RequiresApi
import com.privateplayer.domain.repository.WebUploadNetwork
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal data class LanAddress(val kind: WebUploadNetwork, val interfaceName: String, val address: Inet4Address)

/** kind 为空的系统链路也要保留：地址绑定不能区分持有相同 IP 的两个接口。 */
internal data class LanLink(val interfaceName: String?, val kind: WebUploadNetwork?, val addresses: List<Inet4Address>)

/** 用户每次启动只取一次快照；不依赖默认网络、上网能力或网络验证。 */
@Suppress("DEPRECATION") // getAllNetworks 用于一次性枚举，不为临时文件服务保留长期 NetworkCallback。
internal suspend fun discoverLanAddresses(context: Context): List<LanAddress> {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val tethered = if (Build.VERSION.SDK_INT >= 36) wifiTetherSnapshot(context) else emptyMap()
    val links = mutableListOf<LanLink>()
    val unusable = OsConstants.IFA_F_DADFAILED or OsConstants.IFA_F_DEPRECATED or OsConstants.IFA_F_TENTATIVE
    for (network in connectivity.allNetworks) {
        val properties = connectivity.getLinkProperties(network) ?: continue
        val kind = connectivity.getNetworkCapabilities(network)?.let { lanTransport(it::hasTransport) }
        val addresses = properties.linkAddresses.mapNotNull {
            if (kind == null) it.address as? Inet4Address // 地址绑定不能绕过不可信链路的所有权。
            else if (it.flags and unusable != 0) null else localUnicastIpv4(it.address, it.prefixLength)
        }
        links += LanLink(properties.interfaceName, kind, addresses)
    }
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()
    } catch (_: SocketException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: NullPointerException) {
        // API 30 及以下在虚拟接口父接口已消失时可抛出此异常；不影响已取得的系统链路。
        null
    }
    if (interfaces != null) {
        for (iface in interfaces) {
            try {
                val name = iface.name
                if (!isHotspotInterface(name, tethered[name], iface.isUp, iface.isLoopback, iface.isPointToPoint, iface.isVirtual)) continue
                val addresses = iface.interfaceAddresses.mapNotNull {
                    localUnicastIpv4(it.address, it.networkPrefixLength.toInt())?.takeIf(Inet4Address::isSiteLocalAddress)
                }
                links += LanLink(name, WebUploadNetwork.HOTSPOT, addresses)
            } catch (_: SocketException) {
                // 接口可能在快照期间关闭，继续处理其它接口。
            } catch (_: SecurityException) {
                // 无权查看此下游接口，不猜测它的地址。
            }
        }
    }
    return selectLanAddresses(links)
}

/** 新系统能直接指明 Wi-Fi 下游（其名称也可能是 wlan0）；只等首次通知，退出即注销。 */
@RequiresApi(36)
private suspend fun wifiTetherSnapshot(context: Context): Map<String, Boolean> {
    val tethering = context.getSystemService(TetheringManager::class.java) ?: return emptyMap()
    val first = CompletableDeferred<Map<String, Boolean>>()
    val callback = object : TetheringManager.TetheringEventCallback {
        override fun onTetheredInterfacesChanged(interfaces: Set<TetheringInterface>) {
            first.complete(interfaces.associate { it.getInterface() to (it.type == TetheringManager.TETHERING_WIFI) })
        }
    }
    try {
        tethering.registerTetheringEventCallback({ it.run() }, callback)
    } catch (_: RuntimeException) {
        return emptyMap()
    }
    return try {
        withTimeoutOrNull(1_000L) { first.await() } ?: emptyMap()
    } finally {
        try {
            tethering.unregisterTetheringEventCallback(callback)
        } catch (_: RuntimeException) {
            // 连通性服务可能已退出；不妨碍使用独立取得的 Wi-Fi、以太网快照。
        }
    }
}

internal inline fun lanTransport(hasTransport: (Int) -> Boolean): WebUploadNetwork? {
    val wifi = hasTransport(TRANSPORT_WIFI)
    val ethernet = hasTransport(TRANSPORT_ETHERNET)
    if (wifi == ethernet) return null
    // NetworkCapabilities 没有公开枚举 transport 的 API；hasTransport 对未知位返回 false。
    // 只接受单一物理 transport，VPN 即使同时报告其底层 Wi-Fi 也不会入选。
    for (transport in 0 until Long.SIZE_BITS) {
        if (transport != TRANSPORT_WIFI && transport != TRANSPORT_ETHERNET && hasTransport(transport)) return null
    }
    return if (wifi) WebUploadNetwork.WIFI else WebUploadNetwork.ETHERNET
}

private val softApName = Regex("(?:ap[0-9]+|softap[0-9]*|wlan[0-9]+[-_]ap[0-9]*|ap_br_(?:wlan|ap)[0-9]+)")

internal fun isHotspotInterface(name: String, reportedWifi: Boolean?, up: Boolean, loopback: Boolean, pointToPoint: Boolean, virtual: Boolean): Boolean =
    up && !loopback && !pointToPoint && !virtual && (reportedWifi ?: softApName.matches(name))

internal fun localUnicastIpv4(address: InetAddress, prefix: Int): Inet4Address? {
    if (address !is Inet4Address || prefix !in 1..32 || (!address.isSiteLocalAddress && !address.isLinkLocalAddress)) return null
    val bytes = address.address
    if (address.isLinkLocalAddress && (bytes[2].toInt() and 255) !in 1..254) return null // RFC 3927 保留两端 /24。
    if (prefix < 31) {
        var ip = 0
        for (byte in bytes) ip = (ip shl 8) or (byte.toInt() and 255)
        val hostMask = -1 ushr prefix
        val host = ip and hostMask
        if (host == 0 || host == hostMask) return null
    }
    return address
}

internal fun selectLanAddresses(links: List<LanLink>): List<LanAddress> {
    val managedNames = HashSet<String>()
    val blockedNames = HashSet<String>()
    val blockedAddresses = HashSet<Inet4Address>()
    for (link in links) {
        if (link.kind != WebUploadNetwork.HOTSPOT) link.interfaceName?.let(managedNames::add)
        if (link.kind == null) {
            link.interfaceName?.let(blockedNames::add)
            blockedAddresses.addAll(link.addresses)
        }
    }
    val result = mutableListOf<LanAddress>()
    val seen = HashSet<Inet4Address>()
    for (link in links.sortedWith(compareBy({ it.kind }, { it.interfaceName }))) {
        val kind = link.kind ?: continue
        val name = link.interfaceName ?: continue
        if (name.isBlank() || name in blockedNames || (kind == WebUploadNetwork.HOTSPOT && name in managedNames)) continue
        for (address in link.addresses) {
            if (address !in blockedAddresses && seen.add(address)) result += LanAddress(kind, name, address)
        }
    }
    return result
}
