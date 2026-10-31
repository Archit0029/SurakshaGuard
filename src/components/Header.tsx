import React from 'react';
import { Shield, Globe } from 'lucide-react';
import type { InterceptionState } from '../types';
import { getStateLabel } from '../engine/stateMachine';

interface HeaderProps {
  language: 'en' | 'hi';
  onToggleLanguage: () => void;
  interceptionState: InterceptionState;
}

/**
 * App header with SurakshaPay branding, state indicator, and language toggle.
 */
const Header: React.FC<HeaderProps> = ({
  language,
  onToggleLanguage,
  interceptionState,
}) => {
  const stateLabel = getStateLabel(interceptionState);
  const isDanger =
    interceptionState === 'PRE_AUTH_FROZEN' ||
    interceptionState === 'TRANSACTION_ABORTED';

  return (
    <header className="w-full px-6 py-3 flex items-center justify-between border-b border-suraksha-border bg-suraksha-dark/80 backdrop-blur-md sticky top-0 z-50">
      {/* Brand */}
      <div className="flex items-center gap-3">
        <div className="relative">
          <Shield
            className={`w-8 h-8 ${
              isDanger ? 'text-red-500 animate-shield-pulse' : 'text-emerald-500'
            }`}
            fill={isDanger ? 'rgba(239,68,68,0.2)' : 'rgba(16,185,129,0.15)'}
          />
        </div>
        <div>
          <h1 className="text-xl font-bold tracking-tight">
            <span className="text-gradient-brand">SurakshaPay</span>
          </h1>
          <p className="text-[10px] text-slate-500 font-medium tracking-widest uppercase">
            Real-Time UPI Phishing Interceptor
          </p>
        </div>
      </div>

      {/* Center — State Indicator */}
      <div
        className={`hidden md:flex items-center gap-2 px-4 py-1.5 rounded-full text-xs font-semibold
          ${
            isDanger
              ? 'bg-red-500/10 text-red-400 border border-red-500/30'
              : interceptionState === 'LISTENING_ANALYZING'
              ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
              : interceptionState === 'TRANSACTION_PERMITTED'
              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
              : 'bg-slate-800 text-slate-400 border border-slate-700'
          }`}
      >
        <span
          className={`w-2 h-2 rounded-full ${
            isDanger
              ? 'bg-red-500 animate-pulse'
              : interceptionState === 'LISTENING_ANALYZING'
              ? 'bg-amber-500 animate-pulse'
              : interceptionState === 'TRANSACTION_PERMITTED'
              ? 'bg-emerald-500'
              : 'bg-slate-500'
          }`}
        />
        {stateLabel}
      </div>

      {/* Right — Language Toggle + Hackathon Badge */}
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleLanguage}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-medium transition-colors"
          title="Toggle language"
        >
          <Globe className="w-3.5 h-3.5 text-blue-400" />
          {language === 'en' ? 'EN 🇬🇧' : 'हिं 🇮🇳'}
        </button>
        <div className="hidden lg:block px-3 py-1 rounded-full bg-violet-600/20 border border-violet-500/30 text-violet-300 text-[10px] font-bold tracking-wider">
          GRAVITON 2026
        </div>
      </div>
    </header>
  );
};

export default Header;

