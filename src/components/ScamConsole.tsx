import React from 'react';
import {
  ShoppingCart,
  Zap,
  Monitor,
  Play,
  RotateCcw,
  MessageSquareWarning,
  Phone,
  Smartphone,
  Activity,
} from 'lucide-react';
import type { ScamScenario, InterceptionState, RiskBreakdown, TimelineEvent } from '../types';
import { SCENARIOS } from '../data/scenarios';
import RiskGauge from './RiskGauge';
import ThreatTimeline from './ThreatTimeline';

interface ScamConsoleProps {
  selectedScenario: ScamScenario | null;
  interceptionState: InterceptionState;
  riskBreakdown: RiskBreakdown | null;
  isAnalyzing: boolean;
  timeline: TimelineEvent[];
  onSelectScenario: (scenario: ScamScenario) => void;
  onActivate: () => void;
  onReset: () => void;
}

const ICON_MAP: Record<string, React.ReactNode> = {
  ShoppingCart: <ShoppingCart className="w-5 h-5" />,
  Zap: <Zap className="w-5 h-5" />,
  Monitor: <Monitor className="w-5 h-5" />,
};

/**
 * Scam Testing Console — Right panel of the split-screen dashboard.
 *
 * Provides controls to:
 * 1. Select from pre-built scam scenarios
 * 2. Activate the scenario (feeds data to the risk engine)
 * 3. View real-time risk gauges during analysis
 * 4. Monitor the event timeline
 * 5. Reset the demo
 */
const ScamConsole: React.FC<ScamConsoleProps> = ({
  selectedScenario,
  interceptionState,
  riskBreakdown,
  isAnalyzing,
  timeline,
  onSelectScenario,
  onActivate,
  onReset,
}) => {
  const canActivate =
    selectedScenario &&
    (interceptionState === 'IDLE' || interceptionState === 'TRANSACTION_ABORTED' || interceptionState === 'TRANSACTION_PERMITTED');

  const canReset = interceptionState !== 'IDLE';

  return (
    <div className="h-full flex flex-col gap-4 overflow-y-auto pr-1">
      {/* ── Section: Scenario Selector ─────────────────────────── */}
      <div>
        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2.5 flex items-center gap-2">
          <MessageSquareWarning className="w-3.5 h-3.5 text-violet-400" />
          Scenario Selector
        </h3>
        <div className="space-y-2">
          {SCENARIOS.map((scenario) => {
            const isSelected = selectedScenario?.id === scenario.id;
            const colorClass =
              scenario.threatColor === 'green'
                ? 'border-emerald-500/30 bg-emerald-500/5'
                : 'border-red-500/30 bg-red-500/5';
            const selectedClass = isSelected
              ? colorClass
              : 'border-slate-800 bg-slate-900/50 hover:bg-slate-800/50';

            return (
              <button
                key={scenario.id}
                onClick={() => onSelectScenario(scenario)}
                className={`w-full text-left p-3 rounded-xl border transition-all ${selectedClass} ${
                  isSelected ? 'ring-1 ring-offset-0 ring-offset-transparent' : ''
                } ${isSelected && scenario.threatColor === 'green' ? 'ring-emerald-500/40' : ''} ${
                  isSelected && scenario.threatColor !== 'green' ? 'ring-red-500/40' : ''
                }`}
              >
                <div className="flex items-start gap-2.5">
                  <div
                    className={`shrink-0 p-1.5 rounded-lg ${
                      scenario.threatColor === 'green'
                        ? 'bg-emerald-500/10 text-emerald-400'
                        : 'bg-red-500/10 text-red-400'
                    }`}
                  >
                    {ICON_MAP[scenario.icon]}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold text-white truncate">
                      {scenario.name}
                    </p>
                    <p className="text-[10px] text-slate-500 leading-relaxed mt-0.5 line-clamp-2">
                      {scenario.description}
                    </p>
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Section: Scenario Details ──────────────────────────── */}
      {selectedScenario && (
        <div className="glass-card rounded-xl p-3">
          <h4 className="text-[10px] text-slate-500 font-bold uppercase tracking-wider mb-2">
            Scenario Details
          </h4>

          {/* Caller / SMS info */}
          {selectedScenario.callerID && (
            <div className="flex items-center gap-2 mb-1.5">
              <Phone className="w-3 h-3 text-red-400" />
              <span className="text-[10px] text-slate-400">
                Caller: <span className="text-red-300">{selectedScenario.callerID}</span>
              </span>
            </div>
          )}
          {selectedScenario.activeApps.length > 0 && (
            <div className="flex items-center gap-2 mb-1.5">
              <Smartphone className="w-3 h-3 text-red-400" />
              <span className="text-[10px] text-red-300 font-semibold">
                ⚠ {selectedScenario.activeApps.join(', ')}
              </span>
            </div>
          )}

          {/* Transcript preview */}
          <div className="mt-2 p-2 rounded-lg bg-slate-950/80 border border-slate-800 max-h-24 overflow-y-auto">
            <p className="text-[9px] text-slate-500 font-semibold mb-1 uppercase">Transcript</p>
            <p className="text-[10px] text-slate-400 leading-relaxed whitespace-pre-line">
              {selectedScenario.transcript.length > 200
                ? selectedScenario.transcript.slice(0, 200) + '...'
                : selectedScenario.transcript}
            </p>
          </div>
        </div>
      )}

      {/* ── Section: Action Buttons ────────────────────────────── */}
      <div className="flex gap-2">
        <button
          onClick={onActivate}
          disabled={!canActivate}
          className={`flex-1 py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition-all
            ${
              canActivate
                ? 'bg-gradient-to-r from-violet-600 to-indigo-600 hover:from-violet-500 hover:to-indigo-500 text-white shadow-lg shadow-violet-500/20'
                : 'bg-slate-800 text-slate-500 cursor-not-allowed'
            }`}
        >
          <Play className="w-3.5 h-3.5" />
          Trigger Scenario
        </button>
        <button
          onClick={onReset}
          disabled={!canReset}
          className={`px-4 py-2.5 rounded-xl text-xs font-bold flex items-center justify-center gap-2 transition-all
            ${
              canReset
                ? 'bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700'
                : 'bg-slate-900 text-slate-600 cursor-not-allowed border border-slate-800'
            }`}
        >
          <RotateCcw className="w-3.5 h-3.5" />
          Reset
        </button>
      </div>

      {/* ── Section: Real-Time Risk Gauges ─────────────────────── */}
      {riskBreakdown && (
        <div className="glass-card rounded-xl p-3">
          <h4 className="text-[10px] text-slate-500 font-bold uppercase tracking-wider mb-3 flex items-center gap-2">
            <Activity className="w-3 h-3 text-amber-400" />
            Real-Time Risk Analysis
          </h4>
          <div className="flex items-center justify-around">
            <RiskGauge
              score={riskBreakdown.totalScore}
              label="Total Risk"
              size="md"
              threatLevel={riskBreakdown.threatLevel}
            />
            <div className="flex flex-col gap-3">
              <RiskGauge
                score={riskBreakdown.urgencyScore}
                label="NLP / Text"
                size="sm"
              />
              <RiskGauge
                score={riskBreakdown.deviceRiskScore}
                label="Device"
                size="sm"
              />
              <RiskGauge
                score={riskBreakdown.vpaRiskScore}
                label="VPA"
                size="sm"
              />
            </div>
          </div>
        </div>
      )}

      {/* ── Section: Event Timeline ────────────────────────────── */}
      <div>
        <h4 className="text-[10px] text-slate-500 font-bold uppercase tracking-wider mb-2 flex items-center gap-2">
          <Activity className="w-3 h-3 text-blue-400" />
          Event Timeline
        </h4>
        <ThreatTimeline events={timeline} />
      </div>
    </div>
  );
};

export default ScamConsole;

