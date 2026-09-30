package lk.payments.sdk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentsLkCheckoutTest {

    private fun allowed(url: String) = PaymentsLkCheckout.isAllowedCheckoutUrl(url)

    @Test
    fun allowsHttps() {
        assertTrue(allowed("https://checkout.example.lk/c/chk_abc123"))
        assertTrue(allowed("https://checkout.example.lk:8443/c/chk_abc123?lang=si#top"))
        assertTrue(allowed("HTTPS://checkout.example.lk/c/chk_abc123"))
    }

    @Test
    fun rejectsHttpsWithUserInfo() {
        assertFalse(allowed("https://checkout.example.lk@evil.example/c/chk_abc123"))
        assertFalse(allowed("https://user:secret@checkout.example.lk/c/chk_abc123"))
        assertFalse(allowed("https://@checkout.example.lk/c/chk_abc123"))
    }

    @Test
    fun allowsHttpOnLocalDevelopmentHosts() {
        assertTrue(allowed("http://localhost:3311/c/chk_abc123"))
        assertTrue(allowed("http://LOCALHOST/c/chk_abc123"))
        assertTrue(allowed("http://127.0.0.1:3311/c/chk_abc123"))
        assertTrue(allowed("http://10.0.2.2:3311/c/chk_abc123"))
    }

    @Test
    fun rejectsHttpElsewhere() {
        assertFalse(allowed("http://checkout.example.lk/c/chk_abc123"))
        assertFalse(allowed("http://localhost.evil.example/c/chk_abc123"))
        assertFalse(allowed("http://127.0.0.1.evil.example/c/chk_abc123"))
        assertFalse(allowed("http://10.0.2.20/c/chk_abc123"))
        assertFalse(allowed("http://192.168.1.10/c/chk_abc123"))
    }

    @Test
    fun rejectsHttpLocalHostWithUserInfo() {
        assertFalse(allowed("http://evil@localhost/c/chk_abc123"))
    }

    @Test
    fun rejectsOtherSchemes() {
        assertFalse(allowed("javascript:alert(1)"))
        assertFalse(allowed("intent://checkout#Intent;scheme=https;end"))
        assertFalse(allowed("file:///sdcard/checkout.html"))
        assertFalse(allowed("content://lk.payments/checkout"))
        assertFalse(allowed("myshop://payments-lk/return"))
        assertFalse(allowed("ftp://checkout.example.lk/"))
        assertFalse(allowed("data:text/html,hi"))
    }

    @Test
    fun rejectsMalformedUrls() {
        assertFalse(allowed(""))
        assertFalse(allowed("not a url"))
        assertFalse(allowed("https://"))
        assertFalse(allowed("https:///c/chk_abc123"))
        assertFalse(allowed("//checkout.example.lk/c/chk_abc123"))
        assertFalse(allowed(" https://checkout.example.lk/c/chk_abc123"))
        assertFalse(allowed("https://checkout.example.lk\\@evil.example/"))
        assertFalse(allowed("https:checkout.example.lk"))
    }
}
