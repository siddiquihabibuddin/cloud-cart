/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}"
  ],
  theme: {
    extend: {
      colors: {
        'cc-violet': '#7c3aed',
        'cc-electric': '#6366f1',
        'cc-orange': '#f97316',
        'cc-cyan': '#06b6d4',
        'cc-emerald': '#10b981',
      }
    }
  },
  plugins: []
}
