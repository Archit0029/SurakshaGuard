package com.surakshaguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Screen-Reading Background Monitor Sentinel Service.
 *
 * Extends [AccessibilityService] to inspect visible text nodes rendered on-screen
 * inside target Indian UPI payment applications (Google Pay, PhonePe, Paytm, BHIM).
 *
 * Implements recursive tree traversal over [AccessibilityNodeInfo], combining text and
 * content descriptions to catch social engineering and fraud keywords (e.g. AnyDesk,
 * urgent disconnection, fake lottery/refund claims) embedded in custom UPI notes or payee titles.
 */
class PaymentMonitorService : AccessibilityService() {

    companion object {
        const val TAG = "SurakshaThreat"

        const val PREFS_NAME = "suraksha_prefs"
        const val KEY_DEMO_MODE = "demo_mode_enabled"

        const val EXTRA_REASON = "EXTRA_REASON"
        const val EXTRA_TARGET_PACKAGE = "EXTRA_TARGET_PACKAGE"
        const val EXTRA_TARGET_APP_NAME = "EXTRA_TARGET_APP_NAME"
        const val EXTRA_THREAT_TYPE = "EXTRA_THREAT_TYPE"
        const val EXTRA_THREAT_TITLE = "EXTRA_THREAT_TITLE"
        const val EXTRA_THREAT_TITLE_HI = "EXTRA_THREAT_TITLE_HI"
        const val EXTRA_THREAT_DESC = "EXTRA_THREAT_DESC"
        const val EXTRA_THREAT_DESC_HI = "EXTRA_THREAT_DESC_HI"
        const val EXTRA_OFFENDING_PACKAGE = "EXTRA_OFFENDING_PACKAGE"
        const val EXTRA_OFFENDING_APP_NAME = "EXTRA_OFFENDING_APP_NAME"
        const val EXTRA_ACTION_GUIDE = "EXTRA_ACTION_GUIDE"
        const val EXTRA_DETECTED_KEYWORD = "EXTRA_DETECTED_KEYWORD"
        const val EXTRA_FULL_TEXT = "EXTRA_FULL_TEXT"

        /**
         * Target Indian UPI applications monitored for pre-authorization fraud interception.
         */
        val TARGET_UPI_PACKAGES = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "net.one97.paytm" to "Paytm",
            "com.phonepe.app" to "PhonePe",
            "in.org.npci.upiapp" to "BHIM UPI"
        )

        // 1. Urgency & Coercion triggers
        val URGENCY_TRIGGERS = listOf(
            "urgent", "immediate", "bijli", "disconnect", "penalty", "overdue",
            "power cut", "bill due", "discom", "challan", "customs", "arrest"
        )

        // 2. Remote access triggers
        val REMOTE_ACCESS_TRIGGERS = listOf(
            "anydesk", "teamviewer", "quicksupport", "rustdesk", "airdroid", "screen share"
        )

        // 3. Fraud bait & reverse-payment triggers (compound phrases to avoid matching native UI tabs like 'Rewards')
        val FRAUD_BAIT_TRIGGERS = QrThreatEngine.REVERSE_PAYMENT_PATTERNS

        // 4. Authority & Fake support impersonation triggers
        val IMPERSONATION_TRIGGERS = listOf(
            "customer care", "customercare", "helpline", "support desk",
            "kyc update", "pan update", "sim block", "account blocked"
        )

        /**
         * Timestamp until which interception is temporarily bypassed (e.g. when user clicks "I am Safe (2-Min Pass)").
         */
        @Volatile
        var bypassUntilTimestamp: Long = 0L

        private const val DEBOUNCE_COOLDOWN_MS = 5000L
        private var lastTriggerTime = 0L
        private var lastTriggerReason: String? = null

        var isRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "SurakshaGuard PaymentMonitorService connected and listening.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        // 1. Event Handling: Verify focused package is in target UPI list
        if (!TARGET_UPI_PACKAGES.containsKey(pkgName)) return

        val now = System.currentTimeMillis()

        // Honor temporary user bypass (e.g. "I am Safe" pass)
        if (now < bypassUntilTimestamp) return

        // Check if demo simulation mode is toggled ON
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDemoMode = prefs.getBoolean(KEY_DEMO_MODE, false)

        val root = rootInActiveWindow ?: event.source ?: return

        // 2. Tree Traversal: Extract all text from active node hierarchy + event payload
        val nodeText = extractAllTextFromNode(root)
        val eventText = event.text.joinToString(" ")
        val fullScreenText = (nodeText + " " + eventText).trim()
        if (fullScreenText.isBlank()) return

        // 3. Heuristic & Deep QR Threat Detection: Normalize to lowercase
        val lowerText = fullScreenText.lowercase()

        // CRITICAL GUARD 1: If user is on the home/navigation screen, NEVER evaluate or intercept!
        if (QrThreatEngine.isHomeScreen(lowerText)) {
            return
        }

        // CRITICAL GUARD 2: Must be on an active payment checkout screen
        val vpa = QrThreatEngine.extractVpaFallback(fullScreenText)
        if (!isDemoMode && !QrThreatEngine.isPaymentScreen(lowerText, vpa)) {
            return
        }

        // Evaluate screen contents through QrThreatEngine intelligence
        val qrAnalysis = QrThreatEngine.analyzeScreenText(fullScreenText)

        val matchedUrgency = URGENCY_TRIGGERS.filter { lowerText.contains(it) }
        val matchedRemote = REMOTE_ACCESS_TRIGGERS.filter { lowerText.contains(it) }
        val matchedBait = FRAUD_BAIT_TRIGGERS.filter { lowerText.contains(it) }
        val matchedImpersonation = IMPERSONATION_TRIGGERS.filter { lowerText.contains(it) }

        val hasKeywordThreat = matchedRemote.isNotEmpty() || matchedBait.isNotEmpty() ||
                matchedUrgency.isNotEmpty() || matchedImpersonation.isNotEmpty()

        val hasThreat = isDemoMode || qrAnalysis.isFraud || hasKeywordThreat

        if (!hasThreat) return

        // Construct exact threat reason and matched keyword
        val detectedKeywords = mutableListOf<String>()
        detectedKeywords.addAll(matchedRemote)
        detectedKeywords.addAll(matchedBait)
        detectedKeywords.addAll(matchedUrgency)
        detectedKeywords.addAll(matchedImpersonation)
        if (qrAnalysis.flaggedPatterns.isNotEmpty()) {
            detectedKeywords.addAll(qrAnalysis.flaggedPatterns)
        }

        val keywordSummary = if (detectedKeywords.isNotEmpty()) {
            detectedKeywords.distinct().take(4).joinToString("', '", prefix = "'", postfix = "'")
        } else if (isDemoMode) {
            "demo_simulation"
        } else {
            "suspicious_payment_qr"
        }

        // Formulate tailored threat titles, descriptions, and action advice
        val threatTitleEn: String
        val threatTitleHi: String
        val reasonText: String
        val threatDescHi: String
        val actionGuide: String

        when {
            qrAnalysis.isFraud -> {
                threatTitleEn = qrAnalysis.title
                threatTitleHi = qrAnalysis.titleHindi
                reasonText = "${qrAnalysis.explanation}\n\n• Payee: ${qrAnalysis.payeeName}\n• VPA Handle: ${qrAnalysis.payeeVpa}"
                threatDescHi = qrAnalysis.explanationHindi
                actionGuide = qrAnalysis.actionAdvice
            }
            matchedRemote.isNotEmpty() -> {
                threatTitleEn = "⚠️ PAYMENT INTERCEPTED: REMOTE ACCESS FRAUD"
                threatTitleHi = "⚠️ यूपीआई भुगतान रोका गया: रिमोट ऐप धोखाधड़ी"
                reasonText = "Detected dangerous remote desktop / screen control keyword: $keywordSummary on payment screen."
                threatDescHi = "पेमेंट स्क्रीन पर रिमोट ऐप या स्क्रीन शेयरिंग के संदिग्ध शब्द पाए गए हैं।"
                actionGuide = "Do NOT enter your UPI PIN. Never make payments requested over AnyDesk or TeamViewer."
            }
            matchedBait.isNotEmpty() -> {
                threatTitleEn = "⚠️ PAYMENT INTERCEPTED: FAKE CASHBACK / REWARD TRAP"
                threatTitleHi = "⚠️ यूपीआई भुगतान रोका गया: फर्जी कैशबैक/इनाम का झांसा"
                reasonText = "Detected deceptive reverse-payment trap ($keywordSummary). In UPI, you never scan a QR or enter PIN to receive money."
                threatDescHi = "कैशबैक या पैसे पाने के लिए कभी भी यूपीआई पिन डालने की जरूरत नहीं होती।"
                actionGuide = "Never enter your UPI PIN to claim money, refunds, or lottery prizes."
            }
            matchedUrgency.isNotEmpty() -> {
                threatTitleEn = "⚠️ PAYMENT INTERCEPTED: HIGH-URGENCY COERCION"
                threatTitleHi = "⚠️ यूपीआई भुगतान रोका गया: तात्कालिक दबाव/बिजली बिल धमकी"
                reasonText = "Detected high-urgency extortion / power disconnection coercion trigger: $keywordSummary."
                threatDescHi = "बिजली कटने या जुर्माने की धमकी देकर दबाव में पेमेंट करवाने की कोशिश पाई गई।"
                actionGuide = "Do not pay under pressure. Official utility discoms never disconnect immediately via UPI."
            }
            matchedImpersonation.isNotEmpty() -> {
                threatTitleEn = "⚠️ PAYMENT INTERCEPTED: FAKE SUPPORT / IMPERSONATION"
                threatTitleHi = "⚠️ यूपीआई भुगतान रोका गया: फर्जी कस्टमर केयर / बैंक जालसाजी"
                reasonText = "Detected suspicious helpline or fake customer support marker: $keywordSummary."
                threatDescHi = "फर्जी कस्टमर केयर या बैंक अधिकारी बनकर पैसे ट्रांसफर करवाने का प्रयास पाया गया।"
                actionGuide = "Disconnect any phone call and verify contact details through official apps."
            }
            isDemoMode -> {
                threatTitleEn = "🛡️ DEMO TEST: PAYMENT INTERCEPTION VERIFIED"
                threatTitleHi = "🛡️ टेस्ट डेमो: यूपीआई सुरक्षा परीक्षण सक्रिय"
                reasonText = "Demo Simulation Mode: Successfully intercepted active payment application."
                threatDescHi = "डेमो मोड: सुरक्षागार्ड ने पेमेंट ऐप की सुरक्षा जांच सफलतापूर्वक पूरी की।"
                actionGuide = "Disable Demo Mode in Suraksha Guard when you are done testing."
            }
            else -> {
                threatTitleEn = "⚠️ PAYMENT INTERCEPTED: HIGH RISK OF SCAM"
                threatTitleHi = "⚠️ यूपीआई भुगतान रोका गया: धोखाधड़ी का गंभीर खतरा"
                reasonText = "Detected high-risk social engineering markers on payment screen."
                threatDescHi = "सुरक्षागार्ड ने इस भुगतान में धोखाधड़ी के संदिग्ध शब्द पाए हैं।"
                actionGuide = "Do not enter your UPI PIN. Verify the payment details independently."
            }
        }

        // 4. Prevent duplicate spam triggers using 5-second debounce window
        if (now - lastTriggerTime < DEBOUNCE_COOLDOWN_MS && lastTriggerReason == reasonText) {
            return
        }

        lastTriggerTime = now
        lastTriggerReason = reasonText

        // Action 1: Log exact match and full screen text
        Log.e(TAG, "🚨 THREAT DETECTED in $pkgName! Reason: $reasonText | Keywords: $keywordSummary | Full Text: $fullScreenText")

        // Action 2: Immediately call performGlobalAction(GLOBAL_ACTION_HOME) to minimize UPI app
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Action 3: Launch BlockOverlayService via Intent with payload
        val appName = TARGET_UPI_PACKAGES[pkgName] ?: pkgName
        val overlayIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(EXTRA_REASON, reasonText)
            putExtra(EXTRA_TARGET_PACKAGE, pkgName)
            putExtra(EXTRA_TARGET_APP_NAME, appName)
            putExtra(EXTRA_DETECTED_KEYWORD, keywordSummary)
            putExtra(EXTRA_FULL_TEXT, fullScreenText)
            putExtra(EXTRA_THREAT_TYPE, if (isDemoMode) ThreatType.DEMO_MODE.name else ThreatType.FRAUD_QR_CODE.name)
            putExtra(EXTRA_THREAT_TITLE, threatTitleEn)
            putExtra(EXTRA_THREAT_TITLE_HI, threatTitleHi)
            putExtra(EXTRA_THREAT_DESC, reasonText)
            putExtra(EXTRA_THREAT_DESC_HI, threatDescHi)
            putExtra(EXTRA_ACTION_GUIDE, actionGuide)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
    }

    /**
     * Recursive helper function that traverses the entire active node hierarchy (`rootInActiveWindow`),
     * combining `node.text` and `node.contentDescription`.
     */
    fun extractAllTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        collectTextRecursively(node, sb)
        return sb.toString().trim()
    }

    private fun collectTextRecursively(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return

        node.text?.let {
            val s = it.toString().trim()
            if (s.isNotBlank()) {
                sb.append(s).append(" ")
            }
        }

        node.contentDescription?.let {
            val s = it.toString().trim()
            if (s.isNotBlank()) {
                sb.append(s).append(" ")
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            collectTextRecursively(child, sb)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SurakshaGuard PaymentMonitorService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "SurakshaGuard PaymentMonitorService destroyed.")
    }
}
