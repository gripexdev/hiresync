/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts,scss}'],
  important: true,
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#2E86AB',
          50:  '#e8f4f8',
          100: '#c5e3ef',
          200: '#9fd0e5',
          300: '#78bddb',
          400: '#59afd5',
          500: '#2E86AB',
          600: '#2677a0',
          700: '#1d6491',
          800: '#145283',
          900: '#0a3564',
        },
        accent: {
          DEFAULT: '#17A589',
          light: '#1ec9a5',
          dark: '#0f7a65',
        },
        dark: {
          DEFAULT: '#1B4F72',
          lighter: '#2d6898',
          deeper: '#0f2d42',
        },
        surface: '#F8FAFC',
        elevated: '#FFFFFF',
        border: '#E2E8F0',
        muted: '#94A3B8',
        success: '#10B981',
        warning: '#F59E0B',
        danger: '#EF4444',
        info: '#3B82F6',
      },
      fontFamily: {
        sans: ['Inter', 'Roboto', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        card:       '0 1px 3px rgba(0,0,0,0.07), 0 1px 2px -1px rgba(0,0,0,0.05)',
        'card-hover':'0 10px 25px -3px rgba(0,0,0,0.1), 0 4px 6px -4px rgba(0,0,0,0.07)',
        sidebar:    '2px 0 8px rgba(0,0,0,0.08)',
        modal:      '0 20px 60px rgba(0,0,0,0.15)',
      },
      animation: {
        'fade-in':  'fadeIn 0.3s ease-in-out',
        'slide-up': 'slideUp 0.3s ease-out',
      },
      keyframes: {
        fadeIn:  { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        slideUp: { '0%': { opacity: '0', transform: 'translateY(12px)' }, '100%': { opacity: '1', transform: 'translateY(0)' } },
      },
    },
  },
  plugins: [],
}
