// ─────────────────────────────────────────────────────────────────────────────
// SurakshaPay — Threat Interception State Machine
// ─────────────────────────────────────────────────────────────────────────────
//
// Defines the deterministic state transitions for the transaction
// interception pipeline. Each transition maps to a real-world event
// in the UPI payment lifecycle.
//
// State Diagram:
//
//   ┌──────┐   scenario    ┌───────────────────┐
//   │ IDLE │──────────────▶│ LISTENING_ANALYZING│
//   └──────┘   activated   └─────────┬─────────┘
//                                    │ user clicks "Pay"
//                          ┌─────────▼──────────┐
//                          │ TRANSACTION_INITIATED│
//                          └─────────┬──────────┘
//                     ┌──────────────┼──────────────┐
//                     │ score > 65   │              │ score ≤ 65
//              ┌──────▼───────┐              ┌─────▼────────────┐
//              │PRE_AUTH_FROZEN│              │TRANSACTION_PERMIT│
//              └──────┬───────┘              └──────────────────┘
//           ┌─────────┼──────────┐
//           │ abort   │          │ override
//   ┌───────▼────────┐   ┌──────▼───────────┐
//   │TRANS._ABORTED  │   │TRANS._PERMITTED  │
//   └────────────────┘   └──────────────────┘
//
// ─────────────────────────────────────────────────────────────────────────────

import type { InterceptionState } from '../types';
import { CRITICAL_THRESHOLD } from './riskEngine';

/** Valid state transitions with their trigger conditions */
interface StateTransition {
  from: InterceptionState;
  to: InterceptionState;
  trigger: string;
  condition?: (riskScore: number) => boolean;
}

const STATE_TRANSITIONS: StateTransition[] = [
  {
    from: 'IDLE',
    to: 'LISTENING_ANALYZING',
    trigger: 'SCENARIO_ACTIVATED',
  },
  {
    from: 'LISTENING_ANALYZING',
    to: 'TRANSACTION_INITIATED',
    trigger: 'PAYMENT_REQUESTED',
  },
  {
    from: 'TRANSACTION_INITIATED',
    to: 'PRE_AUTH_FROZEN',
    trigger: 'RISK_THRESHOLD_EXCEEDED',
    condition: (score) => score > CRITICAL_THRESHOLD,
  },
  {
    from: 'TRANSACTION_INITIATED',
    to: 'TRANSACTION_PERMITTED',
    trigger: 'RISK_ACCEPTABLE',
    condition: (score) => score <= CRITICAL_THRESHOLD,
  },
  {
    from: 'PRE_AUTH_FROZEN',
    to: 'TRANSACTION_ABORTED',
    trigger: 'USER_ABORT',
  },
  {
    from: 'PRE_AUTH_FROZEN',
    to: 'TRANSACTION_PERMITTED',
    trigger: 'USER_OVERRIDE',
  },
  // Reset transitions — any terminal state can go back to IDLE
  {
    from: 'TRANSACTION_ABORTED',
    to: 'IDLE',
    trigger: 'RESET',
  },
  {
    from: 'TRANSACTION_PERMITTED',
    to: 'IDLE',
    trigger: 'RESET',
  },
  {
    from: 'PRE_AUTH_FROZEN',
    to: 'IDLE',
    trigger: 'RESET',
  },
  {
    from: 'LISTENING_ANALYZING',
    to: 'IDLE',
    trigger: 'RESET',
  },
];

/**
 * Attempts a state transition and returns the new state.
 * Returns the current state unchanged if the transition is invalid.
 *
 * In a real Android app, this state machine would be driven by:
 *   - AccessibilityService events (detecting UPI PIN entry screens)
 *   - CallScreeningService callbacks (incoming call analysis)
 *   - BroadcastReceiver for SMS_RECEIVED events
 */
export function transition(
  currentState: InterceptionState,
  trigger: string,
  riskScore: number = 0
): InterceptionState {
  const validTransition = STATE_TRANSITIONS.find(
    (t) =>
      t.from === currentState &&
      t.trigger === trigger &&
      (t.condition ? t.condition(riskScore) : true)
  );

  return validTransition ? validTransition.to : currentState;
}

/**
 * Determines whether the current state should block PIN entry.
 * Used by the UI to decide whether to show the SurakshaLock overlay
 * instead of the PIN dialog.
 */
export function shouldBlockTransaction(
  state: InterceptionState,
  riskScore: number
): boolean {
  return (
    state === 'PRE_AUTH_FROZEN' ||
    (state === 'TRANSACTION_INITIATED' && riskScore > CRITICAL_THRESHOLD)
  );
}

/**
 * Returns a human-readable label for each state (used in the timeline).
 */
export function getStateLabel(state: InterceptionState): string {
  const labels: Record<InterceptionState, string> = {
    IDLE: '🟢 System Idle',
    LISTENING_ANALYZING: '🔍 Analyzing Signals...',
    TRANSACTION_INITIATED: '💳 Transaction Requested',
    PRE_AUTH_FROZEN: '🔒 Transaction FROZEN',
    TRANSACTION_ABORTED: '✅ Transaction Safely Aborted',
    TRANSACTION_PERMITTED: '💸 Transaction Permitted',
  };
  return labels[state];
}

