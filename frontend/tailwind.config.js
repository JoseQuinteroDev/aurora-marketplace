/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        aurora: {
          ink: '#111827',
          mist: '#F6F8FB',
          paper: '#FFFFFF',
          line: '#DDE4EE',
          muted: '#5B6472',
          graphite: '#1F2937',
          ocean: '#2563EB',
          emerald: '#10B981',
          amber: '#F59E0B',
          iris: '#6D5DF6',
          rose: '#F43F5E'
        }
      },
      boxShadow: {
        premium: '0 22px 70px rgba(17, 24, 39, 0.12)',
        lift: '0 14px 34px rgba(17, 24, 39, 0.10)'
      },
      borderRadius: {
        ui: '8px'
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'sans-serif']
      }
    }
  },
  plugins: []
};
