/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#1e5eff',
          dark: '#1747c4',
          light: '#6f9bff',
        },
      },
    },
  },
  plugins: [],
};
