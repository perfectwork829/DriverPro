package com.driver.pro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.net.ssl.SSLHandshakeException

class FriendlyNetworkMessageTest {

    @Test
    fun sslCarrierCertificate_isShortAndDoesNotDumpSans() {
        val e = SSLHandshakeException(
            "No server host: idrivesmart.co.uk in the server certificate. " +
                "Provided in certificate: *.t-mobile.pl, *.eglb.t-mobile.pl, " +
                "*.esvc.t-mobile.pl, t-mobile.pl",
        )
        val msg = friendlyNetworkMessage(e)
        assertTrue(msg.contains("idrivesmart.co.uk"))
        assertTrue(msg.contains("Wi", ignoreCase = true) || msg.contains("retry", ignoreCase = true))
        assertFalse(msg.contains("t-mobile", ignoreCase = true))
        assertFalse(msg.startsWith("API call failed:"))
    }

    @Test
    fun unknownHost_isReachabilityMessage() {
        val msg = friendlyNetworkMessage(java.net.UnknownHostException("Unable to resolve host idrivesmart.co.uk"))
        assertTrue(msg.contains("idrivesmart.co.uk"))
        assertFalse(msg.contains("Unable to resolve", ignoreCase = true))
    }

    @Test
    fun genericError_keepsApiPrefix() {
        val msg = friendlyNetworkMessage(IllegalStateException("Ride request data missing"))
        assertEquals("API call failed: Ride request data missing", msg)
    }

    @Test
    fun wrapApiFailure_usesFriendlyMessage() {
        val wrapped = wrapApiFailure(
            SSLHandshakeException("No server host: idrivesmart.co.uk in the server certificate."),
        )
        assertEquals(
            "Cannot reach idrivesmart.co.uk on this connection (HTTPS blocked or intercepted). Try Wi‑Fi, then retry.",
            wrapped.message,
        )
    }

    @Test
    fun ipv4AddressesAreTriedFirst() {
        val v6 = InetAddress.getByName("2001:db8::1") as Inet6Address
        val v4a = InetAddress.getByName("93.184.216.34") as Inet4Address
        val v4b = InetAddress.getByName("1.1.1.1") as Inet4Address
        val ordered = orderAddressesIpv4First(listOf(v6, v4a, v4b))
        assertTrue(ordered[0] is Inet4Address)
        assertTrue(ordered[1] is Inet4Address)
        assertTrue(ordered[2] is Inet6Address)
        assertEquals(3, ordered.size)
    }

    @Test
    fun ipv6OnlyList_isUnchanged() {
        val v6 = InetAddress.getByName("2001:db8::1")
        assertEquals(listOf(v6), orderAddressesIpv4First(listOf(v6)))
    }
}
