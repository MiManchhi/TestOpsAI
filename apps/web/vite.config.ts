import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { buildContractValidators } from './src/app/contract-build';

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'frozen-contract-validators',
      resolveId(id) {
        if (id === 'virtual:testops-contracts') return '\0' + id;
      },
      load(id) {
        if (id === '\0virtual:testops-contracts') return buildContractValidators();
      },
    },
  ],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': process.env.TESTOPS_SERVER_URL ?? 'http://127.0.0.1:8080',
      '/actuator/health': process.env.TESTOPS_SERVER_URL ?? 'http://127.0.0.1:8080',
    },
  },
});
