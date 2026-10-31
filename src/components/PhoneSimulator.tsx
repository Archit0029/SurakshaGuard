import React from 'react';
import {
  Wifi,
  Signal,
  BatteryFull,
} from 'lucide-react';

interface PhoneSimulatorProps {
  children: React.ReactNode;
}

/**
 * Smartphone mockup frame that wraps the UPI payment screen.
 * Includes a realistic status bar, notch, and rounded device frame.
 *
 * Simulates an Android device running a UPI payment app.
 */
const PhoneSimulator: React.FC<PhoneSimulatorProps> = ({ children }) => {
  const time = new Date().toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

  return (
    <div className="flex flex-col items-center">
      {/* Device Label */}
      <div className="text-[10px] text-slate-500 font-medium mb-2 tracking-wider uppercase">
        Target Device Simulator
      </div>

      {/* Phone Frame */}
      <div className="relative w-[320px] h-[640px] rounded-[40px] border-[3px] border-slate-700 bg-black shadow-2xl shadow-black/50 overflow-hidden">
        {/* Notch */}
        <div className="phone-notch" />

        {/* Status Bar */}
        <div className="relative z-20 flex items-center justify-between px-8 pt-1.5 pb-1 text-white text-[10px] font-medium bg-black">
          <span>{time}</span>
          <div className="flex items-center gap-1.5">
            <Signal className="w-3 h-3" />
            <Wifi className="w-3 h-3" />
            <BatteryFull className="w-3.5 h-3.5" />
          </div>
        </div>

        {/* Screen Content */}
        <div className="relative h-[calc(100%-36px)] overflow-hidden bg-slate-950">
          {children}
        </div>

        {/* Bottom Navigation Bar */}
        <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/20 mx-24 mb-2 rounded-full" />
      </div>
    </div>
  );
};

export default PhoneSimulator;

