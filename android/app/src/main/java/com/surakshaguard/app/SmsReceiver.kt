package com.surakshaguard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Real-Time Incoming SMS Debit OTP Sentinel BroadcastReceiver.
 *
 * Listens for `android.provider.Telephony.SMS_RECEIVED` events to detect
 * bank debit OTPs in real time and trigger immediate safety shield overlays
 * and loud emergency warnings.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "SurakshaDebitOtp"
        private const val OTP_CHANNEL_ID = "surakshaguard_debit_otp_channel"
        private const val NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming SMS intent: ${e.message}")
            return
        }

        if (messages.isNullOrEmpty()) return

        for (sms in messages) {
            val body = sms?.displayMessageBody ?: continue
            val sender = sms.displayOriginatingAddress ?: ""

            val analysis = DebitOtpEngine.analyze(body, sender, context)
            if (analysis.isDebitOtp) {
                Log.e(TAG, "🚨 DEBIT OTP INTERCEPTED! Amount: ${analysis.amount} | Code: ${analysis.otpCode} | Bank: ${analysis.bankOrCard}")

                // 1. Post High-Priority Emergency Heads-Up Notification
                sendEmergencyNotification(context, analysis)

                // 2. Deploy Full-Screen Safety Shield Overlay
                deploySafetyShieldOverlay(context, analysis, body)
                break
            }
        }
    }

    private fun deploySafetyShieldOverlay(context: Context, analysis: DebitOtpAnalysis, fullSmsBody: String) {
        val overlayIntent = Intent(context, BlockOverlayService::class.java).apply {
            putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, analysis.bankOrCard ?: "Bank Account")
            putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Bank Debit OTP: ${analysis.bankOrCard ?: "Banking Service"}")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.SUSPICIOUS_DEBIT_OTP.name)
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE, analysis.threatTitle)
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI, analysis.threatTitleHindi)
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC,
                "${analysis.explanation}\n\n• Debiting Amount: ${if (analysis.amount != null) "₹" + analysis.amount else "Unspecified"}\n• OTP Code: ${analysis.otpCode ?: "Hidden in SMS"}\n• Source: ${analysis.bankOrCard ?: "Bank Alert"}"
            )
            putExtra(PaymentMonitorService.EXTRA_THREAT_DESC_HI, analysis.explanationHindi)
            putExtra(PaymentMonitorService.EXTRA_DETECTED_KEYWORD, "OTP: ${analysis.otpCode ?: ""} | ₹${analysis.amount ?: ""}")
            putExtra(PaymentMonitorService.EXTRA_FULL_TEXT, fullSmsBody)
            putExtra(PaymentMonitorService.EXTRA_ACTION_GUIDE, analysis.actionAdvice)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(overlayIntent)
        } else {
            context.startService(overlayIntent)
        }
    }

    private fun sendEmergencyNotification(context: Context, analysis: DebitOtpAnalysis) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                OTP_CHANNEL_ID,
                "Emergency Debit OTP Security Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud emergency warnings when debit OTPs are received."
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amtText = if (analysis.amount != null) "₹${analysis.amount}" else "Money"
        val builder = NotificationCompat.Builder(context, OTP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle("🚨 DEBIT OTP RECEIVED: $amtText")
            .setContentText("Never share OTP with anyone over call or WhatsApp. Bank staff never ask for OTP.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${analysis.explanation}\n\n⚠️ OTP: ${analysis.otpCode ?: "Check SMS"} | Amount: $amtText\n\n${analysis.actionAdvice}"
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
