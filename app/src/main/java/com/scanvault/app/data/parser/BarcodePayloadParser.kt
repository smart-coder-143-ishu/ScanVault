package com.scanvault.app.data.parser

import android.net.Uri
import com.scanvault.app.data.model.BarcodeFormatType
import com.scanvault.app.data.model.ScanContent
import java.net.URI
import java.util.Locale

object BarcodePayloadParser {

    fun parse(rawValue: String, format: BarcodeFormatType): ScanContent {
        val trimmed = rawValue.trim()

        // 1. Check Product Barcode formats first if 1D product barcode
        if (format == BarcodeFormatType.EAN_13 ||
            format == BarcodeFormatType.EAN_8 ||
            format == BarcodeFormatType.UPC_A ||
            format == BarcodeFormatType.UPC_E
        ) {
            val checksumValid = validateChecksum(trimmed, format)
            val details = if (checksumValid) "Valid Modulo-10 Checksum" else "Invalid Checksum"
            return ScanContent.Product(
                barcode = trimmed,
                format = format,
                isChecksumValid = checksumValid,
                checksumDetails = details
            )
        }

        // 2. Wi-Fi network payload (WIFI:S:ssid;T:WPA;P:password;H:false;;)
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            return parseWifiPayload(trimmed)
        }

        // 3. Contact (vCard or MECARD)
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) ||
            trimmed.startsWith("MECARD:", ignoreCase = true)
        ) {
            return parseContactPayload(trimmed)
        }

        // 4. Email (mailto: or MATMSG:)
        if (trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("MATMSG:", ignoreCase = true)
        ) {
            return parseEmailPayload(trimmed)
        }

        // 5. Telephone (tel:)
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val number = trimmed.substring(4)
            return ScanContent.Phone(number)
        }

        // 6. SMS (smsto: or sms:)
        if (trimmed.startsWith("smsto:", ignoreCase = true) ||
            trimmed.startsWith("sms:", ignoreCase = true)
        ) {
            return parseSmsPayload(trimmed)
        }

        // 7. Geo (geo:lat,long)
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            return parseGeoPayload(trimmed)
        }

        // 8. Website URL check
        if (isLikelyUrl(trimmed)) {
            return parseUrlPayload(trimmed)
        }

        // 9. If digits only and matches 1D lengths, treat as product barcode
        if (trimmed.matches(Regex("^\\d{8}\$")) && format != BarcodeFormatType.QR_CODE) {
            val valid = validateEan8Checksum(trimmed)
            return ScanContent.Product(trimmed, BarcodeFormatType.EAN_8, valid, if (valid) "Valid EAN-8" else "Invalid checksum")
        }
        if (trimmed.matches(Regex("^\\d{12}\$")) && format != BarcodeFormatType.QR_CODE) {
            val valid = validateUpcAChecksum(trimmed)
            return ScanContent.Product(trimmed, BarcodeFormatType.UPC_A, valid, if (valid) "Valid UPC-A" else "Invalid checksum")
        }
        if (trimmed.matches(Regex("^\\d{13}\$")) && format != BarcodeFormatType.QR_CODE) {
            val valid = validateEan13Checksum(trimmed)
            return ScanContent.Product(trimmed, BarcodeFormatType.EAN_13, valid, if (valid) "Valid EAN-13" else "Invalid checksum")
        }

        // 10. Fallback: Plain text
        return ScanContent.PlainText(trimmed)
    }

    private fun isLikelyUrl(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("ftp://") ||
                (lower.contains(".") && !lower.contains(" ") &&
                        (lower.endsWith(".com") || lower.endsWith(".org") ||
                                lower.endsWith(".io") || lower.endsWith(".net") ||
                                lower.endsWith(".gov") || lower.endsWith(".edu") ||
                                lower.endsWith(".app") || lower.endsWith(".ai")))
    }

    fun parseUrlPayload(urlStr: String): ScanContent.Url {
        val completeUrl = if (!urlStr.startsWith("http://", ignoreCase = true) &&
            !urlStr.startsWith("https://", ignoreCase = true) &&
            !urlStr.startsWith("ftp://", ignoreCase = true)
        ) {
            "https://$urlStr"
        } else {
            urlStr
        }

        return try {
            val uri = URI(completeUrl)
            val scheme = uri.scheme ?: "unknown"
            val host = uri.host ?: uri.authority ?: "unknown host"
            val isHttps = scheme.equals("https", ignoreCase = true)

            var isSuspicious = false
            var warning: String? = null

            // Security warning heuristic
            if (scheme.equals("http", ignoreCase = true)) {
                warning = "Insecure connection (HTTP). Data transmitted is not encrypted."
            } else if (!isHttps) {
                isSuspicious = true
                warning = "Potentially dangerous URL scheme: $scheme"
            }

            // Check for raw IP address in URL
            if (host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}\$"))) {
                isSuspicious = true
                warning = "Warning: URL points directly to an IP address ($host) rather than a verified domain name."
            }

            // Check for deceptive subdomain abuse
            val parts = host.split(".")
            if (parts.size >= 4 && (host.contains("login") || host.contains("secure") || host.contains("verify") || host.contains("paypal") || host.contains("apple"))) {
                val registeredDomain = parts.takeLast(2).joinToString(".")
                if (!registeredDomain.contains("paypal") && !registeredDomain.contains("apple")) {
                    isSuspicious = true
                    warning = "Phishing alert: Subdomain mimics a sensitive brand but parent domain is $registeredDomain."
                }
            }

            ScanContent.Url(
                fullUrl = completeUrl,
                host = host,
                scheme = scheme,
                isHttps = isHttps,
                isSuspicious = isSuspicious,
                warningMessage = warning
            )
        } catch (_: Exception) {
            ScanContent.Url(
                fullUrl = completeUrl,
                host = "Unparseable Host",
                scheme = "unknown",
                isHttps = false,
                isSuspicious = true,
                warningMessage = "Malformed URL format. Proceed with extreme caution."
            )
        }
    }

    private fun parseWifiPayload(raw: String): ScanContent.Wifi {
        // Format: WIFI:S:MySSID;T:WPA;P:MyPassword;H:false;;
        var ssid = ""
        var type = "WPA"
        var password = ""
        var hidden = false

        val tokens = raw.removePrefix("WIFI:").removeSuffix(";;").removeSuffix(";").split(";")
        for (token in tokens) {
            when {
                token.startsWith("S:") -> ssid = token.removePrefix("S:")
                token.startsWith("T:") -> type = token.removePrefix("T:")
                token.startsWith("P:") -> password = token.removePrefix("P:")
                token.startsWith("H:") -> hidden = token.removePrefix("H:").equals("true", ignoreCase = true)
            }
        }

        val maskedPassword = if (password.isNotEmpty()) {
            "•".repeat(password.length.coerceAtMost(16))
        } else {
            "(Open network / No password)"
        }

        return ScanContent.Wifi(
            ssid = if (ssid.isNotEmpty()) ssid else "Unnamed Network",
            authType = type,
            isHidden = hidden,
            passwordMasked = maskedPassword,
            rawPassword = password
        )
    }

    private fun parseContactPayload(raw: String): ScanContent.Contact {
        var name = "Contact"
        var phone: String? = null
        var email: String? = null
        var org: String? = null
        var address: String? = null

        val lines = raw.lines()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("FN:", ignoreCase = true) -> name = trimmed.substring(3).trim()
                trimmed.startsWith("N:", ignoreCase = true) && name == "Contact" -> {
                    name = trimmed.substring(2).replace(";", " ").trim()
                }
                trimmed.startsWith("TEL", ignoreCase = true) -> {
                    val idx = trimmed.indexOf(":")
                    if (idx != -1) phone = trimmed.substring(idx + 1).trim()
                }
                trimmed.startsWith("EMAIL", ignoreCase = true) -> {
                    val idx = trimmed.indexOf(":")
                    if (idx != -1) email = trimmed.substring(idx + 1).trim()
                }
                trimmed.startsWith("ORG:", ignoreCase = true) -> org = trimmed.substring(4).trim()
                trimmed.startsWith("ADR", ignoreCase = true) -> {
                    val idx = trimmed.indexOf(":")
                    if (idx != -1) address = trimmed.substring(idx + 1).replace(";", ", ").trim()
                }
            }
        }

        // Support MECARD format
        if (raw.startsWith("MECARD:", ignoreCase = true)) {
            val tokens = raw.removePrefix("MECARD:").split(";")
            for (token in tokens) {
                when {
                    token.startsWith("N:") -> name = token.removePrefix("N:")
                    token.startsWith("TEL:") -> phone = token.removePrefix("TEL:")
                    token.startsWith("EMAIL:") -> email = token.removePrefix("EMAIL:")
                    token.startsWith("ORG:") -> org = token.removePrefix("ORG:")
                    token.startsWith("ADR:") -> address = token.removePrefix("ADR:")
                }
            }
        }

        return ScanContent.Contact(name, phone, email, org, address)
    }

    private fun parseEmailPayload(raw: String): ScanContent.Email {
        if (raw.startsWith("mailto:", ignoreCase = true)) {
            val clean = raw.substring(7)
            val parts = clean.split("?")
            val recipient = parts[0]
            var subject: String? = null
            var body: String? = null

            if (parts.size > 1) {
                val params = parts[1].split("&")
                for (param in params) {
                    val kv = param.split("=")
                    if (kv.size == 2) {
                        when (kv[0].lowercase(Locale.ROOT)) {
                            "subject" -> subject = Uri.decode(kv[1])
                            "body" -> body = Uri.decode(kv[1])
                        }
                    }
                }
            }
            return ScanContent.Email(recipient, subject, body)
        } else if (raw.startsWith("MATMSG:", ignoreCase = true)) {
            var to = ""
            var sub: String? = null
            var body: String? = null
            val tokens = raw.removePrefix("MATMSG:").split(";")
            for (token in tokens) {
                when {
                    token.startsWith("TO:") -> to = token.removePrefix("TO:")
                    token.startsWith("SUB:") -> sub = token.removePrefix("SUB:")
                    token.startsWith("BODY:") -> body = token.removePrefix("BODY:")
                }
            }
            return ScanContent.Email(to, sub, body)
        }
        return ScanContent.Email(raw, null, null)
    }

    private fun parseSmsPayload(raw: String): ScanContent.Sms {
        val clean = if (raw.startsWith("smsto:", ignoreCase = true)) raw.substring(6) else raw.substring(4)
        val colonIdx = clean.indexOf(":")
        val qIdx = clean.indexOf("?")

        return if (colonIdx != -1) {
            ScanContent.Sms(clean.substring(0, colonIdx), clean.substring(colonIdx + 1))
        } else if (qIdx != -1) {
            ScanContent.Sms(clean.substring(0, qIdx), clean.substring(qIdx + 1).removePrefix("body="))
        } else {
            ScanContent.Sms(clean, null)
        }
    }

    private fun parseGeoPayload(raw: String): ScanContent.Geo {
        val clean = raw.removePrefix("geo:").removePrefix("GEO:")
        val parts = clean.split("?")
        val coords = parts[0].split(",")
        val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val lng = coords.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val query = if (parts.size > 1) parts[1].removePrefix("q=") else null

        return ScanContent.Geo(lat, lng, query)
    }

    // --- Checksum Validators ---

    fun validateChecksum(code: String, format: BarcodeFormatType): Boolean {
        if (!code.all { it.isDigit() }) return false
        return when (format) {
            BarcodeFormatType.EAN_13 -> validateEan13Checksum(code)
            BarcodeFormatType.EAN_8 -> validateEan8Checksum(code)
            BarcodeFormatType.UPC_A -> validateUpcAChecksum(code)
            else -> true
        }
    }

    fun validateEan13Checksum(code: String): Boolean {
        if (code.length != 13 || !code.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 12) {
            val digit = code[i].digitToInt()
            sum += if (i % 2 == 0) digit else digit * 3
        }
        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        return calculatedCheckDigit == code[12].digitToInt()
    }

    fun validateEan8Checksum(code: String): Boolean {
        if (code.length != 8 || !code.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 7) {
            val digit = code[i].digitToInt()
            sum += if (i % 2 == 0) digit * 3 else digit
        }
        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        return calculatedCheckDigit == code[7].digitToInt()
    }

    fun validateUpcAChecksum(code: String): Boolean {
        if (code.length != 12 || !code.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 11) {
            val digit = code[i].digitToInt()
            sum += if (i % 2 == 0) digit * 3 else digit
        }
        val calculatedCheckDigit = (10 - (sum % 10)) % 10
        return calculatedCheckDigit == code[11].digitToInt()
    }
}
