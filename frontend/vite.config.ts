import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// In development the UI runs on :5173 and forwards /api calls to the Spring Boot app on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: { environment: 'node' },
});
