import React, { useState } from 'react';
import {
  ArrowLeft,
  User,
  IndianRupee,
  ShieldCheck,
  CheckCircle2,
  Lock,
  AlertTriangle,
} from 'lucide-react';
import type { ScamScenario, InterceptionState, RiskBreakdown } from '../types';

interface UPIPaymentScreenProps {
  scenario: ScamScenario | null;
  interceptionState: InterceptionState;
  riskBreakdown: RiskBreakdown | null;
  isAnalyzing: boolean;
  onPay: () => void;
  onReset: () => void;
}

/**
 * Realistic Indian UPI payment interface mockup.
 *
 * Simulates the payment flow of popular UPI apps (PhonePe / GPay / Paytm):
 * 1. Enter/confirm amount
 * 2. Verify recipient VPA
 * 3. Click "Pay Securely" → triggers risk evaluation
 * 4. If safe → mock PIN dialog → success
 * 5. If threat → SurakshaLock overlay takes over
 */
const UPIPaymentScreen: React.FC<UPIPaymentScreenProps> = ({
  scenario,
  interceptionState,
  riskBreakdown,
  isAnalyzing,
  onPay,
  onReset,
}) => {
  const [pinDigits, setPinDigits] = useState<string[]>([]);

  // No scenario selected — show idle screen
  if (!scenario) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center px-6">
        <ShieldCheck className="w-16 h-16 text-emerald-500/40 mb-4" />
        <h2 className="text-lg font-bold text-white mb-2">SurakshaPay Active</h2>
        <p className="text-xs text-slate-500 leading-relaxed">
          Select a scenario from the console to simulate a UPI transaction and see the phishing
          interceptor in action.
        </p>
      </div>
    );
  }

  // Transaction completed states
  if (interceptionState === 'TRANSACTION_PERMITTED') {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center px-6">
        <div className="w-16 h-16 rounded-full bg-emerald-500/20 flex items-center justify-center mb-4">
          <CheckCircle2 className="w-10 h-10 text-emerald-500" />
        </div>
        <h2 className="text-lg font-bold text-white mb-1">Payment Successful</h2>
        <p className="text-xs text-slate-400 mb-1">
          ₹{scenario.amount.toLocaleString('en-IN')} sent to {scenario.recipientName}
        </p>
        <p className="text-[10px] text-slate-600 mb-6">
          VPA: {scenario.recipientVPA}
        </p>
        <button
          onClick={onReset}
          className="px-4 py-2 rounded-lg bg-slate-800 text-xs text-slate-300 hover:bg-slate-700 transition-colors"
        >
          ← Back to Demo
        </button>
      </div>
    );
  }

  if (interceptionState === 'TRANSACTION_ABORTED') {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center px-6">
        <div className="w-16 h-16 rounded-full bg-emerald-500/20 flex items-center justify-center mb-4 animate-fade-in">
          <ShieldCheck className="w-10 h-10 text-emerald-500" />
        </div>
        <h2 className="text-lg font-bold text-emerald-400 mb-1">You're Safe! 🎉</h2>
        <p className="text-xs text-slate-400 mb-1">
          Transaction to <span className="text-red-400">{scenario.recipientVPA}</span> was blocked.
        </p>
        <p className="text-[10px] text-emerald-500/80 mb-6 font-medium">
          ₹{scenario.amount.toLocaleString('en-IN')} saved from potential fraud
        </p>
        <button
          onClick={onReset}
          className="px-4 py-2 rounded-lg bg-emerald-600 text-xs text-white hover:bg-emerald-500 transition-colors font-medium"
        >
          ← Try Another Scenario
        </button>
      </div>
    );
  }

  // Mock PIN entry (only shown when transaction is permitted with low risk)
  if (interceptionState === 'TRANSACTION_INITIATED' && riskBreakdown && riskBreakdown.totalScore <= 65) {
    const handlePinPress = (digit: string) => {
      if (pinDigits.length < 6) {
        const newDigits = [...pinDigits, digit];
        setPinDigits(newDigits);
        if (newDigits.length === 6) {
          setTimeout(() => {
            setPinDigits([]);
            onPay(); // This will trigger TRANSACTION_PERMITTED
          }, 500);
        }
      }
    };

    return (
      <div className="h-full flex flex-col bg-slate-950 animate-slide-up">
        {/* PIN Entry Header */}
        <div className="px-4 py-3 flex items-center gap-3 border-b border-slate-800">
          <Lock className="w-4 h-4 text-emerald-500" />
          <span className="text-xs font-semibold text-white">Enter UPI PIN</span>
        </div>

        <div className="flex-1 flex flex-col items-center justify-center px-6">
          <p className="text-xs text-slate-400 mb-4">
            Paying ₹{scenario.amount.toLocaleString('en-IN')} to {scenario.recipientName}
          </p>

          {/* PIN dots */}
          <div className="flex gap-3 mb-8">
            {Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className={`w-3 h-3 rounded-full border-2 transition-colors ${
                  i < pinDigits.length
                    ? 'bg-emerald-500 border-emerald-500'
                    : 'border-slate-600'
                }`}
              />
            ))}
          </div>

          {/* Numpad */}
          <div className="grid grid-cols-3 gap-3 w-48">
            {['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫'].map(
              (key) =>
                key === '' ? (
                  <div key="empty" />
                ) : (
                  <button
                    key={key}
                    onClick={() =>
                      key === '⌫'
                        ? setPinDigits((d) => d.slice(0, -1))
                        : handlePinPress(key)
                    }
                    className="w-14 h-14 rounded-full bg-slate-800 hover:bg-slate-700 text-white text-lg font-medium flex items-center justify-center transition-colors active:scale-95"
                  >
                    {key}
                  </button>
                )
            )}
          </div>
        </div>
      </div>
    );
  }

  // ─── Main Payment Screen ───────────────────────────────────────────────
  return (
    <div className="h-full flex flex-col bg-slate-950">
      {/* UPI App Header */}
      <div className="upi-gradient px-4 py-3 flex items-center gap-3">
        <ArrowLeft className="w-5 h-5 text-white/80" />
        <span className="text-sm font-semibold text-white flex-1">Send Money</span>
        <ShieldCheck className="w-4 h-4 text-white/60" />
      </div>

      {/* SMS Alert (if scenario has SMS) */}
      {scenario.smsContent && (
        <div className="mx-3 mt-3 p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/20 animate-slide-up">
          <div className="flex items-start gap-2">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-500 shrink-0 mt-0.5" />
            <div>
              <p className="text-[9px] text-amber-400 font-semibold mb-0.5">Incoming SMS</p>
              <p className="text-[10px] text-amber-200/80 leading-relaxed">
                {scenario.smsContent.length > 120
                  ? scenario.smsContent.slice(0, 120) + '...'
                  : scenario.smsContent}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Recipient Info */}
      <div className="px-4 pt-5 pb-3 flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center">
          <User className="w-5 h-5 text-slate-400" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-white truncate">
            {scenario.recipientName}
          </p>
          <p className="text-[10px] text-slate-500 font-mono truncate">
            {scenario.recipientVPA}
          </p>
        </div>
        {scenario.isVPAVerified ? (
          <span className="text-[9px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold">
            ✓ Verified
          </span>
        ) : (
          <span className="text-[9px] px-2 py-0.5 rounded-full bg-red-500/10 text-red-400 border border-red-500/20 font-semibold">
            ✗ Unverified
          </span>
        )}
      </div>

      <div className="border-t border-slate-800/50 mx-4" />

      {/* Amount Display */}
      <div className="flex-1 flex flex-col items-center justify-center px-4">
        <p className="text-[10px] text-slate-500 mb-2">Amount</p>
        <div className="flex items-center gap-1 mb-1">
          <IndianRupee className="w-7 h-7 text-white" />
          <span className="text-4xl font-bold text-white tabular-nums">
            {scenario.amount.toLocaleString('en-IN')}
          </span>
        </div>
        <p className="text-[10px] text-slate-600 mb-8">
          {scenario.id === 'normal' ? 'Grocery payment' : 'Requested by caller'}
        </p>

        {/* Active Screen Sharing Warning */}
        {scenario.activeApps.length > 0 && (
          <div className="w-full mb-4 p-2 rounded-lg bg-red-500/10 border border-red-500/20 animate-pulse-red">
            <p className="text-[10px] text-red-400 font-semibold text-center">
              ⚠️ Screen Sharing Active: {scenario.activeApps[0]}
            </p>
          </div>
        )}

        {/* Analyzing indicator */}
        {isAnalyzing && (
          <div className="w-full mb-4 overflow-hidden rounded-lg h-1 bg-slate-800">
            <div className="h-full w-full bg-gradient-to-r from-transparent via-emerald-500 to-transparent animate-scan" />
          </div>
        )}
      </div>

      {/* Pay Button */}
      <div className="px-4 pb-6">
        <button
          onClick={onPay}
          disabled={isAnalyzing}
          className={`w-full py-3.5 rounded-xl text-sm font-bold transition-all flex items-center justify-center gap-2
            ${
              isAnalyzing
                ? 'bg-slate-800 text-slate-500 cursor-not-allowed'
                : 'bg-gradient-to-r from-violet-600 to-purple-600 hover:from-violet-500 hover:to-purple-500 text-white shadow-lg shadow-violet-500/25 active:scale-[0.98]'
            }`}
        >
          {isAnalyzing ? (
            <>
              <div className="w-4 h-4 border-2 border-slate-500 border-t-emerald-400 rounded-full animate-spin" />
              Analyzing Security...
            </>
          ) : (
            <>
              <Lock className="w-4 h-4" />
              Pay Securely
            </>
          )}
        </button>
        <p className="text-center text-[9px] text-slate-600 mt-2">
          🛡️ Protected by SurakshaPay Interceptor
        </p>
      </div>
    </div>
  );
};

export default UPIPaymentScreen;

