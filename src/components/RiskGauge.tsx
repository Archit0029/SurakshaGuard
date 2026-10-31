import React from 'react';
import type { ThreatLevel } from '../types';

interface RiskGaugeProps {
  score: number;
  label: string;
  size?: 'sm' | 'md' | 'lg';
  threatLevel?: ThreatLevel;
}

/**
 * Animated circular SVG gauge that visualizes a risk score from 0-100.
 * Color transitions from green → yellow → orange → red as risk increases.
 */
const RiskGauge: React.FC<RiskGaugeProps> = ({
  score,
  label,
  size = 'md',
  threatLevel,
}) => {
  const dimensions = { sm: 80, md: 110, lg: 150 };
  const dim = dimensions[size];
  const strokeWidth = size === 'sm' ? 6 : size === 'md' ? 8 : 10;
  const radius = (dim - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (score / 100) * circumference;

  // Color based on score
  const getColor = (s: number): string => {
    if (s <= 20) return '#10b981'; // emerald
    if (s <= 40) return '#22c55e'; // green
    if (s <= 55) return '#f59e0b'; // amber
    if (s <= 65) return '#f97316'; // orange
    return '#ef4444'; // red
  };

  const color = getColor(score);
  const textSize = size === 'sm' ? 'text-lg' : size === 'md' ? 'text-2xl' : 'text-4xl';
  const labelSize = size === 'sm' ? 'text-[9px]' : 'text-[10px]';

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="relative" style={{ width: dim, height: dim }}>
        <svg width={dim} height={dim} className="transform -rotate-90">
          {/* Background track */}
          <circle
            cx={dim / 2}
            cy={dim / 2}
            r={radius}
            fill="none"
            stroke="rgba(30, 41, 59, 0.5)"
            strokeWidth={strokeWidth}
          />
          {/* Animated fill arc */}
          <circle
            cx={dim / 2}
            cy={dim / 2}
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            style={{
              transition: 'stroke-dashoffset 1.2s cubic-bezier(0.4, 0, 0.2, 1), stroke 0.5s ease',
              filter: `drop-shadow(0 0 6px ${color}40)`,
            }}
          />
        </svg>
        {/* Center score text */}
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span
            className={`${textSize} font-bold tabular-nums`}
            style={{ color }}
          >
            {score}
          </span>
          {size !== 'sm' && (
            <span className="text-[9px] text-slate-500 font-medium">/ 100</span>
          )}
        </div>
      </div>
      <span className={`${labelSize} text-slate-400 font-medium text-center leading-tight`}>
        {label}
      </span>
      {threatLevel && size !== 'sm' && (
        <span
          className={`text-[9px] px-2 py-0.5 rounded-full font-bold tracking-wider
            ${
              threatLevel === 'SAFE'
                ? 'bg-emerald-500/10 text-emerald-400'
                : threatLevel === 'LOW'
                ? 'bg-green-500/10 text-green-400'
                : threatLevel === 'MEDIUM'
                ? 'bg-amber-500/10 text-amber-400'
                : threatLevel === 'HIGH'
                ? 'bg-orange-500/10 text-orange-400'
                : 'bg-red-500/10 text-red-400'
            }`}
        >
          {threatLevel}
        </span>
      )}
    </div>
  );
};

export default RiskGauge;

