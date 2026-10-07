import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// This package is ESM ("type": "module"), so __dirname does not exist here.
// Resolving against import.meta.url keeps the entry paths absolute regardless of
// which directory npm was run from.
const here = fileURLToPath(new URL('.', import.meta.url));

// /api is proxied to Spring Boot, so the browser only ever talks to one origin
// in development and no CORS preflight is involved.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Two documents, not one app with a route.
    //
    // The dashboard is a different job for a different person: a dense worklist
    // rather than a feed. Keeping it a separate entry means a resident never
    // downloads it, and the two layouts cannot quietly grow into each other.
    // Dev needs nothing extra - Vite serves any .html in the root directly - but
    // `vite build` only emits what is listed here.
    rollupOptions: {
      input: {
        main: `${here}index.html`,
        admin: `${here}admin.html`,
      },
    },
  },
});
