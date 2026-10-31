import React from 'react';
import type { TimelineEvent } from '../types';

interface ThreatTimelineProps {
  events: TimelineEvent[];
}

/**
 * Real-time event timeline showing the analysis and interception flow.
 * Events are displayed in reverse chronological order.
 */
const ThreatTimeline: React.FC<ThreatTimelineProps> = ({ events }) => {
  if (events.length === 0) {
    return (
      <div className="text-center py-8">
        <p className="text-xs text-slate-600">
          No events yet. Activate a scenario to begin.
        </p>
      </div>
    );
  }

  const typeStyles: Record<string, { dot: string; text: string; bg: string }> = {
    info: {
      dot: 'bg-blue-500',
      text: 'text-blue-300',
      bg: 'bg-blue-500/5',
    },
    warning: {
      dot: 'bg-amber-500',
      text: 'text-amber-300',
      bg: 'bg-amber-500/5',
    },
    danger: {
      dot: 'bg-red-500 animate-pulse',
      text: 'text-red-300',
      bg: 'bg-red-500/5',
    },
    success: {
      dot: 'bg-emerald-500',
      text: 'text-emerald-300',
      bg: 'bg-emerald-500/5',
    },
  };

  return (
    <div className="space-y-1.5 max-h-[280px] overflow-y-auto pr-1">
      {[...events].reverse().map((event, i) => {
        const style = typeStyles[event.type] || typeStyles.info;
        return (
          <div
            key={event.id}
            className={`flex items-start gap-2.5 p-2 rounded-lg ${style.bg} animate-fade-in`}
            style={{ animationDelay: `${i * 50}ms` }}
          >
            {/* Timeline dot */}
            <div className={`w-2 h-2 rounded-full shrink-0 mt-1 ${style.dot}`} />

            <div className="flex-1 min-w-0">
              <p className={`text-[11px] font-medium leading-tight ${style.text}`}>
                {event.message}
              </p>
              {event.detail && (
                <p className="text-[9px] text-slate-500 mt-0.5 leading-relaxed">
                  {event.detail}
                </p>
              )}
              <p className="text-[8px] text-slate-600 mt-0.5 tabular-nums">
                {event.timestamp.toLocaleTimeString('en-IN', {
                  hour: '2-digit',
                  minute: '2-digit',
                  second: '2-digit',
                })}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default ThreatTimeline;

