import React, { useReducer, useCallback } from 'react';
import type {
  AppState,
  AppAction,
  ScamScenario,
  TimelineEvent,
} from './types';
import { computeRiskScore, CRITICAL_THRESHOLD } from './engine/riskEngine';
import { generateWarning } from './engine/vernacularAgent';
import { stopSpeaking } from './engine/vernacularAgent';
import Header from './components/Header';
import PhoneSimulator from './components/PhoneSimulator';
import UPIPaymentScreen from './components/UPIPaymentScreen';
import SurakshaLockOverlay from './components/SurakshaLockOverlay';
import ScamConsole from './components/ScamConsole';

// ─────────────────────────────────────────────────────────────────────────────
// SurakshaPay — Main Application Orchestrator
// ─────────────────────────────────────────────────────────────────────────────
//
// This component manages the entire demo flow:
//
// 1. User selects a scam scenario from the right panel
// 2. "Trigger Scenario" feeds transcript/device data into the risk engine
// 3. Risk gauges animate in real-time as analysis completes
// 4. User clicks "Pay Securely" on the phone simulator
// 5. If risk > 65 → SurakshaLock overlay freezes the transaction
// 6. If risk ≤ 65 → Normal PIN entry → Payment succeeds
//
// Architecture: useReducer state machine mirrors the engine's InterceptionState
// ─────────────────────────────────────────────────────────────────────────────

// ── Helper: unique timeline event creator ────────────────────────────────
let eventCounter = 0;
function createEvent(
  type: TimelineEvent['type'],
  message: string,
  detail?: string
): TimelineEvent {
  return {
    id: `evt-${++eventCounter}`,
    timestamp: new Date(),
    type,
    message,
    detail,
  };
}

// ── Initial State ────────────────────────────────────────────────────────
const initialState: AppState = {
  currentScenario: null,
  interceptionState: 'IDLE',
  riskBreakdown: null,
  warning: null,
  timeline: [],
  language: 'en',
  isAnalyzing: false,
  showPinDialog: false,
  showLockOverlay: false,
};

// ── Reducer ──────────────────────────────────────────────────────────────
function appReducer(state: AppState, action: AppAction): AppState {
  switch (action.type) {
    case 'SELECT_SCENARIO':
      return {
        ...initialState,
        language: state.language,
        currentScenario: action.scenario,
        timeline: [
          createEvent(
            'info',
            `Scenario loaded: ${action.scenario.name}`,
            `VPA: ${action.scenario.recipientVPA} | Amount: ₹${action.scenario.amount}`
          ),
        ],
      };

    case 'START_ANALYSIS':
      return {
        ...state,
        interceptionState: 'LISTENING_ANALYZING',
        isAnalyzing: true,
        timeline: [
          ...state.timeline,
          createEvent(
            'info',
            '🔍 Analysis started — scanning transcript, device state, VPA...',
            'Heuristic NLP engine + device state analyzer activated'
          ),
        ],
      };

    case 'ANALYSIS_COMPLETE':
      return {
        ...state,
        interceptionState: 'LISTENING_ANALYZING',
        isAnalyzing: false,
        riskBreakdown: action.risk,
        warning: action.warning,
        timeline: [
          ...state.timeline,
          createEvent(
            action.risk.totalScore > CRITICAL_THRESHOLD ? 'danger' : 'success',
            `Analysis complete — Risk Score: ${action.risk.totalScore}/100 (${action.risk.threatLevel})`,
            `NLP: ${action.risk.urgencyScore} | Device: ${action.risk.deviceRiskScore} | VPA: ${action.risk.vpaRiskScore} | Detected: ${action.risk.detectedKeywords.length} signals`
          ),
          ...(action.risk.detectedApps.length > 0
            ? [
                createEvent(
                  'danger' as const,
                  `🖥️ Dangerous apps detected: ${action.risk.detectedApps.join(', ')}`,
                  'Screen-sharing or remote access software is active'
                ),
              ]
            : []),
        ],
      };

    case 'INITIATE_PAYMENT':
      return {
        ...state,
        interceptionState: 'TRANSACTION_INITIATED',
        timeline: [
          ...state.timeline,
          createEvent(
            'warning',
            '💳 Payment initiated — evaluating pre-authorization gate...',
            `Threshold check: Score ${state.riskBreakdown?.totalScore ?? 0} vs. limit ${CRITICAL_THRESHOLD}`
          ),
        ],
      };

    case 'FREEZE_TRANSACTION':
      return {
        ...state,
        interceptionState: 'PRE_AUTH_FROZEN',
        showLockOverlay: true,
        timeline: [
          ...state.timeline,
          createEvent(
            'danger',
            '🚨 TRANSACTION FROZEN — SurakshaLock engaged!',
            `Risk ${state.riskBreakdown?.totalScore ?? 0} > threshold ${CRITICAL_THRESHOLD}. PIN entry blocked.`
          ),
        ],
      };

    case 'ABORT_TRANSACTION':
      stopSpeaking();
      return {
        ...state,
        interceptionState: 'TRANSACTION_ABORTED',
        showLockOverlay: false,
        timeline: [
          ...state.timeline,
          createEvent(
            'success',
            '✅ Transaction safely aborted by user!',
            `₹${state.currentScenario?.amount.toLocaleString('en-IN')} saved from potential fraud`
          ),
        ],
      };

    case 'PERMIT_TRANSACTION':
      stopSpeaking();
      return {
        ...state,
        interceptionState: 'TRANSACTION_PERMITTED',
        showLockOverlay: false,
        showPinDialog: false,
        timeline: [
          ...state.timeline,
          createEvent(
            state.riskBreakdown && state.riskBreakdown.totalScore > CRITICAL_THRESHOLD
              ? 'warning'
              : 'success',
            state.riskBreakdown && state.riskBreakdown.totalScore > CRITICAL_THRESHOLD
              ? '⚠️ User overrode SurakshaLock — transaction permitted at user\'s risk'
              : '✅ Transaction permitted — risk within acceptable range',
          ),
        ],
      };

    case 'RESET':
      stopSpeaking();
      return {
        ...initialState,
        language: state.language,
      };

    case 'TOGGLE_LANGUAGE':
      return {
        ...state,
        language: state.language === 'en' ? 'hi' : 'en',
      };

    case 'ADD_TIMELINE_EVENT':
      return {
        ...state,
        timeline: [...state.timeline, action.event],
      };

    default:
      return state;
  }
}

// ── Main App Component ───────────────────────────────────────────────────

const App: React.FC = () => {
  const [state, dispatch] = useReducer(appReducer, initialState);

  // ── Scenario Selection ─────────────────────────────────────────────
  const handleSelectScenario = useCallback((scenario: ScamScenario) => {
    dispatch({ type: 'SELECT_SCENARIO', scenario });
  }, []);

  // ── Trigger Scenario Analysis ──────────────────────────────────────
  const handleActivateScenario = useCallback(() => {
    if (!state.currentScenario) return;

    const scenario = state.currentScenario;
    dispatch({ type: 'START_ANALYSIS' });

    // Simulate progressive analysis with realistic timing
    // In production, this runs in real-time as the call/SMS is received
    setTimeout(() => {
      const risk = computeRiskScore(
        scenario.transcript + ' ' + (scenario.smsContent || ''),
        scenario.activeApps,
        scenario.recipientVPA,
        scenario.isVPAVerified
      );

      const warning =
        risk.totalScore > CRITICAL_THRESHOLD
          ? generateWarning(risk)
          : null;

      dispatch({ type: 'ANALYSIS_COMPLETE', risk, warning });
    }, 2000); // 2s simulated analysis time
  }, [state.currentScenario]);

  // ── Payment Flow ───────────────────────────────────────────────────
  const handlePay = useCallback(() => {
    dispatch({ type: 'INITIATE_PAYMENT' });

    // Pre-authorization gate check
    setTimeout(() => {
      if (
        state.riskBreakdown &&
        state.riskBreakdown.totalScore > CRITICAL_THRESHOLD
      ) {
        dispatch({ type: 'FREEZE_TRANSACTION' });
      } else {
        // For low-risk: the UPI screen will show PIN entry,
        // and on PIN completion it will call handlePay again
        // which triggers PERMIT_TRANSACTION
        dispatch({ type: 'PERMIT_TRANSACTION' });
      }
    }, 800);
  }, [state.riskBreakdown]);

  // ── Lock Overlay Actions ───────────────────────────────────────────
  const handleAbort = useCallback(() => {
    dispatch({ type: 'ABORT_TRANSACTION' });
  }, []);

  const handleOverride = useCallback(() => {
    dispatch({ type: 'PERMIT_TRANSACTION' });
  }, []);

  // ── Reset ──────────────────────────────────────────────────────────
  const handleReset = useCallback(() => {
    dispatch({ type: 'RESET' });
  }, []);

  // ── Language Toggle ────────────────────────────────────────────────
  const handleToggleLanguage = useCallback(() => {
    dispatch({ type: 'TOGGLE_LANGUAGE' });
  }, []);

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <Header
        language={state.language}
        onToggleLanguage={handleToggleLanguage}
        interceptionState={state.interceptionState}
      />

      <main className="flex-1 flex flex-col lg:flex-row gap-6 p-4 lg:p-6 overflow-hidden">
        {/* ── Left Panel: Phone Simulator ──────────────────────── */}
        <div className="flex-shrink-0 flex items-start justify-center pt-2">
          <PhoneSimulator>
            {/* UPI Payment Screen */}
            <UPIPaymentScreen
              scenario={state.currentScenario}
              interceptionState={state.interceptionState}
              riskBreakdown={state.riskBreakdown}
              isAnalyzing={state.isAnalyzing}
              onPay={handlePay}
              onReset={handleReset}
            />

            {/* SurakshaLock Overlay — renders ON TOP of the payment screen */}
            {state.showLockOverlay &&
              state.riskBreakdown &&
              state.warning && (
                <SurakshaLockOverlay
                  riskBreakdown={state.riskBreakdown}
                  warning={state.warning}
                  language={state.language}
                  onAbort={handleAbort}
                  onOverride={handleOverride}
                />
              )}
          </PhoneSimulator>
        </div>

        {/* ── Right Panel: Scam Console ────────────────────────── */}
        <div className="flex-1 min-w-0 overflow-hidden">
          <div className="h-full glass-card rounded-2xl p-4 lg:p-5 overflow-y-auto">
            <h2 className="text-sm font-bold text-white mb-4 flex items-center gap-2">
              <span className="text-gradient-danger">⚡ Scam Testing Console</span>
              <span className="text-[9px] px-2 py-0.5 rounded-full bg-violet-500/10 text-violet-300 border border-violet-500/20 font-semibold">
                DEMO MODE
              </span>
            </h2>
            <ScamConsole
              selectedScenario={state.currentScenario}
              interceptionState={state.interceptionState}
              riskBreakdown={state.riskBreakdown}
              isAnalyzing={state.isAnalyzing}
              timeline={state.timeline}
              onSelectScenario={handleSelectScenario}
              onActivate={handleActivateScenario}
              onReset={handleReset}
            />
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="px-6 py-2 border-t border-suraksha-border text-center">
        <p className="text-[9px] text-slate-600">
          SurakshaPay v1.0 — GRAVITON 2026 Hackathon Prototype | Track: AI / Agentic Systems / Real-World Impact | SDG 16: Peace, Justice & Strong Institutions
        </p>
      </footer>
    </div>
  );
};

export default App;

