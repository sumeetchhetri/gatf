import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Served by the embedded Grizzly static handler under /app (see GatfConfigToolUtil).
// Maven overrides --outDir to target/classes/gatf-viewer; local `npm run build` -> dist/.
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
  },
});
