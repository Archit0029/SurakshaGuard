package com.surakshaguard.app

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import android.view.Display

enum class ThreatType {
    NONE,
    REMOTE_SCREEN_SHARE,
    ACTIVE_PHONE_CALL,
    FRAUD_QR_CODE,
    DEMO_MODE
}

data class ThreatReport(
    val type: ThreatType,
    val title: String,
    val titleHindi: String,
    val description: String,
    val descriptionHindi: String,
    val offendingPackage: String? = null,
    val offendingAppName: String? = null,
    val actionGuide: String = ""
)

data class RemoteAppInfo(
    val packageName: String,
    val displayName: String
)

object ThreatDetector {

    const val PREFS_NAME = "suraksha_prefs"
    const val KEY_DEMO_MODE = "demo_mode_enabled"

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
     * Evaluates whether an actual dangerous threat exists in the environment.
     * Pure and strictly calibrated to prevent any false positives on standard clean devices.
     */
    fun evaluate(
        isDemoMode: Boolean,
        hasActiveCall: Boolean,
        activeRemoteApp: RemoteAppInfo?,
        isMirroringActive: Boolean
    ): ThreatReport {
        // 1. Explicit user demo mode toggle (for hackathon / testing)
        if (isDemoMode) {
            return ThreatReport(
                type = ThreatType.DEMO_MODE,
                title = "DEMO SIMULATION: Test Interception Active",
                titleHindi = "डेमो परीक्षण: टेस्ट सुरक्षा इंटरसेप्टर सक्रिय",
                description = "Demo Mode is enabled in SurakshaGuard settings for testing. In Live Mode, SurakshaGuard only intercepts when a genuine threat is present.",
                descriptionHindi = "सुरक्षागार्ड सेटिंग्स में डेमो मोड चालू है। सामान्य लाइव मोड में केवल वास्तविक खतरे पर ही पेमेंट रुकता है।",
                offendingPackage = null,
                offendingAppName = null,
                actionGuide = "You can disable Demo Mode on the SurakshaGuard main screen to switch to live threat-only monitoring."
            )
        }

        // 2. Active phone call in progress during payment initiation
        if (hasActiveCall) {
            return ThreatReport(
                type = ThreatType.ACTIVE_PHONE_CALL,
                title = "CRITICAL ALERT: Active Phone Call in Progress",
                titleHindi = "गंभीर चेतावनी: चालू फोन कॉल के दौरान पेमेंट का प्रयास",
                description = "You are currently on a live phone call while opening a payment application. Cybercriminals keep victims on calls using urgency (fake electricity bills, customs, or bank freezes) to coerce them into authorizing UPI transfers.",
                descriptionHindi = "आप इस समय फोन कॉल पर बात कर रहे हैं। जालसाज अक्सर बिजली बिल कटने, बैंक या पुलिस का डर दिखाकर कॉल पर रखते हुए दबाव में यूपीआई पिन डलवाते हैं।",
                offendingPackage = null,
                offendingAppName = null,
                actionGuide = "Please disconnect your phone call first. Legitimate authorities never demand payments while keeping you on the phone."
            )
        }

        // 3. Known remote access application actively running or recently interacted with
        if (activeRemoteApp != null) {
            return ThreatReport(
                type = ThreatType.REMOTE_SCREEN_SHARE,
                title = "HIGH THREAT: Screen-Sharing App Active",
                titleHindi = "खतरे की चेतावनी: स्क्रीन शेयरिंग ऐप सक्रिय पाई गई",
                description = "The remote access tool '${activeRemoteApp.displayName}' is currently active or running on your device. Scammers use this to view your screen in real time, capture OTPs, and steal your UPI PIN.",
                descriptionHindi = "आपके फोन में '${activeRemoteApp.displayName}' स्क्रीन शेयरिंग ऐप सक्रिय है। जालसाज इसके जरिए आपकी स्क्रीन और यूपीआई पिन देखकर पैसे निकाल सकते हैं।",
                offendingPackage = activeRemoteApp.packageName,
                offendingAppName = activeRemoteApp.displayName,
                actionGuide = "Tap below to open App Info and Force Stop or Uninstall ${activeRemoteApp.displayName} immediately."
            )
        }

        // 4. Live external Wi-Fi or presentation display casting (strictly filtered to exclude internal displays)
        if (isMirroringActive) {
            return ThreatReport(
                type = ThreatType.REMOTE_SCREEN_SHARE,
                title = "HIGH THREAT: Live Screen Casting Active",
                titleHindi = "खतरे की चेतावनी: लाइव स्क्रीन कास्टिंग सक्रिय है",
                description = "Your device screen is currently being broadcast to an external display. Entering your UPI PIN now will expose your credentials.",
                descriptionHindi = "आपके फोन की स्क्रीन इस समय बाहरी स्क्रीन पर शेयर हो रही है। इस समय यूपीआई पिन डालना असुरक्षित है।",
                offendingPackage = null,
                offendingAppName = "Active Screen Cast",
                actionGuide = "Stop screen sharing or casting before completing this payment."
            )
        }

        // 5. Clean, safe environment: zero threat, zero disturbance!
        return ThreatReport(
            type = ThreatType.NONE,
            title = "",
            titleHindi = "",
            description = "",
            descriptionHindi = "",
            offendingPackage = null,
            offendingAppName = null,
            actionGuide = ""
        )
    }

    /**
     * Checks if a phone call is genuinely active using TelephonyManager and AudioManager.
     * Strictly avoids false positives from Bluetooth media routing, VoIP background audio, etc.
     */
    fun checkActivePhoneCall(context: Context): Boolean {
        // Method A: Check TelephonyManager call state
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null) {
                val state = tm.callState
                if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING) {
                    return true
                }
            }
        } catch (_: SecurityException) {
            // Fall through to audio manager
        }

        // Method B: AudioManager.MODE_IN_CALL is ONLY set during an active cellular call.
        // We strictly DO NOT check MODE_IN_COMMUNICATION to prevent false alarms from Bluetooth headsets or games.
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.mode == AudioManager.MODE_IN_CALL
    }

    /**
     * Checks whether any known remote desktop tool is installed AND actively running / recently opened.
     * Mere installation without being active or running does NOT trigger false alarms.
     */
    fun checkActiveRemoteApp(context: Context): RemoteAppInfo? {
        val pm = context.packageManager

        for (tool in KNOWN_REMOTE_TOOLS) {
            val isInstalled = try {
                pm.getPackageInfo(tool.packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }

            if (!isInstalled) continue

            // If installed, verify if it is ACTUALLY running or recently active
            if (isProcessOrRecentActivityPresent(context, tool.packageName)) {
                return tool
            }
        }
        return null
    }

    private fun isProcessOrRecentActivityPresent(context: Context, packageName: String): Boolean {
        // 1. Check if process is actively running in foreground or foreground service
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val runningProcesses = am?.runningAppProcesses
        if (runningProcesses != null) {
            for (proc in runningProcesses) {
                if (proc.processName.startsWith(packageName)) {
                    if (proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE) {
                        return true
                    }
                }
            }
        }

        // 2. Check if the app was used in the last 10 minutes via UsageStatsManager
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager != null) {
            val now = System.currentTimeMillis()
            val tenMinutesAgo = now - (10 * 60 * 1000L)
            val usageEvents = usageStatsManager.queryEvents(tenMinutesAgo, now)
            if (usageEvents != null) {
                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    if (event.packageName == packageName) {
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                            event.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Checks if actual external screen casting or mirroring is active.
     * Accurately filters out internal OEM virtual displays (Under-Display Fingerprint Sensors,
     * Always-On Display, Smart Sidebars, Dual Screens).
     */
    fun checkScreenMirroringActive(context: Context): Boolean {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return false
        val displays = displayManager.displays

        for (display in displays) {
            if (display.displayId == Display.DEFAULT_DISPLAY) continue

            val name = display.name.lowercase()
            // Ignore all internal device displays
            if (name.contains("fingerprint") ||
                name.contains("udfps") ||
                name.contains("aod") ||
                name.contains("builtin") ||
                name.contains("internal") ||
                name.contains("overlay") ||
                name.contains("side") ||
                name.contains("edge") ||
                name.contains("camera")) {
                continue
            }

            val isPresentation = (display.flags and Display.FLAG_PRESENTATION) != 0
            val isPrivate = (display.flags and Display.FLAG_PRIVATE) != 0

            // Only flag if it is an un-private external presentation display
            if (isPresentation && !isPrivate) {
                return true
            }
        }
        return false
    }

    /**
     * Evaluates live environment directly with Android system services.
     */
    fun evaluateLiveEnvironment(context: Context): ThreatReport {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDemoMode = prefs.getBoolean(KEY_DEMO_MODE, false)
        val hasActiveCall = checkActivePhoneCall(context)
        val activeRemoteApp = checkActiveRemoteApp(context)
        val isMirroring = checkScreenMirroringActive(context)

        return evaluate(
            isDemoMode = isDemoMode,
            hasActiveCall = hasActiveCall,
            activeRemoteApp = activeRemoteApp,
            isMirroringActive = isMirroring
        )
    }
}

