import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(
      ['/api', '/hello', '/me'].map((path) => [
        path, { target: 'http://127.0.0.1:8080' },
      ]),
    ),
  },
});
