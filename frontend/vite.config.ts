import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The production build is written into the Spring Boot static resources, so the server never needs Node.js.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
