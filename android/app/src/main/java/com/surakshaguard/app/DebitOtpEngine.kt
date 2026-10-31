package com.surakshaguard.app

import android.content.Context

data class DebitOtpAnalysis(
    val isDebitOtp: Boolean,
    val otpCode: String?,
    val amount: String?,
    val bankOrCard: String?,
    val isCallActive: Boolean,
    val isRemoteAppActive: Boolean,
    val threatTitle: String,
    val threatTitleHindi: String,
    val explanation: String,
    val explanationHindi: String,
    val actionAdvice: String
)

/**
 * Intelligent Real-Time Bank Debit OTP Sentinel Engine.
 *
 * Scans incoming SMS and bank notifications to identify One-Time Passwords (OTPs)
 * authorizing monetary debits or fund transfers from the user's bank accounts/cards.
 *
 * Correlates debit OTPs with live active phone calls or active remote access tools (AnyDesk/TeamViewer)
 * to instantly intercept phone-call social engineering scams and unauthorized account drain attempts.
 */
object DebitOtpEngine {

    // Keywords indicating an authentication / authorization OTP
    private val OTP_INDICATORS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "secret code", "auth code", "security code", "passcode"
    )

    // Keywords indicating monetary debit / deduction from the user's account
    private val DEBIT_INDICATORS = listOf(
        "debit", "debited", "transfer", "txn", "spent", "purchase",
        "payment of", "paying", "deducted", "withdraw", "mandate",
        "approve", "authorization of", "deduct", "to pay"
    )

    // Major Indian Banks and payment networks
    private val KNOWN_FINANCIAL_ENTITIES = listOf(
        "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "bank of baroda",
        "canara", "union bank", "indusind", "idfc", "yes bank", "rbl", "paytm",
        "federal bank", "au small", "slice", "cred", "onecard", "amex"
    )

    /**
     * Analyzes incoming SMS or notification text for debit OTP patterns.
     */
    fun analyze(messageBody: String, sender: String? = null, context: Context? = null): DebitOtpAnalysis {
        val cleanText = messageBody.trim()
        val lowerText = cleanText.lowercase()

        // 1. Must contain an OTP indicator
        val hasOtpIndicator = OTP_INDICATORS.any { lowerText.contains(it) }
        if (!hasOtpIndicator) {
            return safeResult()
        }

        // 2. Must indicate a DEBIT / spending / transfer transaction (exclude pure credit deposits)
        val hasDebitIndicator = DEBIT_INDICATORS.any { lowerText.contains(it) }

        // Exclusion: Pure credit deposit SMS without debit action (e.g. "Your account is credited with...")
        val isCreditOnly = (lowerText.contains("credited") || lowerText.contains("deposited")) &&
                !lowerText.contains("debit") && !lowerText.contains("transfer") && !lowerText.contains("spent")

        if (isCreditOnly || !hasDebitIndicator) {
            return safeResult()
        }

        // 3. Extract Monetary Amount
        val extractedAmount = extractAmount(cleanText)
        if (extractedAmount == null && !lowerText.contains("debit") && !lowerText.contains("transfer")) {
            return safeResult()
        }

        // 4. Extract OTP Code
        val extractedOtp = extractOtpCode(cleanText)

        // 5. Extract Bank or Card Details
        val bankOrCard = extractBankOrCard(cleanText, sender)

        // 6. Cross-reference live environment risks
        val isCallActive = if (context != null) ThreatDetector.checkActivePhoneCall(context) else false
        val remoteApp = if (context != null) ThreatDetector.checkActiveRemoteApp(context) else null
        val isRemoteActive = remoteApp != null

        // 7. Formulate bilingual threat descriptions and advice
        val amtDisplay = if (extractedAmount != null) "₹$extractedAmount" else "Money"
        val bankDisplay = bankOrCard ?: "Your Bank Account"

        val titleEn: String
        val titleHi: String
        val explanationEn: String
        val explanationHi: String
        val adviceEn: String

        when {
            isCallActive -> {
                titleEn = "🚨 CRITICAL: DEBIT OTP RECEIVED WHILE ON CALL ($amtDisplay)"
                titleHi = "🚨 गंभीर खतरा: फोन कॉल के दौरान पैसे कटने का ओटीपी आया ($amtDisplay)"
                explanationEn = "You just received an OTP for debiting $amtDisplay from $bankDisplay while actively speaking on a phone call! Scammers keep victims on calls to coerce them into reading OTPs aloud."
                explanationHi = "आप इस समय फोन कॉल पर हैं और आपके $bankDisplay से $amtDisplay कटने का ओटीपी आया है! कॉल पर मौजूद व्यक्ति को यह ओटीपी कभी न बताएं!"
                adviceEn = "DO NOT read or share this OTP. Disconnect your phone call immediately."
            }
            isRemoteActive -> {
                titleEn = "🚨 CRITICAL: REMOTE APP ACTIVE DURING DEBIT OTP ($amtDisplay)"
                titleHi = "🚨 गंभीर खतरा: स्क्रीन शेयरिंग ऐप चालू है और ओटीपी आया ($amtDisplay)"
                explanationEn = "An OTP authorizing a debit of $amtDisplay was received while '${remoteApp?.displayName ?: "Remote Tool"}' is running. Remote screen control apps can see this code!"
                explanationHi = "आपके फोन में रिमोट स्क्रीन शेयरिंग चालू है और $amtDisplay कटने का ओटीपी आया है! रिमोट ऐप आपकी स्क्रीन देख सकता है।"
                adviceEn = "Turn off or disconnect ${remoteApp?.displayName ?: "screen sharing"} immediately."
            }
            else -> {
                titleEn = "⚠️ ALERT: BANK DEBIT OTP RECEIVED ($amtDisplay)"
                titleHi = "⚠️ सतर्क रहें: बैंक से $amtDisplay कटने का ओटीपी प्राप्त हुआ"
                explanationEn = "An OTP was received to debit $amtDisplay from $bankDisplay. Did you personally initiate this transaction?"
                explanationHi = "आपके $bankDisplay से $amtDisplay काटने के लिए यह ओटीपी भेजा गया है। क्या यह ट्रांजेक्शन आपने खुद शुरू किया है?"
                adviceEn = "Never share this OTP with anyone over call, SMS, or WhatsApp. Bank employees NEVER ask for your OTP."
            }
        }

        return DebitOtpAnalysis(
            isDebitOtp = true,
            otpCode = extractedOtp,
            amount = extractedAmount,
            bankOrCard = bankOrCard,
            isCallActive = isCallActive,
            isRemoteAppActive = isRemoteActive,
            threatTitle = titleEn,
            threatTitleHindi = titleHi,
            explanation = explanationEn,
            explanationHindi = explanationHi,
            actionAdvice = adviceEn
        )
    }

    private fun safeResult(): DebitOtpAnalysis {
        return DebitOtpAnalysis(
            isDebitOtp = false,
            otpCode = null,
            amount = null,
            bankOrCard = null,
            isCallActive = false,
            isRemoteAppActive = false,
            threatTitle = "",
            threatTitleHindi = "",
            explanation = "",
            explanationHindi = "",
            actionAdvice = ""
        )
    }

    /**
     * Extracts currency debit amount from text: e.g. "INR 25,000.00", "Rs. 4,999", "₹ 10000"
     */
    fun extractAmount(text: String): String? {
        val regexPatterns = listOf(
            Regex("(?:₹|INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("(?:amount|amt|sum)(?:\\s+of)?\\s*(?:₹|INR|Rs\\.?)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("(?:for|of)\\s*(?:₹|INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
        )

        for (pattern in regexPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val rawAmt = match.groupValues[1].replace(",", "").trim()
                if (rawAmt.toDoubleOrNull() != null) {
                    return match.groupValues[1].trim()
                }
            }
        }
        return null
    }

    /**
     * Extracts the 4-8 digit OTP numeric code from the message.
     */
    fun extractOtpCode(text: String): String? {
        // Pattern A: "OTP is 482910" or "OTP: 482910" or "code is 482910"
        val labeledRegex = Regex("(?:otp|code|pin|passcode)[^\\d]{1,15}(\\d{4,8})\\b", RegexOption.IGNORE_CASE)
        val labeledMatch = labeledRegex.find(text)
        if (labeledMatch != null) {
            return labeledMatch.groupValues[1]
        }

        // Pattern B: "482910 is your OTP"
        val leadingRegex = Regex("\\b(\\d{4,8})\\b[^\\d]{1,15}(?:is your|is the|to approve|to transfer|for txn)", RegexOption.IGNORE_CASE)
        val leadingMatch = leadingRegex.find(text)
        if (leadingMatch != null) {
            return leadingMatch.groupValues[1]
        }

        // Pattern C: Any 4-8 digit standalone number
        val generalRegex = Regex("\\b(\\d{4,8})\\b")
        val allMatches = generalRegex.findAll(text).toList()
        for (m in allMatches) {
            val candidate = m.groupValues[1]
            // Avoid extracting years like 2026 or small 4-digit card endings if preceded by "ending"
            if (candidate != "2024" && candidate != "2025" && candidate != "2026") {
                return candidate
            }
        }
        return null
    }

    /**
     * Extracts bank name or card/account identifier from message or sender address.
     */
    fun extractBankOrCard(text: String, sender: String?): String? {
        val lowerText = text.lowercase()

        // 1. Match card ending or account ending
        val cardRegex = Regex("(?:card|a/c|account)[^\\d]{1,15}([xX*]{0,4}\\d{3,4})\\b", RegexOption.IGNORE_CASE)
        val cardMatch = cardRegex.find(text)
        val cardSuffix = cardMatch?.groupValues?.getOrNull(1)

        // 2. Match bank name
        var foundBank: String? = null
        for (bank in KNOWN_FINANCIAL_ENTITIES) {
            if (lowerText.contains(bank) || (sender != null && sender.lowercase().contains(bank))) {
                foundBank = bank.uppercase()
                break
            }
        }

        return when {
            foundBank != null && cardSuffix != null -> "$foundBank ($cardSuffix)"
            foundBank != null -> foundBank
            cardSuffix != null -> "Account ($cardSuffix)"
            sender != null && sender.isNotBlank() -> "Bank ($sender)"
            else -> "Bank Account"
        }
    }
}

