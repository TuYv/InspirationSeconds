import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/wx': 'http://localhost:8080'
    }
  },
  preview: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/wx': 'http://localhost:8080'
    }
  }
});
