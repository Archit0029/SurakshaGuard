import type { ScamScenario } from '../types';

// ─────────────────────────────────────────────────────────────────────────────
// Pre-built Scam Scenarios for Live Demo
// Each scenario simulates a real-world Indian social engineering pattern
// ─────────────────────────────────────────────────────────────────────────────

export const SCENARIOS: ScamScenario[] = [
  // ── Scenario 1: Safe Transaction ────────────────────────────────────────
  {
    id: 'normal',
    name: 'Normal Transaction',
    description:
      'Paying ₹150 for groceries to a verified local kirana merchant. No suspicious signals.',
    icon: 'ShoppingCart',
    transcript:
      'Customer purchasing groceries at a local kirana store. Regular transaction with known merchant.',
    activeApps: [],
    recipientVPA: 'ramkirana.store@upi',
    recipientName: 'Ram Kirana Store ✓',
    amount: 150,
    isVPAVerified: true,
    threatColor: 'green',
  },

  // ── Scenario 2: Electricity Bill Scam ───────────────────────────────────
  {
    id: 'electricity-scam',
    name: 'Electricity Bill Disconnection Scam',
    description:
      'High urgency SMS + live call: "Pay ₹10 within 15 min or your bijli kat jayegi!"',
    icon: 'Zap',
    transcript: `Caller: "Hello ji, main BSES Electricity Board se bol raha hoon. Aapka bijli ka bill overdue hai.
Aapki bijli kat jayegi within 15 minutes if you don't make immediate payment right now.
Yeh aapka final notice hai — account block ho jayega permanently.
Turant ₹10 hamare DISCOM officer ke UPI ID par pay karo.
Main government official hoon, Electricity Board se, this is last warning.
Agar 10 minute mein payment nahi hua, toh aapke area mein power disconnection ho jayega."`,
    smsContent:
      '⚠️ URGENT: Your electricity connection (CA: 30291XXXX) will be TERMINATED in 15 minutes due to non-payment. Pay ₹10 immediately to avoid disconnection. Contact: 9876XXXXXX. — BSES DISCOM (Final Notice)',
    callerID: '+91-9876XXXXXX (Unknown Number)',
    activeApps: [],
    recipientVPA: 'discom.payment92@okaxis',
    recipientName: 'DISCOM Payment Dept',
    amount: 10,
    isVPAVerified: false,
    threatColor: 'red',
  },

  // ── Scenario 3: AnyDesk Remote Access Scam ──────────────────────────────
  {
    id: 'anydesk-scam',
    name: 'AnyDesk / Screen-Share Remote Access Scam',
    description:
      'Active screen mirroring detected + caller asks user to enter UPI PIN to "receive ₹5000 cashback"',
    icon: 'Monitor',
    transcript: `Caller: "Hello sir, main SBI Bank head office se SBI officer bol raha hoon.
Aapke account mein ₹5000 ka cashback refund aaya hai RBI scheme se.
Cashback receive karne ke liye please AnyDesk app download karo aur 9-digit code share karo.
Ab apna UPI app kholo aur enter PIN karo to receive the cashback amount.
Don't worry sir, main bank ka SBI officer hoon, yeh standard refund process hai.
Jaldi karo, PIN enter karo, refund 10 minute mein expire ho jayega.
OTP aayega toh woh bhi forward kar dena for verification."`,
    smsContent:
      'SBI: Cashback of ₹5,000 credited to your a/c. Install QuickSupport app for verification. Ref: SBI/CB/2026/KYC. Do not share with anyone.',
    callerID: '+91-8765XXXXXX (Claimed: SBI Bank)',
    activeApps: ['AnyDesk Remote Desktop', 'Screen Mirroring Service — Active'],
    recipientVPA: 'refund.process841@ybl',
    recipientName: 'SBI Refund Cell',
    amount: 5000,
    isVPAVerified: false,
    threatColor: 'red',
  },
];

