// ─────────────────────────────────────────────────────────────────────────────
// SurakshaPay — Vernacular Explainability & Voice Alert Agent
// ─────────────────────────────────────────────────────────────────────────────
//
// Generates context-aware warning messages in Hindi and English,
// explaining WHY a transaction was blocked in simple, non-technical language.
//
// Voice synthesis uses the Web Speech API (speechSynthesis), which is
// available on all modern browsers and Android WebView. In a production
// Android app, this would use:
//   - Android TextToSpeech (TTS) API with Hindi language pack
//   - Google Cloud TTS for higher quality voice synthesis
//   - Offline TTS engine for low-connectivity scenarios
// ─────────────────────────────────────────────────────────────────────────────

import type { RiskBreakdown, VernacularWarning } from '../types';

/** Mapping of detected threat patterns to vernacular explanations */
interface ThreatExplanation {
  pattern: RegExp;
  english: string;
  hindi: string;
}

const THREAT_EXPLANATIONS: ThreatExplanation[] = [
  // Electricity scam
  {
    pattern: /bijli|electricity|power disconnection|discom|bses/i,
    english:
      'Suspicious call detected claiming your power will be disconnected. Real electricity departments never demand immediate UPI payments over phone calls.',
    hindi:
      'बिजली बिल कटने के नाम पर आपसे दबाव में पैसे ट्रांसफर करवाए जा रहे हैं। आधिकारिक बिजली विभाग कभी फोन पर UPI पेमेंट नहीं मांगता।',
  },
  // Bank/SBI scam
  {
    pattern: /sbi officer|bank manager|head office|rbi|refund process/i,
    english:
      'Caller is impersonating a bank official. No bank ever asks you to enter your UPI PIN to RECEIVE money — this is a confirmed scam pattern.',
    hindi:
      'कॉल करने वाला बैंक अधिकारी बनकर धोखा दे रहा है। कोई भी बैंक पैसे प्राप्त करने के लिए UPI PIN दर्ज करने को नहीं कहता — यह एक पक्का फ्रॉड है।',
  },
  // AnyDesk/Screen sharing
  {
    pattern: /anydesk|teamviewer|quicksupport|screen mirroring|remote/i,
    english:
      'DANGER: Screen-sharing app is active! A remote attacker can see everything on your screen, including your UPI PIN. Never share your screen during financial transactions.',
    hindi:
      'खतरा! स्क्रीन शेयरिंग ऐप चालू है! हैकर आपकी स्क्रीन पर सब कुछ देख सकता है, UPI PIN भी। लेन-देन के दौरान कभी स्क्रीन शेयर न करें।',
  },
  // OTP/PIN sharing
  {
    pattern: /forward otp|share otp|enter pin|pin to receive|cashback/i,
    english:
      'You are being asked to share OTP or enter PIN to receive money. This is IMPOSSIBLE — UPI PIN is only needed to SEND money, never to receive it.',
    hindi:
      'आपसे OTP शेयर करने या PIN डालने को कहा जा रहा है। यह असंभव है — UPI PIN सिर्फ पैसे भेजने के लिए होता है, प्राप्त करने के लिए नहीं!',
  },
  // Authority impersonation
  {
    pattern: /trai|customs|police|cyber cell|cbi|income tax|ed officer|narcotics/i,
    english:
      'Caller claims to be from a government authority. Real officials never call demanding instant UPI payments — they send official written notices.',
    hindi:
      'कॉल करने वाला सरकारी अधिकारी होने का दावा कर रहा है। असली अधिकारी कभी फोन पर तुरंत UPI पेमेंट नहीं मांगते — वे लिखित नोटिस भेजते हैं।',
  },
  // Generic urgency
  {
    pattern: /urgent|immediate|last warning|final notice|within.*minute|account block/i,
    english:
      'High-urgency pressure tactics detected. Scammers create panic to prevent you from thinking clearly. Take a moment — real deadlines are never this immediate.',
    hindi:
      'जल्दबाज़ी और दबाव की चाल पकड़ी गई। ठग घबराहट पैदा करते हैं ताकि आप सोच न पाएं। रुकें — असली डेडलाइन कभी इतनी तुरंत नहीं होती।',
  },
];

/**
 * Generates a context-aware vernacular warning based on the risk analysis.
 * Matches detected keywords against known scam patterns and produces
 * tailored Hindi + English explanations.
 *
 * @param risk - The complete risk breakdown from the scoring engine
 * @returns VernacularWarning with bilingual text and recommended action
 */
export function generateWarning(risk: RiskBreakdown): VernacularWarning {
  const allSignals = [
    ...risk.detectedKeywords,
    ...risk.detectedApps,
  ].join(' ');

  const matchedEnglish: string[] = [];
  const matchedHindi: string[] = [];
  const triggers: string[] = [];

  for (const explanation of THREAT_EXPLANATIONS) {
    if (explanation.pattern.test(allSignals)) {
      matchedEnglish.push(explanation.english);
      matchedHindi.push(explanation.hindi);
      triggers.push(explanation.pattern.source);
    }
  }

  // Fallback if no specific pattern matched but score is high
  if (matchedEnglish.length === 0) {
    matchedEnglish.push(
      'Multiple suspicious signals detected during this transaction. Please verify the recipient independently before proceeding.'
    );
    matchedHindi.push(
      'इस लेन-देन में कई संदिग्ध संकेत मिले हैं। आगे बढ़ने से पहले प्राप्तकर्ता की स्वतंत्र रूप से जांच करें।'
    );
  }

  const english =
    `⚠️ WARNING — Transaction Frozen!\n\n` +
    matchedEnglish.join('\n\n') +
    `\n\n🛡️ Your money is safe. This transaction has been frozen by SurakshaPay. Risk Score: ${risk.totalScore}/100.`;

  const hindi =
    `⚠️ चेतावनी — लेन-देन रोक दिया गया!\n\n` +
    matchedHindi.join('\n\n') +
    `\n\n🛡️ आपका पैसा सुरक्षित है। सुरक्षापे ने यह लेन-देन रोक दिया है। जोखिम स्कोर: ${risk.totalScore}/100।`;

  return {
    english,
    hindi,
    triggers,
    recommendedAction:
      risk.detectedApps.length > 0
        ? 'IMMEDIATELY close all screen-sharing apps and do NOT enter your UPI PIN.'
        : 'Do NOT proceed with this payment. Verify the request by calling the organization directly using their official website number.',
  };
}

// ─── Web Speech API TTS Integration ──────────────────────────────────────────

/**
 * Speaks the warning aloud using the Web Speech API (speechSynthesis).
 *
 * @param text - The warning text to speak
 * @param lang - Language code: 'en' for English, 'hi' for Hindi
 *
 * Browser compatibility:
 *   - Chrome/Edge: Full Hindi support via Google TTS voices
 *   - Firefox: English only (Hindi may fall back to default voice)
 *   - Android WebView: Uses device TTS engine (supports Hindi)
 *
 * Production Android app would use:
 *   android.speech.tts.TextToSpeech with Locale("hi", "IN")
 */
export function speakWarning(text: string, lang: 'en' | 'hi'): void {
  // Cancel any ongoing speech
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang === 'hi' ? 'hi-IN' : 'en-IN';
    utterance.rate = 0.9;  // Slightly slower for clarity
    utterance.pitch = 1.0;
    utterance.volume = 1.0;

    // Try to find a matching voice
    const voices = window.speechSynthesis.getVoices();
    const targetLang = lang === 'hi' ? 'hi' : 'en';
    const matchingVoice = voices.find((v) =>
      v.lang.toLowerCase().startsWith(targetLang)
    );
    if (matchingVoice) {
      utterance.voice = matchingVoice;
    }

    window.speechSynthesis.speak(utterance);
  }
}

/**
 * Stops any ongoing speech synthesis.
 */
export function stopSpeaking(): void {
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.cancel();
  }
}

