package com.scanvault.app.data.model

sealed class ScanContent {
    abstract val categoryName: String

    data class Url(
        val fullUrl: String,
        val host: String,
        val scheme: String,
        val isHttps: Boolean,
        val isSuspicious: Boolean,
        val warningMessage: String? = null
    ) : ScanContent() {
        override val categoryName = "Website URL"
    }

    data class Wifi(
        val ssid: String,
        val authType: String,
        val isHidden: Boolean,
        val passwordMasked: String,
        val rawPassword: String // Kept strictly local, never transmitted
    ) : ScanContent() {
        override val categoryName = "Wi-Fi Network"
    }

    data class Contact(
        val name: String,
        val phone: String?,
        val email: String?,
        val organization: String?,
        val address: String?
    ) : ScanContent() {
        override val categoryName = "Contact / vCard"
    }

    data class Email(
        val recipient: String,
        val subject: String?,
        val body: String?
    ) : ScanContent() {
        override val categoryName = "Email Address"
    }

    data class Phone(
        val phoneNumber: String
    ) : ScanContent() {
        override val categoryName = "Phone Number"
    }

    data class Sms(
        val recipient: String,
        val messageBody: String?
    ) : ScanContent() {
        override val categoryName = "SMS Message"
    }

    data class Geo(
        val latitude: Double,
        val longitude: Double,
        val query: String? = null
    ) : ScanContent() {
        override val categoryName = "Geographic Location"
    }

    data class Product(
        val barcode: String,
        val format: BarcodeFormatType,
        val isChecksumValid: Boolean,
        val checksumDetails: String? = null
    ) : ScanContent() {
        override val categoryName = "Product Barcode"
    }

    data class PlainText(
        val text: String
    ) : ScanContent() {
        override val categoryName = "Plain Text"
    }
}
