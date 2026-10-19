package com.scanvault.app

import com.scanvault.app.data.model.BarcodeFormatType
import com.scanvault.app.data.model.ScanContent
import com.scanvault.app.data.parser.BarcodePayloadParser
import org.junit.Assert.*
import org.junit.Test

class BarcodePayloadParserTest {

    @Test
    fun testValidEan13Checksum() {
        // Valid EAN-13 barcode: 5901234123457
        val isValid = BarcodePayloadParser.validateEan13Checksum("5901234123457")
        assertTrue("Expected valid EAN-13 checksum", isValid)

        // Invalid check digit: change last digit to 8
        val isInvalid = BarcodePayloadParser.validateEan13Checksum("5901234123458")
        assertFalse("Expected invalid EAN-13 check digit", isInvalid)
    }

    @Test
    fun testValidUpcAChecksum() {
        // Valid UPC-A barcode: 012345678905
        val isValid = BarcodePayloadParser.validateUpcAChecksum("012345678905")
        assertTrue("Expected valid UPC-A checksum", isValid)

        // Invalid UPC-A check digit
        val isInvalid = BarcodePayloadParser.validateUpcAChecksum("012345678904")
        assertFalse("Expected invalid UPC-A check digit", isInvalid)
    }

    @Test
    fun testValidEan8Checksum() {
        // Valid EAN-8 barcode: 96385074
        val isValid = BarcodePayloadParser.validateEan8Checksum("96385074")
        assertTrue("Expected valid EAN-8 checksum", isValid)
    }

    @Test
    fun testUrlClassificationAndSecurity() {
        // HTTPS secure URL
        val secureUrl = BarcodePayloadParser.parse("https://google.com/search?q=test", BarcodeFormatType.QR_CODE)
        assertTrue(secureUrl is ScanContent.Url)
        val url = secureUrl as ScanContent.Url
        assertEquals("google.com", url.host)
        assertTrue(url.isHttps)
        assertFalse(url.isSuspicious)

        // Insecure HTTP URL
        val httpUrl = BarcodePayloadParser.parse("http://example.com/login", BarcodeFormatType.QR_CODE)
        assertTrue(httpUrl is ScanContent.Url)
        val http = httpUrl as ScanContent.Url
        assertFalse(http.isHttps)
        assertNotNull(http.warningMessage)

        // Suspicious IP Address host
        val ipUrl = BarcodePayloadParser.parse("http://192.168.1.50/admin", BarcodeFormatType.QR_CODE)
        assertTrue(ipUrl is ScanContent.Url)
        val suspicious = ipUrl as ScanContent.Url
        assertTrue(suspicious.isSuspicious)
    }

    @Test
    fun testWifiPayloadParsing() {
        val rawWifi = "WIFI:S:MyHomeWifi;T:WPA;P:SuperSecret123;H:false;;"
        val parsed = BarcodePayloadParser.parse(rawWifi, BarcodeFormatType.QR_CODE)
        assertTrue(parsed is ScanContent.Wifi)
        val wifi = parsed as ScanContent.Wifi
        assertEquals("MyHomeWifi", wifi.ssid)
        assertEquals("WPA", wifi.authType)
        assertFalse(wifi.isHidden)
        assertEquals("SuperSecret123", wifi.rawPassword)
        assertTrue(wifi.passwordMasked.contains("•"))
    }

    @Test
    fun testContactVCardParsing() {
        val rawVcard = "BEGIN:VCARD\nVERSION:3.0\nFN:Alex Vance\nTEL:+1555019283\nEMAIL:alex@example.com\nEND:VCARD"
        val parsed = BarcodePayloadParser.parse(rawVcard, BarcodeFormatType.QR_CODE)
        assertTrue(parsed is ScanContent.Contact)
        val contact = parsed as ScanContent.Contact
        assertEquals("Alex Vance", contact.name)
        assertEquals("+1555019283", contact.phone)
        assertEquals("alex@example.com", contact.email)
    }

    @Test
    fun testPlainTextFallback() {
        val text = "Lorem ipsum dolor sit amet, barcode test sample."
        val parsed = BarcodePayloadParser.parse(text, BarcodeFormatType.QR_CODE)
        assertTrue(parsed is ScanContent.PlainText)
        assertEquals(text, (parsed as ScanContent.PlainText).text)
    }
}
