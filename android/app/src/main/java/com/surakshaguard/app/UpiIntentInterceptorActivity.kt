package com.surakshaguard.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * Real-Time UPI QR Code & Payment Deep-Link Interceptor Activity.
 *
 * Hooks into standard Android UPI payment intents (upi://pay) triggered when
 * a user scans a QR code using camera, QR scanner, or taps a payment link.
 *
 * Intercepts and physically blocks the payment if QrThreatEngine flags it as
 * a fake refund QR, utility impersonation, or malicious payload.
 */
class UpiIntentInterceptorActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData = intent?.data
        if (intentData == null) {
            finish()
            return
        }

        val uriString = intentData.toString()

        // 0. Master Protection Power Check: If user switched OFF, bypass interception and forward directly
        if (!PaymentMonitorService.isMasterProtectionEnabled(this)) {
            forwardToGenuinePaymentApp(intentData)
            finish()
            return
        }

        val analysis = QrThreatEngine.analyze(uriString)

        if (analysis.isFraud) {
            // Step 1: Block payment authorization and trigger full-screen safety shield
            val overlayIntent = Intent(this, BlockOverlayService::class.java).apply {
                putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, "Scanned UPI QR Code")
                putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Scanned QR: ${analysis.payeeName}")
                putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.FRAUD_QR_CODE.name)
                putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE, analysis.title)
                putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI, analysis.titleHindi)
                putExtra(
                    PaymentMonitorService.EXTRA_THREAT_DESC,
                    "${analysis.explanation}\n\n• Payee VPA: ${analysis.payeeVpa}\n• Requested Amount: ${if (analysis.requestedAmount != null) "₹" + analysis.requestedAmount else "Variable"}"
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

            Toast.makeText(
                this,
                "🚨 Suraksha Guard: Blocked Fraudulent Payment QR!",
                Toast.LENGTH_LONG
            ).show()

            finish()
        } else {
            // Step 2: Clean and legitimate QR -> Forward to actual payment applications
            forwardToGenuinePaymentApp(intentData)
            finish()
        }
    }

    private fun forwardToGenuinePaymentApp(uri: Uri) {
        val forwardIntent = Intent(Intent.ACTION_VIEW, uri)
        val pm = packageManager

        // Query available UPI handlers excluding Suraksha Guard itself
        val resolveList = pm.queryIntentActivities(forwardIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val targetActivity = resolveList.firstOrNull { it.activityInfo.packageName != packageName }

        if (targetActivity != null) {
            forwardIntent.setClassName(
                targetActivity.activityInfo.packageName,
                targetActivity.activityInfo.name
            )
            forwardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(forwardIntent)
        } else {
            Toast.makeText(
                this,
                "Suraksha Guard: Verified Safe QR (${uri.getQueryParameter("pa") ?: "UPI"})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

