// ─────────────────────────────────────────────────────────────────────────────
// SurakshaPay — Heuristic & NLP Risk Scoring Engine
// ─────────────────────────────────────────────────────────────────────────────
//
// Architecture Note (Real-World Android Mapping):
// ─────────────────────────────────────────────────
// In production, this engine would run on-device using:
//   1. Android CallScreeningService — intercepts incoming calls before ringing
//   2. SpeechRecognizer API — real-time speech-to-text for call audio
//   3. ML Kit / TFLite — on-device NLP model for intent classification
//   4. AccessibilityService — monitors UPI app state and PIN entry screens
//
// For the hackathon demo, we use deterministic regex heuristics that match
// real-world Indian social engineering keyword patterns collected from
// reported scam databases (I4C, Cybercrime.gov.in).
// ─────────────────────────────────────────────────────────────────────────────

import type { RiskBreakdown, ThreatLevel } from '../types';

// ─── Indian Social Engineering Keyword Database ──────────────────────────────
// Sourced from real scam patterns reported to Indian Cyber Crime portal

/** Urgency triggers — create artificial time pressure */
const URGENCY_KEYWORDS: string[] = [
  // Hindi urgency
  'bijli kat jayegi',
  'bijli katne',
  'turant payment',
  'abhi pay karo',
  'jaldi karo',
  'account band ho jayega',
  'sim band',
  'sim deactivate',
  'warrant issue',
  'arrest warrant',
  'police complaint',
  'legal action hoga',
  'court case',
  'fir darj',
  // English urgency
  'immediate debit',
  'account block',
  'account suspended',
  'urgent payment',
  'power disconnection',
  'service terminated',
  'last warning',
  'final notice',
  'penalty charges',
  'overdue amount',
  'clearance required',
  'within 10 minute',
  'within 15 minute',
  'within 1 hour',
  'expire ho jayega',
];

/** Authority impersonation — pretending to be officials */
const AUTHORITY_KEYWORDS: string[] = [
  'sbi officer',
  'rbi',
  'trai',
  'mumbai police',
  'cyber cell',
  'customs department',
  'electricity board',
  'income tax',
  'ed officer',
  'enforcement directorate',
  'narcotics',
  'cbi',
  'government official',
  'bank manager',
  'head office',
  'telecom authority',
  'discom',
  'bses',
  'ndpl',
];

/** Coercion actions — dangerous instructions given to victim */
const COERCION_KEYWORDS: string[] = [
  'download apk',
  'install apk',
  'quicksupport',
  'anydesk',
  'teamviewer',
  'forward otp',
  'share otp',
  'otp forward',
  'enter pin',
  'upi pin',
  'pin to receive',
  'pin enter karo',
  'cashback',
  'refund process',
  'verification payment',
  'kyc update',
  'click link',
  'click here',
  'verify account',
];

/** Known dangerous screen-sharing / remote access apps */
const DANGEROUS_APPS: string[] = [
  'anydesk',
  'teamviewer',
  'quicksupport',
  'airdroid',
  'screen mirroring',
  'remote desktop',
  'vnc viewer',
  'rustdesk',
];

// ─── Core Analysis Functions ─────────────────────────────────────────────────

/**
 * Analyzes transcript text for social engineering patterns using
 * multi-category keyword matching with weighted scoring.
 *
 * @param transcript - Call transcript or SMS text to analyze
 * @returns Urgency score (0-100) and list of detected keyword triggers
 */
export function analyzeTranscript(transcript: string): {
  urgencyScore: number;
  detectedKeywords: string[];
} {
  const lower = transcript.toLowerCase();
  const detected: string[] = [];
  let rawScore = 0;

  // Scan urgency keywords (high weight — time pressure is the #1 scam signal)
  for (const kw of URGENCY_KEYWORDS) {
    if (lower.includes(kw)) {
      detected.push(`🔴 Urgency: "${kw}"`);
      rawScore += 20;
    }
  }

  // Scan authority impersonation (medium-high — builds false trust)
  for (const kw of AUTHORITY_KEYWORDS) {
    if (lower.includes(kw)) {
      detected.push(`🟠 Authority: "${kw}"`);
      rawScore += 18;
    }
  }

  // Scan coercion actions (high — direct dangerous instructions)
  for (const kw of COERCION_KEYWORDS) {
    if (lower.includes(kw)) {
      detected.push(`🔴 Coercion: "${kw}"`);
      rawScore += 25;
    }
  }

  return {
    urgencyScore: Math.min(rawScore, 100),
    detectedKeywords: detected,
  };
}

/**
 * Analyzes active apps for screen-sharing / remote-access threats.
 *
 * Real Android implementation:
 *   - UsageStatsManager.queryUsageStats() for active app detection
 *   - MediaProjectionManager callbacks for screen recording detection
 *   - AccessibilityService.onAccessibilityEvent() for overlay detection
 *   - PackageManager queries for side-loaded APK detection
 */
export function analyzeDeviceState(activeApps: string[]): {
  deviceRiskScore: number;
  detectedApps: string[];
} {
  const detected: string[] = [];
  let score = 0;

  for (const app of activeApps) {
    const lower = app.toLowerCase();
    for (const dangerous of DANGEROUS_APPS) {
      if (lower.includes(dangerous)) {
        detected.push(`⚠️ ${app}`);
        score += 50; // Each dangerous app is a major risk signal
      }
    }
  }

  return {
    deviceRiskScore: Math.min(score, 100),
    detectedApps: detected,
  };
}

/**
 * Evaluates VPA (Virtual Payment Address) trustworthiness.
 *
 * Real implementation would:
 *   - Query NPCI VPA directory for merchant verification status
 *   - Check against local trusted merchant database
 *   - Evaluate VPA string for scam-associated patterns
 *   - Cross-reference with community threat reports
 */
export function analyzeVPA(vpa: string, isVerified: boolean): number {
  if (isVerified) return 10; // Verified merchants get minimal risk

  // Suspicious VPA naming patterns commonly used in scams
  const suspiciousPatterns: RegExp[] = [
    /\d{5,}/,       // Many consecutive digits (random-looking)
    /payment\d+/i,  // Generic "payment" + number
    /refund/i,      // "refund" in VPA (common cashback scam)
    /cashback/i,    // "cashback" in VPA
    /kyc/i,         // "KYC" in VPA (KYC update scam)
    /process\d*/i,  // "process" + digits
  ];

  let score = 55; // Base score for any unverified VPA

  for (const pattern of suspiciousPatterns) {
    if (pattern.test(vpa)) {
      score += 12;
    }
  }

  return Math.min(score, 100);
}

// ─── Threat Level Classification ─────────────────────────────────────────────

function getThreatLevel(score: number): ThreatLevel {
  if (score <= 20) return 'SAFE';
  if (score <= 40) return 'LOW';
  if (score <= 55) return 'MEDIUM';
  if (score <= 65) return 'HIGH';
  return 'CRITICAL'; // Score > 65 → pre-authorization freeze triggered
}

// ─── Multi-Factor Risk Scoring Engine ────────────────────────────────────────

/**
 * Computes the composite risk score by combining three independent signal channels:
 *
 *   Channel 1 — NLP/Heuristic Analysis (35% weight)
 *     Scans call transcripts and SMS for urgency, authority impersonation,
 *     and coercion keywords in Hindi and English.
 *
 *   Channel 2 — Device State Analysis (35% weight)
 *     Detects active screen-sharing apps (AnyDesk, TeamViewer, etc.)
 *     and suspicious accessibility permissions.
 *
 *   Channel 3 — VPA Reputation (30% weight)
 *     Checks if the payment recipient is a verified merchant or matches
 *     known scam VPA patterns.
 *
 * If totalScore > CRITICAL_THRESHOLD (65), the state machine transitions
 * to PRE_AUTH_FROZEN, intercepting the transaction before PIN entry.
 */
export function computeRiskScore(
  transcript: string,
  activeApps: string[],
  recipientVPA: string,
  isVPAVerified: boolean
): RiskBreakdown {
  const textAnalysis = analyzeTranscript(transcript);
  const deviceAnalysis = analyzeDeviceState(activeApps);
  const vpaScore = analyzeVPA(recipientVPA, isVPAVerified);

  // Weighted composite: NLP 35% + Device 35% + VPA 30%
  const totalScore = Math.round(
    textAnalysis.urgencyScore * 0.35 +
    deviceAnalysis.deviceRiskScore * 0.35 +
    vpaScore * 0.30
  );

  return {
    urgencyScore: textAnalysis.urgencyScore,
    deviceRiskScore: deviceAnalysis.deviceRiskScore,
    vpaRiskScore: vpaScore,
    totalScore: Math.min(totalScore, 100),
    detectedKeywords: textAnalysis.detectedKeywords,
    detectedApps: deviceAnalysis.detectedApps,
    vpaVerified: isVPAVerified,
    threatLevel: getThreatLevel(Math.min(totalScore, 100)),
  };
}

/** Critical threshold — scores above this trigger a pre-authorization freeze */
export const CRITICAL_THRESHOLD = 65;

