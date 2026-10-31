package com.surakshaguard.app

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Suraksha Guard — Premium Multi-Screen Security Dashboard & UPI Threat Interceptor Activity.
 */
class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasUsagePermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var hasSmsPermission by mutableStateOf(false)
    private var isProtectionArmed by mutableStateOf(true)
    private var isDemoMode by mutableStateOf(false)
    private var isCallActive by mutableStateOf(false)
    private var detectedRemoteApp by mutableStateOf<RemoteAppInfo?>(null)

    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        updatePermissionStates()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF070B14),
                    surface = Color(0xFF0F172A),
                    primary = Color(0xFF10B981),
                    error = Color(0xFFEF4444)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF070B14)
                ) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            updatePermissionStates()
                            delay(2000L)
                        }
                    }

                    MainAppNavigation(
                        isProtectionArmed = isProtectionArmed,
                        onToggleMasterProtection = { toggleMasterProtection() },
                        hasOverlay = hasOverlayPermission,
                        hasUsageAccess = hasUsagePermission,
                        hasAccessibility = hasAccessibilityPermission,
                        hasSmsAccess = hasSmsPermission,
                        isDemoMode = isDemoMode,
                        isCallActive = isCallActive,
                        detectedRemoteApp = detectedRemoteApp,
                        onToggleDemoMode = { enabled ->
                            isDemoMode = enabled
                            saveDemoMode(enabled)
                        },
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestUsageAccess = { requestUsageAccessPermission() },
                        onRequestAccessibility = { requestAccessibilityPermission() },
                        onRequestSmsAccess = { requestSmsPermission() },
                        onOpenAppInfo = { openAppInfoForRestrictedSettings() },
                        onTestBlocker = { triggerTestBlocker() },
                        onTestQrBlocker = { sampleQr -> triggerQrBlockerTest(sampleQr) },
                        onTestDebitOtp = { triggerDebitOtpTest() },
                        onTestActiveCall = { triggerActiveCallTest() },
                        onTestRemoteShare = { triggerRemoteScreenShareTest() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPreferences()
        updatePermissionStates()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PaymentMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        isDemoMode = prefs.getBoolean(PaymentMonitorService.KEY_DEMO_MODE, false)
        isProtectionArmed = PaymentMonitorService.isMasterProtectionEnabled(this)
    }

    private fun toggleMasterProtection() {
        val newState = !isProtectionArmed
        isProtectionArmed = newState
        PaymentMonitorService.setMasterProtectionEnabled(this, newState)
        val msg = if (newState) {
            "🛡️ Suraksha Guard: Background Sentinel Armed & Active!"
        } else {
            "⚠️ Suraksha Guard: Background Sentinel Disarmed / Paused"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun saveDemoMode(enabled: Boolean) {
        val prefs = getSharedPreferences(PaymentMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PaymentMonitorService.KEY_DEMO_MODE, enabled).apply()
    }

    private fun updatePermissionStates() {
        hasOverlayPermission = isOverlayPermissionGranted(this)
        hasUsagePermission = isUsageAccessGranted(this)
        hasAccessibilityPermission = isAccessibilityServiceEnabled(this)
        hasSmsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        isCallActive = ThreatDetector.checkActivePhoneCall(this)
        detectedRemoteApp = ThreatDetector.checkActiveRemoteApp(this)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestUsageAccessPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestSmsPermission() {
        requestSmsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    private fun openAppInfoForRestrictedSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun triggerDebitOtpTest() {
        val sampleSms = "HDFC Bank Alert: OTP is 849201 for debit of INR 25,000.00 from a/c **4021. Do not share OTP with anyone."
        val analysis = DebitOtpEngine.analyze(sampleSms, "HDFCBK", this)
        val testIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, analysis.bankOrCard ?: "HDFC Bank")
            putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Bank Debit OTP: ${analysis.bankOrCard ?: "HDFC Bank"}")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.SUSPICIOUS_DEBIT_OTP.name)
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE, analysis.threatTitle)
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI, analysis.threatTitleHindi)
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC,
                "${analysis.explanation}\n\n• Debiting Amount: ₹25,000.00\n• OTP Code: 849201\n• Source: HDFC Bank (A/c **4021)"
            )
            putExtra(PaymentMonitorService.EXTRA_THREAT_DESC_HI, analysis.explanationHindi)
            putExtra(PaymentMonitorService.EXTRA_DETECTED_KEYWORD, "OTP: 849201 | ₹25,000")
            putExtra(PaymentMonitorService.EXTRA_FULL_TEXT, sampleSms)
            putExtra(PaymentMonitorService.EXTRA_ACTION_GUIDE, analysis.actionAdvice)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(testIntent)
        } else {
            startService(testIntent)
        }
    }

    private fun triggerActiveCallTest() {
        val testIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, "com.google.android.apps.nbu.paisa.user")
            putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Google Pay")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.ACTIVE_PHONE_CALL.name)
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE, "CRITICAL ALERT: Active Phone Call in Progress")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI, "गंभीर चेतावनी: चालू फोन कॉल के दौरान पेमेंट का प्रयास")
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC,
                "You are currently on a live phone call while opening a payment application. Cybercriminals keep victims on calls using urgency (fake electricity bills, customs, or bank freezes) to coerce them into authorizing UPI transfers."
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC_HI,
                "आप इस समय फोन कॉल पर बात कर रहे हैं। जालसाज अक्सर बिजली बिल कटने, बैंक या पुलिस का डर दिखाकर कॉल पर रखते हुए दबाव में यूपीआई पिन डलवाते हैं।"
            )
            putExtra(
                PaymentMonitorService.EXTRA_ACTION_GUIDE,
                "Please disconnect your phone call first. Legitimate authorities never demand payments while keeping you on the phone."
            )
            putExtra(PaymentMonitorService.EXTRA_DETECTED_KEYWORD, "active_phone_call")
            putExtra(PaymentMonitorService.EXTRA_FULL_TEXT, "Simulated call interception")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(testIntent)
        } else {
            startService(testIntent)
        }
    }

    private fun triggerRemoteScreenShareTest() {
        val testIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, "com.google.android.apps.nbu.paisa.user")
            putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Google Pay")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.REMOTE_SCREEN_SHARE.name)
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE, "HIGH THREAT: Screen-Sharing App Active")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TITLE_HI, "खतरे की चेतावनी: स्क्रीन शेयरिंग ऐप सक्रिय पाई गई")
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC,
                "The remote access tool 'AnyDesk Remote Desktop' is currently active on your device. Scammers use this to view your screen in real time, capture OTPs, and steal your UPI PIN."
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC_HI,
                "आपके फोन में 'AnyDesk Remote Desktop' स्क्रीन शेयरिंग ऐप सक्रिय है। जालसाज इसके जरिए आपकी स्क्रीन और यूपीआई पिन देखकर पैसे निकाल सकते हैं।"
            )
            putExtra(PaymentMonitorService.EXTRA_OFFENDING_PACKAGE, "com.anydesk.anydeskandroid")
            putExtra(PaymentMonitorService.EXTRA_OFFENDING_APP_NAME, "AnyDesk Remote Desktop")
            putExtra(
                PaymentMonitorService.EXTRA_ACTION_GUIDE,
                "Tap below to open App Info and Force Stop or Uninstall AnyDesk Remote Desktop immediately."
            )
            putExtra(PaymentMonitorService.EXTRA_DETECTED_KEYWORD, "AnyDesk Remote Desktop")
            putExtra(PaymentMonitorService.EXTRA_FULL_TEXT, "Simulated screen share interception")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(testIntent)
        } else {
            startService(testIntent)
        }
    }

    private fun triggerTestBlocker() {
        val testIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, "com.google.android.apps.nbu.paisa.user")
            putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Google Pay (Demo Sandbox)")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.DEMO_MODE.name)
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_TITLE,
                "⚠️ PAYMENT INTERCEPTED: HIGH RISK OF SCAM"
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_TITLE_HI,
                "⚠️ यूपीआई भुगतान रोका गया: धोखाधड़ी का गंभीर खतरा"
            )
            putExtra(
                PaymentMonitorService.EXTRA_REASON,
                "Detected dangerous keyword: 'urgent' / 'anydesk' in transaction note."
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC,
                "Detected dangerous keyword: 'urgent' / 'anydesk' in transaction note."
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC_HI,
                "ट्रांजेक्शन नोट में संदिग्ध 'urgent' / 'anydesk' शब्द पाए गए।"
            )
            putExtra(
                PaymentMonitorService.EXTRA_ACTION_GUIDE,
                "Do not enter your UPI PIN. Verify payee identity independently before transferring funds."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(testIntent)
        } else {
            startService(testIntent)
        }
    }

    private fun triggerQrBlockerTest(sampleQr: String) {
        val analysis = QrThreatEngine.analyze(sampleQr)
        val testIntent = Intent(this, BlockOverlayService::class.java).apply {
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
            startForegroundService(testIntent)
        } else {
            startService(testIntent)
        }
    }

    companion object {
        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun isUsageAccessGranted(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (PaymentMonitorService.isRunning) return true
            val expectedComponentName = ComponentName(context, PaymentMonitorService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}

/**
 * Clean Tab Model for Multi-Page Figma-Style App Architecture.
 */
enum class AppTab(
    val title: String,
    val icon: ImageVector
) {
    SHIELD("Shield", Icons.Default.Security),
    SCANNER("QR Guard", Icons.Default.QrCodeScanner),
    PERMISSIONS("Setup", Icons.Default.Lock),
    SANDBOX("Testing", Icons.Default.Science)
}

/**
 * Main Scaffolding Container managing multi-page tab routing and glassmorphism bottom navigation.
 */
@Composable
fun MainAppNavigation(
    isProtectionArmed: Boolean,
    onToggleMasterProtection: () -> Unit,
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    hasAccessibility: Boolean,
    hasSmsAccess: Boolean,
    isDemoMode: Boolean,
    isCallActive: Boolean,
    detectedRemoteApp: RemoteAppInfo?,
    onToggleDemoMode: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestSmsAccess: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onTestBlocker: () -> Unit,
    onTestQrBlocker: (String) -> Unit,
    onTestDebitOtp: () -> Unit,
    onTestActiveCall: () -> Unit,
    onTestRemoteShare: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.SHIELD) }
    val hasPendingPermissions = !hasOverlay || !hasUsageAccess || !hasAccessibility || !hasSmsAccess
    val hasLiveThreat = isCallActive || (detectedRemoteApp != null)

    Scaffold(
        containerColor = Color(0xFF070B14),
        bottomBar = {
            SurakshaBottomNavigationBar(
                currentTab = selectedTab,
                onTabSelected = { selectedTab = it },
                hasPendingPermissions = hasPendingPermissions,
                hasLiveThreat = hasLiveThreat
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.SHIELD -> ShieldScreen(
                    isProtectionArmed = isProtectionArmed,
                    onToggleMasterProtection = onToggleMasterProtection,
                    hasOverlay = hasOverlay,
                    hasUsageAccess = hasUsageAccess,
                    hasAccessibility = hasAccessibility,
                    hasSmsAccess = hasSmsAccess,
                    isDemoMode = isDemoMode,
                    isCallActive = isCallActive,
                    detectedRemoteApp = detectedRemoteApp,
                    onToggleDemoMode = onToggleDemoMode,
                    onNavigateToPermissions = { selectedTab = AppTab.PERMISSIONS }
                )

                AppTab.SCANNER -> QrScannerScreen(
                    onTestQrBlocker = onTestQrBlocker,
                    hasOverlay = hasOverlay
                )

                AppTab.PERMISSIONS -> PermissionsScreen(
                    hasOverlay = hasOverlay,
                    hasUsageAccess = hasUsageAccess,
                    hasAccessibility = hasAccessibility,
                    hasSmsAccess = hasSmsAccess,
                    onRequestOverlay = onRequestOverlay,
                    onRequestUsageAccess = onRequestUsageAccess,
                    onRequestAccessibility = onRequestAccessibility,
                    onRequestSmsAccess = onRequestSmsAccess,
                    onOpenAppInfo = onOpenAppInfo
                )

                AppTab.SANDBOX -> SandboxScreen(
                    hasOverlay = hasOverlay,
                    onRequestOverlay = onRequestOverlay,
                    onTestBlocker = onTestBlocker,
                    onTestDebitOtp = onTestDebitOtp,
                    onTestActiveCall = onTestActiveCall,
                    onTestRemoteShare = onTestRemoteShare
                )
            }
        }
    }
}

/**
 * Screen 1: ShieldScreen (Clean Hero Dashboard, Power Button & Live Sensors).
 */
@Composable
fun ShieldScreen(
    isProtectionArmed: Boolean,
    onToggleMasterProtection: () -> Unit,
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    hasAccessibility: Boolean,
    hasSmsAccess: Boolean,
    isDemoMode: Boolean,
    isCallActive: Boolean,
    detectedRemoteApp: RemoteAppInfo?,
    onToggleDemoMode: (Boolean) -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val isSystemProtected = hasOverlay && hasUsageAccess
    val isFullyGuarded = isSystemProtected && hasAccessibility && hasSmsAccess

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Protection Status Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Suraksha Guard",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Real-Time Payment, QR & OTP Sentinel",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (!isProtectionArmed) Color(0xFF330C12)
                        else if (isFullyGuarded) Color(0xFF042F2C)
                        else Color(0xFF331E05)
                    )
                    .border(
                        1.dp,
                        if (!isProtectionArmed) Color(0xFFEF4444)
                        else if (isFullyGuarded) Color(0xFF10B981)
                        else Color(0xFFF59E0B),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (!isProtectionArmed) "PAUSED"
                    else if (isFullyGuarded) "100% GUARDED"
                    else "SETUP NEEDED",
                    color = if (!isProtectionArmed) Color(0xFFFCA5A5)
                    else if (isFullyGuarded) Color(0xFF34D399)
                    else Color(0xFFFDE68A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // VPN Master Power Hero Button
        VpnPowerHeroCard(
            isArmed = isProtectionArmed,
            onToggle = onToggleMasterProtection
        )

        // Action prompt if permissions are incomplete
        if (!isFullyGuarded && isProtectionArmed) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD97706), RoundedCornerShape(14.dp))
                    .clickable(onClick = onNavigateToPermissions),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261905)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privileges Setup Incomplete",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap here to complete OS permissions and enable 100% protection.",
                            color = Color(0xFFD4D4D8),
                            fontSize = 10.5.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Live Environment Telemetry Sensors Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ENVIRONMENT SENSORS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isProtectionArmed) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                }

                SensorRow(
                    icon = Icons.Default.Call,
                    title = "Active Phone Call Sensor",
                    statusText = if (!isProtectionArmed)
                        "PAUSED (Power Switch OFF)"
                    else if (isCallActive)
                        "CALL IN PROGRESS (HIGH RISK)"
                    else
                        "IDLE / SAFE",
                    isSafe = isProtectionArmed && !isCallActive
                )

                SensorRow(
                    icon = Icons.AutoMirrored.Filled.ScreenShare,
                    title = "Remote Screen-Share Sensor",
                    statusText = if (!isProtectionArmed)
                        "PAUSED (Power Switch OFF)"
                    else if (detectedRemoteApp != null)
                        "DETECTED: ${detectedRemoteApp.displayName}"
                    else
                        "NO REMOTE TOOLS (SAFE)",
                    isSafe = isProtectionArmed && detectedRemoteApp == null
                )

                SensorRow(
                    icon = Icons.Default.QrCodeScanner,
                    title = "In-App Payment QR Sentinel",
                    statusText = if (!isProtectionArmed)
                        "PAUSED (Power Switch OFF)"
                    else if (hasAccessibility)
                        "ACTIVE (Google Pay / PhonePe Guarded)"
                    else
                        "INACTIVE (Setup Required)",
                    isSafe = isProtectionArmed && hasAccessibility
                )

                SensorRow(
                    icon = Icons.Default.Sms,
                    title = "Debit OTP Fraud Shield",
                    statusText = if (!isProtectionArmed)
                        "PAUSED (Power Switch OFF)"
                    else if (hasSmsAccess)
                        "ACTIVE (Real-Time Bank SMS Guarded)"
                    else
                        "INACTIVE (Setup Required)",
                    isSafe = isProtectionArmed && hasSmsAccess
                )
            }
        }

        // Mode Configuration Card: Live Protection vs Demo Mode
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isDemoMode) Color(0xFF0284C7) else Color(0xFF059669),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isDemoMode) Color(0xFF0A1C2C) else Color(0xFF06241E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDemoMode) "🧪 Demo Simulation Mode (Active)" else "🛡️ Live Fraud Shield (Guarding)",
                            color = if (isDemoMode) Color(0xFF38BDF8) else Color(0xFF34D399),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDemoMode)
                                "Always blocks payments when Google Pay or PhonePe is opened (for testing & presentations)."
                            else
                                "Only intercepts genuine threats (active calls, AnyDesk, phishing QRs, debit OTPs). Safe payments pass smoothly.",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                        )
                    }

                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = onToggleDemoMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Screen 2: QrScannerScreen (Dedicated QR Code Phishing & Scam Inspector).
 */
@Composable
fun QrScannerScreen(
    onTestQrBlocker: (String) -> Unit,
    hasOverlay: Boolean
) {
    var selectedQrDemo by remember { mutableStateOf<String?>(null) }
    var qrAnalysis by remember { mutableStateOf<QrThreatAnalysis?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Column {
            Text(
                text = "QR Code Fraud Detector",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Inspect and test how Suraksha Guard analyzes UPI deep-links to block QR traps before you authorize payments.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        // Preset Test Scenarios
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SELECT SCAM PATTERN TO TEST",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val qr = "upi://pay?pa=fake-bses-power@ybl&pn=BSES%20Electricity%20Discom&am=4999&cu=INR"
                            selectedQrDemo = qr
                            qrAnalysis = QrThreatEngine.analyze(qr)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B090F)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(text = "⚡ Fake Electricity", fontSize = 10.5.sp, color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val qr = "upi://pay?pa=scam-rewards99@paytm&pn=GooglePay%20Cashback%20Reward&am=2500&cu=INR&tn=Claim%20Cashback"
                            selectedQrDemo = qr
                            qrAnalysis = QrThreatEngine.analyze(qr)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B090F)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(text = "🎁 'Cashback' Trap", fontSize = 10.5.sp, color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val qr = "upi://pay?pa=customs-help@okaxis&pn=Customs%20Clearance&am=12000&url=http://bit.ly/fake-customs-apk"
                            selectedQrDemo = qr
                            qrAnalysis = QrThreatEngine.analyze(qr)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B090F)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(text = "🔗 Malicious Link", fontSize = 10.5.sp, color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val qr = "upi://pay?pa=kirana-store@icici&pn=Sharma%20Kirana%20Store&mc=5411&am=250&cu=INR"
                            selectedQrDemo = qr
                            qrAnalysis = QrThreatEngine.analyze(qr)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF042F2C)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(text = "✅ Clean Grocery", fontSize = 10.5.sp, color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Analysis Output Card
        if (qrAnalysis != null) {
            val analysis = qrAnalysis!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (analysis.isFraud) Color(0xFFEF4444) else Color(0xFF10B981),
                        RoundedCornerShape(14.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (analysis.isFraud) Color(0xFF26080D) else Color(0xFF052119)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (analysis.isFraud) "🚨 FRAUD DETECTED" else "🛡️ VERIFIED SAFE QR",
                            color = if (analysis.isFraud) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Text(
                            text = "Risk: ${analysis.riskScore}/100",
                            color = if (analysis.isFraud) Color(0xFFFCA5A5) else Color(0xFFA7F3D0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = analysis.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "• Payee: ${analysis.payeeName}\n• VPA: ${analysis.payeeVpa}\n• Type: ${analysis.fraudType}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF110306))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = analysis.explanation,
                                color = Color(0xFFF1F5F9),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = analysis.explanationHindi,
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    if (analysis.isFraud) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { selectedQrDemo?.let { onTestQrBlocker(it) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasOverlay,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (hasOverlay) "🛑 Preview Blocker Shield for this QR" else "Grant Overlay Permission First",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 3: PermissionsScreen (Dedicated Step-by-Step System Privileges Hub).
 */
@Composable
fun PermissionsScreen(
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    hasAccessibility: Boolean,
    hasSmsAccess: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestSmsAccess: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val totalGranted = listOf(hasOverlay, hasUsageAccess, hasAccessibility, hasSmsAccess).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "System Privileges & Setup",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Required Android OS permissions to monitor UPI applications, intercept screen sharing, and protect against OTP fraud.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        // Progress Overview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (totalGranted == 4) Color(0xFF10B981) else Color(0xFF0284C7),
                    RoundedCornerShape(14.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (totalGranted == 4) Color(0xFF042F2C) else Color(0xFF0A1C2C)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (totalGranted == 4) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (totalGranted == 4) Color(0xFF34D399) else Color(0xFF38BDF8),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (totalGranted == 4) "All 4 Privileges Granted" else "$totalGranted of 4 Privileges Granted",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalGranted == 4)
                            "Device is 100% armed and ready to protect your payments."
                        else
                            "Grant the remaining privileges below to enable complete protection.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Card 1: Draw Over Other Apps
        PermissionCard(
            title = "1. Display Over Other Apps",
            description = "Allows Suraksha Guard to draw the emergency safety shield over payment applications when a threat is active.",
            isGranted = hasOverlay,
            buttonText = "Grant Overlay Permission",
            onAction = onRequestOverlay
        )

        // Card 2: Usage Access Sentinel
        PermissionCard(
            title = "2. Usage Access Sentinel",
            description = "Enables the background service to detect when Google Pay, PhonePe, or Paytm is opened (Uses official Android AppLock API).",
            isGranted = hasUsageAccess,
            buttonText = "Enable Usage Access",
            onAction = onRequestUsageAccess
        )

        // Card 3: In-App QR Protection (Accessibility Service)
        PermissionCard(
            title = "3. In-App QR Protection (Google Pay, PhonePe)",
            description = "Inspects visible on-screen text nodes inside UPI payment apps to detect fraud keywords (urgent, AnyDesk, fake cashback). Enable 'SurakshaGuard' under Downloaded Apps / Installed Services.",
            isGranted = hasAccessibility,
            buttonText = "Enable 'SurakshaGuard' in Accessibility",
            onAction = onRequestAccessibility,
            secondaryButtonText = "🔓 Step 1: Open App Info (If Restricted)",
            onSecondaryAction = onOpenAppInfo,
            helpNotice = "⚠️ Android 13/14 Notice: If toggle is greyed out / restricted:\n1. Tap 'Step 1: Open App Info' above\n2. Tap the 3 dots (⋮) in top-right corner\n3. Tap 'Allow restricted settings'\n4. Return here and enable 'SurakshaGuard' in Accessibility"
        )

        // Card 4: Debit OTP Fraud Shield & Phone Call State
        PermissionCard(
            title = "4. Bank Debit OTP & Call Sensor",
            description = "Monitors incoming SMS for bank debit OTPs (money deductions) and verifies active phone call state during payment initiation.",
            isGranted = hasSmsAccess,
            buttonText = "Grant SMS & Call Detection Permission",
            onAction = onRequestSmsAccess
        )
    }
}

/**
 * Screen 4: SandboxScreen (Dedicated Testing & Simulation Lab).
 */
@Composable
fun SandboxScreen(
    hasOverlay: Boolean,
    onRequestOverlay: () -> Unit,
    onTestBlocker: () -> Unit,
    onTestDebitOtp: () -> Unit,
    onTestActiveCall: () -> Unit,
    onTestRemoteShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Threat Simulation Lab",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Preview the full-screen safety shields, vernacular Hindi warnings, and emergency disconnect actions safely without real attacks.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        if (!hasOverlay) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E090F)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚠️ Overlay Permission Required",
                        color = Color(0xFFFECACA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "To preview the full-screen safety shield, grant 'Display over other apps' first.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestOverlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Grant Overlay Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Test Launcher Cards Grid
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SELECT THREAT VECTOR TO SIMULATE",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                // 1. General Demo Blocker
                SimulationTestCard(
                    icon = Icons.Default.Layers,
                    title = "1. Payment Interception Shield",
                    description = "Simulates detecting urgent coercion while opening Google Pay.",
                    buttonText = "Launch Shield Preview",
                    buttonColor = Color(0xFFDC2626),
                    enabled = hasOverlay,
                    onLaunch = onTestBlocker
                )

                // 2. Bank Debit OTP SMS
                SimulationTestCard(
                    icon = Icons.Default.Sms,
                    title = "2. Bank Debit OTP Alert (₹25,000)",
                    description = "Simulates receiving an unauthorized bank deduction OTP SMS.",
                    buttonText = "Test Debit OTP Alert",
                    buttonColor = Color(0xFFB91C1C),
                    enabled = hasOverlay,
                    onLaunch = onTestDebitOtp
                )

                // 3. Active Phone Call
                SimulationTestCard(
                    icon = Icons.Default.Call,
                    title = "3. Active Phone Call Alert",
                    description = "Simulates opening a UPI app while talking on a phone call.",
                    buttonText = "Test Call Warning",
                    buttonColor = Color(0xFF991B1B),
                    enabled = hasOverlay,
                    onLaunch = onTestActiveCall
                )

                // 4. Remote Screen Share
                SimulationTestCard(
                    icon = Icons.AutoMirrored.Filled.ScreenShare,
                    title = "4. AnyDesk Screen-Share Alert",
                    description = "Simulates detecting AnyDesk broadcasting your screen with uninstall action.",
                    buttonText = "Test AnyDesk Alert",
                    buttonColor = Color(0xFF7F1D1D),
                    enabled = hasOverlay,
                    onLaunch = onTestRemoteShare
                )
            }
        }

        // Educational Safe Pass Info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ℹ️ Built-in Safe Pass Architecture",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Suraksha Guard features a 2-Minute 'I am Safe' pass on the shield overlay. If a user knowingly makes a genuine transaction, tapping the pass temporarily pauses interception for 120 seconds.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Modern Figma-Standard Glassmorphism Bottom Navigation Bar.
 */
@Composable
fun SurakshaBottomNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    hasPendingPermissions: Boolean,
    hasLiveThreat: Boolean
) {
    NavigationBar(
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFF1E293B))
    ) {
        AppTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    when (tab) {
                        AppTab.SHIELD -> {
                            if (hasLiveThreat) {
                                BadgedBox(badge = { Badge(containerColor = Color(0xFFEF4444)) }) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        }
                        AppTab.PERMISSIONS -> {
                            if (hasPendingPermissions) {
                                BadgedBox(badge = { Badge(containerColor = Color(0xFFF59E0B)) }) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        }
                        else -> {
                            Icon(tab.icon, contentDescription = tab.title)
                        }
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF10B981),
                    selectedTextColor = Color(0xFF10B981),
                    unselectedIconColor = Color(0xFF64748B),
                    unselectedTextColor = Color(0xFF64748B),
                    indicatorColor = Color(0xFF042F2C)
                )
            )
        }
    }
}

/**
 * Reusable Simulation Test Card Component.
 */
@Composable
fun SimulationTestCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    enabled: Boolean,
    onLaunch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF070B14))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            color = Color(0xFF94A3B8),
            fontSize = 10.5.sp,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onLaunch,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            Text(
                text = if (enabled) buttonText else "Grant Overlay Permission First",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VpnPowerHeroCard(
    isArmed: Boolean,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vpnPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isArmed) Color(0xFF059669) else Color(0xFF991B1B),
                RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isArmed) Color(0xFF041E1C) else Color(0xFF1F090E)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Capsule Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isArmed) Color(0xFF064E3B) else Color(0xFF3B1219)
                    )
                    .border(
                        1.dp,
                        if (isArmed) Color(0xFF10B981) else Color(0xFFEF4444),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isArmed) Color(0xFF34D399) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArmed) "SENTINEL ARMED & GUARDING" else "SENTINEL DISARMED / SLEEPING",
                        color = if (isArmed) Color(0xFFA7F3D0) else Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Large Circular VPN-Style Power Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(150.dp)
            ) {
                // Outer Pulsing Glow Aura
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(if (isArmed) pulseScale else 1.0f)
                        .clip(CircleShape)
                        .background(
                            if (isArmed) {
                                Color(0xFF10B981).copy(alpha = pulseAlpha * 0.35f)
                            } else {
                                Color(0xFFEF4444).copy(alpha = 0.12f)
                            }
                        )
                )

                // Middle Ring
                Box(
                    modifier = Modifier
                        .size(134.dp)
                        .clip(CircleShape)
                        .background(
                            if (isArmed) Color(0xFF042F2C) else Color(0xFF200C11)
                        )
                        .border(
                            2.dp,
                            if (isArmed) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.4f),
                            CircleShape
                        )
                )

                // Inner Main Circular Power Switch
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(
                            if (isArmed) {
                                Brush.radialGradient(
                                    listOf(Color(0xFF059669), Color(0xFF064E3B), Color(0xFF022C22))
                                )
                            } else {
                                Brush.radialGradient(
                                    listOf(Color(0xFF3B1219), Color(0xFF260D12), Color(0xFF17080B))
                                )
                            }
                        )
                        .border(
                            3.dp,
                            if (isArmed) Color(0xFF34D399) else Color(0xFFEF4444),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = true, radius = 58.dp),
                            onClick = onToggle
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (isArmed) "Disarm Protection" else "Arm Protection",
                        tint = if (isArmed) Color.White else Color(0xFFF87171),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title & Instruction
            Text(
                text = if (isArmed) "PROTECTION IS ACTIVE" else "PROTECTION IS PAUSED",
                color = if (isArmed) Color.White else Color(0xFFFCA5A5),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isArmed)
                    "Tap button to pause background monitoring. When armed, Google Pay, PhonePe, Paytm & bank debit OTPs are guarded."
                else
                    "Tap button to arm background monitoring. While paused, payment apps and OTPs will NOT be intercepted.",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
fun SensorRow(
    icon: ImageVector,
    title: String,
    statusText: String,
    isSafe: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSafe) Color(0xFF34D399) else Color(0xFFEF4444),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusText,
                    color = if (isSafe) Color(0xFF34D399) else Color(0xFFEF4444),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    helpNotice: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) Color(0xFF1E293B) else Color(0xFF78350F),
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isGranted) Color(0xFF064E3B) else Color(0xFF451A03))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isGranted) "GRANTED" else "PENDING",
                        color = if (isGranted) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            if (!isGranted) {
                if (helpNotice != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF261905))
                            .border(1.dp, Color(0xFFD97706), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = helpNotice,
                            color = Color(0xFFFDE68A),
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (secondaryButtonText != null && onSecondaryAction != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSecondaryAction,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = secondaryButtonText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
