/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      colors: {
        suraksha: {
          dark: '#0a0a1a',
          deeper: '#060612',
          card: '#111827',
          border: '#1e293b',
          accent: '#10b981',
          danger: '#ef4444',
          warning: '#f59e0b',
          info: '#3b82f6',
        },
      },
      animation: {
        'pulse-red': 'pulse-red 2s ease-in-out infinite',
        'shield-pulse': 'shield-pulse 1.5s ease-in-out infinite',
        'scan-line': 'scan-line 2s linear infinite',
        'slide-up': 'slide-up 0.5s ease-out',
        'fade-in': 'fade-in 0.3s ease-out',
        'glow-danger': 'glow-danger 2s ease-in-out infinite',
      },
      keyframes: {
        'pulse-red': {
          '0%, 100%': { boxShadow: '0 0 0 0 rgba(239, 68, 68, 0.7)' },
          '50%': { boxShadow: '0 0 0 20px rgba(239, 68, 68, 0)' },
        },
        'shield-pulse': {
          '0%, 100%': { transform: 'scale(1)', opacity: '1' },
          '50%': { transform: 'scale(1.15)', opacity: '0.8' },
        },
        'scan-line': {
          '0%': { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(400%)' },
        },
        'slide-up': {
          from: { transform: 'translateY(30px)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        'glow-danger': {
          '0%, 100%': { boxShadow: '0 0 15px 0 rgba(239, 68, 68, 0.3)' },
          '50%': { boxShadow: '0 0 30px 5px rgba(239, 68, 68, 0.6)' },
        },
      },
    },
  },
  plugins: [],
};

