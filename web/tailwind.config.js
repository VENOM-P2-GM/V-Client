/** @type {import('tailwindcss').Config} */
export default {
  content: ['./client/index.html', './client/src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#05050a',
          900: '#0a0a14',
          850: '#0e0e1c',
          800: '#131324',
          700: '#1b1b31'
        },
        venom: 'rgb(var(--c1) / <alpha-value>)',
        venom2: 'rgb(var(--c2) / <alpha-value>)',
        acid: 'rgb(var(--c3) / <alpha-value>)'
      },
      fontFamily: {
        sans: ['Cairo', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace']
      },
      boxShadow: {
        glow: '0 0 45px -10px rgb(var(--c1) / 0.55)',
        'glow-acid': '0 0 45px -10px rgb(var(--c3) / 0.5)'
      }
    }
  },
  plugins: []
};
