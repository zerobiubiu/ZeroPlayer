package com.privateplayer.data.webupload

import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import com.privateplayer.domain.repository.WebUploadNetwork
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressDiscoveryTest {
    private fun ipv4(text: String) = InetAddress.getByAddress(text.split('.').map { it.toInt().toByte() }.toByteArray()) as Inet4Address

    private fun kind(vararg transports: Int) = lanTransport { it in transports }

    @Test
    fun wifiEthernetAndHotspotCoexistAndDuplicateAddressesPreferManagedLinks() {
        val wifi = ipv4("192.168.1.2")
        val secondary = ipv4("192.168.1.3")
        val wired = ipv4("10.0.1.2")
        val hotspot = ipv4("192.168.43.1")
        val links = listOf(
            LanLink("ap0", WebUploadNetwork.HOTSPOT, listOf(wifi, hotspot)),
            LanLink("eth0", kind(TRANSPORT_ETHERNET), listOf(wired, wifi)),
            LanLink("wlan0", kind(TRANSPORT_WIFI), listOf(wifi, secondary, wifi)),
        )
        val expected = listOf(
            LanAddress(WebUploadNetwork.WIFI, "wlan0", wifi),
            LanAddress(WebUploadNetwork.WIFI, "wlan0", secondary),
            LanAddress(WebUploadNetwork.ETHERNET, "eth0", wired),
            LanAddress(WebUploadNetwork.HOTSPOT, "ap0", hotspot),
        )
        assertEquals(expected, selectLanAddresses(links))
        assertEquals(expected, selectLanAddresses(links.reversed()))
    }

    @Test
    fun defaultVpnOrCellularDoesNotHideOfflineWifi() {
        val lan = ipv4("10.0.2.16")
        for (default in listOf(kind(TRANSPORT_VPN, TRANSPORT_WIFI), kind(TRANSPORT_CELLULAR))) {
            val links = listOf(
                LanLink("default", default, listOf(ipv4("10.8.0.1"))),
                LanLink("unusual-radio-name", kind(TRANSPORT_WIFI), listOf(lan)),
            )
            assertEquals(listOf(LanAddress(WebUploadNetwork.WIFI, "unusual-radio-name", lan)), selectLanAddresses(links))
        }
    }

    @Test
    fun unknownMixedOrConflictingNetworksCannotBeReclassifiedAsHotspots() {
        assertNull(kind())
        assertNull(kind(TRANSPORT_WIFI, TRANSPORT_ETHERNET))
        assertNull(kind(TRANSPORT_WIFI, TRANSPORT_CELLULAR))
        assertNull(kind(TRANSPORT_ETHERNET, TRANSPORT_BLUETOOTH))
        assertNull(kind(TRANSPORT_WIFI, 19))
        val shared = ipv4("192.168.43.1")
        val other = ipv4("192.168.43.2")
        val links = listOf(
            LanLink("ap0", WebUploadNetwork.HOTSPOT, listOf(other)),
            LanLink("ap0", kind(TRANSPORT_CELLULAR), listOf(shared)),
            LanLink("softap0", WebUploadNetwork.HOTSPOT, listOf(shared)),
            LanLink("tun0", kind(TRANSPORT_VPN, TRANSPORT_WIFI), listOf(shared)),
            LanLink("ap0", kind(TRANSPORT_WIFI), listOf(other)),
            LanLink(null, kind(TRANSPORT_WIFI), listOf(other)),
            LanLink("eth0", null, listOf(shared)),
        )
        assertTrue(selectLanAddresses(links).isEmpty())
        assertTrue(selectLanAddresses(emptyList()).isEmpty())
        assertTrue(selectLanAddresses(listOf(
            LanLink("wlan0", WebUploadNetwork.WIFI, emptyList()),
            LanLink("wlan0", WebUploadNetwork.HOTSPOT, listOf(other)),
        )).isEmpty())
    }

    @Test
    fun fallbackRequiresExplicitApIdentityOrSystemWifiTetherAndUsableFlags() {
        fun allowed(name: String, reported: Boolean? = null, up: Boolean = true, loopback: Boolean = false,
                    pointToPoint: Boolean = false, virtual: Boolean = false) =
            isHotspotInterface(name, reported, up, loopback, pointToPoint, virtual)

        listOf("ap0", "softap0", "wlan0-ap", "ap_br_wlan1").forEach { assertTrue(it, allowed(it)) }
        listOf("wlan0", "wlan1", "eth0", "en0", "wifi0", "tun0", "rmnet0", "apple0", "p2p0", "lo").forEach {
            assertFalse(it, allowed(it))
        }
        assertTrue(allowed("wlan0", reported = true))
        assertFalse(allowed("ap0", reported = false))
        assertFalse(allowed("ap0", up = false))
        assertFalse(allowed("ap0", loopback = true))
        assertFalse(allowed("ap0", pointToPoint = true))
        assertFalse(allowed("ap0", virtual = true))
    }

    @Test
    fun onlyUsableLocalUnicastHostAddressesQualify() {
        listOf("10.0.0.1", "172.16.0.1", "192.168.0.1", "169.254.1.8").forEach {
            val ip = ipv4(it)
            assertEquals(ip, localUnicastIpv4(ip, 24))
        }
        listOf("0.0.0.0", "127.0.0.1", "224.0.0.1", "255.255.255.255", "8.8.8.8", "100.64.0.1",
            "192.168.1.0", "192.168.1.255", "169.254.0.1", "169.254.255.2").forEach {
            assertNull(it, localUnicastIpv4(ipv4(it), 24))
        }
        assertNull(localUnicastIpv4(InetAddress.getByAddress(ByteArray(16)), 64))
        val host = ipv4("10.0.0.3")
        assertNull(localUnicastIpv4(host, 30))
        assertNull(localUnicastIpv4(host, 0))
        assertNull(localUnicastIpv4(host, 33))
        assertEquals(host, localUnicastIpv4(host, 31))
        assertEquals(host, localUnicastIpv4(host, 32))
    }
}
