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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Suraksha Guard Setup, Monitoring & QR Phishing Interceptor Activity.
 */
class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasUsagePermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var hasSmsPermission by mutableStateOf(false)
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
                    background = Color(0xFF0A0A1A),
                    surface = Color(0xFF111827),
                    primary = Color(0xFF10B981),
                    error = Color(0xFFEF4444)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0A1A)
                ) {
                    SetupScreen(
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
                        onTestDebitOtp = { triggerDebitOtpTest() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPreferences()
        updatePermissionStates()
        ensureSentinelRunning()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PaymentMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        isDemoMode = prefs.getBoolean(PaymentMonitorService.KEY_DEMO_MODE, false)
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

    private fun ensureSentinelRunning() {
        // PaymentMonitorService is an AccessibilityService bound and managed directly
        // by the Android Accessibility Framework once enabled in system settings.
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
                Manifest.permission.READ_SMS
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

@Composable
fun SetupScreen(
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
    onTestDebitOtp: () -> Unit
) {
    val isSystemProtected = hasOverlay && hasUsageAccess
    val isFullyGuarded = isSystemProtected && hasAccessibility && hasSmsAccess
    var selectedQrDemo by remember { mutableStateOf<String?>(null) }
    var qrAnalysis by remember { mutableStateOf<QrThreatAnalysis?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
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
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Real-Time Payment, QR & Debit OTP Interceptor",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = if (isFullyGuarded) Color(0xFF10B981) else if (isSystemProtected) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // System Protection Status Badge Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isFullyGuarded) Color(0xFF059669) else if (isSystemProtected) Color(0xFF0284C7) else Color(0xFFD97706),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isFullyGuarded) Color(0xFF06281E) else if (isSystemProtected) Color(0xFF0B1E2E) else Color(0xFF2E1B05)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFullyGuarded) Icons.Default.CheckCircle else if (isSystemProtected) Icons.Default.Security else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isFullyGuarded) Color(0xFF34D399) else if (isSystemProtected) Color(0xFF38BDF8) else Color(0xFFFBBF24),
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = when {
                            isFullyGuarded -> "100% COMPLETE PROTECTION ARMED"
                            isSystemProtected -> "CORE PROTECTION ARMED"
                            else -> "SETUP REQUIRED"
                        },
                        color = when {
                            isFullyGuarded -> Color(0xFF34D399)
                            isSystemProtected -> Color(0xFF38BDF8)
                            else -> Color(0xFFFBBF24)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = when {
                            isFullyGuarded -> if (isDemoMode)
                                "Demo Mode active: Payment apps will be blocked for testing."
                            else
                                "Complete Protection: Screen-sharing, active calls, In-App QR scans, and bank debit OTPs are all guarded."
                            isSystemProtected -> "Core protection active. Enable In-App QR and SMS OTP protection below for complete security."
                            else -> "Grant the permissions below to arm background protection."
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
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
                containerColor = if (isDemoMode) Color(0xFF0B1E2E) else Color(0xFF081F1A)
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
                            text = if (isDemoMode) "🧪 Demo Simulation Mode (ON)" else "🛡️ Live Fraud Protection (Active)",
                            color = if (isDemoMode) Color(0xFF38BDF8) else Color(0xFF34D399),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDemoMode)
                                "Always blocks payments when Google Pay/Paytm is opened (for hackathon demo)."
                            else
                                "Only blocks when an active call, screen share, or fraud QR is detected. Safe payments pass smoothly.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
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

        // Live Threat Telemetry Sensors Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "LIVE ENVIRONMENT SENSORS",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                SensorRow(
                    icon = Icons.Default.Call,
                    title = "Active Phone Call Sensor",
                    statusText = if (isCallActive) "CALL IN PROGRESS (HIGH RISK)" else "IDLE / SAFE",
                    isSafe = !isCallActive
                )

                SensorRow(
                    icon = Icons.AutoMirrored.Filled.ScreenShare,
                    title = "Remote Screen-Share Sensor",
                    statusText = if (detectedRemoteApp != null)
                        "DETECTED: ${detectedRemoteApp.displayName}"
                    else
                        "NO REMOTE TOOLS (SAFE)",
                    isSafe = detectedRemoteApp == null
                )

                SensorRow(
                    icon = Icons.Default.QrCodeScanner,
                    title = "In-App Payment QR Sentinel",
                    statusText = if (hasAccessibility)
                        "ACTIVE (Google Pay / PhonePe Shielded)"
                    else
                        "INACTIVE (Enable Permission #3)",
                    isSafe = hasAccessibility
                )

                SensorRow(
                    icon = Icons.Default.Sms,
                    title = "Debit OTP Fraud Shield",
                    statusText = if (hasSmsAccess)
                        "ACTIVE (Real-Time Bank SMS Guarded)"
                    else
                        "INACTIVE (Enable Permission #4)",
                    isSafe = hasSmsAccess
                )
            }
        }

        // ── QR Code Fraud & Phishing Interceptor Showcase ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1928)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "QR CODE PHISHING & FRAUD DETECTOR",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Suraksha Guard intercepts UPI deep-links whenever a QR is scanned. Test common Indian QR scams below:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                // Quick Preset Buttons for Testing Scam QRs
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
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(text = "⚡ Fake Electricity QR", fontSize = 10.sp, color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val qr = "upi://pay?pa=scam-rewards99@paytm&pn=GooglePay%20Cashback%20Reward&am=2500&cu=INR&tn=Claim%20Cashback"
                            selectedQrDemo = qr
                            qrAnalysis = QrThreatEngine.analyze(qr)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B090F)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(text = "🎁 'Cashback' Trap QR", fontSize = 10.sp, color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(text = "🔗 Malicious Link QR", fontSize = 10.sp, color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val qr = "upi://pay?pa=kirana-store@icici&pn=Sharma%20Kirana%20Store&mc=5411&am=250&cu=INR"
                            selectedQrDemo = qr
                            qrAnalysis = QrThreatEngine.analyze(qr)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF063628)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(text = "✅ Clean Grocery QR", fontSize = 10.sp, color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold)
                    }
                }

                // Display Live Analysis Result
                if (qrAnalysis != null) {
                    val analysis = qrAnalysis!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (analysis.isFraud) Color(0xFFEF4444) else Color(0xFF10B981),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (analysis.isFraud) Color(0xFF1F0609) else Color(0xFF041C15)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (analysis.isFraud) "🚨 FRAUD QR DETECTED" else "✅ VERIFIED SAFE QR",
                                    color = if (analysis.isFraud) Color(0xFFEF4444) else Color(0xFF34D399),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (analysis.isFraud) Color(0xFFDC2626) else Color(0xFF059669))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Risk: ${analysis.riskScore}/100",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = analysis.title,
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "• Payee: ${analysis.payeeName} (${analysis.payeeVpa})\n• Amount: ${if (analysis.requestedAmount != null) "₹" + analysis.requestedAmount else "Variable"}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )

                            Text(
                                text = analysis.explanation,
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )

                            if (analysis.isFraud) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { onTestQrBlocker(selectedQrDemo!!) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Simulate Full Blocker for this Fraud QR",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "REQUIRED OS PRIVILEGES",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // Card 1: Draw Over Other Apps (Overlay Permission)
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
            title = "3. In-App QR Protection (Google Pay, PhonePe, Paytm)",
            description = "Inspects visible on-screen text nodes inside UPI payment apps to detect fraud keywords (urgent, AnyDesk, fake cashback). Enable 'SurakshaGuard' under Downloaded Apps / Installed Services.",
            isGranted = hasAccessibility,
            buttonText = "Enable 'SurakshaGuard' in Accessibility",
            onAction = onRequestAccessibility,
            secondaryButtonText = "🔓 Step 1: Open App Info (If Restricted)",
            onSecondaryAction = onOpenAppInfo,
            helpNotice = "⚠️ Android 13/14 Notice: If toggle is greyed out / restricted:\n1. Tap 'Step 1: Open App Info' above\n2. Tap the 3 dots (⋮) in top-right corner\n3. Tap 'Allow restricted settings'\n4. Return here and enable 'SurakshaGuard' in Accessibility"
        )

        // Card 4: Debit OTP Fraud Shield (SMS Receiver)
        PermissionCard(
            title = "4. Bank Debit OTP Fraud Shield",
            description = "Monitors incoming SMS for bank debit OTPs (money being deducted). Instantly intercepts unauthorized deductions and phone-call coercion.",
            isGranted = hasSmsAccess,
            buttonText = "Grant SMS Detection Permission",
            onAction = onRequestSmsAccess
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Demo Sandbox: Immediate Simulation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF3730A3), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TEST BLOCKER SHIELD OVERLAY",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Instantly preview the full-screen safety shield, threat explanations, resolution buttons, and 2-min pass.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onTestBlocker,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasOverlay) "Launch Shield Overlay Preview" else "Grant Overlay Permission First",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onTestDebitOtp,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasOverlay) "🧪 Test Debit OTP Alert (₹25,000)" else "Grant Overlay Permission First",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
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
