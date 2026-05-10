/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        aurora: {
          ink: '#12100D',
          night: '#0C0A09',
          charcoal: '#1F2933',
          mist: '#F7F4EF',
          pearl: '#FEFCF8',
          paper: '#FFFFFF',
          line: '#E7E0D6',
          muted: '#625D55',
          ocean: '#1D4ED8',
          emerald: '#0F9F6E',
          amber: '#C98717',
          gold: '#B7791F',
          iris: '#5B5BD6',
          rose: '#D94663'
        }
      },
      boxShadow: {
        premium: '0 24px 80px rgba(18, 16, 13, 0.13)',
        lift: '0 18px 42px rgba(18, 16, 13, 0.12)',
        glow: '0 0 0 1px rgba(255,255,255,0.55), 0 24px 70px rgba(185, 115, 31, 0.18)',
        innerline: 'inset 0 1px 0 rgba(255,255,255,0.65)'
      },
      borderRadius: {
        ui: '8px',
        soft: '14px'
      },
      fontFamily: {
        sans: ['"DM Sans"', 'Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'sans-serif']
      },
      backgroundImage: {
        'aurora-radial': 'radial-gradient(circle at 20% 15%, rgba(201, 135, 23, 0.16), transparent 30rem), radial-gradient(circle at 85% 0%, rgba(29, 78, 216, 0.12), transparent 34rem), linear-gradient(135deg, #FEFCF8 0%, #F7F4EF 52%, #EEF6F1 100%)',
        'aurora-dark-radial': 'radial-gradient(circle at 20% 15%, rgba(201, 135, 23, 0.18), transparent 30rem), radial-gradient(circle at 85% 0%, rgba(91, 91, 214, 0.18), transparent 34rem), linear-gradient(135deg, #0C0A09 0%, #12100D 48%, #0B1512 100%)'
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-10px)' }
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' }
        },
        fadeUp: {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        }
      },
      animation: {
        float: 'float 6s ease-in-out infinite',
        shimmer: 'shimmer 1.8s infinite',
        fadeUp: 'fadeUp 500ms ease-out both'
      }
    }
  },
  plugins: []
};
