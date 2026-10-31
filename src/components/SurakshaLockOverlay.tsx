import React from 'react';
import {
  ShieldAlert,
  ShieldOff,
  Volume2,
  VolumeX,
  X,
  AlertTriangle,
  Monitor,
  User,
  Zap,
} from 'lucide-react';
import type { RiskBreakdown, VernacularWarning } from '../types';
import { speakWarning, stopSpeaking } from '../engine/vernacularAgent';

interface SurakshaLockOverlayProps {
  riskBreakdown: RiskBreakdown;
  warning: VernacularWarning;
  language: 'en' | 'hi';
  onAbort: () => void;
  onOverride: () => void;
}

/**
 * SurakshaLock — Full-screen security overlay that freezes a suspicious transaction.
 *
 * This is the core protective mechanism of SurakshaPay. When the risk score
 * exceeds the critical threshold (65), this overlay immediately takes over
 * the payment screen, preventing PIN entry.
 *
 * Real Android implementation:
 *   - AccessibilityService would detect the UPI PIN entry Activity
 *   - A high-priority system overlay (TYPE_APPLICATION_OVERLAY) would block input
 *   - The overlay would request SYSTEM_ALERT_WINDOW permission
 *   - Touch events on the PIN dialog would be consumed by the overlay
 */
const SurakshaLockOverlay: React.FC<SurakshaLockOverlayProps> = ({
  riskBreakdown,
  warning,
  language,
  onAbort,
  onOverride,
}) => {
  const [isSpeaking, setIsSpeaking] = React.useState(false);

  const handleSpeak = () => {
    if (isSpeaking) {
      stopSpeaking();
      setIsSpeaking(false);
    } else {
      const text = language === 'hi' ? warning.hindi : warning.english;
      speakWarning(text, language);
      setIsSpeaking(true);
      // Auto-reset after estimated speech duration
      setTimeout(() => setIsSpeaking(false), 15000);
    }
  };

  const riskBars = [
    {
      label: 'NLP / Urgency Analysis',
      score: riskBreakdown.urgencyScore,
      weight: '35%',
      icon: <Zap className="w-3 h-3" />,
    },
    {
      label: 'Device / Screen Share',
      score: riskBreakdown.deviceRiskScore,
      weight: '35%',
      icon: <Monitor className="w-3 h-3" />,
    },
    {
      label: 'VPA Reputation',
      score: riskBreakdown.vpaRiskScore,
      weight: '30%',
      icon: <User className="w-3 h-3" />,
    },
  ];

  const getBarColor = (score: number) => {
    if (score <= 30) return 'bg-emerald-500';
    if (score <= 60) return 'bg-amber-500';
    return 'bg-red-500';
  };

  return (
    <div className="absolute inset-0 z-50 flex flex-col bg-black/95 backdrop-blur-sm animate-fade-in overflow-y-auto">
      {/* Pulsing danger header */}
      <div className="relative px-4 pt-4 pb-3 bg-gradient-to-b from-red-950/80 to-transparent">
        {/* Pulsing shield */}
        <div className="flex flex-col items-center mb-3">
          <div className="relative">
            <ShieldAlert className="w-14 h-14 text-red-500 animate-shield-pulse" />
            <div className="absolute inset-0 w-14 h-14 rounded-full animate-pulse-red" />
          </div>
          <h2 className="text-base font-black text-red-500 mt-2 tracking-tight">
            🚨 TRANSACTION FROZEN
          </h2>
          <p className="text-[10px] text-red-300/80 font-medium">
            SurakshaLock engaged — PIN entry blocked
          </p>
        </div>

        {/* Risk Score Badge */}
        <div className="flex justify-center">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-red-500/20 border border-red-500/40">
            <span className="text-xs text-red-300">Risk Score:</span>
            <span className="text-lg font-black text-red-500 tabular-nums">
              {riskBreakdown.totalScore}
            </span>
            <span className="text-xs text-red-400">/100</span>
          </div>
        </div>
      </div>

      {/* Risk Breakdown Bars */}
      <div className="px-4 py-3 space-y-2.5">
        <p className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider">
          Risk Breakdown
        </p>
        {riskBars.map((bar) => (
          <div key={bar.label}>
            <div className="flex items-center justify-between mb-1">
              <div className="flex items-center gap-1.5 text-[10px] text-slate-400">
                {bar.icon}
                {bar.label}
                <span className="text-slate-600">({bar.weight})</span>
              </div>
              <span className="text-[10px] text-slate-300 font-bold tabular-nums">
                {bar.score}/100
              </span>
            </div>
            <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full risk-bar-fill ${getBarColor(bar.score)}`}
                style={{ width: `${bar.score}%` }}
              />
            </div>
          </div>
        ))}
      </div>

      {/* Detected Threats */}
      {riskBreakdown.detectedKeywords.length > 0 && (
        <div className="px-4 pb-2">
          <p className="text-[10px] text-slate-500 font-semibold uppercase tracking-wider mb-1.5">
            Detected Signals
          </p>
          <div className="space-y-1 max-h-20 overflow-y-auto">
            {riskBreakdown.detectedKeywords.slice(0, 5).map((kw, i) => (
              <div
                key={i}
                className="text-[10px] text-red-300/90 bg-red-500/5 rounded px-2 py-0.5 border border-red-500/10"
              >
                {kw}
              </div>
            ))}
            {riskBreakdown.detectedKeywords.length > 5 && (
              <p className="text-[9px] text-slate-600">
                +{riskBreakdown.detectedKeywords.length - 5} more signals
              </p>
            )}
          </div>
        </div>
      )}

      {/* Vernacular Warning Message */}
      <div className="px-4 py-3">
        <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800">
          <div className="flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
            <p className="text-[11px] text-slate-300 leading-relaxed whitespace-pre-line">
              {language === 'hi' ? warning.hindi : warning.english}
            </p>
          </div>
        </div>
      </div>

      {/* Voice Alert Button */}
      <div className="px-4 pb-2">
        <button
          onClick={handleSpeak}
          className={`w-full py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition-all
            ${
              isSpeaking
                ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                : 'bg-blue-600/20 text-blue-400 border border-blue-500/30 hover:bg-blue-600/30'
            }`}
        >
          {isSpeaking ? (
            <>
              <VolumeX className="w-4 h-4" />
              Stop Voice Alert
            </>
          ) : (
            <>
              <Volume2 className="w-4 h-4" />
              🔊 {language === 'hi' ? 'हिंदी में सुनें' : 'Listen in English'}
            </>
          )}
        </button>
      </div>

      {/* Action Buttons */}
      <div className="px-4 pt-2 pb-4 space-y-2 mt-auto">
        <button
          onClick={onAbort}
          className="w-full py-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold flex items-center justify-center gap-2 transition-colors shadow-lg shadow-emerald-500/20"
        >
          <ShieldAlert className="w-4 h-4" />
          Cancel Transaction — Stay Safe
        </button>
        <button
          onClick={onOverride}
          className="w-full py-2.5 rounded-xl bg-transparent hover:bg-red-500/10 text-red-400/60 text-[10px] font-medium flex items-center justify-center gap-1.5 transition-colors border border-red-500/20"
        >
          <ShieldOff className="w-3 h-3" />
          I understand the risk — Proceed anyway
        </button>
      </div>
    </div>
  );
};

export default SurakshaLockOverlay;

