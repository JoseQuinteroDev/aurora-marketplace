/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        aurora: {
          // Quiet-luxury: near-black ink, warm bone, single pine accent.
          ink: '#0E0D0B',
          night: '#0E0D0B',
          charcoal: '#1A1813',
          mist: '#F4F1EA',
          pearl: '#FBF9F4',
          paper: '#FBF9F4',
          line: '#E4DECF',
          muted: '#8A857B',
          // Accent family — every legacy accent name repainted to pine so existing
          // templates turn green without per-file edits.
          pine: '#2A5A47',
          pinedeep: '#1F3D32',
          pinebright: '#3E7C63',
          amber: '#2A5A47',
          gold: '#3E7C63',
          emerald: '#2A5A47',
          ocean: '#3E7C63',
          iris: '#3E7C63',
          rose: '#C9756F'
        }
      },
      boxShadow: {
        premium: '0 18px 50px rgba(0, 0, 0, 0.38)',
        lift: '0 10px 28px rgba(0, 0, 0, 0.22)',
        glow: '0 0 0 1px rgba(62, 124, 99, 0.38)',
        innerline: 'inset 0 1px 0 rgba(255, 255, 255, 0.05)'
      },
      borderRadius: {
        ui: '3px',
        soft: '6px'
      },
      fontFamily: {
        sans: ['"Hanken Grotesk"', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'sans-serif'],
        display: ['"Cormorant Garamond"', 'Georgia', 'ui-serif', 'serif']
      },
      backgroundImage: {
        'aurora-radial': 'linear-gradient(180deg, #FBF9F4 0%, #F4F1EA 100%)',
        'aurora-dark-radial': 'linear-gradient(180deg, #16140F 0%, #0E0D0B 100%)'
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
