package com.surakshaguard.app

data class QrThreatAnalysis(
    val isFraud: Boolean,
    val riskScore: Int, // 0 to 100
    val riskLevel: String, // "CRITICAL", "HIGH", "MODERATE", "SAFE"
    val fraudType: String,
    val title: String,
    val titleHindi: String,
    val explanation: String,
    val explanationHindi: String,
    val payeeVpa: String,
    val payeeName: String,
    val requestedAmount: String?,
    val flaggedPatterns: List<String>,
    val actionAdvice: String
)

/**
 * Real-Time UPI QR Code & Deep-Link Threat Intelligence Engine.
 *
 * Implements NPCI UPI specification validation, heuristic pattern matching,
 * and social engineering detection to intercept fraudulent or malicious payment QR codes
 * scanned externally or directly inside payment apps (Google Pay, PhonePe, Paytm).
 */
object QrThreatEngine {

    // Reverse QR Trap keywords: Scammers send payment QRs claiming victim will "receive" money
    private val REVERSE_PAYMENT_KEYWORDS = listOf(
        "refund", "cashback", "reward", "lottery", "win", "winner", "prize",
        "bonus", "claim", "receive", "credit", "gift", "subsidy", "voucher"
    )

    // Authority / Utility impersonation keywords
    private val IMPERSONATION_KEYWORDS = listOf(
        "electricity", "bijli", "power", "discom", "bses", "uppcl", "tneb", "bescom", "sbpdcl",
        "customs", "police", "challan", "trai", "court", "cbi", "cyber",
        "kyc", "pan", "aadhar", "account block", "unblock", "suspended",
        "customer care", "customercare", "helpline", "support desk", "helpdesk"
    )

    // Markers that indicate user is browsing the home/navigation screen of a payment app
    // Note: 'scan any qr' is strictly excluded because the scanner view itself displays that text!
    val HOME_SCREEN_INDICATORS = listOf(
        "pay contacts", "pay phone number", "bank transfer",
        "pay upi id", "self transfer", "pay bills", "manage your money",
        "check bank balance", "see transaction history", "to mobile number",
        "to bank/upi id", "to self account", "balance & history", "bills & recharges"
    )

    fun hasBlatantScamMarker(lowerText: String): Boolean {
        // 1. Remote access keywords
        val hasRemote = listOf("anydesk", "teamviewer", "quicksupport", "rustdesk", "airdroid").any { lowerText.contains(it) }
        if (hasRemote) return true

        // 2. Reverse payment bait compound phrases
        val hasBait = REVERSE_PAYMENT_PATTERNS.any { lowerText.contains(it) }
        if (hasBait) return true

        // 3. Fake electricity / discom markers
        val hasDiscom = lowerText.contains("discom") || lowerText.contains("bses") || lowerText.contains("uppcl")
        val hasUrgentBill = lowerText.contains("electricity") && (lowerText.contains("bill due") || lowerText.contains("power cut") || lowerText.contains("disconnect"))
        if (hasDiscom || hasUrgentBill) return true

        // 4. Malicious redirect links
        val hasBadUrl = SUSPICIOUS_URL_PATTERNS.any { lowerText.contains(it) }
        if (hasBadUrl) return true

        return false
    }

    fun isHomeScreen(lowerText: String): Boolean {
        // If a verified fraud pattern is on screen, it is NEVER an innocent home screen!
        if (hasBlatantScamMarker(lowerText)) {
            return false
        }
        return HOME_SCREEN_INDICATORS.any { lowerText.contains(it) }
    }

    fun isPaymentScreen(lowerText: String, vpa: String): Boolean {
        if (hasBlatantScamMarker(lowerText)) {
            return true
        }
        val paymentCues = listOf(
            "paying", "proceed to pay", "pay securely", "pay ₹", "pay to",
            "transfer to", "banking name", "add a note", "enter upi pin",
            "enter amount", "send money"
        )
        val hasCue = paymentCues.any { lowerText.contains(it) }
        val hasPayWithAmount = (lowerText.contains("pay") || lowerText.contains("send") || lowerText.contains("to")) &&
                (lowerText.contains("₹") || lowerText.contains("inr") || vpa.isNotBlank())
        return hasCue || hasPayWithAmount
    }

    // High-confidence compound reverse payment fraud patterns for on-screen inspection
    val REVERSE_PAYMENT_PATTERNS = listOf(
        "enter pin to receive", "pin to receive", "pin to get", "receive money",
        "claim cashback", "cashback claim", "receive cashback", "cashback reward",
        "cashback lottery", "claim refund", "refund claim", "refund verification",
        "prize claim", "claim prize", "lottery winner", "lucky draw winner",
        "claim bonus", "claim subsidy", "paying cashback", "paying refund",
        "paying reward", "paying lottery", "to cashback", "to refund"
    )

    // Suspicious URL indicators inside the UPI `url` parameter
    private val SUSPICIOUS_URL_PATTERNS = listOf(
        "bit.ly", "tinyurl.com", "is.gd", "t.co", "cutt.ly", "rb.gy", "shorturl.at",
        ".apk", ".exe", ".scr", ".zip", "ngrok", ".xyz", ".top", ".tk"
    )

    /**
     * Parses and evaluates a raw UPI URI or QR code string (e.g. upi://pay?pa=...&pn=...)
     */
    fun analyze(rawInput: String): QrThreatAnalysis {
        val cleanInput = rawInput.trim()
        val params = extractUpiParams(cleanInput)

        val vpa = params["pa"]?.trim() ?: extractVpaFallback(cleanInput)
        val name = params["pn"]?.trim() ?: "Unknown Payee"
        val amount = params["am"]?.trim()
        val note = params["tn"]?.trim() ?: ""
        val mc = params["mc"]?.trim() ?: "" // Merchant Category Code
        val urlParam = params["url"]?.trim() ?: ""

        val flaggedReasons = mutableListOf<String>()
        var score = 0
        var fraudType = "UNKNOWN"

        val combinedText = "$vpa $name $note".lowercase()

        // 1. Vector: Reverse Payment / "Receive Money" Scam Trap
        val matchedReverseKeywords = REVERSE_PAYMENT_KEYWORDS.filter { combinedText.contains(it) }
        if (matchedReverseKeywords.isNotEmpty()) {
            score += 55
            fraudType = "REVERSE_QR_SCAM"
            flaggedReasons.add("Deceptive 'Receive Money / Cashback / Refund' keywords in debit QR: ${matchedReverseKeywords.joinToString(", ")}")
        }

        // 2. Vector: Utility / Government / Bank Impersonation
        val matchedImpersonation = IMPERSONATION_KEYWORDS.filter { combinedText.contains(it) }
        if (matchedImpersonation.isNotEmpty()) {
            score += 45
            fraudType = if (fraudType == "UNKNOWN") "IMPERSONATION_SCAM" else fraudType
            flaggedReasons.add("Impersonating official authority, electricity board, or bank: ${matchedImpersonation.joinToString(", ")}")

            // If claiming to be a utility/bank but missing a legitimate merchant code (e.g., 4900 for utilities)
            if (mc.isBlank() || mc == "0000") {
                score += 25
                flaggedReasons.add("Claims to be utility/government service but uses unverified personal Peer-to-Peer (P2P) VPA (Missing MCC)")
            }
        }

        // 3. Vector: Malicious or Shortened URL Parameter in QR
        if (urlParam.isNotBlank()) {
            val matchedUrlSuspicion = SUSPICIOUS_URL_PATTERNS.filter { urlParam.lowercase().contains(it) }
            val isIpAddress = urlParam.matches(Regex(".*://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*"))

            if (matchedUrlSuspicion.isNotEmpty() || isIpAddress) {
                score += 50
                fraudType = "MALICIOUS_LINK_QR"
                flaggedReasons.add("QR code contains hidden shortened URL or direct download link: $urlParam")
            }
        }

        // 4. Vector: High-Urgency Static Amount Request
        if (amount != null && amount.toDoubleOrNull() != null) {
            val amtVal = amount.toDouble()
            if (amtVal >= 2000.0 && matchedReverseKeywords.isNotEmpty()) {
                score += 20
                flaggedReasons.add("High-value debit request (₹$amtVal) masquerading as reward/refund claim")
            }
        }

        // 5. Vector: Remote Desktop / Screen Coercion keyword in note or payee
        val remoteTools = listOf("anydesk", "teamviewer", "quicksupport", "rustdesk", "airdroid")
        val matchedRemote = remoteTools.filter { combinedText.contains(it) }
        if (matchedRemote.isNotEmpty()) {
            score += 70
            fraudType = "REMOTE_ACCESS_COERCION"
            flaggedReasons.add("QR code contains remote screen control keyword: ${matchedRemote.joinToString(", ")}")
        }

        // Normalize score between 0 and 100
        val finalScore = score.coerceIn(0, 100)
        val isFraud = finalScore >= 50

        val riskLevel = when {
            finalScore >= 75 -> "CRITICAL"
            finalScore >= 50 -> "HIGH"
            finalScore >= 25 -> "MODERATE"
            else -> "SAFE"
        }

        // Generate tailored explanations
        val title: String
        val titleHindi: String
        val explanation: String
        val explanationHindi: String
        val advice: String

        when (fraudType) {
            "REVERSE_QR_SCAM" -> {
                title = "CRITICAL ALERT: Fake 'Receive Money' QR Trap"
                titleHindi = "गंभीर चेतावनी: फर्जी 'पैसे पाने वाला' क्यूआर जाल"
                explanation = "This QR code is engineered to DEBIT (deduct) ${if (amount != null) "₹$amount" else "money"} from YOUR account. In UPI, scanning a QR code is ONLY for paying money, NEVER for receiving money or claiming refunds."
                explanationHindi = "यह क्यूआर कोड आपके खाते से पैसे काटने (Debit) के लिए बनाया गया है! यूपीआई में किसी से पैसे प्राप्त करने या रिफंड लेने के लिए कभी भी क्यूआर स्कैन या यूपीआई पिन डालने की जरूरत नहीं होती।"
                advice = "Do NOT enter your UPI PIN. Discard this QR immediately."
            }
            "IMPERSONATION_SCAM" -> {
                title = "HIGH THREAT: Impersonation / Fake Utility QR"
                titleHindi = "खतरे की चेतावनी: फर्जी बिजली बिल या बैंक खाता क्यूआर"
                explanation = "This QR code claims to belong to '$name' but routes funds to a personal, unverified VPA ($vpa). Legitimate utility boards and banks use verified enterprise merchant accounts."
                explanationHindi = "यह क्यूआर कोड '$name' का होने का दावा करता है, लेकिन यह पैसे सीधे एक अज्ञात निजी खाते ($vpa) में भेज रहा है। यह एक फर्जी बिजली बिल या फर्जी बैंक जालसाजी है।"
                advice = "Pay only through official biller portals or electricity provider apps."
            }
            "MALICIOUS_LINK_QR" -> {
                title = "HIGH THREAT: QR Contains Malicious URL"
                titleHindi = "खतरे की चेतावनी: क्यूआर में संदिग्ध लिंक पाया गया"
                explanation = "This QR code contains an external redirect link ($urlParam) associated with malicious APK downloads or phishing portals."
                explanationHindi = "इस क्यूआर कोड में एक छिपा हुआ लिंक है जो आपके फोन में संदिग्ध ऐप डाउनलोड करवा सकता है।"
                advice = "Do not open any link and do not authorize this payment."
            }
            "REMOTE_ACCESS_COERCION" -> {
                title = "HIGH THREAT: Remote Screen-Share Coercion Detected"
                titleHindi = "खतरे की चेतावनी: रिमोट स्क्रीन शेयरिंग धोखाधड़ी"
                explanation = "A remote access tool (AnyDesk/TeamViewer) keyword was detected in this QR payload. Scammers use remote tools to steal money."
                explanationHindi = "इस क्यूआर कोड में रिमोट एक्सेस ऐप (जैसे AnyDesk) का संदर्भ पाया गया है। जालसाज इसके जरिए यूपीआई पिन चुराते हैं।"
                advice = "Do NOT enter your UPI PIN. Disconnect any screen sharing immediately."
            }
            else -> {
                if (isFraud) {
                    title = "SECURITY ALERT: Suspicious Payment QR"
                    titleHindi = "सुरक्षा चेतावनी: संदिग्ध पेमेंट क्यूआर"
                    explanation = "Suraksha Guard identified high-risk social engineering markers associated with known payment fraud in this QR code."
                    explanationHindi = "सुरक्षागार्ड ने इस क्यूआर कोड में वित्तीय धोखाधड़ी से जुड़े संदिग्ध लक्षण पाए हैं।"
                    advice = "Verify the merchant in person before approving."
                } else {
                    title = "SAFE: Verified Payment QR"
                    titleHindi = "सुरक्षित: वैध पेमेंट क्यूआर"
                    explanation = "No fraudulent patterns or impersonation markers detected in this UPI QR code."
                    explanationHindi = "इस क्यूआर कोड में कोई धोखाधड़ी या संदिग्ध पैटर्न नहीं पाया गया।"
                    advice = "Safe to proceed with normal payment."
                }
            }
        }

        return QrThreatAnalysis(
            isFraud = isFraud,
            riskScore = finalScore,
            riskLevel = riskLevel,
            fraudType = fraudType,
            title = title,
            titleHindi = titleHindi,
            explanation = explanation,
            explanationHindi = explanationHindi,
            payeeVpa = if (vpa.isNotBlank()) vpa else "unspecified@vpa",
            payeeName = name,
            requestedAmount = amount,
            flaggedPatterns = flaggedReasons,
            actionAdvice = advice
        )
    }

    /**
     * Inspects text extracted from active payment app window (e.g. Google Pay, PhonePe, Paytm).
     * Detects when a user has scanned a fraudulent QR inside Google Pay and is on the checkout screen.
     */
    fun analyzeScreenText(screenText: String): QrThreatAnalysis {
        val lowerText = screenText.lowercase()

        // 1. Critical Home Screen Guard: If on home / dashboard screen, NEVER flag as fraud!
        if (isHomeScreen(lowerText)) {
            return QrThreatAnalysis(
                isFraud = false,
                riskScore = 0,
                riskLevel = "SAFE",
                fraudType = "NONE",
                title = "SAFE: Home Screen",
                titleHindi = "सुरक्षित: होम स्क्रीन",
                explanation = "Browsing home navigation screen.",
                explanationHindi = "सामान्य नेविगेशन स्क्रीन।",
                payeeVpa = "",
                payeeName = "",
                requestedAmount = null,
                flaggedPatterns = emptyList(),
                actionAdvice = ""
            )
        }

        val vpa = extractVpaFallback(screenText)
        val amount = extractAmountFallback(screenText)
        val payeeName = extractPayeeNameFallback(screenText, vpa)
        val lowerPayee = payeeName.lowercase()

        // 2. Must be on an active payment / checkout screen
        if (!isPaymentScreen(lowerText, vpa)) {
            return QrThreatAnalysis(
                isFraud = false,
                riskScore = 0,
                riskLevel = "SAFE",
                fraudType = "NONE",
                title = "SAFE: Browsing",
                titleHindi = "सुरक्षित: ब्राउज़िंग",
                explanation = "No active payment transaction.",
                explanationHindi = "कोई सक्रिय भुगतान नहीं।",
                payeeVpa = "",
                payeeName = "",
                requestedAmount = null,
                flaggedPatterns = emptyList(),
                actionAdvice = ""
            )
        }

        val flaggedReasons = mutableListOf<String>()
        var score = 0
        var fraudType = "UNKNOWN"

        // 3. Vector: Reverse Payment / Cashback / Refund scam inside Google Pay
        val matchedReverseKeywords = REVERSE_PAYMENT_PATTERNS.filter {
            lowerPayee.contains(it) || lowerText.contains(it)
        }
        if (matchedReverseKeywords.isNotEmpty()) {
            score += 65
            fraudType = "REVERSE_QR_SCAM"
            flaggedReasons.add("Deceptive 'Cashback / Refund / Prize' keywords in payment details: ${matchedReverseKeywords.joinToString(", ")}")
        }

        // 4. Vector: Remote Desktop / Screen Coercion on payment checkout screen
        val remoteTools = listOf("anydesk", "teamviewer", "quicksupport", "rustdesk")
        val matchedRemote = remoteTools.filter { lowerText.contains(it) }
        if (matchedRemote.isNotEmpty()) {
            score += 70
            fraudType = "REMOTE_ACCESS_COERCION"
            flaggedReasons.add("Remote desktop keyword detected on payment checkout screen: ${matchedRemote.joinToString(", ")}")
        }

        // 5. Vector: Fake Electricity / Utility Impersonation
        val matchedImpersonation = IMPERSONATION_KEYWORDS.filter {
            lowerPayee.contains(it) || lowerText.contains(it)
        }
        if (matchedImpersonation.isNotEmpty()) {
            if (vpa.isNotBlank() && isPersonalVpaHandle(vpa)) {
                score += 85
                fraudType = "IMPERSONATION_SCAM"
                flaggedReasons.add("Utility/official authority impersonation ($matchedImpersonation) routes funds to personal VPA: $vpa")
            } else if (lowerText.contains("disconnect") || lowerText.contains("power cut") || lowerText.contains("overdue")) {
                score += 75
                fraudType = "IMPERSONATION_SCAM"
                flaggedReasons.add("High-urgency electricity disconnection threat in payment note: ${matchedImpersonation.joinToString(", ")}")
            }
        }

        // 6. Vector: Suspicious high-risk URL or APK references in payment notes
        val matchedUrl = SUSPICIOUS_URL_PATTERNS.filter { lowerText.contains(it) }
        if (matchedUrl.isNotEmpty()) {
            score += 60
            fraudType = "MALICIOUS_LINK_QR"
            flaggedReasons.add("Suspicious external link or APK reference detected on payment screen")
        }

        val finalScore = score.coerceIn(0, 100)
        val isFraud = finalScore >= 50

        val riskLevel = when {
            finalScore >= 75 -> "CRITICAL"
            finalScore >= 50 -> "HIGH"
            finalScore >= 25 -> "MODERATE"
            else -> "SAFE"
        }

        val title: String
        val titleHindi: String
        val explanation: String
        val explanationHindi: String
        val advice: String

        when (fraudType) {
            "REVERSE_QR_SCAM" -> {
                title = "CRITICAL ALERT: Fake 'Receive Money' QR Trap in Payment App"
                titleHindi = "गंभीर धोखाधड़ी: फर्जी 'कैशबैक / रिफंड' पेमेंट जाल"
                explanation = "You scanned a QR code offering a refund or reward, but your payment app is preparing to DEBIT ${if (amount != null) "₹$amount" else "money"} from YOUR account. Entering your UPI PIN will send money to the scammer."
                explanationHindi = "आपने जो क्यूआर स्कैन किया है वह आपके खाते से पैसे काटने (Debit) के लिए बना है! यूपीआई में पैसे प्राप्त करने या कैशबैक लेने के लिए कभी भी पिन डालने की जरूरत नहीं होती।"
                advice = "Do NOT enter your UPI PIN. Cancel this transaction immediately."
            }
            "IMPERSONATION_SCAM" -> {
                title = "HIGH THREAT: Fake Utility / Electricity Bill QR"
                titleHindi = "खतरे की चेतावनी: फर्जी बिजली बिल या बैंक खाता क्यूआर"
                explanation = "This payment is labeled as a utility or bill payment, but points to an individual private account (${if (vpa.isNotBlank()) vpa else "unverified person"}). Fraudsters use fake electricity QR codes to steal bill payments."
                explanationHindi = "यह पेमेंट किसी बिजली विभाग के नाम पर दिख रहा है, लेकिन पैसे एक अज्ञात निजी खाते में जा रहे हैं। यह एक फर्जी बिजली बिल पेमेंट जालसाजी है।"
                advice = "Pay electricity and utility bills only through verified official portals or discom apps."
            }
            "REMOTE_ACCESS_COERCION" -> {
                title = "HIGH THREAT: Remote Screen-Share Coercion Detected"
                titleHindi = "खतरे की चेतावनी: रिमोट स्क्रीन शेयरिंग धोखाधड़ी"
                explanation = "A remote access tool (AnyDesk/TeamViewer) keyword was detected on your transaction screen. Scammers use remote tools to steal money."
                explanationHindi = "आपके पेमेंट स्क्रीन पर रिमोट एक्सेस ऐप का संदर्भ पाया गया है। जालसाज ऐसे ऐप्स के जरिए यूपीआई पिन चुराते हैं।"
                advice = "Do NOT enter your UPI PIN. Disconnect any screen sharing immediately."
            }
            else -> {
                if (isFraud) {
                    title = "SECURITY ALERT: Suspicious Scanned QR Detected"
                    titleHindi = "सुरक्षा चेतावनी: संदिग्ध स्कैन किया गया क्यूआर"
                    explanation = "Suraksha Guard detected deceptive patterns in the payment details on your screen."
                    explanationHindi = "सुरक्षागार्ड ने आपके स्क्रीन पर दिख रहे पेमेंट विवरण में वित्तीय धोखाधड़ी के लक्षण पाए हैं।"
                    advice = "Verify the merchant before proceeding."
                } else {
                    title = "SAFE: Legitimate Transaction"
                    titleHindi = "सुरक्षित: वैध पेमेंट"
                    explanation = "No suspicious scam patterns detected on this payment screen."
                    explanationHindi = "इस पेमेंट स्क्रीन पर कोई धोखाधड़ी के लक्षण नहीं पाए गए।"
                    advice = "Safe to proceed."
                }
            }
        }

        return QrThreatAnalysis(
            isFraud = isFraud,
            riskScore = finalScore,
            riskLevel = riskLevel,
            fraudType = fraudType,
            title = title,
            titleHindi = titleHindi,
            explanation = explanation,
            explanationHindi = explanationHindi,
            payeeVpa = if (vpa.isNotBlank()) vpa else "Detected On Screen",
            payeeName = if (payeeName.isNotBlank()) payeeName else "Unknown Payee",
            requestedAmount = amount,
            flaggedPatterns = flaggedReasons,
            actionAdvice = advice
        )
    }

    private fun isPersonalVpaHandle(vpa: String): Boolean {
        val handle = vpa.substringAfter("@", "").lowercase()
        return handle in listOf("okaxis", "okhdfcbank", "okicici", "oksbi", "ybl", "ibl", "axl", "paytm", "apl")
    }

    private fun extractAmountFallback(text: String): String? {
        val regex = Regex("(?:₹|INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        val match = regex.find(text)
        return match?.groupValues?.getOrNull(1)?.replace(",", "")
    }

    private fun extractPayeeNameFallback(text: String, vpa: String): String {
        val payingRegex = Regex("(?:paying|to)\\s+([a-zA-Z0-9\\s]{3,30})", RegexOption.IGNORE_CASE)
        val match = payingRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        if (vpa.isNotBlank()) {
            return vpa.substringBefore("@")
        }
        return "Unknown Payee"
    }

    /**
     * Extracts parameters from standard UPI URIs: upi://pay?pa=...&pn=...&am=...
     * Uses pure URL decoding for 100% compatibility across both JVM and Android runtimes.
     */
    fun extractUpiParams(uriString: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        try {
            val queryPart = if (uriString.contains("?")) uriString.substringAfter("?") else uriString
            val pairs = queryPart.split("&")
            for (pair in pairs) {
                val tokens = pair.split("=")
                if (tokens.size >= 2) {
                    val key = tokens[0].trim()
                    val rawVal = tokens.subList(1, tokens.size).joinToString("=")
                    val decodedVal = try {
                        java.net.URLDecoder.decode(rawVal, "UTF-8")
                    } catch (_: Exception) {
                        rawVal
                    }
                    params[key] = decodedVal
                }
            }
        } catch (_: Exception) {
            // Graceful parse fallback
        }
        return params
    }

    fun extractVpaFallback(input: String): String {
        // Match standard VPA format: username@bankhandle
        val regex = Regex("[a-zA-Z0-9.\\-_]{2,50}@[a-zA-Z]{2,20}")
        val match = regex.find(input)
        return match?.value ?: ""
    }
}
