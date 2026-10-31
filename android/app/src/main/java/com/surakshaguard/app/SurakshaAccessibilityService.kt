package com.surakshaguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * In-App Screen Content Sentinel & In-App QR Fraud Interceptor.
 *
 * Inspects window content directly inside Google Pay, PhonePe, and Paytm
 * when a user scans a QR code with the payment app's internal scanner.
 *
 * Traverses on-screen text nodes to identify deceptive "Receive Money / Cashback"
 * reverse payment traps and fake utility bill impersonation before the user enters their PIN.
 */
class SurakshaAccessibilityService : AccessibilityService() {

    companion object {
        var isRunning = false
            private set

        private const val SCAN_DEBOUNCE_MS = 2500L
        private var lastScanTime = 0L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        // Only inspect target Indian UPI payment applications
        if (!PaymentMonitorService.TARGET_UPI_PACKAGES.containsKey(pkg)) return

        val now = System.currentTimeMillis()

        // Respect 2-minute safe bypass window
        if (now < PaymentMonitorService.bypassUntilTimestamp) return

        // Debounce to prevent heavy repeated scans while scrolling
        if (now - lastScanTime < SCAN_DEBOUNCE_MS) return

        val rootNode = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectAllNodeTexts(rootNode, texts)

        if (texts.isEmpty()) return

        val screenString = texts.joinToString(" ")
        val analysis = QrThreatEngine.analyzeScreenText(screenString)

        if (analysis.isFraud) {
            lastScanTime = now

            // Step 1: Instantly minimize the payment app to prevent UPI PIN entry
            performGlobalAction(GLOBAL_ACTION_HOME)

            // Step 2: Deploy the full-screen emergency fraud shield
            val appName = PaymentMonitorService.TARGET_UPI_PACKAGES[pkg] ?: pkg
            val overlayIntent = Intent(this, BlockOverlayService::class.java).apply {
                putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, pkg)
                putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "$appName: ${analysis.payeeName}")
                putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.FRAUD_QR_CODE.name)
                putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE, analysis.title)
                putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI, analysis.titleHindi)
                putExtra(
                    PaymentMonitorService.EXTRA_THREAT_DESC,
                    "${analysis.explanation}\n\n• Payee Detected: ${analysis.payeeName} (${analysis.payeeVpa})\n• Scanned Inside: $appName"
                )
                putExtra(PaymentMonitorService.EXTRA_THREAT_DESC_HI, analysis.explanationHindi)
                putExtra(PaymentMonitorService.EXTRA_OFFENDING_APP_NAME, analysis.payeeVpa)
                putExtra(PaymentMonitorService.EXTRA_ACTION_GUIDE, analysis.actionAdvice)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
        }
    }

    private fun collectAllNodeTexts(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null) return

        node.text?.let {
            val s = it.toString().trim()
            if (s.isNotBlank() && !output.contains(s)) {
                output.add(s)
            }
        }

        node.contentDescription?.let {
            val s = it.toString().trim()
            if (s.isNotBlank() && !output.contains(s)) {
                output.add(s)
            }
        }

        for (i in 0 until node.childCount) {
            collectAllNodeTexts(node.getChild(i), output)
        }
    }

    override fun onInterrupt() {
        // Accessibility event stream interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
