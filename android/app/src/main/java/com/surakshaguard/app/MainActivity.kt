package com.surakshaguard.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Security
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
 * SurakshaGuard Setup & Permission Onboarding Activity.
 *
 * Provides a clean Jetpack Compose interface allowing users to:
 * 1. Grant Overlay Permission (Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
 * 2. Grant Usage Access Sentinel (Settings.ACTION_USAGE_ACCESS_SETTINGS)
 * 3. Toggle between "Live Threat Protection" (Default) and "Demo Simulation Mode"
 * 4. Monitor live threat detection sensors in real time.
 */
class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasUsagePermission by mutableStateOf(false)
    private var isDemoMode by mutableStateOf(false)
    private var isCallActive by mutableStateOf(false)
    private var detectedRemoteApp by mutableStateOf<RemoteAppInfo?>(null)

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
                        isDemoMode = isDemoMode,
                        isCallActive = isCallActive,
                        detectedRemoteApp = detectedRemoteApp,
                        onToggleDemoMode = { enabled ->
                            isDemoMode = enabled
                            saveDemoMode(enabled)
                        },
                        onRequestOverlay = { requestOverlayPermission() },
                        onRequestUsageAccess = { requestUsageAccessPermission() },
                        onTestBlocker = { triggerTestBlocker() }
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
        isCallActive = ThreatDetector.checkActivePhoneCall(this)
        detectedRemoteApp = ThreatDetector.checkActiveRemoteApp(this)
    }

    private fun ensureSentinelRunning() {
        if (hasOverlayPermission && hasUsagePermission && !PaymentMonitorService.isRunning) {
            val serviceIntent = Intent(this, PaymentMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
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

    private fun triggerTestBlocker() {
        val testIntent = Intent(this, BlockOverlayService::class.java).apply {
            putExtra(PaymentMonitorService.EXTRA_TARGET_PACKAGE, "com.google.android.apps.nbu.paisa.user")
            putExtra(PaymentMonitorService.EXTRA_TARGET_APP_NAME, "Google Pay (Demo Sandbox)")
            putExtra(PaymentMonitorService.EXTRA_THREAT_TYPE, ThreatType.DEMO_MODE.name)
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_TITLE,
                "DEMO TEST: Payment Interceptor Triggered"
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_TITLE_HI,
                "डेमो टेस्ट: यूपीआई पेमेंट सुरक्षा इंटरसेप्टर सक्रिय"
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC,
                "This is a demonstration of SurakshaGuard's real-time interception shield. In live mode, it only blocks if an active phone call or screen-sharing application is detected."
            )
            putExtra(
                PaymentMonitorService.EXTRA_THREAT_DESC_HI,
                "यह सुरक्षागार्ड के सुरक्षा शील्ड का लाइव परीक्षण है। सामान्य उपयोग में यह केवल फोन कॉल या स्क्रीन शेयरिंग चालू होने पर ही रोकता है।"
            )
            putExtra(
                PaymentMonitorService.EXTRA_ACTION_GUIDE,
                "Test successful. Use 'I am Safe' or 'Dismiss' to close."
            )
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
    }
}

@Composable
fun SetupScreen(
    hasOverlay: Boolean,
    hasUsageAccess: Boolean,
    isDemoMode: Boolean,
    isCallActive: Boolean,
    detectedRemoteApp: RemoteAppInfo?,
    onToggleDemoMode: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onTestBlocker: () -> Unit
) {
    val isSystemProtected = hasOverlay && hasUsageAccess

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
                    text = "SurakshaGuard",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Real-Time Payment Interceptor Sentinel",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = if (isSystemProtected) Color(0xFF10B981) else Color(0xFFF59E0B),
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
                    if (isSystemProtected) Color(0xFF059669) else Color(0xFFD97706),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSystemProtected) Color(0xFF06281E) else Color(0xFF2E1B05)
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
                    imageVector = if (isSystemProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSystemProtected) Color(0xFF34D399) else Color(0xFFFBBF24),
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (isSystemProtected) "SYSTEM ARMED & ACTIVE" else "SETUP REQUIRED",
                        color = if (isSystemProtected) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isSystemProtected)
                            if (isDemoMode)
                                "Demo Mode active: Payment apps will be blocked for testing."
                            else
                                "Live Protection: Payments are shielded against active calls & screen sharing."
                        else
                            "Grant the two permissions below to arm background protection.",
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
                                "Only blocks when an active call or screen-sharing app is detected. Safe payments pass smoothly.",
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
            description = "Allows SurakshaGuard to draw the emergency safety shield over payment applications when a threat is active.",
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
    onAction: () -> Unit
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
                Spacer(modifier = Modifier.height(12.dp))
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
