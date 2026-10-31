package com.surakshaguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.view.Display
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background Payment Monitor Sentinel Service.
 *
 * Utilizes the official Android UsageStats API (standard for AppLock and Security sentinels).
 * This completely avoids BIND_ACCESSIBILITY_SERVICE so that Google Play Protect does NOT flag
 * the application with financial fraud / identity theft heuristic warnings.
 */
class PaymentMonitorService : Service() {

    companion object {
        const val PREFS_NAME = "suraksha_prefs"
        const val KEY_DEMO_MODE = "demo_mode_enabled"

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

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "surakshaguard_sentinel_channel"

        /**
         * Target Indian UPI applications monitored for pre-authorization fraud interception.
         */
        val TARGET_UPI_PACKAGES = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "net.one97.paytm" to "Paytm",
            "com.phonepe.app" to "PhonePe",
            "in.org.npci.upiapp" to "BHIM UPI",
            "com.cred.club" to "CRED"
        )

        /**
         * Known remote desktop & screen-sharing tools commonly leveraged in social engineering scams.
         */
        val KNOWN_REMOTE_TOOLS = listOf(
            RemoteAppInfo("com.anydesk.anydeskandroid", "AnyDesk Remote Desktop"),
            RemoteAppInfo("com.teamviewer.quicksupport.market", "TeamViewer QuickSupport"),
            RemoteAppInfo("com.teamviewer.teamviewer.market.mobile", "TeamViewer Remote"),
            RemoteAppInfo("com.sand.airdroid", "AirDroid Remote Support"),
            RemoteAppInfo("com.rustdesk.rustdesk", "RustDesk Remote Desktop"),
            RemoteAppInfo("com.zoho.assist", "Zoho Assist"),
            RemoteAppInfo("com.splashtop.remote", "Splashtop Remote"),
            RemoteAppInfo("com.logmein.rescuesupport", "LogMeIn Rescue")
        )

        /**
         * Timestamp until which interception is temporarily bypassed (e.g. when user clicks "I am Safe (2-Min Pass)").
         */
        @Volatile
        var bypassUntilTimestamp: Long = 0L

        private const val DEBOUNCE_INTERVAL_MS = 3000L
        private var lastInterceptionTime = 0L

        var isRunning = false
            private set

        /**
         * Evaluates threat environment for real-time fraud interception via ThreatDetector.
         */
        fun evaluateThreat(context: Context): ThreatReport {
            return ThreatDetector.evaluateLiveEnvironment(context)
        }

        fun hasActivePhoneCall(context: Context): Boolean {
            return ThreatDetector.checkActivePhoneCall(context)
        }

        fun detectRemoteApp(context: Context): RemoteAppInfo? {
            return ThreatDetector.checkActiveRemoteApp(context)
        }

        fun isScreenMirroringActive(context: Context): Boolean {
            return ThreatDetector.checkScreenMirroringActive(context)
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Check if 2-minute safe bypass pass is currently active
                    val now = System.currentTimeMillis()
                    if (now < bypassUntilTimestamp) {
                        delay(500L)
                        continue
                    }

                    val foregroundPackage = getForegroundPackageName()
                    if (foregroundPackage != null && TARGET_UPI_PACKAGES.containsKey(foregroundPackage)) {
                        if (now - lastInterceptionTime >= DEBOUNCE_INTERVAL_MS) {
                            val threat = evaluateThreat(this@PaymentMonitorService)
                            if (threat.type != ThreatType.NONE) {
                                lastInterceptionTime = now
                                val targetAppName = TARGET_UPI_PACKAGES[foregroundPackage] ?: foregroundPackage
                                interceptAndBlock(foregroundPackage, targetAppName, threat)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fail-safe handling for system state reads
                }
                delay(350L) // Lightweight 350ms monitoring cadence
            }
        }
    }

    /**
     * Extracts the currently focused foreground package using the official Android UsageStats API.
     */
    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 3000L

        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime) ?: return null
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    /**
     * Minimizes the target payment application and deploys the full-screen blocker overlay.
     */
    private fun interceptAndBlock(targetPackage: String, targetAppName: String, threat: ThreatReport) {
        // Step 1: Instantly minimize the payment application to the Android home screen
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)

        // Step 2: Launch the emergency full-screen red shield overlay with comprehensive threat payload
        val overlayIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(EXTRA_TARGET_APP_NAME, targetAppName)
            putExtra(EXTRA_THREAT_TYPE, threat.type.name)
            putExtra(EXTRA_THREAT_TITLE, threat.title)
            putExtra(EXTRA_THREAT_TITLE_HI, threat.titleHindi)
            putExtra(EXTRA_THREAT_DESC, threat.description)
            putExtra(EXTRA_THREAT_DESC_HI, threat.descriptionHindi)
            putExtra(EXTRA_OFFENDING_PACKAGE, threat.offendingPackage)
            putExtra(EXTRA_OFFENDING_APP_NAME, threat.offendingAppName)
            putExtra(EXTRA_ACTION_GUIDE, threat.actionGuide)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suraksha Guard Payment Sentinel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors payment app focus to intercept unauthorized transactions."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setContentTitle("Suraksha Guard Active")
            .setContentText("Background Sentinel is actively shielding UPI payment transactions.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
