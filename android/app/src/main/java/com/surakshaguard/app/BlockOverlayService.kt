package com.surakshaguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Emergency Full-Screen Blocker Overlay Service.
 *
 * Utilizes WindowManager with TYPE_APPLICATION_OVERLAY to draw a full-screen,
 * non-bypassable semi-transparent red safety shield when a suspicious payment
 * initiation is detected.
 *
 * Explains the exact threat in plain English and Hindi, provides direct resolution
 * buttons (e.g. stopping screen-sharing apps), and offers a 2-minute "I am Safe" pass
 * for legitimate user transactions.
 */
class BlockOverlayService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "surakshaguard_blocking_channel"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPackage = intent?.getStringExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE)
            ?: "Payment Application"
        val targetAppName = intent?.getStringExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME)
            ?: targetPackage

        val threatTypeStr = intent?.getStringExtra(PaymentMonitorService.EXTRA_THREAT_TYPE)
            ?: ThreatType.DEMO_MODE.name
        val threatType = runCatching { ThreatType.valueOf(threatTypeStr) }.getOrDefault(ThreatType.DEMO_MODE)

        val threatTitle = intent?.getStringExtra(PaymentMonitorService.EXTRA_THREAT_TITLE)
            ?: "⚠️ PAYMENT INTERCEPTED: HIGH RISK OF SCAM"
        val threatTitleHi = intent?.getStringExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI)
            ?: "⚠️ यूपीआई भुगतान रोका गया: धोखाधड़ी का गंभीर खतरा"

        val threatDesc = intent?.getStringExtra(PaymentMonitorService.EXTRA_REASON)
            ?: intent?.getStringExtra(PaymentMonitorService.EXTRA_THREAT_DESC)
            ?: "Suraksha Guard intercepted this transaction because an unsafe condition was detected."
        val threatDescHi = intent?.getStringExtra(PaymentMonitorService.EXTRA_THREAT_DESC_HI)
            ?: "असुरक्षित माहौल पाए जाने पर यूपीआई पेमेंट रोक दिया गया।"

        val offendingPackage = intent?.getStringExtra(PaymentMonitorService.EXTRA_OFFENDING_PACKAGE)
        val offendingAppName = intent?.getStringExtra(PaymentMonitorService.EXTRA_OFFENDING_APP_NAME)
        val actionGuide = intent?.getStringExtra(PaymentMonitorService.EXTRA_ACTION_GUIDE) ?: ""

        displayBlockingOverlay(
            targetAppName = targetAppName,
            threatType = threatType,
            threatTitle = threatTitle,
            threatTitleHi = threatTitleHi,
            threatDesc = threatDesc,
            threatDescHi = threatDescHi,
            offendingPackage = offendingPackage,
            offendingAppName = offendingAppName,
            actionGuide = actionGuide
        )
        return START_NOT_STICKY
    }

    private fun displayBlockingOverlay(
        targetAppName: String,
        threatType: ThreatType,
        threatTitle: String,
        threatTitleHi: String,
        threatDesc: String,
        threatDescHi: String,
        offendingPackage: String?,
        offendingAppName: String?,
        actionGuide: String
    ) {
        removeOverlayView()

        val windowLayoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }

        val context = this

        // Root backdrop container
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#EE100407")) // 93% opacity dark crimson
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        // ScrollView to guarantee complete visibility on all screen heights
        val scrollView = ScrollView(context).apply {
            isFillViewport = false
        }

        // Main Card Container with High-Contrast Red Stroke
        val cardDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#1A070B"))
            cornerRadius = dp(24).toFloat()
            setStroke(dp(2), Color.parseColor("#EF4444"))
        }

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardDrawable
            setPadding(dp(20), dp(22), dp(20), dp(22))
        }

        // 1. Red Shield Warning Icon
        val shieldIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_shield_alert)
        }
        val shieldLp = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
            bottomMargin = dp(12)
        }
        cardLayout.addView(shieldIcon, shieldLp)

        // 2. Bilingual Primary Threat Header
        val titleView = TextView(context).apply {
            text = threatTitle
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }
        cardLayout.addView(titleView)

        val titleHiView = TextView(context).apply {
            text = threatTitleHi
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        cardLayout.addView(titleHiView)

        // 3. Intercepted Application Badge
        val targetBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2D0D12"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#7F1D1D"))
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        val targetText = TextView(context).apply {
            text = "🛡️ Intercepted: $targetAppName (Payment Suspended)"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FECACA"))
        }
        targetBox.addView(targetText)
        val targetBoxLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }
        cardLayout.addView(targetBox, targetBoxLp)

        // 4. Problem & Scam Context Box (English + Hindi)
        val explanationBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#150406"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#991B1B"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val descEnView = TextView(context).apply {
            text = threatDesc
            textSize = 12f
            setTextColor(Color.parseColor("#F1F5F9"))
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(0, 0, 0, dp(8))
        }
        explanationBox.addView(descEnView)

        val descHiView = TextView(context).apply {
            text = threatDescHi
            textSize = 11.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setLineSpacing(dp(2).toFloat(), 1.15f)
        }
        explanationBox.addView(descHiView)

        val explanationLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }
        cardLayout.addView(explanationBox, explanationLp)

        // 5. Threat-Specific Resolution / Guidance Action
        if (offendingPackage != null) {
            // Screen-Sharing App detected -> Direct button to open app settings & force stop / uninstall
            val solveAppButton = Button(context).apply {
                text = "🛑 Turn Off / Uninstall ${offendingAppName ?: "Remote App"}"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DC2626")) // Vibrant danger red
                    cornerRadius = dp(12).toFloat()
                }
                setOnClickListener {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$offendingPackage")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback to general installed apps settings
                        val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                    removeOverlayView()
                    stopSelf()
                }
            }
            val solveLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply {
                bottomMargin = dp(10)
            }
            cardLayout.addView(solveAppButton, solveLp)

        } else if (threatType == ThreatType.ACTIVE_PHONE_CALL) {
            // Active Phone Call detected -> Guidance banner to disconnect call
            val callGuidanceBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2E1C03"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#D97706"))
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val callGuideText = TextView(context).apply {
                text = if (actionGuide.isNotBlank()) {
                    "📞 $actionGuide"
                } else {
                    "📞 Action Required: Please disconnect your phone call first. Legitimate authorities, banks, and electricity boards NEVER ask you to make payments or share UPI PINs while on call."
                }
                textSize = 11.5f
                setTextColor(Color.parseColor("#FDE68A"))
                setTypeface(typeface, Typeface.BOLD)
                setLineSpacing(dp(2).toFloat(), 1.15f)
            }
            callGuidanceBox.addView(callGuideText)

            val callLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            cardLayout.addView(callGuidanceBox, callLp)

        } else if (threatType == ThreatType.FRAUD_QR_CODE) {
            // Fraudulent Payment QR Code detected
            val qrNoticeBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3B090F"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val qrNoticeText = TextView(context).apply {
                text = if (actionGuide.isNotBlank()) {
                    "🛑 QR Safety Rule: $actionGuide"
                } else {
                    "🛑 QR Safety Rule: Never scan a QR code or enter your PIN to receive money. Discard this QR immediately."
                }
                textSize = 11.5f
                setTextColor(Color.parseColor("#FECACA"))
                setTypeface(typeface, Typeface.BOLD)
                setLineSpacing(dp(2).toFloat(), 1.15f)
            }
            qrNoticeBox.addView(qrNoticeText)

            val qrLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            cardLayout.addView(qrNoticeBox, qrLp)

        } else if (threatType == ThreatType.SUSPICIOUS_DEBIT_OTP) {
            // Debit OTP Received Notice
            val otpNoticeBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3B090F"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#EF4444"))
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val otpNoticeText = TextView(context).apply {
                text = if (actionGuide.isNotBlank()) {
                    "🚨 Debit OTP Rule: $actionGuide"
                } else {
                    "🚨 Debit OTP Rule: Never share this OTP over call, SMS, or WhatsApp. Bank employees NEVER ask for your OTP."
                }
                textSize = 12f
                setTextColor(Color.parseColor("#FECACA"))
                setTypeface(typeface, Typeface.BOLD)
                setLineSpacing(dp(2).toFloat(), 1.15f)
            }
            otpNoticeBox.addView(otpNoticeText)

            val otpLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            cardLayout.addView(otpNoticeBox, otpLp)

        } else if (threatType == ThreatType.DEMO_MODE) {
            // Demo Mode Notice
            val demoNoticeBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0C2033"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#0284C7"))
                }
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            val demoText = TextView(context).apply {
                text = "🧪 Demo Simulation Active: Turn off Demo Mode on the SurakshaGuard home screen to enable live threat-only monitoring."
                textSize = 11.5f
                setTextColor(Color.parseColor("#BAE6FD"))
            }
            demoNoticeBox.addView(demoText)

            val demoLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            cardLayout.addView(demoNoticeBox, demoLp)
        }

        // 6. Legitimate User Bypass Button ("I am Safe — Allow Payment (2-Min Pass)")
        val allowButton = Button(context).apply {
            text = "✅ I am Safe — Allow Payment (2-Min Pass)"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669")) // Emerald green safety confirmation
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                // Grant 2-minute temporary bypass
                PaymentMonitorService.bypassUntilTimestamp = System.currentTimeMillis() + 120_000L
                removeOverlayView()
                stopSelf()
            }
        }
        val allowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46)
        ).apply {
            bottomMargin = dp(10)
        }
        cardLayout.addView(allowButton, allowLp)

        // 7. Primary Safety Action: Close & Keep Me Safe
        val dismissButton = Button(context).apply {
            text = "🛡️ Close & Keep Me Safe"
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626")) // High-contrast safety red
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                removeOverlayView()
                stopSelf()
            }
        }
        val dismissLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46)
        ).apply {
            bottomMargin = dp(10)
        }
        cardLayout.addView(dismissButton, dismissLp)

        // 8. Emergency Cyber Helpline Button (1930)
        val helplineButton = Button(context).apply {
            text = "Call 1930 (National Cyber Crime Helpline)"
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FCA5A5"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#3B090F"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#DC2626"))
            }
            setOnClickListener {
                removeOverlayView()
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                stopSelf()
            }
        }
        val helplineLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(40)
        )
        cardLayout.addView(helplineButton, helplineLp)

        scrollView.addView(cardLayout)
        rootLayout.addView(scrollView)

        try {
            windowManager?.addView(rootLayout, windowLayoutParams)
            overlayView = rootLayout
        } catch (_: Exception) {
            // Safeguard against missing overlay permission or WindowManager detachment
        }
    }

    private fun removeOverlayView() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {
                // View already detached or invalid
            }
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suraksha Guard Blocker Shield",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays emergency warnings when unauthorized transactions are intercepted."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle("Suraksha Guard Active Defense")
            .setContentText("Emergency fraud blocker overlay deployed.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
