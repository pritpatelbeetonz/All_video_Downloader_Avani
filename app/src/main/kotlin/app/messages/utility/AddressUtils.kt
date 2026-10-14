package app.messages.utility

object AddressUtils {
    fun isValidAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val lower = address.lowercase()
        // Common system placeholders for unknown numbers or hidden senders
        if (lower == "unknown" || lower == "null" || lower == "hidden" || lower == "-1" || lower == "-2" || 
            lower == "unknown_sender" || lower == "anonymous" || lower == "private" || 
            lower == "unknown_address" || lower == "blocked" || lower == "restricted") return false
            
        // Check for ANY address containing "unknown" as a substring in a way that suggests placeholder
        if (lower.contains("unknown") && (lower.contains("sender") || lower.contains("address") || lower.contains("number") || lower.contains("_"))) return false

        // Check for specific numeric placeholders that sometimes appear
        if (lower == "0" || lower == "0000000000") return false

        // Basic check for actual content (at least one letter or digit)
        return address.any { it.isLetterOrDigit() }
    }

    fun normalizeAddress(addr: String): String {
        if (addr.isBlank()) return ""
        val split = addr.split(",").filter { it.isNotBlank() }.map { it.trim() }
        val normalized = split.map { subAddr ->
            if (subAddr.any { it.isLetter() }) {
                subAddr.lowercase()
            } else {
                val digits = subAddr.filter { it.isDigit() }
                if (digits.length >= 10) digits.takeLast(10) else digits
            }
        }.sorted()
        return normalized.joinToString(",")
    }

    fun cleanMessageBody(body: String?): String {
        if (body.isNullOrEmpty()) return ""
        // Removes carrier anti-spam tokens, routing suffixes, or auto-reply hashes (e.g., "*/=3djBnQ", "*/=wj=Mpg")
        return body.replace(Regex("""\s*\*\/=\S+"""), "").trim()
    }

    fun canReply(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        if (!isValidAddress(address)) return false
        if (address.contains("@")) return true // Email
        // If it has letters, it's a sender ID (like AM-JIO), can't reply
        if (address.any { it.isLetter() }) return false
        return true
    }
}
