// ─────────────────────────────────────────────────────────────────────────────
// SurakshaPay — Device State Analyzer (Simulated)
// ─────────────────────────────────────────────────────────────────────────────
//
// In a real Android implementation, this module would use:
//
//   1. UsageStatsManager.queryUsageStats()
//      → Detects recently used apps (AnyDesk, TeamViewer, etc.)
//
//   2. MediaProjectionManager.getMediaProjection()
//      → Detects active screen recording / casting sessions
//
//   3. Settings.canDrawOverlays() + AccessibilityService
//      → Detects overlay apps that could intercept touch events
//
//   4. PackageManager.getInstalledApplications()
//      → Detects side-loaded APKs not from Play Store
//      → Checks ApplicationInfo.FLAG_EXTERNAL_STORAGE
//
//   5. ActivityManager.getRunningAppProcesses()
//      → Monitors foreground/background app states in real-time
//
// For the hackathon, we simulate these checks with a deterministic
// function that accepts a list of "active apps" from the scenario config.
// ─────────────────────────────────────────────────────────────────────────────

/** Information about a potentially dangerous app detected on the device */
export interface DetectedApp {
  name: string;
  packageId: string;
  riskCategory: 'screen_sharing' | 'remote_access' | 'sideloaded_apk' | 'overlay';
  riskLevel: 'high' | 'critical';
  description: string;
}

/** Simulated device state report */
export interface DeviceStateReport {
  isScreenBeingShared: boolean;
  isRemoteAccessActive: boolean;
  hasSuspiciousOverlays: boolean;
  detectedDangerousApps: DetectedApp[];
  /** Overall device risk score 0-100 */
  deviceRiskScore: number;
}

/** Database of known dangerous apps and their metadata */
const DANGEROUS_APP_DB: Record<string, Omit<DetectedApp, 'name'>> = {
  anydesk: {
    packageId: 'com.anydesk.anydeskandroid',
    riskCategory: 'remote_access',
    riskLevel: 'critical',
    description: 'Remote desktop app — attacker can see your screen and control your device',
  },
  teamviewer: {
    packageId: 'com.teamviewer.teamviewer.market.mobile',
    riskCategory: 'remote_access',
    riskLevel: 'critical',
    description: 'Remote control app — full device access possible',
  },
  quicksupport: {
    packageId: 'com.teamviewer.quicksupport.market',
    riskCategory: 'remote_access',
    riskLevel: 'critical',
    description: 'TeamViewer QuickSupport — enables remote device control',
  },
  'screen mirroring': {
    packageId: 'com.screen.mirroring.cast',
    riskCategory: 'screen_sharing',
    riskLevel: 'high',
    description: 'Screen mirroring active — your screen is visible to another device',
  },
  airdroid: {
    packageId: 'com.sand.airdroid',
    riskCategory: 'remote_access',
    riskLevel: 'high',
    description: 'Remote file/screen access app active',
  },
};

/**
 * Simulates a comprehensive device security scan.
 *
 * @param activeApps - List of currently active app names (from scenario)
 * @returns Complete device state security report
 */
export function analyzeDeviceState(activeApps: string[]): DeviceStateReport {
  const detectedApps: DetectedApp[] = [];
  let isScreenBeingShared = false;
  let isRemoteAccessActive = false;

  for (const app of activeApps) {
    const lower = app.toLowerCase();

    for (const [keyword, metadata] of Object.entries(DANGEROUS_APP_DB)) {
      if (lower.includes(keyword)) {
        detectedApps.push({ name: app, ...metadata });

        if (metadata.riskCategory === 'screen_sharing') {
          isScreenBeingShared = true;
        }
        if (metadata.riskCategory === 'remote_access') {
          isRemoteAccessActive = true;
          isScreenBeingShared = true; // Remote access implies screen sharing
        }
      }
    }
  }

  // Score: each dangerous app adds 40-50 points
  const deviceRiskScore = Math.min(
    detectedApps.reduce(
      (sum, app) => sum + (app.riskLevel === 'critical' ? 50 : 35),
      0
    ),
    100
  );

  return {
    isScreenBeingShared,
    isRemoteAccessActive,
    hasSuspiciousOverlays: false, // Would use canDrawOverlays() on real device
    detectedDangerousApps: detectedApps,
    deviceRiskScore,
  };
}

